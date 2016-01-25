package jsr223.docker.compose;

import jsr223.docker.compose.utils.DockerComposeUtilities;
import lombok.extern.log4j.Log4j;
import processbuilder.SingletonProcessBuilderFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Log4j
public class DockerComposeScriptEngineFactory implements ScriptEngineFactory {

    // Script engine parameters
    private static final String NAME = "docker-compose";
    private static final String ENGINE = "docker-compose";
    private static final String ENGINE_VERSION;
    private static final String LANGUAGE = "yaml";

    private static final Map<String, Object> parameters = new HashMap<>();

    static {
        // The gradle.properties contains the current version. And the automated release process only updates
        // the gradle.properties file. Therefore we use this file to determine the version.
        Properties gradleProperties = new Properties();
        try {
            gradleProperties.load(DockerComposeScriptEngineFactory.class.getClassLoader().getResourceAsStream("version.properties"));
        } catch (Exception e) {
            log.warn("Could not load gradle.properties.");
            System.out.println("Could not load gradle.properties.");
        } finally {
            ENGINE_VERSION = gradleProperties.getProperty("version", "notSpecified");
        }
    }

    public DockerComposeScriptEngineFactory() {

        parameters.put(ScriptEngine.NAME, NAME);
        parameters.put(ScriptEngine.ENGINE_VERSION, ENGINE_VERSION);
        parameters.put(ScriptEngine.LANGUAGE, LANGUAGE);
        parameters.put(ScriptEngine.ENGINE, ENGINE);
    }


    @Override
    public String getEngineName() {
        return (String) parameters.get(ScriptEngine.NAME);
    }

    @Override
    public String getEngineVersion() {
        return (String) parameters.get(ScriptEngine.ENGINE_VERSION);
    }

    @Override
    public List<String> getExtensions() {
        return Arrays.asList("yml", "yaml");
    }

    @Override
    public List<String> getMimeTypes() {
        return Arrays.asList("text/yaml");
    }

    @Override
    public List<String> getNames() {
        return Arrays.asList("docker-compose", "fig");
    }

    @Override
    public String getLanguageName() {
        return (String) parameters.get(ScriptEngine.LANGUAGE);
    }

    @Override
    public String getLanguageVersion() {
        return DockerComposeUtilities.
                getDockerComposeVersion(SingletonProcessBuilderFactory.getInstance());
    }

    @Override
    public Object getParameter(String key) {
        return parameters.get(key);
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args) {
        return null;
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        return null;
    }

    @Override
    public String getProgram(String... statements) {
        return null;
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new DockerComposeScriptEngine();
    }
}
