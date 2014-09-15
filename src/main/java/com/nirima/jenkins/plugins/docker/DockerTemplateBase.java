package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.nirima.docker.client.DockerClient;
import com.nirima.docker.client.DockerException;
import com.nirima.docker.client.model.ContainerConfig;
import com.nirima.docker.client.model.ContainerCreateResponse;
import com.nirima.docker.client.model.ContainerInspectResponse;
import com.nirima.docker.client.model.HostConfig;
import com.nirima.docker.client.model.PortBinding;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by magnayn on 15/09/2014.
 */
public class DockerTemplateBase {
    private static final Logger LOGGER = Logger.getLogger(DockerTemplate.class.getName());


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

    public final boolean privileged;

    @DataBoundConstructor
    public DockerTemplateBase(String image,
                          String dnsString,
                          String dockerCommand,
                          String volumesString, String volumesFrom,
                          String lxcConfString,
                          String hostname,
                          boolean privileged

    ) {
        this.image = image;

        this.dockerCommand = dockerCommand;
        this.lxcConfString = lxcConfString;
        this.privileged = privileged;
        this.hostname = hostname;

        this.dnsHosts = splitAndFilterEmpty(dnsString);
        this.volumes = splitAndFilterEmpty(volumesString);
        this.volumesFrom = volumesFrom;
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

        ContainerConfig containerConfig = new ContainerConfig();
        containerConfig.setImage(image);

        String[] dockerCommandArray;

        if(dockerCommand != null && !dockerCommand.isEmpty()){
            dockerCommandArray = dockerCommand.split(" ");
        } else {
            //default value to preserve comptability
            dockerCommandArray = new String[]{"/usr/sbin/sshd", "-D"};
        }

        if (hostname != null && !hostname.isEmpty()) {
            containerConfig.setHostName(hostname);
        }
        containerConfig.setCmd(dockerCommandArray);
        containerConfig.setPortSpecs(new String[]{"22/tcp"});
        //containerConfig.setPortSpecs(new String[]{"22/tcp"});
        //containerConfig.getExposedPorts().put("22/tcp",new ExposedPort());
        if( dnsHosts.length > 0 )
            containerConfig.setDns(dnsHosts);
        if( volumesFrom != null && !volumesFrom.isEmpty() )
            containerConfig.setVolumesFrom(volumesFrom);

        ContainerCreateResponse container = dockerClient.containers().create(containerConfig);

        // Launch it.. :
        // MAybe should be in computerLauncher

        Map<String, PortBinding[]> bports = new HashMap<String, PortBinding[]>();
        PortBinding binding = new PortBinding();
        binding.hostIp = "0.0.0.0";
        //binding.hostPort = ":";
        bports.put("22/tcp", new PortBinding[] { binding });

        HostConfig hostConfig = new HostConfig();
        hostConfig.setPortBindings(bports);
        hostConfig.setPrivileged(this.privileged);
        if( dnsHosts.length > 0 )
            hostConfig.setDns(dnsHosts);

        if (volumes.length > 0)
            hostConfig.setBinds(volumes);

        List<HostConfig.LxcConf> temp = new ArrayList<HostConfig.LxcConf>();
        for (String item : lxcConfString.split(" ")) {
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

        if (!temp.isEmpty())
            hostConfig.setLxcConf(temp.toArray(new HostConfig.LxcConf[temp.size()]));

        if(volumesFrom != null && !volumesFrom.isEmpty())
            hostConfig.setVolumesFrom(new String[] {volumesFrom});

        dockerClient.container(container.getId()).start(hostConfig);

        String containerId = container.getId();

        return dockerClient.container(containerId).inspect();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("image", image)
                .toString();
    }
}
