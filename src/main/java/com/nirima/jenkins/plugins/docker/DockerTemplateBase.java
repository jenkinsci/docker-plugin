package com.nirima.jenkins.plugins.docker;

import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.bldToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.endToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.filterStringArray;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.fixEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndFilterEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndFilterEmptyList;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndFilterEmptyMap;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.startToString;
import static org.apache.commons.lang.StringUtils.trimToNull;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BindOptions;
import com.github.dockerjava.api.model.BindPropagation;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.PropagationMode;
import com.github.dockerjava.api.model.TmpfsOptions;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.core.NameParser;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Base for docker templates - does not include Jenkins items like labels.
 */
public class DockerTemplateBase implements Describable<DockerTemplateBase>, Serializable {
    private static final long serialVersionUID = 1838584884066776725L;

    private @NonNull final String image;

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
     * @deprecated use {@link #mounts}
     */
    @Deprecated
    public @CheckForNull String[] volumes;

    /**
     * Every String is mount specification
     */
    public @CheckForNull String[] mounts;

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
    public @CheckForNull String cpus;
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
     * @deprecated Call {@link #DockerTemplateBase(String)} then use DataBoundSetters
     * @param image              See {@link #DockerTemplateBase(String)}
     * @param pullCredentialsId  See {@link #setPullCredentialsId(String)}
     * @param dnsString          See {@link #setDnsString(String)}
     * @param network            See {@link #setNetwork(String)}
     * @param dockerCommand      See {@link #setDockerCommand(String)}
     * @param mountsString       See {@link #setMountsString(String)}
     * @param volumesFromString  See {@link #setVolumesFromString(String)}
     * @param environmentsString See {@link #setEnvironmentsString(String)}
     * @param hostname           See {@link #setHostname(String)}
     * @param user               See {@link #setUser(String)}
     * @param extraGroupsString  See {@link #setExtraGroupsString(String)}
     * @param memoryLimit        See {@link #setMemoryLimit(Integer)}
     * @param memorySwap         See {@link #setMemorySwap(Integer)}
     * @param cpuPeriod          See {@link #setCpuPeriod(Long)}
     * @param cpuQuota           See {@link #setCpuQuota(Long)}
     * @param cpuShares          See {@link #setCpuShares(Integer)}
     * @param shmSize            See {@link #setShmSize(Integer)}
     * @param bindPorts          See {@link #setBindPorts(String)}
     * @param bindAllPorts       See {@link #setBindAllPorts(boolean)}
     * @param privileged         See {@link #setPrivileged(boolean)}
     * @param tty                See {@link #setTty(boolean)}
     * @param macAddress         See {@link #setMacAddress(String)}
     * @param extraHostsString   See {@link #setExtraHostsString(String)}
     */
    @Deprecated
    public DockerTemplateBase(
            String image,
            String pullCredentialsId,
            String dnsString,
            String network,
            String dockerCommand,
            String mountsString,
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
            String extraHostsString) {
        this(image);
        setPullCredentialsId(pullCredentialsId);
        setDnsString(dnsString);
        setNetwork(network);
        setDockerCommand(dockerCommand);
        setMountsString(mountsString);
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
                setVolumesFrom2(new String[] {volumesFrom});
            }
            volumesFrom = null;
        }
        if (volumes != null && volumes.length > 0) {
            setMounts(convertVolumes(volumes));
            volumes = null;
        }
        if (pullCredentialsId == null && registry != null) {
            pullCredentialsId = registry.getCredentialsId();
        }
        return this;
    }

    @NonNull
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

    @NonNull
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

    @NonNull
    public String getDnsString() {
        if (dnsHosts == null) {
            return "";
        }
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
    public String[] getMounts() {
        return fixEmpty(filterStringArray(mounts));
    }

    public void setMounts(String[] mounts) {
        this.mounts = fixEmpty(mounts);
    }

    @NonNull
    public String getMountsString() {
        if (mounts == null) {
            return "";
        }
        return Joiner.on("\n").join(mounts);
    }

    @DataBoundSetter
    public void setMountsString(String mountsString) {
        setMounts(splitAndFilterEmpty(mountsString, "\n"));
    }

    @NonNull
    public String getVolumesFromString() {
        final String[] volumesFrom2OrNull = getVolumesFrom2();
        return volumesFrom2OrNull == null ? "" : Joiner.on("\n").join(volumesFrom2OrNull);
    }

    @DataBoundSetter
    public void setVolumesFromString(String volumesFromString) {
        setVolumesFrom2(splitAndFilterEmpty(volumesFromString, "\n"));
    }

    @CheckForNull
    public String[] getDevices() {
        return fixEmpty(filterStringArray(devices));
    }

    @NonNull
    public String getDevicesString() {
        if (devices == null) {
            return "";
        }
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

    @NonNull
    public String getEnvironmentsString() {
        if (environment == null) {
            return "";
        }
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
    public String getCpus() {
        return Util.fixEmpty(cpus);
    }

    @DataBoundSetter
    public void setCpus(String cpus) {
        this.cpus = Util.fixEmpty(cpus);
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

    @NonNull
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

    @NonNull
    public String getSecurityOptsString() {
        return securityOpts == null ? "" : Joiner.on("\n").join(securityOpts);
    }

    public void setSecurityOpts(List<String> securityOpts) {
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

    @NonNull
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

    @NonNull
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

    @NonNull
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
            registry = DockerRegistryEndpoint.fromImageName(getImage(), pullCredentialsId);
        }
        return registry;
    }

    /**
     * @deprecated use {@link #getVolumesFrom2()}
     * @return unused field
     */
    @Deprecated
    public String getVolumesFrom() {
        return volumesFrom;
    }

    @CheckForNull
    public String[] getVolumesFrom2() {
        return fixEmpty(filterStringArray(volumesFrom2));
    }

    public void setVolumesFrom2(String[] volumesFrom) {
        this.volumesFrom2 = fixEmpty(volumesFrom);
    }

    /**
     * For ConfigurationAsCode compatibility
     * @deprecated use {@link #setMounts(String[])}
     */
    @Deprecated
    public void setVolumes(String[] vols) {
        String[] fixed = fixEmpty(vols);
        this.mounts = fixed != null && fixed.length > 0 ? convertVolumes(fixed) : new String[0];
    }

    /**
     * For ConfigurationAsCode compatibility
     * @deprecated use {@link #getMounts()}
     */
    @Deprecated
    @CheckForNull
    public String[] getVolumes() {
        return getMounts();
    }

    /**
     * For ConfigurationAsCode compatibility
     * @deprecated use {@link #setMountsString(String)}
     */
    @Deprecated
    public void setVolumesString(String volumesString) {
        setMounts(convertVolumes(splitAndFilterEmpty(volumesString, "\n")));
    }

    /**
     * For ConfigurationAsCode compatibility
     * @deprecated use {@link #getMountsString()}
     */
    @Deprecated
    @NonNull
    public String getVolumesString() {
        return getMountsString();
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

    @NonNull
    public Iterable<PortBinding> getPortMappings() {
        if (Strings.isNullOrEmpty(bindPorts)) {
            return Collections.emptyList();
        }
        return Splitter.on(' ')
                .trimResults()
                .omitEmptyStrings()
                .splitToStream(bindPorts)
                .map(PortBinding::parse)
                .collect(Collectors.toList());
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

        hostConfig(containerConfig).withPortBindings(Iterables.toArray(getPortMappings(), PortBinding.class));
        hostConfig(containerConfig).withPublishAllPorts(bindAllPorts);
        hostConfig(containerConfig).withPrivileged(privileged);

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

        final String cpusOrNull = getCpus();
        if (cpusOrNull != null && !cpusOrNull.isEmpty()) {
            final Double cpu_double = Double.parseDouble(cpusOrNull) * 1e9;
            final Long nanoCpus = cpu_double.longValue();
            hostConfig(containerConfig).withNanoCPUs(nanoCpus);
        }

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
            hostConfig(containerConfig).withCpuShares(cpuSharesOrNull);
        }

        final Integer memoryLimitOrNull = getMemoryLimit();
        if (memoryLimitOrNull != null && memoryLimitOrNull > 0) {
            final long memoryInByte = memoryLimitOrNull.longValue() * 1024L * 1024L;
            hostConfig(containerConfig).withMemory(memoryInByte);
        }

        final Integer memorySwapOrNullOrNegative = getMemorySwap();
        if (memorySwapOrNullOrNegative != null) {
            final long memorySwapOrNegative = memorySwapOrNullOrNegative.longValue();
            if (memorySwapOrNegative > 0L) {
                long memorySwapInByte = memorySwapOrNegative * 1024L * 1024L;
                hostConfig(containerConfig).withMemorySwap(memorySwapInByte);
            } else {
                hostConfig(containerConfig).withMemorySwap(memorySwapOrNegative);
            }
        }

        final String[] dnsHostsOrNull = getDnsHosts();
        if (dnsHostsOrNull != null && dnsHostsOrNull.length > 0) {
            hostConfig(containerConfig).withDns(dnsHostsOrNull);
        }

        final String networkOrNull = getNetwork();
        if (networkOrNull != null && networkOrNull.length() > 0) {
            containerConfig.withNetworkDisabled(false);
            hostConfig(containerConfig).withNetworkMode(networkOrNull);
        }

        // https://github.com/docker/docker/blob/ed257420025772acc38c51b0f018de3ee5564d0f/runconfig/parse.go#L182-L196
        final String[] mountsOrNull = getMounts();
        if (mountsOrNull != null && mountsOrNull.length > 0) {
            ArrayList<Mount> mnts = new ArrayList<>();
            parseMountsStrings(mountsOrNull, mnts);
            hostConfig(containerConfig).withMounts(mnts);
        }

        final String[] volumesFrom2OrNull = getVolumesFrom2();
        if (volumesFrom2OrNull != null && volumesFrom2OrNull.length > 0) {
            ArrayList<VolumesFrom> volFrom = new ArrayList<>();
            for (String volFromStr : volumesFrom2OrNull) {
                volFrom.add(VolumesFrom.parse(volFromStr));
            }
            hostConfig(containerConfig).withVolumesFrom(volFrom.toArray(new VolumesFrom[0]));
        }

        final String[] devicesOrNull = getDevices();
        if (devicesOrNull != null && devicesOrNull.length > 0) {
            final List<Device> list = new ArrayList<>();
            for (String deviceStr : devicesOrNull) {
                list.add(Device.parse(deviceStr));
            }
            hostConfig(containerConfig).withDevices(list);
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
            hostConfig(containerConfig).withExtraHosts(extraHostsOrNull.toArray(new String[0]));
        }

        final Integer shmSizeOrNull = getShmSize();
        if (shmSizeOrNull != null && shmSizeOrNull.intValue() > 0) {
            final long shmSizeInByte = shmSizeOrNull.longValue() * 1024L * 1024L;
            hostConfig(containerConfig).withShmSize(shmSizeInByte);
        }

        final List<String> securityOptionsOrNull = getSecurityOpts();
        if (CollectionUtils.isNotEmpty(securityOptionsOrNull)) {
            hostConfig(containerConfig).withSecurityOpts(securityOptionsOrNull);
        }

        final List<String> capabilitiesToAddOrNull = getCapabilitiesToAdd();
        if (CollectionUtils.isNotEmpty(capabilitiesToAddOrNull)) {
            hostConfig(containerConfig).withCapAdd(toCapabilities(capabilitiesToAddOrNull));
        }

        final List<String> capabilitiesToDropOrNull = getCapabilitiesToDrop();
        if (CollectionUtils.isNotEmpty(capabilitiesToDropOrNull)) {
            hostConfig(containerConfig).withCapDrop(toCapabilities(capabilitiesToDropOrNull));
        }

        return containerConfig;
    }

    /**
     * Parses a given mountsString value, appending any {@link Mount}s to the specified lists.
     * @param mounts The strings to be parsed.
     * @param mountListResult List to which any {@link Mount}s should be stored in.
     * @throws IllegalArgumentException if anything is invalid.
     */
    private static void parseMountsStrings(final String[] mounts, List<Mount> mountListResult) {
        for (String mnt : mounts) {
            parseMountsString(mnt, mountListResult);
        }
    }

    private static void parseMountsString(String mnt, List<Mount> mountListResult) {
        Mount mount = new Mount().withType(MountType.VOLUME);
        BindOptions bindOptions = null;
        TmpfsOptions tmpfsOptions = null;

        final String[] tokens = mnt.split(",");
        for (String token : tokens) {
            final String[] parts = token.split("=");
            if (!(parts.length == 2 || parts.length == 1 && ("ro".equals(parts[0]) || "readonly".equals(parts[0])))) {
                throw new IllegalArgumentException(
                        "Invalid mount: expected key=value comma separated pairs, or 'ro' / 'readonly' keywords");
            }

            switch (parts[0]) {
                case "type":
                    mount.withType(MountType.valueOf(parts[1].toUpperCase()));
                    break;
                case "src":
                case "source":
                    mount.withSource(parts[1]);
                    break;
                case "target":
                case "destination":
                case "dst":
                    mount.withTarget(parts[1]);
                    break;
                case "ro":
                case "readonly":
                    String value = parts.length == 2 && parts[1] != null ? parts[1].trim() : "";
                    if (value.isEmpty() || "true".equalsIgnoreCase(value) || "1".equals(value)) {
                        mount.withReadOnly(true);
                    } else if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
                        mount.withReadOnly(false);
                    }
                    break;
                case "bind-propagation":
                    bindOptions = new BindOptions()
                            .withPropagation(BindPropagation.valueOf(
                                    (parts[1].startsWith("r") ? "R_" + parts[1].substring(1) : parts[1])
                                            .toUpperCase()));
                    break;
                case "tmpfs-mode":
                    if (tmpfsOptions == null) {
                        tmpfsOptions = new TmpfsOptions();
                    }
                    tmpfsOptions.withMode(Integer.parseInt(parts[1], 8));
                    break;
                case "tmpfs-size":
                    if (tmpfsOptions == null) {
                        tmpfsOptions = new TmpfsOptions();
                    }
                    tmpfsOptions.withSizeBytes(Long.parseLong(parts[1]));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported keyword: " + parts[0]);
            }
        }

        String target = mount.getTarget();
        if (target == null || target.isEmpty()) {
            throw new IllegalArgumentException("Invalid mount: target/destination must be set");
        }

        if (bindOptions != null) {
            mount.withBindOptions(bindOptions);
        }
        if (tmpfsOptions != null) {
            mount.withTmpfsOptions(tmpfsOptions);
        }
        mountListResult.add(mount);
    }

    private static String[] convertVolumes(String[] vols) {
        List<String> mnts = new ArrayList<>();
        for (String vol : vols) {
            if (!vol.contains(":")) {
                mnts.add("type=volume,destination=" + vol);
            } else {
                StringBuilder builder = new StringBuilder();
                if (vol.startsWith("/")) {
                    Bind bind = Bind.parse(vol);
                    builder.append("type=bind,source=");
                    builder.append(bind.getPath());
                    builder.append(",destination=");
                    builder.append(bind.getVolume().getPath());
                    if (bind.getAccessMode() == AccessMode.ro) {
                        builder.append(",readonly");
                    }
                    if (bind.getPropagationMode() != PropagationMode.DEFAULT) {
                        builder.append(",bind-propagation=");
                        builder.append(bind.getPropagationMode().toString());
                    }
                } else {
                    String[] parts = vol.split(":");
                    builder.append("type=volume,source=");
                    builder.append(parts[0]);
                    builder.append(",destination=");
                    builder.append(parts[1]);
                    if (parts.length == 3
                            && ("readonly".equalsIgnoreCase(parts[2]) || "ro".equalsIgnoreCase(parts[2]))) {
                        builder.append(",readonly");
                    }
                }
                mnts.add(builder.toString());
            }
        }
        return mnts.toArray(new String[0]);
    }

    @NonNull
    private static HostConfig hostConfig(CreateContainerCmd containerConfig) {
        final HostConfig hc = containerConfig.getHostConfig();
        if (hc == null) {
            throw new IllegalStateException("Can't find " + HostConfig.class.getCanonicalName() + " within "
                    + CreateContainerCmd.class.getCanonicalName() + " " + containerConfig);
        }
        return hc;
    }

    private static Capability[] toCapabilities(List<String> capabilitiesString) {
        final ArrayList<Capability> res = new ArrayList<>();
        for (String capability : capabilitiesString) {
            try {
                res.add(Capability.valueOf(capability));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid capability name : " + capability, e);
            }
        }
        return res.toArray(new Capability[0]);
    }

    /**
     * Calculates the value we use for the Docker label called
     * {@link DockerContainerLabelKeys#JENKINS_URL} that we put into every
     * container we make, so that we can recognize our own containers later.
     */
    @NonNull
    static String getJenkinsUrlForContainerLabel() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // Note: Jenkins.getInstanceOrNull() can return null during unit-tests.
        final String rootUrl = jenkins == null ? null : jenkins.getRootUrl();
        return Util.fixNull(rootUrl);
    }

    /**
     * Calculates the value we use for the Docker label called
     * {@link DockerContainerLabelKeys#JENKINS_INSTANCE_ID} that we put into every
     * container we make, so that we can recognize our own containers later.
     */
    @NonNull
    static String getJenkinsInstanceIdForContainerLabel() {
        return JenkinsUtils.getInstanceId();
    }

    @Override
    public Descriptor<DockerTemplateBase> getDescriptor() {
        return Jenkins.get().getDescriptor(DockerTemplateBase.class);
    }

    public String getFullImageId() {
        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(image);
        // if image was specified without tag, then treat as latest
        if (repostag.tag.isEmpty()) {
            if (repostag.repos.contains("@sha256:")) {
                // image has no tag but instead use a digest, do not append anything!
                return repostag.repos;
            }
            // else no tag provided, append latest as tag
            return repostag.repos + ":" + "latest";
        }
        // else use declared tag:
        return repostag.repos + ":" + repostag.tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DockerTemplateBase that = (DockerTemplateBase) o;
        // Maintenance note: This should include all non-transient fields.
        // Fields that are "usually unique" should go first.
        // Primitive fields should be tested before objects.
        // Computationally-expensive fields get tested last.
        // Note: If modifying this code, remember to update hashCode() and toString()
        if (bindAllPorts != that.bindAllPorts) {
            return false;
        }
        if (privileged != that.privileged) {
            return false;
        }
        if (tty != that.tty) {
            return false;
        }
        if (!image.equals(that.image)) {
            return false;
        }
        if (!Objects.equals(pullCredentialsId, that.pullCredentialsId)) {
            return false;
        }
        if (!Objects.equals(dockerCommand, that.dockerCommand)) {
            return false;
        }
        if (!Objects.equals(hostname, that.hostname)) {
            return false;
        }
        if (!Objects.equals(user, that.user)) {
            return false;
        }
        if (!Objects.equals(extraGroups, that.extraGroups)) {
            return false;
        }
        if (!Arrays.equals(dnsHosts, that.dnsHosts)) {
            return false;
        }
        if (!Objects.equals(network, that.network)) {
            return false;
        }
        if (!Arrays.equals(mounts, that.mounts)) {
            return false;
        }
        if (!Arrays.equals(volumesFrom2, that.volumesFrom2)) {
            return false;
        }
        if (!Arrays.equals(devices, that.devices)) {
            return false;
        }
        if (!Arrays.equals(environment, that.environment)) {
            return false;
        }
        if (!Objects.equals(bindPorts, that.bindPorts)) {
            return false;
        }
        if (!Objects.equals(memoryLimit, that.memoryLimit)) {
            return false;
        }
        if (!Objects.equals(memorySwap, that.memorySwap)) {
            return false;
        }
        if (!Objects.equals(cpus, that.cpus)) {
            return false;
        }
        if (!Objects.equals(cpuPeriod, that.cpuPeriod)) {
            return false;
        }
        if (!Objects.equals(cpuQuota, that.cpuQuota)) {
            return false;
        }
        if (!Objects.equals(cpuShares, that.cpuShares)) {
            return false;
        }
        if (!Objects.equals(shmSize, that.shmSize)) {
            return false;
        }
        if (!Objects.equals(macAddress, that.macAddress)) {
            return false;
        }
        if (!Objects.equals(securityOpts, that.securityOpts)) {
            return false;
        }
        if (!Objects.equals(capabilitiesToAdd, that.capabilitiesToAdd)) {
            return false;
        }
        if (!Objects.equals(capabilitiesToDrop, that.capabilitiesToDrop)) {
            return false;
        }
        if (!Objects.equals(extraHosts, that.extraHosts)) {
            return false;
        }
        if (!Objects.equals(extraDockerLabels, that.extraDockerLabels)) {
            return false;
        }
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
        result = 31 * result + Arrays.hashCode(mounts);
        result = 31 * result + Arrays.hashCode(volumesFrom2);
        result = 31 * result + Arrays.hashCode(devices);
        result = 31 * result + Arrays.hashCode(environment);
        result = 31 * result + (bindPorts != null ? bindPorts.hashCode() : 0);
        result = 31 * result + (bindAllPorts ? 1 : 0);
        result = 31 * result + (memoryLimit != null ? memoryLimit.hashCode() : 0);
        result = 31 * result + (memorySwap != null ? memorySwap.hashCode() : 0);
        result = 31 * result + (cpus != null ? cpus.hashCode() : 0);
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
        bldToString(sb, "mounts", mounts);
        bldToString(sb, "volumesFrom2", volumesFrom2);
        bldToString(sb, "devices", devices);
        bldToString(sb, "environment", environment);
        bldToString(sb, "bindPorts'", bindPorts);
        bldToString(sb, "bindAllPorts", bindAllPorts);
        bldToString(sb, "memoryLimit", memoryLimit);
        bldToString(sb, "memorySwap", memorySwap);
        bldToString(sb, "cpus", cpus);
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

        public FormValidation doCheckMountsString(@QueryParameter String mountsString) {
            try {
                final String[] mounts = splitAndFilterEmpty(mountsString, "\n");
                parseMountsStrings(mounts, new ArrayList<>());
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

        public FormValidation doCheckCpus(@QueryParameter String cpus) {
            try {
                if ((cpus == null) || cpus.isEmpty()) {
                    return FormValidation.ok();
                }

                Pattern pat = Pattern.compile("^(\\d+(\\.\\d+)?)$");
                if (!pat.matcher(cpus.trim()).matches()) {
                    return FormValidation.error("Wrong cpus format: '%s' (floating point number expected) ", cpus);
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
                if (!(securityOpt.trim().split("=").length == 2
                        || securityOpt.trim().startsWith("no-new-privileges"))) {
                    return FormValidation.warning(
                            "Security option may be incorrect. Please double check syntax: '%s'", securityOpt);
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
                } catch (IllegalArgumentException handledByCode) {
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
            final DockerRegistryEndpoint.DescriptorImpl descriptor = (DockerRegistryEndpoint.DescriptorImpl)
                    Jenkins.get().getDescriptorOrDie(DockerRegistryEndpoint.class);
            return descriptor.doFillCredentialsIdItems(context);
        }

        @Override
        public String getDisplayName() {
            return "Docker template base";
        }
    }
}
