package com.nirima.jenkins.plugins.docker.apidesc;

import com.github.dockerjava.api.command.BuildImageCmd;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.io.Serializable;
import java.net.URI;

/**
 * Plain Docker Build Describable. For splitting API wrapping and jenkins specific features.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerBuildDescribable implements Describable<DockerBuildDescribable>, Serializable {

//    /**
//     *  A Git repository URI or HTTP/HTTPS context URI. If the URI points to a single text file,
//     *  the file’s contents are placed into a file called Dockerfile and the image is built from that file.
//     *  If the URI points to a tarball, the file is downloaded by the daemon and the contents
//     *  therein used as the context for the build. If the URI points to a tarball and the dockerfile
//     *  parameter is also specified, there must be a file with the corresponding path inside the tarball.
//     */
//    @CheckForNull
//    private URI remote;

    /**
     * Suppress verbose build output. API
     */
    private boolean q;

    /**
     * Do not use the cache when building the image. API
     */
    private boolean nocache;

    /**
     * Attempt to pull the image even if an older image exists locally. API
     */
    private boolean pull;

    /**
     * Remove intermediate containers after a successful build (default behavior). API
     */
    private boolean rm = true;

//    /**
//     *  Always remove intermediate containers (includes rm). API
//     */
//    private boolean forcerm;
//
//    /**
//     *  Set memory limit for build. API
//     */
//    private long memory;
//
//    /**
//     * Total memory (memory + swap), -1 to disable swap. API
//     */
//    private long memswap;
//
//    /**
//     * CPU shares (relative weight).
//     */
//    private long cpushares;

    @DataBoundConstructor
    public DockerBuildDescribable() {
    }

//
//    /**
//     *  CPUs in which to allow execution (e.g., 0-3, 0,1).
//     */
//    @CheckForNull
//    private String cpusetcpus;
//
//    public URI getRemote() {
//        return remote;
//    }
//
//    public void setRemote(URI remote) {
//        this.remote = remote;
//    }

    public boolean isQ() {
        return q;
    }

    @DataBoundSetter
    public void setQ(boolean q) {
        this.q = q;
    }

    public boolean isNocache() {
        return nocache;
    }

    @DataBoundSetter
    public void setNocache(boolean nocache) {
        this.nocache = nocache;
    }

    public boolean isPull() {
        return pull;
    }

    @DataBoundSetter
    public void setPull(boolean pull) {
        this.pull = pull;
    }

    public boolean isRm() {
        return rm;
    }

    @DataBoundSetter
    public void setRm(boolean rm) {
        this.rm = rm;
    }

//    public boolean isForcerm() {
//        return forcerm;
//    }
//
//    public void setForcerm(boolean forcerm) {
//        this.forcerm = forcerm;
//    }
//
//    public long getMemory() {
//        return memory;
//    }
//
//    public void setMemory(long memory) {
//        this.memory = memory;
//    }
//
//    public long getMemswap() {
//        return memswap;
//    }
//
//    public void setMemswap(long memswap) {
//        this.memswap = memswap;
//    }
//
//    public long getCpushares() {
//        return cpushares;
//    }
//
//    public void setCpushares(long cpushares) {
//        this.cpushares = cpushares;
//    }
//
//    public String getCpusetcpus() {
//        return cpusetcpus;
//    }
//
//    public void setCpusetcpus(String cpusetcpus) {
//        this.cpusetcpus = cpusetcpus;
//    }

    public void fillBuildImageCmd(BuildImageCmd buildImageCmd) {
        buildImageCmd
                .withNoCache(isNocache())
                .withQuiet(isQ())
                .withPull(isPull());
    //                .withForceRm(isForcerm())
//                .withMemory(getMemory())

        if (!isRm()) {
            buildImageCmd.withRemove(false);
        }
    }

    @Override
    public Descriptor<DockerBuildDescribable> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(DockerBuildDescribable.class);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerBuildDescribable> {
        @Override
        public String getDisplayName() {
            return "Docker Build API Describable";
        }
    }
}
