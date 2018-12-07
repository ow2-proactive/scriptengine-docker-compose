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

import java.util.*;

import javax.script.Bindings;

import org.ow2.proactive.scheduler.common.SchedulerConstants;

import jsr223.docker.compose.utils.CommandlineOptionsFromBindingsExtractor;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j;


@NoArgsConstructor
@Log4j
public class DockerFileCommandCreator {

    // Constants for building the image
    public static final String BUILD_ARGUMENT = "build";

    public static final String IMAGE_TAG_OPTION_ARGUMENT = "-t";

    public static final String CONTAINER_NAME_OPTION_ARGUMENT = "--name";

    public static final String FILENAME = "Dockerfile";

    // Constants for running the container
    public static final String RUN_ARGUMENT = "run";

    // Constants for stopping the container
    public static final String STOP_ARGUMENT = "stop";

    // Constants for deleting the container
    public static final String RM_ARGUMENT = "rm";

    // Constants for deleting image
    public static final String RMI_ARGUMENT = "rmi";

    public final static String GENERIC_INFORMATION_KEY = SchedulerConstants.GENERIC_INFO_BINDING_NAME;

    public final static String DOCKER_FILE_COMMANDLINE_OPTIONS_SPLIT_REGEX_DEFAULT = " ";

    public final static String DOCKER_BUILD_COMMANDLINE_OPTIONS_KEY = "docker-build-options";

    public final static String DOCKER_RUN_COMMANDLINE_OPTIONS_KEY = "docker-run-options";

    public final static String DOCKER_STOP_COMMANDLINE_OPTIONS_KEY = "docker-stop-options";

    public final static String DOCKER_RMI_COMMANDLINE_OPTIONS_KEY = "docker-rmi-options";

    public final static String DOCKER_FILE_COMMANDLINE_OPTIONS_SPLIT_REGEX_KEY = "docker-file-options-split-regex";

    /**
     * This method creates a bash command which build an image based on a given dockerfile.
     *
     * @return A String array which contains the command as a separate @String and each
     * argument as a separate String.
     */
    public String[] createDockerBuildExecutionCommand(String imageTagName, Bindings bindings) {
        List<String> command = new ArrayList<>();
        addSudoAndDockerFileCommand(command);

        // Add the build command
        command.add(BUILD_ARGUMENT);

        // Add custom options
        command.addAll(getDockerCommandOptions(bindings, DOCKER_BUILD_COMMANDLINE_OPTIONS_KEY));

        // Add the tag option
        command.add(IMAGE_TAG_OPTION_ARGUMENT);

        // Add the tag name
        command.add(imageTagName);

        // Add the docker file
        command.add(".");

        return command.toArray(new String[command.size()]);
    }

    public String[] createDockerRunExecutionCommand(String containerTagName, String imageTagName, Bindings bindings) {
        List<String> command = new ArrayList<>();
        addSudoAndDockerFileCommand(command);

        // Add the build command
        command.add(RUN_ARGUMENT);

        // Add custom options
        command.addAll(getDockerCommandOptions(bindings, DOCKER_RUN_COMMANDLINE_OPTIONS_KEY));

        // Add container tag option
        command.add(CONTAINER_NAME_OPTION_ARGUMENT);

        // Add container tag name
        command.add(containerTagName);

        // Add image
        command.add(imageTagName);

        return command.toArray(new String[command.size()]);
    }

    public String[] createDockerStopExecutionCommand(String containerTagName, Bindings bindings) {
        List<String> command = new ArrayList<>();
        addSudoAndDockerFileCommand(command);

        // Add the build command
        command.add(STOP_ARGUMENT);

        // Add custom options
        command.addAll(getDockerCommandOptions(bindings, DOCKER_STOP_COMMANDLINE_OPTIONS_KEY));

        // Add the tag name
        command.add(containerTagName);

        return command.toArray(new String[command.size()]);
    }

    public String[] createDockerRemoveExecutionCommand(String containerTagName, Bindings bindings) {
        List<String> command = new ArrayList<>();
        addSudoAndDockerFileCommand(command);

        // Add the build command
        command.add(RM_ARGUMENT);

        // Add custom options
        command.addAll(getDockerCommandOptions(bindings, DOCKER_RMI_COMMANDLINE_OPTIONS_KEY));

        // Add the tag name
        command.add(containerTagName);

        return command.toArray(new String[command.size()]);
    }

    public String[] createDockerRemoveImage(String imageTagName) {
        List<String> command = new ArrayList<>();
        addSudoAndDockerFileCommand(command);

        // Add the build command
        command.add(RMI_ARGUMENT);

        // Add the tag name
        command.add(imageTagName);

        return command.toArray(new String[command.size()]);
    }

    /**
     * Adds sudo and docker file command to the given list. Sudo is only added when
     * it is configured to do that.
     *
     * @param command List which gets the command(s) added.
     */
    private void addSudoAndDockerFileCommand(List<String> command) {
        // Add sudo if necessary
        if (DockerFilePropertyLoader.getInstance().isUseSudo()) {
            command.add(DockerFilePropertyLoader.getInstance().getSudoCommand());
        }

        // Add docker file command
        command.add(DockerFilePropertyLoader.getInstance().getDockerFileCommand());
    }

    private Map<String, String> extractGenericInfo(Bindings bindings) {
        Object bindingsObject;
        if (bindings.containsKey(GENERIC_INFORMATION_KEY) && (bindings.get(GENERIC_INFORMATION_KEY) instanceof Map)) {
            bindingsObject = bindings.get(GENERIC_INFORMATION_KEY);
        } else {
            log.warn("Generic Information could not be retrieved. Docker command options could not be extracted.");
            bindingsObject = Collections.emptyMap();
        }
        return (Map<String, String>) bindingsObject;
    }

    public List<String> getDockerCommandOptions(Bindings bindings, String keyName) {
        Map<String, String> genericInformationMap = extractGenericInfo(bindings);
        List<String> generalCmdOptions = Collections.emptyList();
        String splitCharacter = DOCKER_FILE_COMMANDLINE_OPTIONS_SPLIT_REGEX_DEFAULT;
        if (genericInformationMap.get(DOCKER_FILE_COMMANDLINE_OPTIONS_SPLIT_REGEX_KEY) != null) {
            splitCharacter = genericInformationMap.get(DOCKER_FILE_COMMANDLINE_OPTIONS_SPLIT_REGEX_KEY);
        }

        if (genericInformationMap.get(keyName) != null) {
            generalCmdOptions = Arrays.asList(genericInformationMap.get(keyName).split(splitCharacter));
        }
        return generalCmdOptions;
    }

}
