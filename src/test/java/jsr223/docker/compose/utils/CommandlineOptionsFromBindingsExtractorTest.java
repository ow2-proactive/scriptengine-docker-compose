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
package jsr223.docker.compose.utils;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import jsr223.docker.compose.utils.CommandlineOptionsFromBindingsExtractor.OptionType;


/**
 * @author ActiveEon Team
 * @since 01/02/2018
 */
public class CommandlineOptionsFromBindingsExtractorTest {

    @BeforeClass
    public static void before() {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Test
    public void testThatGenericInformationBindingsIsExtractedAndMapContentIsReturnedSplit() {
        CommandlineOptionsFromBindingsExtractor commandlineOptionsFromBindingsExtractor = new CommandlineOptionsFromBindingsExtractor();

        Bindings bindings = new SimpleBindings();
        Map<String, String> genericInformation = new HashMap<>();
        genericInformation.put(CommandlineOptionsFromBindingsExtractor.DOCKER_COMPOSE_UP_COMMANDLINE_OPTIONS_KEY,
                               "--option1 --option2");
        genericInformation.put(CommandlineOptionsFromBindingsExtractor.DOCKER_COMPOSE_COMMANDLINE_OPTIONS_KEY,
                               "--verbose --host localhost");

        bindings.put(CommandlineOptionsFromBindingsExtractor.GENERIC_INFORMATION_KEY, genericInformation);

        Map<OptionType, List<String>> options = commandlineOptionsFromBindingsExtractor.getDockerComposeCommandOptions(bindings);
        List<String> upOptions = options.get(OptionType.UP_OPTION);
        List<String> generalOptions = options.get(OptionType.GENERAL_OPTION);

        assertTrue(upOptions.contains("--option1"));
        assertTrue(upOptions.contains("--option2"));
        assertTrue(upOptions.size() == 2);

        assertTrue(generalOptions.contains("--verbose"));
        assertTrue(generalOptions.contains("--host"));
        assertTrue(generalOptions.contains("localhost"));
        assertTrue(generalOptions.size() == 3);
    }

    @Test
    public void testThatGenericInformationBindingsIsExtractedAndMapContentIsReturnedSplitWithRegex() {
        CommandlineOptionsFromBindingsExtractor commandlineOptionsFromBindingsExtractor = new CommandlineOptionsFromBindingsExtractor();

        Bindings bindings = new SimpleBindings();
        Map<String, String> genericInformation = new HashMap<>();
        genericInformation.put(CommandlineOptionsFromBindingsExtractor.DOCKER_COMPOSE_UP_COMMANDLINE_OPTIONS_KEY,
                               "--option1!SPLIT!--option2");
        genericInformation.put(CommandlineOptionsFromBindingsExtractor.DOCKER_COMPOSE_COMMANDLINE_OPTIONS_SPLIT_REGEX_KEY,
                               "!SPLIT!");
        bindings.put(CommandlineOptionsFromBindingsExtractor.GENERIC_INFORMATION_KEY, genericInformation);

        Map<OptionType, List<String>> options = commandlineOptionsFromBindingsExtractor.getDockerComposeCommandOptions(bindings);
        List<String> upOptions = options.get(OptionType.UP_OPTION);

        assertTrue(upOptions.contains("--option1"));
        assertTrue(upOptions.contains("--option2"));
        assertTrue(upOptions.size() == 2);
    }

    @Test
    public void testThatEmptyOptionsReturnEmptyList() {
        CommandlineOptionsFromBindingsExtractor commandlineOptionsFromBindingsExtractor = new CommandlineOptionsFromBindingsExtractor();

        Bindings bindings = new SimpleBindings();
        Map<String, String> genericInformation = new HashMap<>();
        bindings.put(CommandlineOptionsFromBindingsExtractor.GENERIC_INFORMATION_KEY, genericInformation);

        Map<OptionType, List<String>> options = commandlineOptionsFromBindingsExtractor.getDockerComposeCommandOptions(bindings);
        List<String> upOptions = options.get(OptionType.UP_OPTION);

        assertTrue(upOptions.size() == 0);
    }

}
