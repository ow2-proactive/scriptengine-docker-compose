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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;


@Log4j
public class DockerFilePropertyLoader {

    private final static String CONFIGURATION_FILE = "config/scriptengines/dockerfile.properties";

    public static final String DOCKER_FILE_COMMAND = "docker.file.command";

    public static final String DOCKER_FILE_SUDO_COMMAND = "docker.file.sudo.command";

    public static final String DOCKER_FILE_USE_SUDO = "docker.file.use.sudo";

    public static final String DOCKER_HOST = "docker.host";

    public static final String DOCKER_FILE_KEEP = "docker.file.keepimage";

    public static final String DOCKER_CONTAINER_KEEP = "docker.file.keepcontainer";

    @Getter
    @Setter
    private String dockerHost;

    @Getter
    @Setter
    private String dockerFileCommand;

    @Getter
    @Setter
    private String sudoCommand;

    @Getter
    @Setter
    private boolean useSudo;

    @Getter
    @Setter
    private boolean keepDockerFile = false;

    private Properties properties;

    private DockerFilePropertyLoader() {
        reload();
    }

    /**
     * Reload properties from the configuration file or system properties
     */
    public void reload() {
        properties = new Properties();
        log.debug("Load properties from configuration file: " + CONFIGURATION_FILE);
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIGURATION_FILE)) {
            properties.load(inputStream);
        } catch (IOException | NullPointerException e) {
            log.info("Configuration file " + CONFIGURATION_FILE +
                     " not found. Using system properties or standard values.");
            log.debug("Configuration file " + CONFIGURATION_FILE +
                      " not found. Using system properties or standard values.", e);
        }

        // Get property, specify default value
        this.dockerFileCommand = getOverridenProperty(DOCKER_FILE_COMMAND, "docker");
        // Get property, specify default value
        this.sudoCommand = getOverridenProperty(DOCKER_FILE_SUDO_COMMAND, "/usr/bin/sudo");
        // Get property, specify default value
        this.useSudo = Boolean.parseBoolean(getOverridenProperty(DOCKER_FILE_USE_SUDO, "false"));
        this.dockerHost = getOverridenProperty(DOCKER_HOST, "");
        this.keepDockerFile = Boolean.parseBoolean(getOverridenProperty(DOCKER_FILE_KEEP, "false"));
    }

    private String getOverridenProperty(String key, String defaultValue) {
        if (System.getProperty(key) != null) {
            return System.getProperty(key);
        } else {
            return properties.getProperty(key, defaultValue);
        }
    }

    public static DockerFilePropertyLoader getInstance() {
        return DockerFilePropertyLoaderHolder.INSTANCE;
    }

    /**
     * Initializes DockerFilePropertyLoader.
     * <p>
     * DockerFilePropertyLoaderHolder is loaded on the first execution of DockerFilePropertyLoader.getInstance()
     * or the first access to DockerFilePropertyLoaderHolder.INSTANCE, not before.
     **/
    private static class DockerFilePropertyLoaderHolder {
        private static final DockerFilePropertyLoader INSTANCE = new DockerFilePropertyLoader();

        private DockerFilePropertyLoaderHolder() {
        }
    }

}
