package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.LxcConf;
import com.github.dockerjava.api.model.PortBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

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
    public final String[] volumes;
    public final String volumesFrom;
    public final String[] environment;

    public final String bindPorts;
    public final boolean bindAllPorts;

    public final boolean privileged;

    public DockerTemplateBase(String image,
                          String dnsString,
                          String dockerCommand,
                          String volumesString, String volumesFrom,
                          String environmentsString,
                          String lxcConfString,
                          String hostname,
                          String bindPorts,
                          boolean bindAllPorts,
                          boolean privileged

    ) {
        this.image = image;

        this.dockerCommand = dockerCommand;
        this.lxcConfString = lxcConfString;
        this.privileged = privileged;
        this.hostname = hostname;

        this.bindPorts    = bindPorts;
        this.bindAllPorts = bindAllPorts;

        this.dnsHosts = splitAndFilterEmpty(dnsString);
        this.volumes = splitAndFilterEmpty(volumesString);
        this.volumesFrom = volumesFrom;

        this.environment = splitAndFilterEmpty(environmentsString);
    }

    protected Object readResolve() {
        return this;
    }

    public String[] splitAndFilterEmpty(String s) {
        List<String> list = Splitter.on(' ').omitEmptyStrings().splitToList(s);
        return list.toArray(new String[0]);
    }

    public String getDnsString() {
        return Joiner.on(" ").join(dnsHosts);
    }

    public String getVolumesString() {
        return Joiner.on(" ").join(volumes);
    }

    public String getVolumesFrom() {
        return volumesFrom;
    }

    public String getDisplayName() {
        return "Image of " + image;
    }

    public InspectContainerResponse provisionNew(DockerClient dockerClient) throws DockerException {

        CreateContainerCmd containerConfig = dockerClient.createContainerCmd(image);

        createContainerConfig(containerConfig);

        CreateContainerResponse response = containerConfig.exec();
        String containerId = response.getId();
        // Launch it.. :

        StartContainerCmd startCommand = dockerClient.startContainerCmd(containerId);
        createHostConfig(startCommand);

        startCommand.exec();

        return dockerClient.inspectContainerCmd(containerId).exec();


    }

    public String[] getDockerCommandArray() {
         String[] dockerCommandArray = new String[0];

        if(dockerCommand != null && !dockerCommand.isEmpty()){
            dockerCommandArray = dockerCommand.split(" ");
        }
        return dockerCommandArray;
    }

    public Iterable<PortBinding> getPortMappings() {

        if(Strings.isNullOrEmpty(bindPorts) ) {
            return Collections.EMPTY_LIST;
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

    public CreateContainerCmd createContainerConfig(CreateContainerCmd containerConfig) {

        if (hostname != null && !hostname.isEmpty()) {
            containerConfig.withHostName(hostname);
        }
        String[] cmd = getDockerCommandArray();
        if( cmd.length > 0)
            containerConfig.withCmd(cmd);
        containerConfig.withPortSpecs("22/tcp");

        //containerConfig.setPortSpecs(new String[]{"22/tcp"});
        //containerConfig.getExposedPorts().put("22/tcp",new ExposedPort());
        if( dnsHosts.length > 0 )
            containerConfig.withDns(dnsHosts);
        if( volumesFrom != null && !volumesFrom.isEmpty() )
            containerConfig.withVolumesFrom(volumesFrom);
	if(environment != null && environment.length > 0)
            containerConfig.withEnv(environment);

        return containerConfig;
    }

    public StartContainerCmd createHostConfig(StartContainerCmd hostConfig) {


        hostConfig.withPortBindings(Iterables.toArray(getPortMappings(), PortBinding.class));

        hostConfig.withPublishAllPorts(bindAllPorts);


        hostConfig.withPrivileged(this.privileged);
        if( dnsHosts.length > 0 )
            hostConfig.withDns(dnsHosts);

        // ?
        //if (volumes.length > 0)
        //    hostConfig.with VolumesFrom (volumes);

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
