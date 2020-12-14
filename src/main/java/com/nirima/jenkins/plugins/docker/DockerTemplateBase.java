package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.HostConfig;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.bldToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.endToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.filterStringArray;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.fixEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndFilterEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndFilterEmptyList;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndFilterEmptyMap;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.startToString;
import static org.apache.commons.lang.StringUtils.trimToNull;

/**
 * Base for docker templates - does not include Jenkins items like labels.
 */
public class DockerTemplateBase implements Describable<DockerTemplateBase>, Serializable {
    private static final long serialVersionUID = 1838584884066776725L;

    private @Nonnull final String image;

    private @CheckForNull String pullCredentialsId;
    private @CheckForNull transient DockerRegistryEndpoint registry;

    /**
     * Field dockerCommand
     */
    private @CheckForNull String dockerCommand;

    public @CheckForNull String hostname;

    /**
     * --user argument to docker run command.
     */
    private @CheckForNull String user;

    /**
     * --group-add argument to docker run command.
     */
    private @CheckForNull List<String> extraGroups;

    public @CheckForNull String[] dnsHosts;

    public @CheckForNull String network;

    /**
     * Every String is volume specification
     */
    public @CheckForNull String[] volumes;

    /**
     * @deprecated use {@link #volumesFrom2}
     */
    @Deprecated
    public @CheckForNull String volumesFrom;

    /**
     * Every String is volumeFrom specification
     */
    public @CheckForNull String[] volumesFrom2;

    /**
     * Every String is a device to be mapped
     */
    public @CheckForNull String[] devices;

    public @CheckForNull String[] environment;

    public @CheckForNull String bindPorts;
    public boolean bindAllPorts;

    public @CheckForNull Integer memoryLimit;
    public @CheckForNull Integer memorySwap;
    public @CheckForNull Long cpuPeriod;
    public @CheckForNull Long cpuQuota;
    public @CheckForNull Integer cpuShares;
    public @CheckForNull Integer shmSize;

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
                              Long cpuPeriod,
                              Long cpuQuota,
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
        setCpuPeriod(cpuPeriod);
        setCpuQuota(cpuQuota);
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

    @Nonnull
    public String getImage() {
        return image.trim();
    }

    @CheckForNull
    public String getPullCredentialsId() {
        return Util.fixEmpty(pullCredentialsId);
    }

    @DataBoundSetter
    public void setPullCredentialsId(String pullCredentialsId) {
        this.pullCredentialsId = Util.fixEmpty(pullCredentialsId);
    }

    @CheckForNull
    public String getDockerCommand() {
        return Util.fixEmpty(dockerCommand);
    }

    @DataBoundSetter
    public void setDockerCommand(String dockerCommand) {
        this.dockerCommand = Util.fixEmpty(dockerCommand);
    }

    @CheckForNull
    public String getHostname() {
        return Util.fixEmpty(hostname);
    }

    @DataBoundSetter
    public void setHostname(String hostname) {
        this.hostname = Util.fixEmpty(hostname);
    }

    @CheckForNull
    public String getUser() {
        return Util.fixEmpty(user);
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = Util.fixEmpty(user);
    }

    @CheckForNull
    public List<String> getExtraGroups() {
        return fixEmpty(extraGroups);
    }

    public void setExtraGroups(List<String> extraGroups) {
        this.extraGroups = fixEmpty(extraGroups);
    }

    @DataBoundSetter
    public void setExtraGroupsString(String extraGroupsString) {
        setExtraGroups(splitAndFilterEmptyList(extraGroupsString, "\n"));
    }

    @Nonnull
    public String getExtraGroupsString() {
        if (extraGroups == null) {
            return "";
        }
        return Joiner.on("\n").join(extraGroups);
    }

    @CheckForNull
    public String[] getDnsHosts() {
        return fixEmpty(dnsHosts);
    }

    @Nonnull
    public String getDnsString() {
        if (dnsHosts == null) return "";
        return Joiner.on(" ").join(dnsHosts);
    }

    public void setDnsHosts(String[] dnsHosts) {
        this.dnsHosts = fixEmpty(dnsHosts);
    }

    @DataBoundSetter
    public void setDnsString(String dnsString) {
        setDnsHosts(splitAndFilterEmpty(dnsString, " "));
    }

    @CheckForNull
    public String getNetwork() {
        return Util.fixEmpty(network);
    }

    @DataBoundSetter
    public void setNetwork(String network) {
        this.network = Util.fixEmpty(network);
    }

    @CheckForNull
    public String[] getVolumes() {
        return fixEmpty(filterStringArray(volumes));
    }

    public void setVolumes(String[] volumes) {
        this.volumes = fixEmpty(volumes);
    }

    @Nonnull
    public String getVolumesString() {
        if (volumes == null) return "";
        return Joiner.on("\n").join(volumes);
    }

    @DataBoundSetter
    public void setVolumesString(String volumesString) {
        setVolumes(splitAndFilterEmpty(volumesString, "\n"));
    }

    @Nonnull
    public String getVolumesFromString() {
        final String[] volumesFrom2OrNull = getVolumesFrom2();
        return volumesFrom2OrNull==null ? "" : Joiner.on("\n").join(volumesFrom2OrNull);
    }

    @DataBoundSetter
    public void setVolumesFromString(String volumesFromString) {
        setVolumesFrom2(splitAndFilterEmpty(volumesFromString, "\n"));
    }

    @CheckForNull
    public String[] getDevices() {
        return fixEmpty(filterStringArray(devices));
    }

    @Nonnull
    public String getDevicesString() {
        if (devices == null) return "";
        return Joiner.on("\n").join(devices);
    }

    public void setDevices(String[] devices) {
        this.devices = fixEmpty(devices);
    }

    @DataBoundSetter
    public void setDevicesString(String devicesString) {
        setDevices(splitAndFilterEmpty(devicesString, "\n"));
    }

    @CheckForNull
    public String[] getEnvironment() {
        return fixEmpty(environment);
    }

    @Nonnull
    public String getEnvironmentsString() {
        if (environment == null) return "";
        return Joiner.on("\n").join(environment);
    }

    public void setEnvironment(String[] environment) {
        this.environment = fixEmpty(environment);
    }

    @DataBoundSetter
    public void setEnvironmentsString(String environmentsString) {
        setEnvironment(splitAndFilterEmpty(environmentsString, "\n"));
    }

    @CheckForNull
    public String getBindPorts() {
        return Util.fixEmpty(bindPorts);
    }

    @DataBoundSetter
    public void setBindPorts(String bindPorts) {
        this.bindPorts = Util.fixEmpty(bindPorts);
    }

    public boolean isBindAllPorts() {
        return bindAllPorts;
    }

    @DataBoundSetter
    public void setBindAllPorts(boolean bindAllPorts) {
        this.bindAllPorts = bindAllPorts;
    }

    @CheckForNull
    public Integer getMemoryLimit() {
        return memoryLimit;
    }

    @DataBoundSetter
    public void setMemoryLimit(Integer memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    @CheckForNull
    public Integer getMemorySwap() {
        return memorySwap;
    }

    @DataBoundSetter
    public void setMemorySwap(Integer memorySwap) {
        this.memorySwap = memorySwap;
    }

    @CheckForNull
    public Long getCpuPeriod() {
        return cpuPeriod;
    }

    @DataBoundSetter
    public void setCpuPeriod(Long cpuPeriod) {
        this.cpuPeriod = cpuPeriod;
    }

    @CheckForNull
    public Long getCpuQuota() {
        return cpuQuota;
    }

    @DataBoundSetter
    public void setCpuQuota(Long cpuQuota) {
        this.cpuQuota = cpuQuota;
    }

    @CheckForNull
    public Integer getCpuShares() {
        return cpuShares;
    }

    @DataBoundSetter
    public void setCpuShares(Integer cpuShares) {
        this.cpuShares = cpuShares;
    }

    @CheckForNull
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
        return fixEmpty(extraHosts);
    }

    @Nonnull
    public String getExtraHostsString() {
        if (extraHosts == null) {
            return "";
        }
        return Joiner.on("\n").join(extraHosts);
    }

    public void setExtraHosts(List<String> extraHosts) {
        this.extraHosts = fixEmpty(extraHosts);
    }

    @DataBoundSetter
    public void setExtraHostsString(String extraHostsString) {
        setExtraHosts(splitAndFilterEmptyList(extraHostsString, "\n"));
    }

    @CheckForNull
    public List<String> getSecurityOpts() {
        return fixEmpty(securityOpts);
    }

    @Nonnull
    public String getSecurityOptsString() {
        return securityOpts == null ? "" : Joiner.on("\n").join(securityOpts);
    }

    public void setSecurityOpts( List<String> securityOpts ) {
        this.securityOpts = fixEmpty(securityOpts);
    }

    @DataBoundSetter
    public void setSecurityOptsString(String securityOpts) {
        setSecurityOpts(splitAndFilterEmptyList(securityOpts, "\n"));
    }

    @CheckForNull
    public List<String> getCapabilitiesToAdd() {
        return fixEmpty(capabilitiesToAdd);
    }

    @Nonnull
    public String getCapabilitiesToAddString() {
        if (capabilitiesToAdd == null) {
            return "";
        }
        return Joiner.on("\n").join(capabilitiesToAdd);
    }

    public void setCapabilitiesToAdd(List<String> capabilitiesToAdd) {
        this.capabilitiesToAdd = fixEmpty(capabilitiesToAdd);
    }

    @DataBoundSetter
    public void setCapabilitiesToAddString(String capabilitiesToAddString) {
        setCapabilitiesToAdd(splitAndFilterEmptyList(capabilitiesToAddString, "\n"));
    }

    @CheckForNull
    public List<String> getCapabilitiesToDrop() {
        return fixEmpty(capabilitiesToDrop);
    }

    @Nonnull
    public String getCapabilitiesToDropString() {
        if (capabilitiesToDrop == null) {
            return "";
        }
        return Joiner.on("\n").join(capabilitiesToDrop);
    }

    public void setCapabilitiesToDrop(List<String> capabilitiesToDrop) {
        this.capabilitiesToDrop = fixEmpty(capabilitiesToDrop);
    }

    @DataBoundSetter
    public void setCapabilitiesToDropString(String capabilitiesToDropString) {
        setCapabilitiesToDrop(splitAndFilterEmptyList(capabilitiesToDropString, "\n"));
    }

    @CheckForNull
    public Map<String, String> getExtraDockerLabels() {
        return fixEmpty(extraDockerLabels);
    }

    @Nonnull
    public String getExtraDockerLabelsString() {
        if (extraDockerLabels == null) {
            return "";
        }
        return Joiner.on("\n").withKeyValueSeparator("=").join(extraDockerLabels);
    }

    public void setExtraDockerLabels(Map<String, String> extraDockerLabels) {
        this.extraDockerLabels = fixEmpty(extraDockerLabels);
    }

    @DataBoundSetter
    public void setExtraDockerLabelsString(String extraDockerLabelsString) {
        setExtraDockerLabels(splitAndFilterEmptyMap(extraDockerLabelsString, "\n"));
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

    @CheckForNull
    public String[] getVolumesFrom2() {
        return fixEmpty(filterStringArray(volumesFrom2));
    }

    public void setVolumesFrom2(String[] volumes) {
        this.volumesFrom2 = fixEmpty(volumes);
    }

    public String getDisplayName() {
        return "Image of " + getImage();
    }

    @CheckForNull
    public String[] getDockerCommandArray() {
        String[] dockerCommandArray = new String[0];
        if (dockerCommand != null && !dockerCommand.isEmpty()) {
            dockerCommandArray = dockerCommand.split(" ");
        }
        return fixEmpty(dockerCommandArray);
    }

    @Nonnull
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
        final String hostnameOrNull = getHostname();
        if (hostnameOrNull != null && !hostnameOrNull.isEmpty()) {
            containerConfig.withHostName(hostnameOrNull);
        }

        final String userOrNull = getUser();
        if (userOrNull != null && !userOrNull.isEmpty()) {
            containerConfig.withUser(userOrNull);
        }

        final List<String> extraGroupsOrNull = getExtraGroups();
        if (CollectionUtils.isNotEmpty(extraGroupsOrNull)) {
            hostConfig(containerConfig).withGroupAdd(extraGroupsOrNull);
        }

        final String[] cmdOrNull = getDockerCommandArray();
        if (cmdOrNull != null && cmdOrNull.length > 0) {
            containerConfig.withCmd(cmdOrNull);
        }

        containerConfig.withPortBindings(Iterables.toArray(getPortMappings(), PortBinding.class));
        containerConfig.withPublishAllPorts(bindAllPorts);
        containerConfig.withPrivileged(privileged);

        final Map<String, String> existingLabelsOrNull = containerConfig.getLabels();
        final Map<String, String> labels;
        if (existingLabelsOrNull == null) {
            labels = new HashMap<>();
            containerConfig.withLabels(labels);
        } else {
            labels = existingLabelsOrNull;
        }
        final Map<String, String> extraDockerLabelsOrNull = getExtraDockerLabels();
        if (extraDockerLabelsOrNull != null && !extraDockerLabelsOrNull.isEmpty()) {
            labels.putAll(extraDockerLabelsOrNull);
        }
        labels.put(DockerContainerLabelKeys.JENKINS_INSTANCE_ID, getJenkinsInstanceIdForContainerLabel());
        labels.put(DockerContainerLabelKeys.JENKINS_URL, getJenkinsUrlForContainerLabel());
        labels.put(DockerContainerLabelKeys.CONTAINER_IMAGE, getImage());

        final Long cpuPeriodOrNull = getCpuPeriod();
        if (cpuPeriodOrNull != null && cpuPeriodOrNull > 0) {
            hostConfig(containerConfig).withCpuPeriod(cpuPeriodOrNull);
        }

        final Long cpuQuotaOrNull = getCpuQuota();
        if (cpuQuotaOrNull != null && cpuQuotaOrNull > 0) {
            hostConfig(containerConfig).withCpuQuota(cpuQuotaOrNull);
        }

        final Integer cpuSharesOrNull = getCpuShares();
        if (cpuSharesOrNull != null && cpuSharesOrNull > 0) {
            containerConfig.withCpuShares(cpuSharesOrNull);
        }

        final Integer memoryLimitOrNull = getMemoryLimit();
        if (memoryLimitOrNull != null && memoryLimitOrNull > 0) {
            final long memoryInByte = memoryLimitOrNull.longValue() * 1024L * 1024L;
            containerConfig.withMemory(memoryInByte);
        }

        final Integer memorySwapOrNullOrNegative = getMemorySwap();
        if (memorySwapOrNullOrNegative != null) {
            final long memorySwapOrNegative = memorySwapOrNullOrNegative.longValue();
            if (memorySwapOrNegative > 0L) {
                long memorySwapInByte = memorySwapOrNegative * 1024L * 1024L;
                containerConfig.withMemorySwap(memorySwapInByte);
            } else {
                containerConfig.withMemorySwap(memorySwapOrNegative);
            }
        }

        final String[] dnsHostsOrNull = getDnsHosts();
        if (dnsHostsOrNull != null && dnsHostsOrNull.length > 0) {
            containerConfig.withDns(dnsHostsOrNull);
        }

        final String networkOrNull = getNetwork();
        if (networkOrNull != null && networkOrNull.length() > 0) {
            containerConfig.withNetworkDisabled(false);
            containerConfig.withNetworkMode(networkOrNull);
        }

        // https://github.com/docker/docker/blob/ed257420025772acc38c51b0f018de3ee5564d0f/runconfig/parse.go#L182-L196
        final String[] volumesOrNull = getVolumes();
        if (volumesOrNull !=null && volumesOrNull.length > 0) {
            ArrayList<Volume> vols = new ArrayList<>();
            ArrayList<Bind> binds = new ArrayList<>();
            parseVolumesStrings(volumesOrNull, vols, binds);
            containerConfig.withVolumes(vols.toArray(new Volume[vols.size()]));
            containerConfig.withBinds(binds.toArray(new Bind[binds.size()]));
        }

        final String[] volumesFrom2OrNull = getVolumesFrom2();
        if (volumesFrom2OrNull != null && volumesFrom2OrNull.length > 0) {
            ArrayList<VolumesFrom> volFrom = new ArrayList<>();
            for (String volFromStr : volumesFrom2OrNull) {
                volFrom.add(new VolumesFrom(volFromStr));
            }
            containerConfig.withVolumesFrom(volFrom.toArray(new VolumesFrom[volFrom.size()]));
        }

        final String[] devicesOrNull = getDevices();
        if (devicesOrNull != null && devicesOrNull.length > 0) {
            final List<Device> list = new ArrayList<>();
            for (String deviceStr : devicesOrNull) {
                list.add(Device.parse(deviceStr));
            }
            containerConfig.withDevices(list);
        }

        containerConfig.withTty(tty);

        final String[] environmentOrNull = getEnvironment();
        if (environmentOrNull != null && environmentOrNull.length > 0) {
            containerConfig.withEnv(environmentOrNull);
        }

        final String macAddressOrNull = getMacAddress();
        if (macAddressOrNull != null && !macAddressOrNull.isEmpty()) {
            containerConfig.withMacAddress(macAddressOrNull);
        }

        final List<String> extraHostsOrNull = getExtraHosts();
        if (CollectionUtils.isNotEmpty(extraHostsOrNull)) {
            containerConfig.withExtraHosts(extraHostsOrNull.toArray(new String[extraHostsOrNull.size()]));
        }

        final Integer shmSizeOrNull = getShmSize();
        if (shmSizeOrNull != null && shmSizeOrNull.intValue() > 0) {
            final long shmSizeInByte = shmSizeOrNull.longValue() * 1024L * 1024L;
            hostConfig(containerConfig).withShmSize(shmSizeInByte);
        }

        final List<String> securityOptionsOrNull = getSecurityOpts();
        if (CollectionUtils.isNotEmpty(securityOptionsOrNull)) {
            hostConfig(containerConfig).withSecurityOpts( securityOptionsOrNull );
        }

        final List<String> capabilitiesToAddOrNull = getCapabilitiesToAdd();
        if (CollectionUtils.isNotEmpty(capabilitiesToAddOrNull)) {
            containerConfig.withCapAdd(toCapabilities(capabilitiesToAddOrNull));
        }

        final List<String> capabilitiesToDropOrNull = getCapabilitiesToDrop();
        if (CollectionUtils.isNotEmpty(capabilitiesToDropOrNull)) {
            containerConfig.withCapDrop(toCapabilities(capabilitiesToDropOrNull));
        }

        return containerConfig;
    }

    /**
     * Parses a given volumesString value, appending any {@link Volume}s and {@link Bind}s to the specified lists.
     * @param volumes The strings to be parsed.
     * @param volumeListResult List to which any {@link Volume}s should be stored in.
     * @param bindListResult List to which any {@link Bind}s should be stored in.
     * @throws IllegalArgumentException if anything is invalid.
     */
    private static void parseVolumesStrings(final String[] volumes, List<Volume> volumeListResult, List<Bind> bindListResult) {
        for (String vol : volumes) {
            parseVolumesString(vol, volumeListResult, bindListResult);
        }
    }

    private static void parseVolumesString(String vol, List<Volume> volumeListResult, List<Bind> bindListResult) {
        final String[] group = vol.split(":");
        final int length = group.length;
        if (length > 3) {
            throw new IllegalArgumentException("Invalid bind syntax, '" + vol + "'. Must be x:y or x:y:z.");
        }
        if (length > 1) {
            if (group[1].equals("/")) {
                throw new IllegalArgumentException("Invalid bind mount, '"+vol+"'. Destination may not be '/'");
            }
            final Bind result;
            try {
                result = Bind.parse(vol);
            } catch ( RuntimeException ex ) {
                throw new IllegalArgumentException("Invalid bind mount, '" + vol + "'. " + ex.getMessage(), ex);
            }
            bindListResult.add(result);
            return;
        }
        if (vol.equals("/")) {
            throw new IllegalArgumentException("Invalid volume: path may not be '/'");
        }
        final Volume result;
        try {
            result = new Volume(vol);
        } catch ( RuntimeException ex ) {
            throw new IllegalArgumentException("Invalid volume, '" + vol + "'. " + ex.getMessage(), ex);
        }
        volumeListResult.add(result);
    }

    @Nonnull
    private static HostConfig hostConfig(CreateContainerCmd containerConfig) {
        final HostConfig hc = containerConfig.getHostConfig();
        if (hc == null) {
            throw new IllegalStateException("Can't find " + HostConfig.class.getCanonicalName() + " within "
                    + CreateContainerCmd.class.getCanonicalName() + " " + containerConfig);
        }
        return hc;
    }

    private static List<Capability> toCapabilities(List<String> capabilitiesString) {
        List<Capability> res = new ArrayList<>();
        for(String capability : capabilitiesString) {
            try {
                res.add(Capability.valueOf(capability));
            } catch(IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid capability name : " + capability, e);
            }
        }
        return res;
    }

    /**
     * Calculates the value we use for the Docker label called
     * {@link DockerContainerLabelKeys#JENKINS_URL} that we put into every
     * container we make, so that we can recognize our own containers later.
     */
    @Nonnull
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "It can be null during unit tests.")
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
    @Nonnull
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
        if (cpuPeriod != null ? !cpuPeriod.equals(that.cpuPeriod) : that.cpuPeriod != null) return false;
        if (cpuQuota != null ? !cpuQuota.equals(that.cpuQuota) : that.cpuQuota != null) return false;
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
        result = 31 * result + (cpuPeriod != null ? cpuPeriod.hashCode() : 0);
        result = 31 * result + (cpuQuota != null ? cpuQuota.hashCode() : 0);
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
        bldToString(sb, "cpuPeriod", cpuPeriod);
        bldToString(sb, "cpuQuota", cpuQuota);
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
                final String[] volumes = splitAndFilterEmpty(volumesString, "\n");
                parseVolumesStrings(volumes, new ArrayList<>(), new ArrayList<>());
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
