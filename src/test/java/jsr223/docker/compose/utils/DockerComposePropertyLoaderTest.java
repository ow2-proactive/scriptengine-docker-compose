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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;


public class DockerComposePropertyLoaderTest {

    @Test
    public void loadPropertiesFromPropertyFile() {
        DockerComposePropertyLoader.getInstance().reload();
        boolean isMacOrWindows = System.getProperty("os.name").toLowerCase().contains("windows") ||
                                 System.getProperty("os.name").toLowerCase().contains("mac os");
        if (isMacOrWindows) {
            assertThat(DockerComposePropertyLoader.getInstance().getDockerComposeCommand(), is("test-compose-win"));
        } else {
            assertThat(DockerComposePropertyLoader.getInstance().getDockerComposeCommand(), is("test-compose"));
        }
        assertThat(DockerComposePropertyLoader.getInstance().getSudoCommand(), is("test-sudo"));
        assertThat(DockerComposePropertyLoader.getInstance().getDockerHost(), is("test-test"));
        assertThat(DockerComposePropertyLoader.getInstance().isUseSudo(), is(Boolean.TRUE));
        assertThat(DockerComposePropertyLoader.getInstance().isKeepDockerFile(), is(Boolean.FALSE));
    }

    @Test
    public void loadSystemPropertiesOverridePropertyFile() {
        System.setProperty(DockerComposePropertyLoader.DOCKER_COMPOSE_SUDO_COMMAND, "test2-sudo");
        DockerComposePropertyLoader.getInstance().reload();
        assertThat(DockerComposePropertyLoader.getInstance().getSudoCommand(), is("test2-sudo"));
        assertThat(DockerComposePropertyLoader.getInstance().getDockerHost(), is("test-test"));
    }
}
