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

    public DockerFileScriptEngine() {
        // This is the entry-point of the script engine
        log4jConfigurationLoader.loadLog4jConfiguration();
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {

        // Create docker file command - a simple docker build command 
        String[] dockerFileCommand = dockerFileCommandCreator.createDockerFileExecutionCommand();

        // Create a process builder
        ProcessBuilder processBuilder = SingletonProcessBuilderFactory.getInstance()
                                                                      .getProcessBuilder(dockerFileCommand);

        // Use process builder environment and fill it with environment variables
        Map<String, String> variablesMap = processBuilder.environment();

        // Add string bindings as environment variables
        stringBindingsAdder.addBindingToStringMap(context.getBindings(ScriptContext.ENGINE_SCOPE), variablesMap);

        // Add DOCKER_HOST variable to execution environment
        variablesMap.put(DOCKER_HOST_PROPERTY_NAME, DockerFilePropertyLoader.getInstance().getDockerHost());

        // Replace variables in configuration file
        String scriptReplacedVariables = variablesReplacer.replaceVariables(script, variablesMap);

        File dockerfile = null;

        Thread shutdownHook = null;

        try {
            dockerfile = configurationFileWriter.forceFileToDisk(scriptReplacedVariables,
                                                                 dockerFileCommandCreator.FILENAME);

            // Start process
            Process process = processBuilder.start();

            // Attach streams
            processBuilderUtilities.attachStreamsToProcess(process,
                                                           context.getWriter(),
                                                           context.getErrorWriter(),
                                                           context.getReader());

            // Wait for process to exit
            int exitValue = process.waitFor();

            if (exitValue != 0) {
                throw new ScriptException("Docker File failed with exit code " + exitValue);
            }
            return exitValue;
        } catch (IOException e) {
            log.warn("Failed to execute Docker File.", e);
        } catch (InterruptedException e) {
            log.info("Container execution interrupted. " + e.getMessage());
        } finally {

            // Delete configuration file
            if (dockerfile != null) {
                boolean deleted = dockerfile.delete();
                if (!deleted) {
                    log.warn("File: " + dockerfile.getAbsolutePath() + " was not deleted.");
                }
            }
            if (shutdownHook != null) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
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

}
