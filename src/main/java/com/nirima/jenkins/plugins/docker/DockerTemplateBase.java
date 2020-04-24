package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
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
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.bldToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.endToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.startToString;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;

/**
 * Base for docker templates - does not include Jenkins items like labels.
 */
public class DockerTemplateBase implements Describable<DockerTemplateBase>, Serializable {
    private static final long serialVersionUID = 1838584884066776725L;

    private final String image;

    private String pullCredentialsId;
    private transient DockerRegistryEndpoint registry;

    /**
     * Field dockerCommand
     */
    private String dockerCommand;

    public String hostname;

    /**
     * --user argument to docker run command.
     */
    private String user;

    /**
     * --group-add argument to docker run command.
     */
    private List<String> extraGroups;

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

    public @CheckForNull String[] environment;

    public String bindPorts;
    public boolean bindAllPorts;

    public Integer memoryLimit;
    public Integer memorySwap;
    public Integer cpuShares;
    public Integer shmSize;

    public boolean privileged;
    public boolean tty;

    private @CheckForNull String macAddress;

    private @CheckForNull List<String> extraHosts;

    private @CheckForNull List<String> securityOpts;

    private @CheckForNull List<String> capabilitiesToAdd;
    private @CheckForNull List<String> capabilitiesToDrop;

    private @CheckForNull Map<String, String> extraDockerLabels;

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
                              String user,
                              String extraGroupsString,
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
        setUser(user);
        setExtraGroupsString(extraGroupsString);
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

    private static Map<String, String> splitAndFilterEmptyMap(String s, String separator) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String o : Splitter.on(separator).omitEmptyStrings().split(s)) {
            String[] parts = o.trim().split("=", 2);
            if (parts.length == 2)
                result.put(parts[0].trim(), parts[1].trim());
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

    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    public void setExtraGroups(List<String> extraGroups) {
        this.extraGroups = extraGroups;
    }

    @DataBoundSetter
    public void setExtraGroupsString(String extraGroupsString) {
        setExtraGroups(isEmpty(extraGroupsString) ?
                Collections.EMPTY_LIST :
                splitAndFilterEmptyList(extraGroupsString, "\n"));
    }

    public String getExtraGroupsString() {
        if (extraGroups == null) {
            return "";
        }
        return Joiner.on("\n").join(extraGroups);
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

    @CheckForNull
    public List<String> getExtraHosts() {
        return extraHosts;
    }

    public String getExtraHostsString() {
        if (extraHosts == null) {
            return "";
        }
        return Joiner.on("\n").join(extraHosts);
    }

    public void setExtraHosts(List<String> extraHosts) {
        this.extraHosts = extraHosts;
    }

    @DataBoundSetter
    public void setExtraHostsString(String extraHostsString) {
        setExtraHosts(isEmpty(extraHostsString) ?
                Collections.EMPTY_LIST :
                splitAndFilterEmptyList(extraHostsString, "\n"));
    }

    @CheckForNull
    public List<String> getSecurityOpts() {
        return securityOpts;
    }

    @CheckForNull
    public String getSecurityOptsString() {
        return securityOpts == null ? null : Joiner.on("\n").join(securityOpts);
    }

    public void setSecurityOpts( List<String> securityOpts ) {
        this.securityOpts = securityOpts;
    }

    @DataBoundSetter
    public void setSecurityOptsString(String securityOpts) {
        setSecurityOpts( isEmpty(securityOpts) ? null : splitAndFilterEmptyList(securityOpts, "\n") );
    }

    @CheckForNull
    public List<String> getCapabilitiesToAdd() {
        return capabilitiesToAdd;
    }

    public String getCapabilitiesToAddString() {
        if (capabilitiesToAdd == null) {
            return "";
        }
        return Joiner.on("\n").join(capabilitiesToAdd);
    }

    public void setCapabilitiesToAdd(List<String> capabilitiesToAdd) {
        this.capabilitiesToAdd = capabilitiesToAdd;
    }

    @DataBoundSetter
    public void setCapabilitiesToAddString(String capabilitiesToAddString) {
        setCapabilitiesToAdd(isEmpty(capabilitiesToAddString) ?
                Collections.EMPTY_LIST :
                splitAndFilterEmptyList(capabilitiesToAddString, "\n"));
    }

    @CheckForNull
    public List<String> getCapabilitiesToDrop() {
        return capabilitiesToDrop;
    }

    public String getCapabilitiesToDropString() {
        if (capabilitiesToDrop == null) {
            return "";
        }
        return Joiner.on("\n").join(capabilitiesToDrop);
    }

    public void setCapabilitiesToDrop(List<String> capabilitiesToDrop) {
        this.capabilitiesToDrop = capabilitiesToDrop;
    }

    @DataBoundSetter
    public void setCapabilitiesToDropString(String capabilitiesToDropString) {
        setCapabilitiesToDrop(isEmpty(capabilitiesToDropString) ?
                Collections.EMPTY_LIST :
                splitAndFilterEmptyList(capabilitiesToDropString, "\n"));
    }

    @Nonnull
    public Map<String, String> getExtraDockerLabels() {
        return extraDockerLabels == null ? Collections.EMPTY_MAP : extraDockerLabels;
    }

    public String getExtraDockerLabelsString() {
        if (extraDockerLabels == null) {
            return "";
        }
        return Joiner.on("\n").withKeyValueSeparator("=").join(extraDockerLabels);
    }

    public void setExtraDockerLabels(Map<String, String> extraDockerLabels) {
        this.extraDockerLabels = MapUtils.isEmpty(extraDockerLabels) ? null : extraDockerLabels;
    }

    @DataBoundSetter
    public void setExtraDockerLabelsString(String extraDockerLabelsString) {
        setExtraDockerLabels(isEmpty(extraDockerLabelsString) ?
                Collections.EMPTY_MAP :
                splitAndFilterEmptyMap(extraDockerLabelsString, "\n"));
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

        if (!Strings.isNullOrEmpty(user)) {
            containerConfig.withUser(user);
        }

        if (CollectionUtils.isNotEmpty(extraGroups)) {
            containerConfig.getHostConfig().withGroupAdd(extraGroups);
        }

        String[] cmd = getDockerCommandArray();
        if (cmd.length > 0) {
            containerConfig.withCmd(cmd);
        }

        containerConfig.withPortBindings(Iterables.toArray(getPortMappings(), PortBinding.class));
        containerConfig.withPublishAllPorts(bindAllPorts);
        containerConfig.withPrivileged(privileged);

        Map<String,String> map = new HashMap<>();
        map.putAll(getExtraDockerLabels());
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

        final List<String> securityOptions = getSecurityOpts();
        if (CollectionUtils.isNotEmpty(securityOptions)) {
            containerConfig.getHostConfig().withSecurityOpts( securityOptions );
        }

        final List<String> capabilitiesToAdd = getCapabilitiesToAdd();
        if (CollectionUtils.isNotEmpty(capabilitiesToAdd)) {
            containerConfig.withCapAdd(toCapabilities(capabilitiesToAdd));
        }

        final List<String> capabilitiesToDrop = getCapabilitiesToDrop();
        if (CollectionUtils.isNotEmpty(capabilitiesToDrop)) {
            containerConfig.withCapDrop(toCapabilities(capabilitiesToDrop));
        }

        return containerConfig;
    }

    private List<Capability> toCapabilities(List<String> capabilitiesString) {
        List<Capability> res = new ArrayList<>();
        for(String capability : capabilitiesString) {
            try {
                res.add(Capability.valueOf(capability));
            } catch(IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid capability name : " + capability);
            }
        }
        return res;
    }

    /**
     * Calculates the value we use for the Docker label called
     * {@link DockerContainerLabelKeys#JENKINS_URL} that we put into every
     * container we make, so that we can recognize our own containers later.
     */
    static String getJenkinsUrlForContainerLabel() {
        final Jenkins jenkins = Jenkins.getInstance();
        // Note: While Jenkins.getInstance() claims to be @Nonnull it can
        // return null during unit-tests, so we need to null-proof here.
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DockerTemplateBase that = (DockerTemplateBase) o;
        // Maintenance note: This should include all non-transient fields.
        // Fields that are "usually unique" should go first.
        // Primitive fields should be tested before objects.
        // Computationally-expensive fields get tested last.
        // Note: If modifying this code, remember to update hashCode() and toString()
        if (bindAllPorts != that.bindAllPorts) return false;
        if (privileged != that.privileged) return false;
        if (tty != that.tty) return false;
        if (!image.equals(that.image)) return false;
        if (pullCredentialsId != null ? !pullCredentialsId.equals(that.pullCredentialsId) : that.pullCredentialsId != null)
            return false;
        if (dockerCommand != null ? !dockerCommand.equals(that.dockerCommand) : that.dockerCommand != null)
            return false;
        if (hostname != null ? !hostname.equals(that.hostname) : that.hostname != null) return false;
        if (user != null ? !user.equals(that.user) : that.user != null) return false;
        if (extraGroups != null ? !extraGroups.equals(that.extraGroups) : that.extraGroups != null) return false;
        if (!Arrays.equals(dnsHosts, that.dnsHosts)) return false;
        if (network != null ? !network.equals(that.network) : that.network != null) return false;
        if (!Arrays.equals(volumes, that.volumes)) return false;
        if (!Arrays.equals(volumesFrom2, that.volumesFrom2)) return false;
        if (!Arrays.equals(devices, that.devices)) return false;
        if (!Arrays.equals(environment, that.environment)) return false;
        if (bindPorts != null ? !bindPorts.equals(that.bindPorts) : that.bindPorts != null) return false;
        if (memoryLimit != null ? !memoryLimit.equals(that.memoryLimit) : that.memoryLimit != null) return false;
        if (memorySwap != null ? !memorySwap.equals(that.memorySwap) : that.memorySwap != null) return false;
        if (cpuShares != null ? !cpuShares.equals(that.cpuShares) : that.cpuShares != null) return false;
        if (shmSize != null ? !shmSize.equals(that.shmSize) : that.shmSize != null) return false;
        if (macAddress != null ? !macAddress.equals(that.macAddress) : that.macAddress != null) return false;
        if (securityOpts != null ? !securityOpts.equals(that.securityOpts) : that.securityOpts != null) return false;
        if (capabilitiesToAdd != null ? !capabilitiesToAdd.equals(that.capabilitiesToAdd) : that.capabilitiesToAdd != null) return false;
        if (capabilitiesToDrop != null ? !capabilitiesToDrop.equals(that.capabilitiesToDrop) : that.capabilitiesToDrop != null) return false;
        if (extraHosts != null ? !extraHosts.equals(that.extraHosts) : that.extraHosts != null) return false;
        if (extraDockerLabels != null ? !extraDockerLabels.equals(that.extraDockerLabels) : that.extraDockerLabels != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        // Maintenance node: This should list all the fields from the equals method,
        // preferably in the same order.
        // Note: If modifying this code, remember to update equals() and toString()
        int result = image.hashCode();
        result = 31 * result + (pullCredentialsId != null ? pullCredentialsId.hashCode() : 0);
        result = 31 * result + (dockerCommand != null ? dockerCommand.hashCode() : 0);
        result = 31 * result + (hostname != null ? hostname.hashCode() : 0);
        result = 31 * result + (user != null ? user.hashCode() : 0);
        result = 31 * result + (extraGroups != null ? extraGroups.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(dnsHosts);
        result = 31 * result + (network != null ? network.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(volumes);
        result = 31 * result + Arrays.hashCode(volumesFrom2);
        result = 31 * result + Arrays.hashCode(devices);
        result = 31 * result + Arrays.hashCode(environment);
        result = 31 * result + (bindPorts != null ? bindPorts.hashCode() : 0);
        result = 31 * result + (bindAllPorts ? 1 : 0);
        result = 31 * result + (memoryLimit != null ? memoryLimit.hashCode() : 0);
        result = 31 * result + (memorySwap != null ? memorySwap.hashCode() : 0);
        result = 31 * result + (cpuShares != null ? cpuShares.hashCode() : 0);
        result = 31 * result + (shmSize != null ? shmSize.hashCode() : 0);
        result = 31 * result + (privileged ? 1 : 0);
        result = 31 * result + (securityOpts != null ? securityOpts.hashCode() : 0);
        result = 31 * result + (capabilitiesToAdd != null ? capabilitiesToAdd.hashCode() : 0);
        result = 31 * result + (capabilitiesToDrop != null ? capabilitiesToDrop.hashCode() : 0);
        result = 31 * result + (tty ? 1 : 0);
        result = 31 * result + (macAddress != null ? macAddress.hashCode() : 0);
        result = 31 * result + (extraHosts != null ? extraHosts.hashCode() : 0);
        result = 31 * result + (extraDockerLabels != null ? extraDockerLabels.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = startToString(this);
        // Maintenance node: This should list all the data we use in the equals()
        // method, but in the order the fields are declared in the class.
        // Note: If modifying this code, remember to update hashCode() and toString()
        bldToString(sb, "image", image);
        bldToString(sb, "pullCredentialsId", pullCredentialsId);
        bldToString(sb, "dockerCommand", dockerCommand);
        bldToString(sb, "hostname", hostname);
        bldToString(sb, "user", user);
        bldToString(sb, "extraGroups", extraGroups);
        bldToString(sb, "dnsHosts", dnsHosts);
        bldToString(sb, "network'", network);
        bldToString(sb, "volumes", volumes);
        bldToString(sb, "volumesFrom2", volumesFrom2);
        bldToString(sb, "devices", devices);
        bldToString(sb, "environment", environment);
        bldToString(sb, "bindPorts'", bindPorts);
        bldToString(sb, "bindAllPorts", bindAllPorts);
        bldToString(sb, "memoryLimit", memoryLimit);
        bldToString(sb, "memorySwap", memorySwap);
        bldToString(sb, "cpuShares", cpuShares);
        bldToString(sb, "shmSize", shmSize);
        bldToString(sb, "privileged", privileged);
        bldToString(sb, "tty", tty);
        bldToString(sb, "macAddress'", macAddress);
        bldToString(sb, "extraHosts", extraHosts);
        bldToString(sb, "securityOpts", securityOpts);
        bldToString(sb, "capabilitiesToAdd", capabilitiesToAdd);
        bldToString(sb, "capabilitiesToDrop", capabilitiesToDrop);
        bldToString(sb, "extraDockerLabels", extraDockerLabels);
        endToString(sb);
        return sb.toString();
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
                    return FormValidation.error("Wrong extraHost format: '%s'", extraHost);
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckSecurityOptsString(@QueryParameter String securityOptsString) {
            final List<String> securityOpts = splitAndFilterEmptyList(securityOptsString, "\n");
            for (String securityOpt : securityOpts) {
                if ( !( securityOpt.trim().split("=").length == 2 || securityOpt.trim().startsWith( "no-new-privileges" ) ) ) {
                    return FormValidation.warning("Security option may be incorrect. Please double check syntax: '%s'", securityOpt);
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCapabilitiesToAddString(@QueryParameter String capabilitiesToAddString) {
            return doCheckCapabilitiesString(capabilitiesToAddString);
        }

        public FormValidation doCheckCapabilitiesToDropString(@QueryParameter String capabilitiesToDropString) {
            return doCheckCapabilitiesString(capabilitiesToDropString);
        }

        private static FormValidation doCheckCapabilitiesString(String capabilitiesString) {
            final List<String> capabilities = splitAndFilterEmptyList(capabilitiesString, "\n");
            for (String capability : capabilities) {
                try {
                    Capability.valueOf(capability);
                } catch(IllegalArgumentException e) {
                    return FormValidation.error("Wrong capability : %s", capability);
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckExtraDockerLabelsString(@QueryParameter String extraDockerLabelsString) {
            final List<String> extraDockerLabels = splitAndFilterEmptyList(extraDockerLabelsString, "\n");
            for (String extraDockerLabel : extraDockerLabels) {
                if (extraDockerLabel.trim().split("=").length < 2) {
                    return FormValidation.error("Invalid extraDockerLabel \"%s\" will be ignored", extraDockerLabel);
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckExtraGroupsString(@QueryParameter String extraGroupsString) {
            final List<String> extraGroups = splitAndFilterEmptyList(extraGroupsString, "\n");
            Pattern pat = Pattern.compile("^(\\d+|[a-z_][a-z0-9_-]*[$]?)$");
            for (String extraGroup : extraGroups) {
                if (!pat.matcher(extraGroup.trim()).matches()) {
                    return FormValidation.error("Wrong extraGroup format: '%s'", extraGroup);
                }
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillPullCredentialsIdItems(@AncestorInPath Item context) {
            final DockerRegistryEndpoint.DescriptorImpl descriptor =
                    (DockerRegistryEndpoint.DescriptorImpl)
                            Jenkins.getInstance().getDescriptorOrDie(DockerRegistryEndpoint.class);
            return descriptor.doFillCredentialsIdItems(context);
        }

        @Override
        public String getDisplayName() {
            return "Docker template base";
        }
    }
}
