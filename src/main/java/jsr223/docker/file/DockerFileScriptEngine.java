/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package jsr223.docker.file;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.script.*;

import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.ow2.proactive.scheduler.common.SchedulerConstants;
import org.ow2.proactive.scheduler.task.SchedulerVars;

import com.google.common.io.Files;

import jsr223.docker.compose.DockerComposeScriptEngine;
import jsr223.docker.compose.bindings.MapBindingsAdder;
import jsr223.docker.compose.bindings.StringBindingsAdder;
import jsr223.docker.compose.file.write.ConfigurationFileWriter;
import jsr223.docker.compose.utils.Log4jConfigurationLoader;
import jsr223.docker.compose.yaml.VariablesReplacer;
import lombok.extern.log4j.Log4j;
import processbuilder.SingletonProcessBuilderFactory;
import processbuilder.utils.ProcessBuilderUtilities;


@Log4j
public class DockerFileScriptEngine extends AbstractScriptEngine {

    private static final String DOCKER_HOST_PROPERTY_NAME = "DOCKER_HOST";

    // generic information used to define actions to execute
    public final static String DOCKER_ACTIONS_GI = "docker-actions";

    public final static String DOCKER_IMAGE_TAG_GI = "docker-image-tag";

    public final static String DOCKER_CONTAINER_TAG_GI = "docker-container-tag";

    public final static String DOCKER_ACTIONS_DEFAULT = "build,run,stop,rmi";

    private ProcessBuilderUtilities processBuilderUtilities = new ProcessBuilderUtilities();

    private VariablesReplacer variablesReplacer = new VariablesReplacer();

    private ConfigurationFileWriter configurationFileWriter = new ConfigurationFileWriter();

    private StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

    private DockerFileCommandCreator dockerFileCommandCreator = new DockerFileCommandCreator();

    private Log4jConfigurationLoader log4jConfigurationLoader = new Log4jConfigurationLoader();

    private Process processRun = null;

    private Process processBuild = null;

    private Process processExec = null;

    public static final String DEFAULT_IMAGE_NAME = "image";

    public static final String DEFAULT_CONTAINER_NAME = "container";

    private String imageTagName = DEFAULT_IMAGE_NAME;

    private String containerTagName = DEFAULT_CONTAINER_NAME;

    private String[] dockerFileCommand = null;

    private String[] dockerRunCommand = null;

    private String[] dockerExecCommand = null;

    private String[] dockerStopCommand = null;

    private String[] dockerRMCommand = null;

    private String[] dockerRMICommand = null;

    private boolean imageCreated = false;

    private boolean containerStarted = false;

    private File dockerfile = null;

    private File directory = null;

    private Bindings bindings = null;

    private static long loggerId = 0;

    private Logger engineLogger;

    private Appender engineLoggerAppender;

    private Set<String> dockerActions = new HashSet<>();

    public DockerFileScriptEngine() {
        // This is the entry-point of the script engine
        log4jConfigurationLoader.loadLog4jConfiguration();
    }

    private void initLogger(ScriptContext context) {
        engineLogger = Logger.getLogger(DockerComposeScriptEngine.class.getSimpleName() + loggerId++);
        engineLoggerAppender = new WriterAppender(new SimpleLayout(), context.getErrorWriter());
        engineLogger.addAppender(engineLoggerAppender);
    }

    private void cleanLogger() {
        if (engineLogger != null && engineLoggerAppender != null) {
            engineLogger.removeAppender(engineLoggerAppender);
            engineLoggerAppender.close();
            engineLogger = null;
            engineLoggerAppender = null;
        }
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        initLogger(context);
        updateDockerActions(context);
        updateImageTagName(context);
        updateContainerTagName(context);

        bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);

        // Create docker file command - a simple docker build command 
        dockerFileCommand = dockerFileCommandCreator.createDockerBuildExecutionCommand(imageTagName, bindings);

        // Create docker run command - a simple docker run command 
        dockerRunCommand = dockerFileCommandCreator.createDockerRunExecutionCommand(containerTagName,
                                                                                    imageTagName,
                                                                                    bindings);

        // Create docker run command - a simple docker run command
        dockerExecCommand = dockerFileCommandCreator.createDockerExecExecutionCommand(containerTagName, bindings);

        // Create a process builder for building image
        ProcessBuilder processBuilderBuild = SingletonProcessBuilderFactory.getInstance()
                                                                           .getProcessBuilder(dockerFileCommand);

        // Create a process builder for running image
        ProcessBuilder processBuilderRun = SingletonProcessBuilderFactory.getInstance()
                                                                         .getProcessBuilder(dockerRunCommand);

        // Create a process builder for running image
        ProcessBuilder processBuilderExec = SingletonProcessBuilderFactory.getInstance()
                                                                          .getProcessBuilder(dockerExecCommand);

        // Use process builder environment and fill it with environment variables
        Map<String, String> variablesMap = processBuilderBuild.environment();

        // Add string bindings as environment variables
        stringBindingsAdder.addBindingToStringMap(bindings, variablesMap);

        String localSpace = null;
        if (bindings.containsKey(SchedulerConstants.DS_SCRATCH_BINDING_NAME)) {
            localSpace = (String) bindings.get(SchedulerConstants.DS_SCRATCH_BINDING_NAME);
        }

        if (localSpace != null) {
            directory = new File(localSpace);
        } else {
            directory = Files.createTempDir();
        }
        processBuilderBuild.directory(directory);

        // Add DOCKER_HOST variable to execution environment
        variablesMap.put(DOCKER_HOST_PROPERTY_NAME, DockerFilePropertyLoader.getInstance().getDockerHost());

        // Replace variables in configuration file
        String scriptReplacedVariables = variablesReplacer.replaceVariables(script, variablesMap);

        Thread shutdownHook = null;

        try {

            dockerfile = configurationFileWriter.forceFileToDisk(scriptReplacedVariables,
                                                                 (new File(directory,
                                                                           dockerFileCommandCreator.FILENAME)).getAbsolutePath());
            engineLogger.info("Docker file " + dockerfile + " created.");

            shutdownHook = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        handleShutdown(context);
                    } catch (ScriptException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            int exitValue = 0;

            if (dockerActions.contains(DockerFileCommandCreator.BUILD_ARGUMENT)) {
                exitValue = runDockerBuildCommand(context, processBuilderBuild);
            } else {
                // assume that image is already built on this machine
                imageCreated = true;
            }

            if (dockerActions.contains(DockerFileCommandCreator.RUN_ARGUMENT)) {
                exitValue = runDockerRunCommand(context, processBuilderRun);
            } else {
                // assume that conatiner is already started on this machine
                containerStarted = true;
            }

            if (dockerActions.contains(DockerFileCommandCreator.EXEC_ARGUMENT)) {
                exitValue = runDockerExecCommand(context, processBuilderExec);
            }

            return exitValue;
        } catch (IOException e) {
            engineLogger.warn("Failed to execute Docker File.", e);
        } catch (InterruptedException e) {
            engineLogger.info("Container execution interrupted. " + e.getMessage());
        } finally {

            handleShutdown(context);
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }

        return null;
    }

    private int runDockerRunCommand(ScriptContext context, ProcessBuilder processBuilderRun)
            throws IOException, InterruptedException, ScriptException {
        engineLogger.info("Running command: " + processBuilderRun.command());

        // Start process run
        processRun = processBuilderRun.start();

        containerStarted = true;

        // Attach streams
        processBuilderUtilities.attachStreamsToProcess(processRun,
                                                       context.getWriter(),
                                                       context.getErrorWriter(),
                                                       context.getReader());

        // Wait for process to exit
        int exitValueRun = processRun.waitFor();

        processRun = null;

        if (exitValueRun != 0) {
            throw new ScriptException("Docker Run failed with exit code " + exitValueRun);
        }
        return exitValueRun;
    }

    private int runDockerExecCommand(ScriptContext context, ProcessBuilder processBuilderExec)
            throws IOException, InterruptedException, ScriptException {
        engineLogger.info("Running command: " + processBuilderExec.command());

        // Start process run
        processExec = processBuilderExec.start();

        // Attach streams
        processBuilderUtilities.attachStreamsToProcess(processExec,
                                                       context.getWriter(),
                                                       context.getErrorWriter(),
                                                       context.getReader());

        // Wait for process to exit
        int exitValueExec = processExec.waitFor();

        processExec = null;

        if (exitValueExec != 0) {
            throw new ScriptException("Docker Exec failed with exit code " + exitValueExec);
        }
        return exitValueExec;
    }

    private int runDockerBuildCommand(ScriptContext context, ProcessBuilder processBuilderBuild)
            throws IOException, InterruptedException, ScriptException {
        engineLogger.info("Running command: " + processBuilderBuild.command());
        // Start process build
        processBuild = processBuilderBuild.start();

        imageCreated = true;

        // Attach streams
        processBuilderUtilities.attachStreamsToProcess(processBuild,
                                                       context.getWriter(),
                                                       context.getErrorWriter(),
                                                       context.getReader());

        // Wait for process build to exit
        int exitValueBuild = processBuild.waitFor();

        if (exitValueBuild != 0) {
            throw new ScriptException("Docker File Build failed with exit code " + exitValueBuild);
        }

        processBuild = null;
        return exitValueBuild;
    }

    private void updateImageTagName(ScriptContext context) {
        if (context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(SchedulerConstants.VARIABLES_BINDING_NAME)) {
            Map<String, Serializable> variables = (Map<String, Serializable>) context.getBindings(ScriptContext.ENGINE_SCOPE)
                                                                                     .get(SchedulerConstants.VARIABLES_BINDING_NAME);
            if (variables.containsKey(SchedulerVars.PA_JOB_ID.name()) &&
                variables.containsKey(SchedulerVars.PA_TASK_ID.name())) {
                imageTagName = DEFAULT_IMAGE_NAME + "_" + variables.get(SchedulerVars.PA_JOB_ID.name()) + "t" +
                               variables.get(SchedulerVars.PA_TASK_ID.name());
            }
        }
        if (context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(SchedulerConstants.GENERIC_INFO_BINDING_NAME)) {
            Map<String, String> genericInfo = (Map<String, String>) context.getBindings(ScriptContext.ENGINE_SCOPE)
                                                                           .get(SchedulerConstants.GENERIC_INFO_BINDING_NAME);
            if (genericInfo.containsKey(DOCKER_IMAGE_TAG_GI)) {
                imageTagName = genericInfo.get(DOCKER_IMAGE_TAG_GI);
            }
        }
    }

    private void updateDockerActions(ScriptContext context) {
        dockerActions = new HashSet<>(Arrays.asList(DOCKER_ACTIONS_DEFAULT.split("\\s*,\\s*")));
        if (context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(SchedulerConstants.GENERIC_INFO_BINDING_NAME)) {
            Map<String, String> genericInfo = (Map<String, String>) context.getBindings(ScriptContext.ENGINE_SCOPE)
                                                                           .get(SchedulerConstants.GENERIC_INFO_BINDING_NAME);
            if (genericInfo.containsKey(DOCKER_ACTIONS_GI)) {
                dockerActions = new HashSet<>(Arrays.asList(genericInfo.get(DOCKER_ACTIONS_GI).split("\\s*,\\s*")));
            }
        }
    }

    private void updateContainerTagName(ScriptContext context) {
        if (context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(SchedulerConstants.VARIABLES_BINDING_NAME)) {
            Map<String, Serializable> variables = (Map<String, Serializable>) context.getBindings(ScriptContext.ENGINE_SCOPE)
                                                                                     .get(SchedulerConstants.VARIABLES_BINDING_NAME);
            if (variables.containsKey(SchedulerVars.PA_JOB_ID.name()) &&
                variables.containsKey(SchedulerVars.PA_TASK_ID.name())) {
                containerTagName = DEFAULT_CONTAINER_NAME + "_" + variables.get(SchedulerVars.PA_JOB_ID.name()) + "t" +
                                   variables.get(SchedulerVars.PA_TASK_ID.name());
            }
        }
        if (context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(SchedulerConstants.GENERIC_INFO_BINDING_NAME)) {
            Map<String, String> genericInfo = (Map<String, String>) context.getBindings(ScriptContext.ENGINE_SCOPE)
                                                                           .get(SchedulerConstants.GENERIC_INFO_BINDING_NAME);
            if (genericInfo.containsKey(DOCKER_CONTAINER_TAG_GI)) {
                containerTagName = genericInfo.get(DOCKER_CONTAINER_TAG_GI);
            }
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {

        StringWriter stringWriter = new StringWriter();

        try {
            ProcessBuilderUtilities.pipe(reader, stringWriter);
        } catch (IOException e) {
            engineLogger.warn("Failed to convert Reader into StringWriter. Not possible to execute Docker File script.");
            engineLogger.debug("Filed to convert Reader into StringWriter. Not possible to execute Docker File script.",
                               e);
        }

        return eval(stringWriter.toString(), context);
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return new DockerFileScriptEngineFactory();
    }

    private void stopAndRemoveContainer(String containerTagName, ScriptContext context) throws ScriptException {

        // Create docker stop container command - a simple docker stop command 
        dockerStopCommand = dockerFileCommandCreator.createDockerStopExecutionCommand(containerTagName, bindings);

        // Create docker remove container command - a simple docker rm command 
        dockerRMCommand = dockerFileCommandCreator.createDockerRemoveExecutionCommand(containerTagName, bindings);

        // Create a process builder for stopping container
        ProcessBuilder processBuilderStop = SingletonProcessBuilderFactory.getInstance()
                                                                          .getProcessBuilder(dockerStopCommand);

        // Create a process builder for removing container
        ProcessBuilder processBuilderRM = SingletonProcessBuilderFactory.getInstance()
                                                                        .getProcessBuilder(dockerRMCommand);

        //build processStop
        int exitValue = 0;
        Process processStop;
        try {
            engineLogger.info("Running command: " + processBuilderStop.command());
            processStop = processBuilderStop.start();

            processBuilderUtilities.attachStreamsToProcess(processStop,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());
            exitValue = processStop.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace(new PrintWriter(context.getErrorWriter()));
            ScriptException exception = new ScriptException("Error when running docker stop");
            exception.initCause(e);
            throw exception;
        }

        if (exitValue != 0) {
            throw new ScriptException("Docker stop failed with exit code " + exitValue);
        }

        //build processRM
        Process processRM;
        try {
            engineLogger.info("Running command: " + processBuilderRM.command());
            processRM = processBuilderRM.start();

            processBuilderUtilities.attachStreamsToProcess(processRM,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());
            exitValue = processRM.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace(new PrintWriter(context.getErrorWriter()));
            ScriptException exception = new ScriptException("Error when running docker rm");
            exception.initCause(e);
            throw exception;
        }

        if (exitValue != 0) {
            throw new ScriptException("Docker rm failed with exit code " + exitValue);
        }
    }

    private void removeImage(String imageTagName) throws ScriptException {

        // Create docker remove image command - a simple docker rmi command 
        dockerRMICommand = dockerFileCommandCreator.createDockerRemoveImage(imageTagName);

        // Create a process builder for removing image
        ProcessBuilder processBuilderRMI = SingletonProcessBuilderFactory.getInstance()
                                                                         .getProcessBuilder(dockerRMICommand);

        Process processRMI;
        int exitValue = 0;
        try {
            processRMI = processBuilderRMI.start();

            processBuilderUtilities.attachStreamsToProcess(processRMI,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());
            exitValue = processRMI.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace(new PrintWriter(context.getErrorWriter()));
            ScriptException exception = new ScriptException("Error when running docker rmi");
            exception.initCause(e);
            throw exception;
        }

        if (exitValue != 0) {
            throw new ScriptException("Docker rmi failed with exit code " + exitValue);
        }
    }

    private void handleShutdown(ScriptContext context) throws ScriptException {
        // unfortunately shutdown hooks are not run on windows when the process is terminated using
        // process.destroy(). At the moment, this issue cannot be solved and docker file tasks cannot
        // be killed properly on windows in ProActive Scheduler task fork mode (which uses process.destroy()).
        // see https://bugs.openjdk.java.net/browse/JDK-8056139

        // Delete configuration file
        if (dockerfile != null && !DockerFilePropertyLoader.getInstance().isKeepDockerFile()) {
            boolean deleted = dockerfile.delete();
            if (!deleted) {
                engineLogger.warn("File: " + dockerfile.getAbsolutePath() + " was not deleted.");
            } else {
                engineLogger.info("Docker file " + dockerfile + " successfully deleted.");
            }
        }

        if (processBuild != null) {
            processBuild.destroy();
        }

        if (processRun != null) {
            processRun.destroy();
        }

        if (processExec != null) {
            processExec.destroy();
        }

        if (containerStarted && dockerActions.contains(DockerFileCommandCreator.STOP_ARGUMENT)) {
            stopAndRemoveContainer(containerTagName, context);
        }

        if (imageCreated && dockerActions.contains(DockerFileCommandCreator.RMI_ARGUMENT)) {
            removeImage(imageTagName);
        }

        cleanLogger();
    }
}
