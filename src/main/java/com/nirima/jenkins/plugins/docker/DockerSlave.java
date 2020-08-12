package com.nirima.jenkins.plugins.docker;

import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;

import javax.annotation.Nonnull;
import java.io.IOException;


/**
 * @deprecated use {@link DockerTransientNode}
 */
@Deprecated
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="SE_NO_SERIALVERSIONID", justification="Deprecated; required for backwards compatibility only.")
public class DockerSlave extends Slave {

    private transient  DockerTemplate dockerTemplate;

    private transient String containerId;

    @SuppressWarnings("unused")
    private transient String cloudId;

    @SuppressWarnings("unused")
    private transient DockerAPI dockerAPI;

    private DockerSlave(@Nonnull String name, String remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super(name, remoteFS, launcher);
    }

    @Override
    protected Object readResolve() {
        try {
            return new DockerTransientNode(containerId, containerId, dockerTemplate.remoteFs, getLauncher());
        } catch (Descriptor.FormException | IOException e) {
            throw new RuntimeException("Failed to migrate " + DockerSlave.class.getCanonicalName(), e);
        }
    }
}
