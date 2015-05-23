package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.model.*;
import org.apache.commons.lang.StringUtils;
import shaded.com.google.common.base.Function;
import shaded.com.google.common.base.Joiner;
import shaded.com.google.common.base.Objects;
import shaded.com.google.common.base.Splitter;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.collect.Iterables;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import static org.apache.commons.lang.StringUtils.*;

/**
 * Base for docker templates - does not include Jenkins items like labels.
 */
public abstract class DockerTemplateBase {
    private static final Logger LOGGER = Logger.getLogger(DockerTemplateBase.class.getName());


    public final String image;

    /**
     * Field dockerCommand
     */
    public final String dockerCommand;

    /**
     * Field lxcConfString
     */
    public final String lxcConfString;

    public final String hostname;

    public final String[] dnsHosts;

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

    public final String[] environment;

    public final String bindPorts;
    public final boolean bindAllPorts;

    public final Integer memoryLimit;
    public final Integer cpuShares;

    public final boolean privileged;
    public final boolean tty;

    @CheckForNull private String macAddress;

    public DockerTemplateBase(String image,
                              String dnsString,
                              String dockerCommand,
                              String volumesString,
                              String volumesFromString,
                              String environmentsString,
                              String lxcConfString,
                              String hostname,
                              Integer memoryLimit,
                              Integer cpuShares,
                              String bindPorts,
                              boolean bindAllPorts,
                              boolean privileged,
                              boolean tty,
                              String macAddress

    ) {
        this.image = image;

        this.dockerCommand = dockerCommand;
        this.lxcConfString = lxcConfString;
        this.privileged = privileged;
        this.tty = tty;
        this.hostname = hostname;

        this.bindPorts = bindPorts;
        this.bindAllPorts = bindAllPorts;

        this.memoryLimit = memoryLimit;
        this.cpuShares = cpuShares;

        this.dnsHosts = splitAndFilterEmpty(dnsString, " ");
        this.volumes = splitAndFilterEmpty(volumesString, "\n");
        setVolumesFrom2(splitAndFilterEmpty(volumesFromString, "\n"));

        this.environment = splitAndFilterEmpty(environmentsString, " ");

        setMacAddress(macAddress);
    }

    protected Object readResolve() {
        if (volumesFrom != null) {
            setVolumesFrom2(new String[]{volumesFrom});
            volumesFrom = null;
        }

        return this;
    }

    public static String[] splitAndFilterEmpty(String s, String separator) {
        List<String> list = Splitter.on(separator).omitEmptyStrings().splitToList(s);
        return list.toArray(new String[0]);
    }

    public String getDnsString() {
        return Joiner.on(" ").join(dnsHosts);
    }

    @CheckForNull
    public String[] getVolumes() {
        return volumes;
    }

    public String getVolumesString() {
        return Joiner.on("\n").join(volumes);
    }

    /**
     *
     * @deprecated use {@link #getVolumesFrom2()}
     */
    @Deprecated
    public String getVolumesFrom() {
        return volumesFrom;
    }

    public String[] getVolumesFrom2() {
        return volumesFrom2;
    }

    public void setVolumesFrom2(String[] volumes) {
        this.volumesFrom2 = volumes;
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
        return "Image of " + image;
    }

    public Integer getMemoryLimit() {
        return memoryLimit;
    }

    public Integer getCpuShares() {
        return cpuShares;
    }

    public String provisionNew(DockerClient dockerClient) throws DockerException {

        CreateContainerCmd containerConfig = dockerClient.createContainerCmd(image);

        fillContainerConfig(containerConfig);

        CreateContainerResponse response = containerConfig.exec();
        String containerId = response.getId();
        // Launch it.. :

        StartContainerCmd startCommand = dockerClient.startContainerCmd(containerId);
        createHostConfig(startCommand);

        startCommand.exec();

        return containerId;
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
        if (cmd.length > 0)
            containerConfig.withCmd(cmd);

        containerConfig.withPortSpecs("22/tcp");

        //containerConfig.setPortSpecs(new String[]{"22/tcp"});
        //containerConfig.getExposedPorts().put("22/tcp",new ExposedPort());


        if (cpuShares != null && cpuShares > 0) {
            containerConfig.withCpuShares(cpuShares);
        }

        if (memoryLimit != null && memoryLimit > 0) {
            Long memoryInByte = (long) memoryLimit * 1024 * 1024;
            containerConfig.withMemoryLimit(memoryInByte);
        }

        if (dnsHosts.length > 0)
            containerConfig.withDns(dnsHosts);

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

        containerConfig.withTty(this.tty);

        if (environment != null && environment.length > 0)
            containerConfig.withEnv(environment);

        if (getMacAddress() != null) {
            containerConfig.withMacAddress(getMacAddress());
        }

        return containerConfig;
    }

    public StartContainerCmd createHostConfig(StartContainerCmd hostConfig) {

        hostConfig.withPortBindings(Iterables.toArray(getPortMappings(), PortBinding.class));

        hostConfig.withPublishAllPorts(bindAllPorts);


        hostConfig.withPrivileged(this.privileged);
        if( dnsHosts.length > 0 )
            hostConfig.withDns(dnsHosts);

        List<LxcConf> lxcConfs = getLxcConf();

        if (!lxcConfs.isEmpty()) {
            hostConfig.withLxcConf(Iterables.toArray(lxcConfs, LxcConf.class));
        }

        if(!Strings.isNullOrEmpty(volumesFrom) )
            hostConfig.withVolumesFrom(volumesFrom);

        return hostConfig;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("image", image)
                .toString();
    }


    public List<LxcConf> getLxcConf() {
        List<LxcConf> temp = new ArrayList<LxcConf>();
        if( lxcConfString == null || lxcConfString.trim().equals(""))
            return temp;
        for (String item : lxcConfString.split(",")) {
            String[] keyValuePairs = item.split("=");
            if (keyValuePairs.length == 2 )
            {
                LOGGER.info("lxc-conf option: " + keyValuePairs[0] + "=" + keyValuePairs[1]);
                LxcConf optN = new LxcConf();
                optN.setKey(keyValuePairs[0]);
                optN.setValue(keyValuePairs[1]);
                temp.add(optN);
            }
            else
            {
                LOGGER.warning("Specified option: " + item + " is not in the form X=Y, please correct.");
            }
        }
        return temp;
    }
}
