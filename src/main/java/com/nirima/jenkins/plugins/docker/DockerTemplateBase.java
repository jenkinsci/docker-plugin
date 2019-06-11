package com.nirima.jenkins.plugins.docker;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.core.NameParser;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;

/**
 * Base for docker templates - does not include Jenkins items like labels.
 */
public class DockerTemplateBase implements Describable<DockerTemplateBase>, Serializable {

    private final String image;

    private String pullCredentialsId;
    private transient DockerRegistryEndpoint registry;

    /**
     * Field dockerCommand
     */
    private String dockerCommand;

    public String hostname;

    public String[] dnsHosts;

    public String network;

    /**
     * Every String is volume specification
     */
    public String[] volumes;

    /**
     * @deprecated use {@link #volumesFrom2}
     */
    @Deprecated
    public String volumesFrom;

    /**
     * Every String is volumeFrom specification
     */
    public String[] volumesFrom2;

    /**
     * Every String is a device to be mapped
     */
    public String[] devices;

    @CheckForNull
    public String[] environment;

    public String bindPorts;
    public boolean bindAllPorts;

    public Integer memoryLimit;
    public Integer memorySwap;
    public Integer cpuShares;
    public Integer shmSize;

    public boolean privileged;
    public boolean tty;

    @CheckForNull
    private String macAddress;

    @CheckForNull
    private List<String> extraHosts;

    public boolean usingNodeNameAsContainerName;

    @DataBoundConstructor
    public DockerTemplateBase(String image) {
        if (image == null) {
            throw new IllegalArgumentException("Image can't be null");
        }

        this.image = image.trim();
    }

    /**
     * @deprecated use DataBoundSetters
     */
    @Deprecated
    public DockerTemplateBase(String image,
                              String pullCredentialsId,
                              String dnsString,
                              String network,
                              String dockerCommand,
                              String volumesString,
                              String volumesFromString,
                              String environmentsString,
                              String hostname,
                              Integer memoryLimit,
                              Integer memorySwap,
                              Integer cpuShares,
                              Integer shmSize,
                              String bindPorts,
                              boolean bindAllPorts,
                              boolean privileged,
                              boolean tty,
                              String macAddress,
                              String extraHostsString
    ) {
        this(image);
        setPullCredentialsId(pullCredentialsId);
        setDnsString(dnsString);
        setNetwork(network);
        setDockerCommand(dockerCommand);
        setVolumesString(volumesString);
        setVolumesFromString(volumesFromString);
        setEnvironmentsString(environmentsString);
        setHostname(hostname);
        setMemoryLimit(memoryLimit);
        setMemorySwap(memorySwap);
        setCpuShares(cpuShares);
        setShmSize(shmSize);
        setBindPorts(bindPorts);
        setBindAllPorts(bindAllPorts);
        setPrivileged(privileged);
        setTty(tty);
        setMacAddress(macAddress);
        setExtraHostsString(extraHostsString);
    }

    protected Object readResolve() {
        if (volumesFrom != null) {
            if (StringUtils.isNotBlank(volumesFrom)) {
                setVolumesFrom2(new String[]{volumesFrom});
            }
            volumesFrom = null;
        }
        if (pullCredentialsId == null && registry != null) {
            pullCredentialsId = registry.getCredentialsId();
        }

        return this;
    }

    //TODO move/replace
    public static String[] splitAndFilterEmpty(String s, String separator) {
        if (s == null) {
            return new String[0];
        }
        List<String> result = new ArrayList<>();
        for (String o : Splitter.on(separator).omitEmptyStrings().split(s)) {
            result.add(o);
        }
        return result.toArray(new String[result.size()]);
    }

    public static List<String> splitAndFilterEmptyList(String s, String separator) {
        List<String> result = new ArrayList<>();
        for (String o : Splitter.on(separator).omitEmptyStrings().split(s)) {
            result.add(o);
        }
        return result;
    }

    //TODO move/replace
    public static String[] filterStringArray(String[] arr) {
        final ArrayList<String> strings = new ArrayList<>();
        if (arr != null) {
            for (String s : arr) {
                s = StringUtils.stripToNull(s);
                if (s != null) {
                    strings.add(s);
                }
            }
        }
        return strings.toArray(new String[strings.size()]);
    }

    public String getImage() {
        return image.trim();
    }

    public String getPullCredentialsId() {
        return pullCredentialsId;
    }

    @DataBoundSetter
    public void setPullCredentialsId(String pullCredentialsId) {
        this.pullCredentialsId = pullCredentialsId;
    }

    public String getDockerCommand() {
        return dockerCommand;
    }

    @DataBoundSetter
    public void setDockerCommand(String dockerCommand) {
        this.dockerCommand = dockerCommand;
    }

    public String getHostname() {
        return hostname;
    }

    @DataBoundSetter
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getDnsString() {
        if (dnsHosts == null) return null;
        return Joiner.on(" ").join(dnsHosts);
    }

    @DataBoundSetter
    public void setDnsString(String dnsString) {
        this.dnsHosts = splitAndFilterEmpty(dnsString, " ");
    }

    public String getNetwork() {
        return network;
    }

    @DataBoundSetter
    public void setNetwork(String network) {
        this.network = network;
    }

    @CheckForNull
    public String[] getVolumes() {
        return filterStringArray(volumes);
    }

    public void setVolumes(String[] volumes) {
        this.volumes = volumes;
    }

    public String getVolumesString() {
        if (volumes == null) return null;
        return Joiner.on("\n").join(volumes);
    }

    @DataBoundSetter
    public void setVolumesString(String volumesString) {
        this.volumes = splitAndFilterEmpty(volumesString, "\n");
    }

    public String getVolumesFromString() {
        return Joiner.on("\n").join(getVolumesFrom2());
    }

    @DataBoundSetter
    public void setVolumesFromString(String volumesFromString) {
        setVolumesFrom2(splitAndFilterEmpty(volumesFromString, "\n"));
    }

    @CheckForNull
    public String[] getDevices() {
        return filterStringArray(devices);
    }

    public String getDevicesString() {
        if (devices == null) return null;
        return Joiner.on("\n").join(devices);
    }

    @DataBoundSetter
    public void setDevicesString(String devicesString) {
        this.devices = splitAndFilterEmpty(devicesString, "\n");
    }

    public String getEnvironmentsString() {
        if (environment == null) return null;
        return Joiner.on("\n").join(environment);
    }

    @DataBoundSetter
    public void setEnvironmentsString(String environmentsString) {
        this.environment = splitAndFilterEmpty(environmentsString, "\n");
    }

    public String getBindPorts() {
        return bindPorts;
    }

    @DataBoundSetter
    public void setBindPorts(String bindPorts) {
        this.bindPorts = bindPorts;
    }

    public boolean isBindAllPorts() {
        return bindAllPorts;
    }

    @DataBoundSetter
    public void setBindAllPorts(boolean bindAllPorts) {
        this.bindAllPorts = bindAllPorts;
    }

    public Integer getMemoryLimit() {
        return memoryLimit;
    }

    @DataBoundSetter
    public void setMemoryLimit(Integer memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public Integer getMemorySwap() {
        return memorySwap;
    }

    @DataBoundSetter
    public void setMemorySwap(Integer memorySwap) {
        this.memorySwap = memorySwap;
    }

    public Integer getCpuShares() {
        return cpuShares;
    }

    @DataBoundSetter
    public void setCpuShares(Integer cpuShares) {
        this.cpuShares = cpuShares;
    }

    public Integer getShmSize() {
        return shmSize;
    }

    @DataBoundSetter
    public void setShmSize(Integer shmSize) {
        this.shmSize = shmSize;
    }

    public boolean isPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    public boolean isTty() {
        return tty;
    }

    @DataBoundSetter
    public void setTty(boolean tty) {
        this.tty = tty;
    }

    @CheckForNull
    public String getMacAddress() {
        return trimToNull(macAddress);
    }

    @DataBoundSetter
    public void setMacAddress(String macAddress) {
        this.macAddress = trimToNull(macAddress);
    }

    @DataBoundSetter
    public void setExtraHostsString(String extraHostsString) {
        setExtraHosts(isEmpty(extraHostsString) ?
                Collections.EMPTY_LIST :
                splitAndFilterEmptyList(extraHostsString, "\n"));
    }

    public String getExtraHostsString() {
        if (extraHosts == null) {
            return "";
        }
        return Joiner.on("\n").join(extraHosts);
    }

    public boolean isUsingNodeNameAsContainerName() {
        return usingNodeNameAsContainerName;
    }

    @DataBoundSetter
    public void setUsingNodeNameAsContainerName(boolean usingNodeNameAsContainerName) {
        this.usingNodeNameAsContainerName = usingNodeNameAsContainerName;
    }

    // -- UI binding End


    public DockerRegistryEndpoint getRegistry() {
        if (registry == null) {
            registry = new DockerRegistryEndpoint(null, pullCredentialsId);
        }
        return registry;
    }

    /**
     * @deprecated use {@link #getVolumesFrom2()}
     */
    @Deprecated
    public String getVolumesFrom() {
        return volumesFrom;
    }

    public String[] getVolumesFrom2() {
        return filterStringArray(volumesFrom2);
    }

    public void setVolumesFrom2(String[] volumes) {
        this.volumesFrom2 = volumes;
    }


    @CheckForNull
    public List<String> getExtraHosts() {
        return extraHosts;
    }

    public void setExtraHosts(List<String> extraHosts) {
        this.extraHosts = extraHosts;
    }


    public String getDisplayName() {
        return "Image of " + getImage();
    }


    public String[] getDockerCommandArray() {
        String[] dockerCommandArray = new String[0];
        if (dockerCommand != null && !dockerCommand.isEmpty()) {
            dockerCommandArray = dockerCommand.split(" ");
        }

        return dockerCommandArray;
    }

    public Iterable<PortBinding> getPortMappings() {

        if (Strings.isNullOrEmpty(bindPorts)) {
            return Collections.emptyList();
        }

        return Iterables.transform(Splitter.on(' ')
                        .trimResults()
                        .omitEmptyStrings()
                        .split(bindPorts),
                new Function<String, PortBinding>() {
                    @Nullable
                    @Override
                    public PortBinding apply(String s) {
                        return PortBinding.parse(s);
                    }
                });
    }

    public CreateContainerCmd fillContainerConfig(CreateContainerCmd containerConfig) {
        if (hostname != null && !hostname.isEmpty()) {
            containerConfig.withHostName(hostname);
        }

        String[] cmd = getDockerCommandArray();
        if (cmd.length > 0) {
            containerConfig.withCmd(cmd);
        }

        containerConfig.withPortBindings(Iterables.toArray(getPortMappings(), PortBinding.class));

        containerConfig.withPublishAllPorts(bindAllPorts);

        containerConfig.withPrivileged(privileged);

        Map<String,String> map = new HashMap<>();
        map.put(DockerContainerLabelKeys.JENKINS_INSTANCE_ID, getJenkinsInstanceIdForContainerLabel());
        map.put(DockerContainerLabelKeys.JENKINS_URL, getJenkinsUrlForContainerLabel());
        map.put(DockerContainerLabelKeys.CONTAINER_IMAGE, getImage());

        containerConfig.withLabels(map);

        if (cpuShares != null && cpuShares > 0) {
            containerConfig.withCpuShares(cpuShares);
        }

        if (memoryLimit != null && memoryLimit > 0) {
            Long memoryInByte = (long) memoryLimit * 1024 * 1024;
            containerConfig.withMemory(memoryInByte);
        }

        if (memorySwap != null) {
            if (memorySwap > 0) {
                Long memorySwapInByte = (long) memorySwap * 1024 * 1024;
                containerConfig.withMemorySwap(memorySwapInByte);
            } else {
                containerConfig.withMemorySwap(memorySwap.longValue());
            }
        }

        if (dnsHosts != null && dnsHosts.length > 0) {
            containerConfig.withDns(dnsHosts);
        }

        if (network != null && network.length() > 0) {
            containerConfig.withNetworkDisabled(false);
            containerConfig.withNetworkMode(network);
        }

        // https://github.com/docker/docker/blob/ed257420025772acc38c51b0f018de3ee5564d0f/runconfig/parse.go#L182-L196
        if (getVolumes().length > 0) {
            ArrayList<Volume> vols = new ArrayList<>();
            ArrayList<Bind> binds = new ArrayList<>();

            for (String vol : getVolumes()) {
                final String[] group = vol.split(":");
                if (group.length > 1) {
                    if (group[1].equals("/")) {
                        throw new IllegalArgumentException("Invalid bind mount: destination can't be '/'");
                    }

                    binds.add(Bind.parse(vol));
                } else if (vol.equals("/")) {
                    throw new IllegalArgumentException("Invalid volume: path can't be '/'");
                } else {
                    vols.add(new Volume(vol));
                }
            }

            containerConfig.withVolumes(vols.toArray(new Volume[vols.size()]));
            containerConfig.withBinds(binds.toArray(new Bind[binds.size()]));
        }

        if (getVolumesFrom2().length > 0) {
            ArrayList<VolumesFrom> volFrom = new ArrayList<>();
            for (String volFromStr : getVolumesFrom2()) {
                volFrom.add(new VolumesFrom(volFromStr));
            }

            containerConfig.withVolumesFrom(volFrom.toArray(new VolumesFrom[volFrom.size()]));
        }

        if (getDevices().length > 0) {
            ArrayList<Device> devices = new ArrayList<>();
            for (String deviceStr : getDevices()) {
                devices.add(Device.parse(deviceStr));
            }

            containerConfig.withDevices(devices);
        }

        containerConfig.withTty(tty);

        if (environment != null && environment.length > 0) {
            containerConfig.withEnv(environment);
        }

        if (getMacAddress() != null) {
            containerConfig.withMacAddress(getMacAddress());
        }

        final List<String> extraHosts = getExtraHosts();
        if (CollectionUtils.isNotEmpty(extraHosts)) {
            containerConfig.withExtraHosts(extraHosts.toArray(new String[extraHosts.size()]));
        }

        if (shmSize != null && shmSize > 0) {
            final Long shmSizeInByte = shmSize * 1024L * 1024L;
            containerConfig.getHostConfig().withShmSize(shmSizeInByte);
        }

        return containerConfig;
    }


    /**
     * Calculates the value we use for the Docker label called
     * {@link DockerContainerLabelKeys#JENKINS_URL} that we put into every
     * container we make, so that we can recognize our own containers later.
     */
    static String getJenkinsUrlForContainerLabel() {
        final Jenkins jenkins = Jenkins.getInstance();
        final String rootUrl = jenkins == null ? null : jenkins.getRootUrl();
        return Util.fixNull(rootUrl);
    }

    /**
     * Calculates the value we use for the Docker label called
     * {@link DockerContainerLabelKeys#JENKINS_INSTANCE_ID} that we put into every
     * container we make, so that we can recognize our own containers later.
     */
    static String getJenkinsInstanceIdForContainerLabel() {
        return JenkinsUtils.getInstanceId();
    }

    @Override
    public Descriptor<DockerTemplateBase> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(DockerTemplateBase.class);
    }

    public String getFullImageId() {
        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(image);
        // if image was specified without tag, then treat as latest
        return repostag.repos + ":" + (repostag.tag.isEmpty() ? "latest" : repostag.tag);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DockerTemplateBase{");
        sb.append("image='").append(image).append('\'');
        sb.append(", pullCredentialsId='").append(pullCredentialsId).append('\'');
        sb.append(", registry=").append(registry);
        sb.append(", dockerCommand='").append(dockerCommand).append('\'');
        sb.append(", hostname='").append(hostname).append('\'');
        sb.append(", dnsHosts=").append(Arrays.toString(dnsHosts));
        sb.append(", network='").append(network).append('\'');
        sb.append(", volumes=").append(Arrays.toString(volumes));
        sb.append(", volumesFrom2=").append(Arrays.toString(volumesFrom2));
        sb.append(", environment=").append(Arrays.toString(environment));
        sb.append(", bindPorts='").append(bindPorts).append('\'');
        sb.append(", bindAllPorts=").append(bindAllPorts);
        sb.append(", memoryLimit=").append(memoryLimit);
        sb.append(", memorySwap=").append(memorySwap);
        sb.append(", cpuShares=").append(cpuShares);
        sb.append(", shmSize=").append(shmSize);
        sb.append(", privileged=").append(privileged);
        sb.append(", tty=").append(tty);
        sb.append(", macAddress='").append(macAddress).append('\'');
        sb.append(", extraHosts=").append(extraHosts);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerTemplateBase that = (DockerTemplateBase) o;

        if (bindAllPorts != that.bindAllPorts) return false;
        if (privileged != that.privileged) return false;
        if (tty != that.tty) return false;
        if (!image.equals(that.image)) return false;
        if (pullCredentialsId != null ? !pullCredentialsId.equals(that.pullCredentialsId) : that.pullCredentialsId != null)
            return false;
        if (registry != null ? !registry.equals(that.registry) : that.registry != null) return false;
        if (dockerCommand != null ? !dockerCommand.equals(that.dockerCommand) : that.dockerCommand != null)
            return false;
        if (hostname != null ? !hostname.equals(that.hostname) : that.hostname != null) return false;
        if (!Arrays.equals(dnsHosts, that.dnsHosts)) return false;
        if (network != null ? !network.equals(that.network) : that.network != null) return false;
        if (!Arrays.equals(volumes, that.volumes)) return false;
        if (!Arrays.equals(volumesFrom2, that.volumesFrom2)) return false;
        if (!Arrays.equals(environment, that.environment)) return false;
        if (bindPorts != null ? !bindPorts.equals(that.bindPorts) : that.bindPorts != null) return false;
        if (memoryLimit != null ? !memoryLimit.equals(that.memoryLimit) : that.memoryLimit != null) return false;
        if (memorySwap != null ? !memorySwap.equals(that.memorySwap) : that.memorySwap != null) return false;
        if (cpuShares != null ? !cpuShares.equals(that.cpuShares) : that.cpuShares != null) return false;
        if (shmSize != null ? !shmSize.equals(that.shmSize) : that.shmSize != null) return false;
        if (macAddress != null ? !macAddress.equals(that.macAddress) : that.macAddress != null) return false;
        return extraHosts != null ? extraHosts.equals(that.extraHosts) : that.extraHosts == null;
    }

    @Override
    public int hashCode() {
        int result = image.hashCode();
        result = 31 * result + (pullCredentialsId != null ? pullCredentialsId.hashCode() : 0);
        result = 31 * result + (registry != null ? registry.hashCode() : 0);
        result = 31 * result + (dockerCommand != null ? dockerCommand.hashCode() : 0);
        result = 31 * result + (hostname != null ? hostname.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(dnsHosts);
        result = 31 * result + (network != null ? network.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(volumes);
        result = 31 * result + Arrays.hashCode(volumesFrom2);
        result = 31 * result + Arrays.hashCode(environment);
        result = 31 * result + (bindPorts != null ? bindPorts.hashCode() : 0);
        result = 31 * result + (bindAllPorts ? 1 : 0);
        result = 31 * result + (memoryLimit != null ? memoryLimit.hashCode() : 0);
        result = 31 * result + (memorySwap != null ? memorySwap.hashCode() : 0);
        result = 31 * result + (cpuShares != null ? cpuShares.hashCode() : 0);
        result = 31 * result + (shmSize != null ? shmSize.hashCode() : 0);
        result = 31 * result + (privileged ? 1 : 0);
        result = 31 * result + (tty ? 1 : 0);
        result = 31 * result + (macAddress != null ? macAddress.hashCode() : 0);
        result = 31 * result + (extraHosts != null ? extraHosts.hashCode() : 0);
        return result;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerTemplateBase> {

        public FormValidation doCheckVolumesString(@QueryParameter String volumesString) {
            try {
                final String[] strings = splitAndFilterEmpty(volumesString, "\n");
                for (String s : strings) {
                    if (s.equals("/")) {
                        return FormValidation.error("Invalid volume: path can't be '/'");
                    }

                    final String[] group = s.split(":");
                    if (group.length > 3) {
                        return FormValidation.error("Wrong syntax: " + s);
                    } else if (group.length == 2 || group.length == 3) {
                        if (group[1].equals("/")) {
                            return FormValidation.error("Invalid bind mount: destination can't be '/'");
                        }
                        Bind.parse(s);
                    } else if (group.length == 1) {
                        new Volume(s);
                    } else {
                        return FormValidation.error("Wrong line: " + s);
                    }
                }
            } catch (Throwable t) {
                return FormValidation.error(t.getMessage());
            }

            return FormValidation.ok();

        }

        public FormValidation doCheckVolumesFromString(@QueryParameter String volumesFromString) {
            try {
                final String[] strings = splitAndFilterEmpty(volumesFromString, "\n");
                for (String volFrom : strings) {
                    VolumesFrom.parse(volFrom);
                }
            } catch (Throwable t) {
                return FormValidation.error(t.getMessage());
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckExtraHostsString(@QueryParameter String extraHostsString) {
            final List<String> extraHosts = splitAndFilterEmptyList(extraHostsString, "\n");
            for (String extraHost : extraHosts) {
                if (extraHost.trim().split(":").length < 2) {
                    return FormValidation.error("Wrong extraHost {}", extraHost);
                }
            }

            return FormValidation.ok();
        }


        public ListBoxModel doFillPullCredentialsIdItems(@AncestorInPath Item item) {
            final DockerRegistryEndpoint.DescriptorImpl descriptor =
                    (DockerRegistryEndpoint.DescriptorImpl)
                            Jenkins.getInstance().getDescriptorOrDie(DockerRegistryEndpoint.class);
            return descriptor.doFillCredentialsIdItems(item);
        }


        public static ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {

            AccessControlled ac = (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (!ac.hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }

            return new SSHUserListBoxModel().withMatching(
                    SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            context,
                            ACL.SYSTEM,
                            SSHLauncher.SSH_SCHEME)
            );
        }

        @Override
        public String getDisplayName() {
            return "Docker template base";
        }
    }

}
