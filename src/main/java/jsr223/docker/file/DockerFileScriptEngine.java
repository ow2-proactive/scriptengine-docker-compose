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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;

import javax.script.*;

import jsr223.docker.compose.bindings.MapBindingsAdder;
import jsr223.docker.compose.bindings.StringBindingsAdder;
import jsr223.docker.compose.file.write.ConfigurationFileWriter;
import jsr223.docker.compose.utils.Log4jConfigurationLoader;
import jsr223.docker.compose.yaml.VariablesReplacer;
import jsr223.docker.file.DockerFileCommandCreator;
import jsr223.docker.file.DockerFileScriptEngine;
import jsr223.docker.file.DockerFileScriptEngineFactory;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j;
import processbuilder.SingletonProcessBuilderFactory;
import processbuilder.utils.ProcessBuilderUtilities;


@Log4j
public class DockerFileScriptEngine extends AbstractScriptEngine {

    private static final String DOCKER_HOST_PROPERTY_NAME = "DOCKER_HOST";

    private ProcessBuilderUtilities processBuilderUtilities = new ProcessBuilderUtilities();

    private VariablesReplacer variablesReplacer = new VariablesReplacer();

    private ConfigurationFileWriter configurationFileWriter = new ConfigurationFileWriter();

    private StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

    private DockerFileCommandCreator dockerFileCommandCreator = new DockerFileCommandCreator();

    private Log4jConfigurationLoader log4jConfigurationLoader = new Log4jConfigurationLoader();

    private Process processRun = null;

    private Process processBuild = null;

    private String imageTagName = "demofabienimage";

    private String containerTagName = "demofabiencontainer";

    private String[] dockerFileCommand = null;

    private String[] dockerRunCommand = null;

    private String[] dockerStopCommand = null;

    private String[] dockerRMCommand = null;

    private String[] dockerRMICommand = null;

    private boolean imageCreated = false;

    private boolean containerStarted = false;

    private File dockerfile = null;

    public DockerFileScriptEngine() {
        // This is the entry-point of the script engine
        log4jConfigurationLoader.loadLog4jConfiguration();
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {

        // Create docker file command - a simple docker build command 
        dockerFileCommand = dockerFileCommandCreator.createDockerFileExecutionCommand(imageTagName);

        // Create docker run command - a simple docker run command 
        dockerRunCommand = dockerFileCommandCreator.createDockerRunExecutionCommand(containerTagName, imageTagName);

        // Create a process builder for building image
        ProcessBuilder processBuilderBuild = SingletonProcessBuilderFactory.getInstance()
                                                                           .getProcessBuilder(dockerFileCommand);

        // Create a process builder for running image
        ProcessBuilder processBuilderRun = SingletonProcessBuilderFactory.getInstance()
                                                                         .getProcessBuilder(dockerRunCommand);

        // Use process builder environment and fill it with environment variables
        Map<String, String> variablesMap = processBuilderBuild.environment();

        // Add string bindings as environment variables
        stringBindingsAdder.addBindingToStringMap(context.getBindings(ScriptContext.ENGINE_SCOPE), variablesMap);

        // Add DOCKER_HOST variable to execution environment
        variablesMap.put(DOCKER_HOST_PROPERTY_NAME, DockerFilePropertyLoader.getInstance().getDockerHost());

        // Replace variables in configuration file
        String scriptReplacedVariables = variablesReplacer.replaceVariables(script, variablesMap);

        Thread shutdownHook = null;

        try {

            dockerfile = configurationFileWriter.forceFileToDisk(scriptReplacedVariables,
                                                                 dockerFileCommandCreator.FILENAME);

            // Start process build
            processBuild = processBuilderBuild.start();

            imageCreated = true;

            shutdownHook = new Thread(new Runnable() {
                @Override
                public void run() {
                    handleShutdown();
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            // Attach streams
            processBuilderUtilities.attachStreamsToProcess(processBuild,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());

            // Wait for process build to exit
            int exitValueBuild = processBuild.waitFor();

            processBuild = null;

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

            if (exitValueBuild != 0) {
                throw new ScriptException("Docker File Build failed with exit code " + exitValueBuild);
            }
            if (exitValueRun != 0) {
                throw new ScriptException("Docker Run failed with exit code " + exitValueBuild);
            }

            return exitValueBuild;
        } catch (IOException e) {
            log.warn("Failed to execute Docker File.", e);
        } catch (InterruptedException e) {
            log.info("Container execution interrupted. " + e.getMessage());
        } finally {

            handleShutdown();
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }

        return null;
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {

        StringWriter stringWriter = new StringWriter();

        try {
            ProcessBuilderUtilities.pipe(reader, stringWriter);
        } catch (IOException e) {
            log.warn("Filed to convert Reader into StringWriter. Not possible to execute Docker File script.");
            log.debug("Filed to convert Reader into StringWriter. Not possible to execute Docker File script.", e);
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

    private void stopAndRemoveContainer(String containerTagName) {

        // Create docker stop container command - a simple docker stop command 
        dockerStopCommand = dockerFileCommandCreator.createDockerStopExecutionCommand(containerTagName);

        // Create docker remove container command - a simple docker rm command 
        dockerRMCommand = dockerFileCommandCreator.createDockerRemoveExecutionCommand(containerTagName);

        // Create a process builder for stopping container
        ProcessBuilder processBuilderStop = SingletonProcessBuilderFactory.getInstance()
                                                                          .getProcessBuilder(dockerStopCommand);

        // Create a process builder for removing container
        ProcessBuilder processBuilderRM = SingletonProcessBuilderFactory.getInstance()
                                                                        .getProcessBuilder(dockerRMCommand);

        //build processStop
        Process processStop;
        try {
            processStop = processBuilderStop.start();

            processBuilderUtilities.attachStreamsToProcess(processStop,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());
            processStop.waitFor();

        } catch (IOException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace(new PrintWriter(context.getErrorWriter()));
        }

        //build processStop
        Process processRM;
        try {
            processRM = processBuilderRM.start();

            processBuilderUtilities.attachStreamsToProcess(processRM,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());
            processRM.waitFor();

        } catch (IOException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace(new PrintWriter(context.getErrorWriter()));
        }
    }

    private void removeImage(String imageTagName) {

        // Create docker remove image command - a simple docker rmi command 
        dockerRMICommand = dockerFileCommandCreator.createDockerRemoveImage(imageTagName);

        // Create a process builder for removing image
        ProcessBuilder processBuilderRMI = SingletonProcessBuilderFactory.getInstance()
                                                                         .getProcessBuilder(dockerRMICommand);

        Process processRMI;
        try {
            processRMI = processBuilderRMI.start();

            processBuilderUtilities.attachStreamsToProcess(processRMI,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());
            processRMI.waitFor();

        } catch (IOException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace(new PrintWriter(context.getErrorWriter()));
        }
    }

    private void handleShutdown() {
        // Delete configuration file
        if (dockerfile != null) {
            boolean deleted = dockerfile.delete();
            if (!deleted) {
                log.warn("File: " + dockerfile.getAbsolutePath() + " was not deleted.");
            }
        }

        if (processBuild != null) {
            processBuild.destroy();
        }

        if (processRun != null) {
            processRun.destroy();
        }

        if (containerStarted) {
            stopAndRemoveContainer(containerTagName);
        }

        if (imageCreated) {
            removeImage(imageTagName);
        }
    }
}
