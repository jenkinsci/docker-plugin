package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.nirima.docker.client.DockerClient;
import com.nirima.docker.client.DockerException;
import com.nirima.docker.client.model.ContainerConfig;
import com.nirima.docker.client.model.ContainerCreateResponse;
import com.nirima.docker.client.model.ContainerInspectResponse;
import com.nirima.docker.client.model.HostConfig;
import com.nirima.docker.client.model.PortBinding;
import com.nirima.docker.client.model.PortMapping;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

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

    private String[] splitAndFilterEmpty(String s) {
        List<String> temp = new ArrayList<String>();
        for (String item : s.split(" ")) {
            if (!item.isEmpty())
                temp.add(item);
        }

        return temp.toArray(new String[temp.size()]);

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

    public ContainerInspectResponse provisionNew(DockerClient dockerClient) throws DockerException {

        ContainerConfig containerConfig = createContainerConfig();

        ContainerCreateResponse container = dockerClient.containers().create(containerConfig);

        // Launch it.. :

        HostConfig hostConfig = createHostConfig();

        dockerClient.container(container.getId()).start(hostConfig);

        String containerId = container.getId();

        return dockerClient.container(containerId).inspect();
    }

    protected String[] getDockerCommandArray() {
         String[] dockerCommandArray = new String[0];

        if(dockerCommand != null && !dockerCommand.isEmpty()){
            dockerCommandArray = dockerCommand.split(" ");
        }
        return dockerCommandArray;
    }

    protected Iterable<PortMapping> getPortMappings() {

        if(Strings.isNullOrEmpty(bindPorts) ) {
            return Collections.EMPTY_LIST;
        }

        return PortMapping.parse(bindPorts);
    }

    public ContainerConfig createContainerConfig() {
        ContainerConfig containerConfig = new ContainerConfig();
        containerConfig.setImage(image);

        if (hostname != null && !hostname.isEmpty()) {
            containerConfig.setHostName(hostname);
        }
        String[] cmd = getDockerCommandArray();
        if( cmd.length > 0)
            containerConfig.setCmd(cmd);
        containerConfig.setPortSpecs(new String[]{"22/tcp"});

        //containerConfig.setPortSpecs(new String[]{"22/tcp"});
        //containerConfig.getExposedPorts().put("22/tcp",new ExposedPort());
        if( dnsHosts.length > 0 )
            containerConfig.setDns(dnsHosts);
        if( volumesFrom != null && !volumesFrom.isEmpty() )
            containerConfig.setVolumesFrom(volumesFrom);
        if(environment != null && environment.length > 0)
            containerConfig.setEnv(environment);

        return containerConfig;
    }

    public HostConfig createHostConfig() {
        HostConfig hostConfig = new HostConfig();

        hostConfig.setPortBindings( getPortMappings() );
        hostConfig.setPublishAllPorts( bindAllPorts );


        hostConfig.setPrivileged(this.privileged);
        if( dnsHosts.length > 0 )
            hostConfig.setDns(dnsHosts);

        if (volumes.length > 0)
            hostConfig.setBinds(volumes);

        List<HostConfig.LxcConf> temp = getLxcConf(hostConfig);

        if (!temp.isEmpty())
            hostConfig.setLxcConf(temp.toArray(new HostConfig.LxcConf[temp.size()]));

        if(!Strings.isNullOrEmpty(volumesFrom) )
            hostConfig.setVolumesFrom(new String[] {volumesFrom});

        return hostConfig;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("image", image)
                .toString();
    }


    protected List<HostConfig.LxcConf> getLxcConf(HostConfig hostConfig) {
        List<HostConfig.LxcConf> temp = new ArrayList<HostConfig.LxcConf>();
        if( lxcConfString == null )
            return temp;
        for (String item : lxcConfString.split(",")) {
            String[] keyValuePairs = item.split("=");
            if (keyValuePairs.length == 2 )
            {
                LOGGER.info("lxc-conf option: " + keyValuePairs[0] + "=" + keyValuePairs[1]);
                HostConfig.LxcConf optN = hostConfig.new LxcConf();
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
