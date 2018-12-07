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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import org.objectweb.proactive.utils.OperatingSystem;


@Log4j
public class DockerComposePropertyLoader {

    private final static String CONFIGURATION_FILE = "config/scriptengines/docker-compose.properties";

    public static final String DOCKER_COMPOSE_COMMAND = "docker.compose.command";

    public static final String DOCKER_COMPOSE_COMMAND_WINDOWS = "docker.compose.command.windows";

    public static final String DOCKER_COMPOSE_SUDO_COMMAND = "docker.compose.sudo.command";

    public static final String DOCKER_COMPOSE_USE_SUDO = "docker.compose.use.sudo";

    public static final String DOCKER_HOST = "docker.host";

    public static final String DOCKER_FILE_KEEP = "docker.file.keep";

    @Getter
    @Setter
    private String dockerHost;

    @Getter
    @Setter
    private String dockerComposeCommand;

    @Getter
    @Setter
    private String sudoCommand;

    @Getter
    @Setter
    private boolean useSudo;

    @Getter
    @Setter
    private boolean keepDockerFile;

    private Properties properties;

    private DockerComposePropertyLoader() {
        reload();
    }

    /**
     * Reload properties from the configuration file or system properties
     */
    public void reload() {
        properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIGURATION_FILE)) {
            log.debug("Load properties from configuration file: " + CONFIGURATION_FILE);
            properties.load(inputStream);
        } catch (IOException | NullPointerException e) {
            log.info("Configuration file " + CONFIGURATION_FILE +
                     " not found. Using system properties or standard values.");
            log.debug("Configuration file " + CONFIGURATION_FILE +
                      " not found. Using system properties or standard values.", e);
        }

        boolean isMacOrWindows = System.getProperty("os.name").toLowerCase()
                .contains("windows") || System.getProperty("os.name").toLowerCase().contains("mac os");

        // Get property, specify default value
        this.dockerComposeCommand = isMacOrWindows
                 ? getOverridenProperty(DOCKER_COMPOSE_COMMAND_WINDOWS,
                                                          "docker-compose") : getOverridenProperty(DOCKER_COMPOSE_COMMAND,
                        "/usr/local/bin/docker-compose");
        // Get property, specify default value
        this.sudoCommand = getOverridenProperty(DOCKER_COMPOSE_SUDO_COMMAND, "/usr/bin/sudo");
        // Get property, specify default value
        this.useSudo = Boolean.parseBoolean(getOverridenProperty(DOCKER_COMPOSE_USE_SUDO, "false"));
        this.dockerHost = getOverridenProperty(DOCKER_HOST, "");
        this.keepDockerFile = Boolean.parseBoolean(getOverridenProperty(DOCKER_FILE_KEEP, "true"));
    }

    private String getOverridenProperty(String key, String defaultValue) {
        if (System.getProperty(key) != null) {
            return System.getProperty(key);
        } else {
            return properties.getProperty(key, defaultValue);
        }
    }

    public static DockerComposePropertyLoader getInstance() {
        return DockerComposePropertyLoaderHolder.INSTANCE;
    }

    /**
     * Initializes DockerComposePropertyLoader.
     * <p>
     * DockerComposePropertyLoaderHolder is loaded on the first execution of DockerComposePropertyLoader.getInstance()
     * or the first access to DockerComposePropertyLoaderHolder.INSTANCE, not before.
     **/
    private static class DockerComposePropertyLoaderHolder {
        private static final DockerComposePropertyLoader INSTANCE = new DockerComposePropertyLoader();

        private DockerComposePropertyLoaderHolder() {
        }
    }
}
