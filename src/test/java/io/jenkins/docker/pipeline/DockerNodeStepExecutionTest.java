/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.docker.pipeline;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.io.Serializable;

import org.junit.Test;

import io.jenkins.docker.connector.DockerComputerAttachConnector;
import io.jenkins.docker.connector.DockerComputerConnector;
import io.jenkins.docker.connector.DockerComputerJNLPConnector;
import io.jenkins.docker.connector.DockerComputerSSHConnector;

public class DockerNodeStepExecutionTest {

    @Test
    public void testIsSerializableDockerComputerConnector() throws Exception {
        // Given
        final Serializable serialisable = new Serializable() {
            @Override
            public String toString() {
                return "serialisableButNotConnector";
            }
        };
        final Object neither = new String("neither");
        final DockerComputerConnector attach = new DockerComputerAttachConnector() {
            @Override
            public String toString() {
                return "attach";
            }
        };
        final DockerComputerConnector jnlp = new DockerComputerJNLPConnector(null) {
            @Override
            public String toString() {
                return "jnlpNotSerializable";
            }
        };
        final DockerComputerConnector ssh = new DockerComputerSSHConnector(null) {
            @Override
            public String toString() {
                return "sshNotSerializable";
            }
        };
        final Object[] unusables = { serialisable, neither, jnlp, ssh };

        // When
        final String attachReason = DockerNodeStepExecution
                .getReasonWhyThisIsNotASerializableDockerComputerConnector(attach.toString(), attach.getClass());
        final String serialisableReason = DockerNodeStepExecution
                .getReasonWhyThisIsNotASerializableDockerComputerConnector(serialisable.toString(),
                        serialisable.getClass());
        final String neitherReason = DockerNodeStepExecution
                .getReasonWhyThisIsNotASerializableDockerComputerConnector(neither.toString(), neither.getClass());
        final String jnlpReason = DockerNodeStepExecution
                .getReasonWhyThisIsNotASerializableDockerComputerConnector(jnlp.toString(), jnlp.getClass());
        final String sshReason = DockerNodeStepExecution
                .getReasonWhyThisIsNotASerializableDockerComputerConnector(ssh.toString(), ssh.getClass());

        // Then
        assertNull(attachReason);
        assertNotNull(serialisableReason);
        assertNotNull(neitherReason);
        assertNotNull(jnlpReason);
        assertNotNull(sshReason);
        DockerNodeStepExecution.assertIsSerializableDockerComputerConnector(attach);
        for (final Object unusable : unusables) {
            final String what = unusable.toString();
            try {
                DockerNodeStepExecution.assertIsSerializableDockerComputerConnector(unusable);
            } catch (IllegalArgumentException ex) {
                final String actualMsg = ex.getMessage();
                assertThat(actualMsg, containsString(what));
            }
        }
    }
}
