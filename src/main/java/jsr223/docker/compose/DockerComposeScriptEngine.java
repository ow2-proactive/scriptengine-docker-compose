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
package jsr223.docker.compose;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import javax.script.*;

import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.ow2.proactive.scheduler.common.SchedulerConstants;

import com.google.common.io.Files;

import jsr223.docker.compose.bindings.MapBindingsAdder;
import jsr223.docker.compose.bindings.StringBindingsAdder;
import jsr223.docker.compose.file.write.ConfigurationFileWriter;
import jsr223.docker.compose.utils.CommandlineOptionsFromBindingsExtractor;
import jsr223.docker.compose.utils.CommandlineOptionsFromBindingsExtractor.OptionType;
import jsr223.docker.compose.utils.DockerComposePropertyLoader;
import jsr223.docker.compose.utils.Log4jConfigurationLoader;
import jsr223.docker.compose.utils.ScriptContextBindingsExtractor;
import jsr223.docker.compose.yaml.VariablesReplacer;
import lombok.extern.log4j.Log4j;
import processbuilder.SingletonProcessBuilderFactory;
import processbuilder.utils.ProcessBuilderUtilities;


@Log4j
public class DockerComposeScriptEngine extends AbstractScriptEngine {

    private static final String DOCKER_HOST_PROPERTY_NAME = "DOCKER_HOST";

    private ProcessBuilderUtilities processBuilderUtilities = new ProcessBuilderUtilities();

    private VariablesReplacer variablesReplacer = new VariablesReplacer();

    private ConfigurationFileWriter configurationFileWriter = new ConfigurationFileWriter();

    private StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

    private DockerComposeCommandCreator dockerComposeCommandCreator = new DockerComposeCommandCreator();

    private CommandlineOptionsFromBindingsExtractor commandlineOptionsFromBindingsExtractor = new CommandlineOptionsFromBindingsExtractor();

    private ScriptContextBindingsExtractor scriptContextBindingsExtractor = new ScriptContextBindingsExtractor();

    private Log4jConfigurationLoader log4jConfigurationLoader = new Log4jConfigurationLoader();

    private static long loggerId = 0;

    private Logger engineLogger;

    private Appender engineLoggerAppender;

    File composeDirectory;

    public DockerComposeScriptEngine() {
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
    public Object eval(String script, final ScriptContext context) throws ScriptException {
        initLogger(context);
        Bindings bindings = scriptContextBindingsExtractor.extractFrom(context);
        Map<OptionType, List<String>> options = commandlineOptionsFromBindingsExtractor.getDockerComposeCommandOptions(bindings);

        // Create docker compose command
        String[] dockerComposeCommand = dockerComposeCommandCreator.createDockerComposeExecutionCommand(options);

        // Create a process builder
        ProcessBuilder processBuilder = SingletonProcessBuilderFactory.getInstance()
                                                                      .getProcessBuilder(dockerComposeCommand);

        // Use process builder environment and fill it with environment variables
        Map<String, String> variablesMap = processBuilder.environment();

        // Add string bindings as environment variables
        stringBindingsAdder.addBindingToStringMap(context.getBindings(ScriptContext.ENGINE_SCOPE), variablesMap);

        // Add DOCKER_HOST variable to execution environment
        variablesMap.put(DOCKER_HOST_PROPERTY_NAME, DockerComposePropertyLoader.getInstance().getDockerHost());

        // Replace variables in configuration file
        String scriptReplacedVariables = variablesReplacer.replaceVariables(script, variablesMap);

        File composeYamlFile = null;

        Thread shutdownHook = null;

        String localSpace = null;
        if (context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(SchedulerConstants.DS_SCRATCH_BINDING_NAME)) {
            localSpace = (String) context.getBindings(ScriptContext.ENGINE_SCOPE)
                                         .get(SchedulerConstants.DS_SCRATCH_BINDING_NAME);
        }

        if (localSpace != null) {
            composeDirectory = new File(localSpace);
        } else {
            composeDirectory = Files.createTempDir();
        }
        processBuilder.directory(composeDirectory);

        try {
            composeYamlFile = configurationFileWriter.forceFileToDisk(scriptReplacedVariables,
                                                                      new File(composeDirectory,
                                                                               dockerComposeCommandCreator.YAML_FILE_NAME).getAbsolutePath());

            engineLogger.info("Docker compose file " + composeYamlFile + " created.");
            engineLogger.info("Running command: " + processBuilder.command());
            // Start process
            Process process = processBuilder.start();

            // Attach streams
            processBuilderUtilities.attachStreamsToProcess(process,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());

            // Set shutdown hook
            final Process shutdownHookProcessReference = process;
            shutdownHook = new Thread() {
                @Override
                public void run() {
                    // unfortunately shutdown hooks are not run on windows when the process is terminated using
                    // process.destroy(). At the moment, this issue cannot be solved and docker compose tasks cannot
                    // be killed properly on windows in ProActive Scheduler task fork mode (which uses process.destroy()).
                    // see https://bugs.openjdk.java.net/browse/JDK-8056139
                    try {
                        stopAndRemoveContainers(context).waitFor();
                    } catch (IOException e) {
                        //TODO improve message here
                    } catch (InterruptedException e) {
                        engineLogger.info("Container execution interrupted. " + e.getMessage());
                    }
                    cleanLogger();
                }
            };

            Runtime.getRuntime().addShutdownHook(shutdownHook);

            // Wait for process to exit
            int exitValue = process.waitFor();

            if (exitValue != 0) {
                throw new ScriptException("Docker Compose failed with exit code " + exitValue);
            }
            return exitValue;
        } catch (IOException e) {
            engineLogger.warn("Failed to execute Docker Compose.", e);
        } catch (InterruptedException e) {
            engineLogger.info("Container execution interrupted. " + e.getMessage());
        } finally {
            try {
                // Reset thread's interrupt flag
                Thread.interrupted();
                stopAndRemoveContainers(context).waitFor();
            } catch (Exception e) {
                engineLogger.error("Container removal was interrupted: " + e.getMessage());
            }
            // Delete configuration file
            if (composeYamlFile != null && !DockerComposePropertyLoader.getInstance().isKeepDockerFile()) {
                boolean deleted = composeYamlFile.delete();
                if (!deleted) {
                    engineLogger.warn("File: " + composeYamlFile.getAbsolutePath() + " was not deleted.");
                }
            }
            if (shutdownHook != null) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
            cleanLogger();
        }
        return null;
    }

    private Process stopAndRemoveContainers(ScriptContext context) throws IOException {

        ProcessBuilder builder = SingletonProcessBuilderFactory.getInstance()
                                                               .getProcessBuilder(dockerComposeCommandCreator.createDockerComposeDownCommand());
        builder.directory(composeDirectory);
        engineLogger.info("Running command: " + builder.command());
        Process process = builder.start();
        processBuilderUtilities.attachStreamsToProcess(process,
                                                       context.getWriter(),
                                                       context.getErrorWriter(),
                                                       context.getReader());
        return process;
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {

        StringWriter stringWriter = new StringWriter();

        try {
            ProcessBuilderUtilities.pipe(reader, stringWriter);
        } catch (IOException e) {
            engineLogger.warn("Filed to convert Reader into StringWriter. Not possible to execute Docker Compose script.");
            engineLogger.debug("Filed to convert Reader into StringWriter. Not possible to execute Docker Compose script.",
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
        return new DockerComposeScriptEngineFactory();
    }
}
