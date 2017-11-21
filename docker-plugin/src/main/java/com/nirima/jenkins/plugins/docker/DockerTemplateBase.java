package com.nirima.jenkins.plugins.docker;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.LxcConf;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.core.NameParser;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import com.trilead.ssh2.Connection;
import hudson.Extension;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.apache.commons.lang.StringUtils.trimToNull;

/**
 * Base for docker templates - does not include Jenkins items like labels.
 */
public class DockerTemplateBase implements Describable<DockerTemplateBase>, Serializable {
    private static final Logger LOGGER = Logger.getLogger(DockerTemplateBase.class.getName());

    private final String image;

    private String pullCredentialsId;
    private transient DockerRegistryEndpoint registry;

    /**
     * Field dockerCommand
     */
    public String dockerCommand;

    /**
     * Field lxcConfString
     */
    public String lxcConfString;

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

    @CheckForNull
    public String[] environment;

    public String bindPorts;
    public boolean bindAllPorts;

    public Integer memoryLimit;
    public Integer memorySwap;
    public Integer cpuShares;

    public boolean privileged;
    public boolean tty;

    @CheckForNull
    private String macAddress;

    @CheckForNull
    private List<String> extraHosts;

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
                              String lxcConfString,
                              String hostname,
                              Integer memoryLimit,
                              Integer memorySwap,
                              Integer cpuShares,
                              String bindPorts,
                              boolean bindAllPorts,
                              boolean privileged,
                              boolean tty,
                              String macAddress
    ) {
        this(image);
        this.pullCredentialsId = pullCredentialsId;
        this.dockerCommand = dockerCommand;
        this.lxcConfString = lxcConfString;
        this.privileged = privileged;
        this.tty = tty;
        this.hostname = hostname;

        this.bindPorts = bindPorts;
        this.bindAllPorts = bindAllPorts;

        this.memoryLimit = memoryLimit;
        this.memorySwap = memorySwap;
        this.cpuShares = cpuShares;

        this.dnsHosts = splitAndFilterEmpty(dnsString, " ");

        this.network = network;

        setVolumes(splitAndFilterEmpty(volumesString, "\n"));
        setVolumesFromString(volumesFromString);

        this.environment = splitAndFilterEmpty(environmentsString, "\n");

        setMacAddress(macAddress);
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
        List<String> result = new ArrayList<String>();
        for (String o : Splitter.on(separator).omitEmptyStrings().split(s)) {
            result.add(o);
        }
        return result.toArray(new String[result.size()]);
    }

    public static List<String> splitAndFilterEmptyList(String s, String separator) {
        List<String> result = new ArrayList<String>();
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

    public DockerRegistryEndpoint getRegistry() {
        if (registry == null) {
            registry = new DockerRegistryEndpoint(null, pullCredentialsId);
        }

        return registry;
    }

    public String getDnsString() {
        return Joiner.on(" ").join(dnsHosts);
    }

    @CheckForNull
    public String[] getVolumes() {
        return filterStringArray(volumes);
    }

    public void setVolumes(String[] volumes) {
        this.volumes = volumes;
    }

    public String getVolumesString() {
        return Joiner.on("\n").join(volumes);
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

    public void setVolumesFromString(String volumesFromString) {
        setVolumesFrom2(splitAndFilterEmpty(volumesFromString, "\n"));
    }

    public String getVolumesFromString() {
        return Joiner.on("\n").join(getVolumesFrom2());
    }

    @CheckForNull
    public String getMacAddress() {
        return trimToNull(macAddress);
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = trimToNull(macAddress);
    }

    public String getDisplayName() {
        return "Image of " + getImage();
    }

    public Integer getMemoryLimit() {
        return memoryLimit;
    }
    
    public Integer getMemorySwap() {
   	  return memorySwap;
    }

    public Integer getCpuShares() {
        return cpuShares;
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

    public List<LxcConf> getLxcConf() {
        List<LxcConf> temp = new ArrayList<>();
        if (lxcConfString == null || lxcConfString.trim().equals(""))
            return temp;
        for (String item : lxcConfString.split(",")) {
            String[] keyValuePairs = item.split("=");
            if (keyValuePairs.length == 2) {
                LOGGER.info("lxc-conf option: " + keyValuePairs[0] + "=" + keyValuePairs[1]);
                LxcConf optN = new LxcConf();
                optN.setKey(keyValuePairs[0]);
                optN.setValue(keyValuePairs[1]);
                temp.add(optN);
            } else {
                LOGGER.warning("Specified option: " + item + " is not in the form X=Y, please correct.");
            }
        }
        return temp;
    }

    public String getEnvironmentsString() {
        if (environment == null) return null;
        return Joiner.on("\n").join(environment);
    }

    @CheckForNull
    public List<String> getExtraHosts() {
        return extraHosts;
    }

    public void setExtraHosts(List<String> extraHosts) {
        this.extraHosts = extraHosts;
    }

    @DataBoundSetter
    public void setExtraHostsString(String extraHostsString) {
        setExtraHosts(splitAndFilterEmptyList(extraHostsString, "\n"));
    }

    public String getExtraHostsString() {
        if (CollectionUtils.isEmpty(getExtraHosts())) {
            return "";
        } else {
            return Joiner.on("\n").join(getExtraHosts());
        }
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
        map.put("JenkinsId", JenkinsUtils.getInstanceId());


        containerConfig.withLabels(map);

        List<LxcConf> lxcConfs = getLxcConf();
        if (!lxcConfs.isEmpty()) {
            containerConfig.withLxcConf(Iterables.toArray(lxcConfs, LxcConf.class));
        }

        if (cpuShares != null && cpuShares > 0) {
            containerConfig.withCpuShares(cpuShares);
        }

        if (memoryLimit != null && memoryLimit > 0) {
            Long memoryInByte = (long) memoryLimit * 1024 * 1024;
            containerConfig.withMemory(memoryInByte);
        }
        
        if (memorySwap != null) {
      	  if(memorySwap > 0) {
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

        return containerConfig;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("image", getImage())
                .toString();
    }

    @Override
    public Descriptor<DockerTemplateBase> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(DockerTemplateBase.class);
    }

    public String getFullImageId() {
        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(image);
        // if image was specified without tag, then treat as latest
        return repostag.repos + ":" + (repostag.tag.isEmpty() ? "latest" : repostag.tag);
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
