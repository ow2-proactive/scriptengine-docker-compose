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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jsr223.docker.compose.utils.CommandlineOptionsFromBindingsExtractor.OptionType;
import lombok.NoArgsConstructor;


@NoArgsConstructor
public class DockerFileCommandCreator {

    // Constants
    public static final String BUILD_ARGUMENT = "build";

    public static final String FILENAME = "Dockerfile";

    /**
     * This method creates a bash command which starts docker-file with a given file.
     *
     * @return A String array which contains the command as a separate @String and each
     * argument as a separate String.
     */
    public String[] createDockerFileExecutionCommand() {
        List<String> command = new ArrayList<>();
        addSudoAndDockerFileCommand(command);

        // Add the build command
        command.add(BUILD_ARGUMENT);

        // Add the file
        command.add(FILENAME);

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

}
