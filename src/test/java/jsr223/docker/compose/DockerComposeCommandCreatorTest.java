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

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import jsr223.docker.compose.utils.CommandlineOptionsFromBindingsExtractor.OptionType;
import jsr223.docker.compose.utils.DockerComposePropertyLoader;
import testing.utils.ReflectionUtilities;


public class DockerComposeCommandCreatorTest {

    private final DockerComposeCommandCreator dockerCommandCreator = new DockerComposeCommandCreator();

    @BeforeClass
    public static void before() {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Test
    public void testDockerExecutionCommandWithSudo() throws NoSuchFieldException, IllegalAccessException {
        Field useSudoField = ReflectionUtilities.makeFieldAccessible("useSudo", DockerComposePropertyLoader.class);
        boolean oldValue = (boolean) useSudoField.get(DockerComposePropertyLoader.getInstance());

        // Run test with sudo true
        useSudoField.set(DockerComposePropertyLoader.getInstance(), true);
        testDockerComposeExecutionCommand();

        // Run test with sudo false
        useSudoField.set(DockerComposePropertyLoader.getInstance(), false);
        testDockerComposeExecutionCommand();

        // Restore value from configuration file
        useSudoField.set(DockerComposePropertyLoader.getInstance(), oldValue);
    }

    /**
     * Test whether the remove command has the right structure.
     *
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @Test
    public void testDockerComposeDownCommand() throws NoSuchFieldException, IllegalAccessException {
        String[] command = dockerCommandCreator.createDockerComposeDownCommand();
        // Running index; which position of array will currently be checked.
        int index = 0;

        // Check if sudo is added correctly
        index = checkSudoAndComposeCommand(command, index);

        Assert.assertEquals("No-ansi option must be used.", dockerCommandCreator.NO_ANSI_OPTION, command[index++]);

        // Check if stop and remove (down) argument is next
        Assert.assertEquals("Down option must be used.",
                            dockerCommandCreator.STOP_AND_REMOVE_CONTAINER_ARGUMENT,
                            command[index++]);

        // Check if volume argument is next
        Assert.assertEquals("Volume option must be used after down.",
                            dockerCommandCreator.VOLUMES_ARGUMENT,
                            command[index++]);
    }

    @Test
    public void testThatDockerComposeUpOptionsArePlacedBehindUp() {
        Map<OptionType, List<String>> options = getOptions(Collections.EMPTY_LIST, Arrays.asList("--option1"));
        List<String> cmd = Arrays.asList(dockerCommandCreator.createDockerComposeExecutionCommand(options));

        int indexOfUp = cmd.indexOf(DockerComposeCommandCreator.START_CONTAINER_ARGUMENT);

        assertTrue(cmd.get(indexOfUp + 1).equals("--option1"));
    }

    /**
     * Check whether the execution command has the right structure.
     *
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @Test
    public void testDockerComposeExecutionCommand() throws NoSuchFieldException, IllegalAccessException {
        String[] command = dockerCommandCreator.createDockerComposeExecutionCommand(getOptions(null, null));
        int index = 0;

        // Check if sudo and compose command are added correctly
        index = checkSudoAndComposeCommand(command, index);

        Assert.assertEquals("No ansi option must be used.",
                            DockerComposeCommandCreator.NO_ANSI_OPTION,
                            command[index++]);

        // Check if file argument is used
        Assert.assertEquals("File option must be used.",
                            DockerComposeCommandCreator.FILENAME_ARGUMENT,
                            command[index++]);

        // Check if correct filename is used
        Assert.assertEquals("Correct filename must be used in command.",
                            DockerComposeCommandCreator.YAML_FILE_NAME,
                            command[index++]);

        // Check whether correct start command for yaml file is used
        Assert.assertEquals("Correct argument for compose command must be used.",
                            DockerComposeCommandCreator.START_CONTAINER_ARGUMENT,
                            command[index++]);
    }

    private int checkSudoAndComposeCommand(String[] command, int index) {
        // Check for sudo command
        if (DockerComposePropertyLoader.getInstance().isUseSudo()) {
            Assert.assertEquals("Sudo command must be used when configured.",
                                DockerComposePropertyLoader.getInstance().getSudoCommand(),
                                command[index++]);
        }

        // Check for docker compose command as next command
        Assert.assertEquals("Docker compose command must be used as read from configuration.",
                            DockerComposePropertyLoader.getInstance().getDockerComposeCommand(),
                            command[index++]);
        return index;
    }

    private Map<OptionType, List<String>> getOptions(List<String> generalOptions, List<String> upOptions) {
        Map<OptionType, List<String>> options = new EnumMap<OptionType, List<String>>(OptionType.class);
        if (generalOptions != null) {
            options.put(OptionType.GENERAL_OPTION, generalOptions);
        } else {
            options.put(OptionType.GENERAL_OPTION, Collections.<String> emptyList());
        }
        if (upOptions != null) {
            options.put(OptionType.UP_OPTION, upOptions);
        } else {
            options.put(OptionType.UP_OPTION, Collections.<String> emptyList());
        }
        return options;
    }
}
