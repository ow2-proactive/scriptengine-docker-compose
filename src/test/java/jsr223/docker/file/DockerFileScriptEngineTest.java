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

import static org.junit.Assume.assumeTrue;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ow2.proactive.scheduler.common.SchedulerConstants;
import org.ow2.proactive.scheduler.task.SchedulerVars;
import org.ow2.proactive.scripting.ScriptResult;
import org.ow2.proactive.scripting.SimpleScript;
import org.ow2.proactive.scripting.TaskScript;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;


public class DockerFileScriptEngineTest {

    private boolean dockerCommandExists() {
        try {
            Process p = Runtime.getRuntime().exec("docker -v");
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeClass
    public static void before() {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
        DockerFilePropertyLoader.getInstance().setDockerFileCommand("docker");
        DockerFilePropertyLoader.getInstance().setUseSudo(false);
        DockerFilePropertyLoader.getInstance().setDockerHost("");
    }

    @Test
    public void testReuseImage() throws Exception {
        assumeTrue(dockerCommandExists());

        String imageName = "my-image";

        String dockerScript = "FROM ubuntu:18.04\n" + "RUN echo \"Hello\"";

        SimpleScript ss = new SimpleScript(dockerScript, DockerFileScriptEngineFactory.NAME);
        TaskScript taskScript = new TaskScript(ss);
        HashMap<String, Serializable> variablesMap = new HashMap<>(2);
        variablesMap.put(SchedulerVars.PA_JOB_ID.name(), "1");
        variablesMap.put(SchedulerVars.PA_TASK_ID.name(), "2");
        HashMap<String, String> genericInfo = new HashMap<>(2);
        genericInfo.put(DockerFileScriptEngine.DOCKER_IMAGE_TAG_GI, imageName);
        genericInfo.put(DockerFileScriptEngine.DOCKER_ACTIONS_GI, "build");

        Map<String, Object> aBindings = ImmutableMap.of(SchedulerConstants.VARIABLES_BINDING_NAME,
                                                        (Object) variablesMap,
                                                        SchedulerConstants.GENERIC_INFO_BINDING_NAME,
                                                        genericInfo);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        runAndGetOutput(taskScript, aBindings, output);

        Assert.assertTrue("Output should contain the dedicated image name", output.toString().contains(imageName));

        genericInfo.put(DockerFileScriptEngine.DOCKER_ACTIONS_GI, "run,stop,rmi");

        output = new ByteArrayOutputStream();

        runAndGetOutput(taskScript, aBindings, output);

        Assert.assertTrue("Output should contain the dedicated container name",
                          output.toString().contains(DockerFileScriptEngine.DEFAULT_CONTAINER_NAME + "_1t2"));
    }

    /**
     * This complex test first builds and image and run a container in background
     * it then executes a command inside the container
     * finally, it stops the container and removes the image
     * @throws Exception
     */
    @Test
    public void testReuseImageAndContainer() throws Exception {
        assumeTrue(dockerCommandExists());

        String imageName = "my-image";
        String containerName = "my-container";

        String dockerScript = "FROM ubuntu:18.04\n";

        // Build and run the container in background mode
        SimpleScript ss = new SimpleScript(dockerScript, DockerFileScriptEngineFactory.NAME);
        TaskScript taskScript = new TaskScript(ss);
        HashMap<String, Serializable> variablesMap = new HashMap<>(2);
        variablesMap.put(SchedulerVars.PA_JOB_ID.name(), "1");
        variablesMap.put(SchedulerVars.PA_TASK_ID.name(), "2");
        HashMap<String, String> genericInfo = new HashMap<>(3);
        genericInfo.put(DockerFileScriptEngine.DOCKER_IMAGE_TAG_GI, imageName);
        genericInfo.put(DockerFileScriptEngine.DOCKER_CONTAINER_TAG_GI, containerName);
        genericInfo.put(DockerFileCommandCreator.DOCKER_RUN_COMMANDLINE_OPTIONS_KEY, "-t -d");
        genericInfo.put(DockerFileScriptEngine.DOCKER_ACTIONS_GI, "build,run");

        Map<String, Object> aBindings = ImmutableMap.of(SchedulerConstants.VARIABLES_BINDING_NAME,
                                                        (Object) variablesMap,
                                                        SchedulerConstants.GENERIC_INFO_BINDING_NAME,
                                                        genericInfo);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        runAndGetOutput(taskScript, aBindings, output);

        Assert.assertTrue("Output should contain the dedicated image name", output.toString().contains(imageName));

        Assert.assertTrue("Output should contain the dedicated container name",
                          output.toString().contains(containerName));

        // Execute a command inside the container
        String messageToPrint = "my test message";

        genericInfo.put(DockerFileCommandCreator.DOCKER_FILE_COMMANDLINE_OPTIONS_SPLIT_REGEX_KEY, "!SPLIT!");
        genericInfo.put(DockerFileCommandCreator.DOCKER_EXEC_COMMAND_KEY,
                        "/bin/sh!SPLIT!-c!SPLIT!echo '" + messageToPrint + "'");
        genericInfo.put(DockerFileScriptEngine.DOCKER_ACTIONS_GI, "exec");

        output = new ByteArrayOutputStream();

        runAndGetOutput(taskScript, aBindings, output);

        Assert.assertTrue("Output should contain the custom message printed by the exec command",
                          output.toString().contains(messageToPrint));

        // Clean up
        genericInfo.put(DockerFileScriptEngine.DOCKER_ACTIONS_GI, "stop,rmi");
        runAndGetOutput(taskScript, aBindings, output);
    }

    private void runAndGetOutput(TaskScript taskScript, Map<String, Object> aBindings, ByteArrayOutputStream output) {
        ScriptResult<Serializable> res = taskScript.execute(aBindings,
                                                            new PrintStream(output),
                                                            new PrintStream(output));

        System.out.println("Script output:");
        System.out.println(output.toString());

        System.out.println("Script Exception:");
        System.out.println(res.getException());

        Assert.assertFalse(res.errorOccured());
    }

    @Test
    public void testContainerAndImageShouldUseJobTaskIds() throws Exception {
        assumeTrue(dockerCommandExists());

        String dockerScript = "FROM ubuntu:18.04\n" + "RUN echo \"Hello\"";

        SimpleScript ss = new SimpleScript(dockerScript, DockerFileScriptEngineFactory.NAME);
        TaskScript taskScript = new TaskScript(ss);
        HashMap<String, Serializable> variablesMap = new HashMap<String, Serializable>(2);
        variablesMap.put(SchedulerVars.PA_JOB_ID.name(), "1");
        variablesMap.put(SchedulerVars.PA_TASK_ID.name(), "2");
        Map<String, Object> aBindings = ImmutableMap.of(SchedulerConstants.VARIABLES_BINDING_NAME,
                                                        (Object) variablesMap,
                                                        SchedulerConstants.GENERIC_INFO_BINDING_NAME,
                                                        Collections.EMPTY_MAP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        runAndGetOutput(taskScript, aBindings, output);

        Assert.assertTrue("Output should contain the dedicated image name",
                          output.toString().contains(DockerFileScriptEngine.DEFAULT_IMAGE_NAME + "_1t2"));
        Assert.assertTrue("Output should contain the dedicated container name",
                          output.toString().contains(DockerFileScriptEngine.DEFAULT_CONTAINER_NAME + "_1t2"));
    }

    @Test
    public void testDockerFileShouldBeCreatedInsideScratchSpace() throws Exception {
        assumeTrue(dockerCommandExists());
        String dockerScript = "FROM ubuntu:18.04\n" + "RUN echo \"Hello\"";

        DockerFilePropertyLoader.getInstance().setKeepDockerFile(true);

        File tempDir = Files.createTempDir();

        SimpleScript ss = new SimpleScript(dockerScript, DockerFileScriptEngineFactory.NAME);
        TaskScript taskScript = new TaskScript(ss);

        Map<String, Object> aBindings = ImmutableMap.of(SchedulerConstants.DS_SCRATCH_BINDING_NAME,
                                                        (Object) tempDir.getAbsolutePath(),
                                                        SchedulerConstants.GENERIC_INFO_BINDING_NAME,
                                                        Collections.EMPTY_MAP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        runAndGetOutput(taskScript, aBindings, output);

        Assert.assertTrue("Docker file should be created in temp directory",
                          new File(tempDir, DockerFileCommandCreator.FILENAME).exists());
    }

    @Test
    public void testContainerOutputShouldBeRetrieved() throws Exception {
        assumeTrue(dockerCommandExists());
        String capturedOutput = "Hello from " + this.getClass().getName();
        String dockerScript = "FROM ubuntu:18.04\n" + "RUN echo \"" + capturedOutput + "\"";

        SimpleScript ss = new SimpleScript(dockerScript, DockerFileScriptEngineFactory.NAME);
        TaskScript taskScript = new TaskScript(ss);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        runAndGetOutput(taskScript,
                        ImmutableMap.of(SchedulerConstants.GENERIC_INFO_BINDING_NAME, Collections.EMPTY_MAP),
                        output);

        Assert.assertTrue("Captured output should appear", output.toString().contains(capturedOutput));
    }
}
