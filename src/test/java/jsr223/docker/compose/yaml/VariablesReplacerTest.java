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
package jsr223.docker.compose.yaml;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;


public class VariablesReplacerTest {

    private final static String yamlFileWithVariables = "$name:\n" + "    image: $container\n" +
                                                        "    command: echo \"$greetings\"";

    private final static String yamlFileExpected = "EchoUbuntu:\n" + "    image: dockerfile/ubuntu\n" +
                                                   "    command: echo \"Hello World\"";

    @BeforeClass
    public static void before() {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Test
    public void testVariableSubstitution() throws ScriptException, IOException {
        VariablesReplacer variablesReplacer = new VariablesReplacer();
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put("name", "EchoUbuntu");
        variableMap.put("container", "dockerfile/ubuntu");
        variableMap.put("greetings", "Hello World");

        String stringVariablesReplaced = variablesReplacer.replaceVariables(yamlFileWithVariables, variableMap);

        assertThat(stringVariablesReplaced, is(yamlFileExpected));

    }

    @Test
    public void testScriptIsNull() throws ScriptException, IOException {
        VariablesReplacer variablesReplacer = new VariablesReplacer();
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put("name", "EchoUbuntu");
        variableMap.put("container", "dockerfile/ubuntu");
        variableMap.put("greetings", "Hello World");

        String stringVariablesReplaced = variablesReplacer.replaceVariables(null, variableMap);

        assertThat(stringVariablesReplaced == null, is(true));
    }

    @Test
    public void testVariablesIsNull() throws ScriptException, IOException {
        VariablesReplacer variablesReplacer = new VariablesReplacer();

        String stringVariablesReplaced = variablesReplacer.replaceVariables(yamlFileWithVariables, null);

        assertThat(stringVariablesReplaced, is(yamlFileWithVariables));
    }

    @Test
    public void testScriptAndVariablesIsNull() throws ScriptException, IOException {
        VariablesReplacer variablesReplacer = new VariablesReplacer();

        String stringVariablesReplaced = variablesReplacer.replaceVariables(null, null);

        assertThat(stringVariablesReplaced == null, is(true));
    }

    @Test
    public void testKeyIsNull() throws ScriptException, IOException {
        VariablesReplacer variablesReplacer = new VariablesReplacer();
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put(null, "EchoUbuntu");
        variableMap.put("container", "dockerfile/ubuntu");
        variableMap.put("greetings", "Hello World");

        String stringVariablesReplaced = variablesReplacer.replaceVariables(yamlFileWithVariables, variableMap);

        // Name will not be replaced
        assertThat(stringVariablesReplaced, containsString("$name:"));
    }

    @Test
    public void testValueIsNull() throws ScriptException, IOException {
        VariablesReplacer variablesReplacer = new VariablesReplacer();
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put("name", null);
        variableMap.put("container", "dockerfile/ubuntu");
        variableMap.put("greetings", "Hello World");

        String stringVariablesReplaced = variablesReplacer.replaceVariables(yamlFileWithVariables, variableMap);

        // Name will not be replaced
        assertThat(stringVariablesReplaced, containsString("$name:"));
    }
}
