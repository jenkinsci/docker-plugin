package com.nirima.jenkins.plugins.docker;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * @deprecated use {@link DockerTransientNode}
 */
@Deprecated
@SuppressFBWarnings(
        value = "SE_NO_SERIALVERSIONID",
        justification = "Deprecated; required for backwards compatibility only.")
public class DockerSlave extends Slave {

    private transient DockerTemplate dockerTemplate;

    private transient String containerId;

    @SuppressWarnings("unused")
    private transient String cloudId;

    @SuppressWarnings("unused")
    private transient DockerAPI dockerAPI;

    private DockerSlave(@NonNull String name, String remoteFS, ComputerLauncher launcher)
            throws Descriptor.FormException, IOException {
        super(name, remoteFS, launcher);
    }

    @Override
    protected Object readResolve() {
        try {
            return new DockerTransientNode(containerId, containerId, dockerTemplate.remoteFs, getLauncher());
        } catch (Descriptor.FormException e) {
            throw new RuntimeException("Failed to migrate " + DockerSlave.class.getCanonicalName(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to migrate " + DockerSlave.class.getCanonicalName(), e);
        }
    }
}
