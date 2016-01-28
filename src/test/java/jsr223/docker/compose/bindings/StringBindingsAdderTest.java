package jsr223.docker.compose.bindings;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.junit.Test;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class StringBindingsAdderTest {

    @Test
    public void testStringStringMapsAsVariables() throws ScriptException, IOException {
        Bindings bindings = new SimpleBindings();
        Map<String, String> variables = new HashMap<>();
        variables.put("name", "EchoUbuntu");
        variables.put("container", "dockerfile/ubuntu");
        variables.put("greetings", "Hello World");
        bindings.put("variables", variables);

    }

    @Test
    public void testAddStringVariables() throws ScriptException, IOException {
        StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

        Bindings bindings = new SimpleBindings();
        Map<String, String> variables = new HashMap<>();
        bindings.put("name", "EchoUbuntu");
        bindings.put("container", "dockerfile/ubuntu");
        bindings.put("greetings", "Hello World");

        stringBindingsAdder.addBindingToStringMap(bindings, variables);

        for (Map.Entry<String, String> expectedEntry : variables.entrySet()) {
            assertThat(variables, hasEntry(expectedEntry.getKey(), expectedEntry.getValue()));
        }
    }

    @Test(expected = NullPointerException.class)
    public void testKeyIsNull() throws ScriptException, IOException {
        StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

        Bindings bindings = new SimpleBindings();
        Map<String, String> variables = new HashMap<>();
        bindings.put("name", "EchoUbuntu");
        bindings.put(null, "dockerfile/ubuntu");

        stringBindingsAdder.addBindingToStringMap(bindings, variables);
    }

    @Test
    public void testValueIsNull() throws ScriptException, IOException {
        StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

        Bindings bindings = new SimpleBindings();
        Map<String, String> variables = new HashMap<>();
        bindings.put("name", "EchoUbuntu");
        bindings.put("container", null);

        stringBindingsAdder.addBindingToStringMap(bindings, variables);

        assertThat(variables, hasEntry("name", "EchoUbuntu"));
        assertThat(variables.containsKey("container"), is(false));
    }

    @Test
    public void testBindingsIsNull() throws ScriptException, IOException {
        StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

        Map<String, String> variables = new HashMap<>();

        stringBindingsAdder.addBindingToStringMap(null, variables);

        assertThat(variables.size(), is(0));
    }

    @Test
    public void testVariablesIsNull() throws ScriptException, IOException {
        StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

        Bindings bindings = new SimpleBindings();
        bindings.put("name", "EchoUbuntu");
        bindings.put("container", "dockerfile/ubuntu");

        stringBindingsAdder.addBindingToStringMap(bindings, null);

        assertThat(bindings.size(), is(2));
    }

    @Test
    public void testVariablesAndBindingsIsNull() throws ScriptException, IOException {
        StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(new MapBindingsAdder());

        stringBindingsAdder.addBindingToStringMap(null, null);
    }

    @Test
    public void testUseObjectBindingsAdderIFObjectIsPassed() throws ScriptException, IOException {
        MapBindingsAdder mapBindingsAdderMock = mock(MapBindingsAdder.class);
        doNothing().when(mapBindingsAdderMock).addEntryToEnvironmentOtherThanPureStrings(any(Map.class),
                any(Map.Entry.class));
        StringBindingsAdder stringBindingsAdder = new StringBindingsAdder(mapBindingsAdderMock);
        // Create bindings and variables
        Map<String, String> variables = new HashMap<>();
        Bindings bindings = new SimpleBindings();

        // Add objects to bindings
        bindings.put("int", new Integer(1));
        bindings.put("float", new Float(1.56));
        bindings.put("object", new Object());
        // Add pure strings, on those the object adder should not be called
        bindings.put("name", "EchoUbuntu");
        bindings.put("container", "dockerfile/ubuntu");

        stringBindingsAdder.addBindingToStringMap(bindings, variables);

        // Verify that the object adder was executed three times, for each of the not string objects.
        verify(mapBindingsAdderMock, times(3)).addEntryToEnvironmentOtherThanPureStrings(any(Map.class),
                any(Map.Entry.class));
    }

}