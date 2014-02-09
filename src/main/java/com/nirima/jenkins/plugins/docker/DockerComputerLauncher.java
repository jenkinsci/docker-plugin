package com.nirima.jenkins.plugins.docker;


import com.nirima.docker.client.model.ContainerInspectResponse;
import hudson.Extension;
import hudson.model.*;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;


import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * {@link hudson.slaves.ComputerLauncher} for EC2 that waits for the instance to really come up before proceeding to
 * the real user-specified {@link hudson.slaves.ComputerLauncher}.
 *
 * @author Kohsuke Kawaguchi
 */
public class DockerComputerLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(DockerComputerLauncher.class.getName());


    public final ContainerInspectResponse detail;
    public final DockerTemplate template;

    public DockerComputerLauncher(DockerTemplate template, ContainerInspectResponse containerInspectResponse) {
        this.template = template;
        this.detail = containerInspectResponse;
    }

    @Override
    public void launch(SlaveComputer _computer, TaskListener listener) throws IOException, InterruptedException {

        for(int tries=0;tries < 3; tries++) {
        SSHLauncher launcher = getSSHLauncher();
        launcher.launch(_computer, listener);

            if( launcher.getConnection() != null ) {
                LOGGER.log(Level.INFO, "Launched " + _computer);
                return;
            }

            Thread.sleep(5000 * (tries+1));
        }

            LOGGER.log(Level.WARNING, "Couldn't launch Docker template. Closing.");
            DockerComputer dc = (DockerComputer)_computer;
            dc.getNode().terminate();


        }

    public SSHLauncher getSSHLauncher() throws MalformedURLException {
        /**
         * ContainerInspectResponse{
         * id='970d68eb7410bca37ccc8ac193ae68a324f7d286012c1994dcf58a28daa76da2', created='2014-01-09T12:19:37.322591068Z',
         * path='/usr/sbin/sshd', args=[-D],
         * config=ContainerConfig{hostName=970d68eb7410, portSpecs=null, user=, tty=false, stdinOpen=false, stdInOnce=false, memoryLimit=0, memorySwap=0, cpuShares=0, attachStdin=false, attachStdout=false, attachStderr=false, env=null, cmd=[Ljava.lang.String;@658782a7, dns=null, image=jenkins-3, volumes=null, volumesFrom=, entrypoint=null, networkDisabled=false, privileged=false, workingDir=, domainName=, exposedPorts={22/tcp={}}}, state=ContainerState{running=true, pid=8032, exitCode=0, startedAt='2014-01-09T12:19:37.400471534Z', ghost=false, finishedAt='0001-01-01T00:00:00Z'}, image='0ca6c5d5135db3ffb8abfef6a0861a0d2e44b6f37a33b4012a3f2d5cc99f68e9',
         * networkSettings=NetworkSettings{ipAddress='172.17.0.58', ipPrefixLen=16, gateway='172.17.42.1', bridge='docker0', ports={22/tcp=[Lcom.nirima.docker.client.model.PortBinding;@2392d604}}, sysInitPath='null', resolvConfPath='/etc/resolv.conf', volumes={}, volumesRW={}, hostnamePath='/var/lib/docker/containers/970d68eb7410bca37ccc8ac193ae68a324f7d286012c1994dcf58a28daa76da2/hostname', hostsPath='/var/lib/docker/containers/970d68eb7410bca37ccc8ac193ae68a324f7d286012c1994dcf58a28daa76da2/hosts', name='/prickly_turing', driver='aufs'}

         */
        int port = Integer.parseInt(detail.getNetworkSettings().ports.get("22/tcp")[0].hostPort);

        URL hostUrl = new URL(template.getParent().serverUrl);
        String host = hostUrl.getHost();
        
        LOGGER.log(Level.INFO, "Creating slave SSH launcher for " + host + ":" + port);

        return new SSHLauncher(host, port, template.credentialsId, template.jvmOptions , template.javaPath, template.prefixStartSlaveCmd, template.suffixStartSlaveCmd);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return "Docker SSH Launcher";
        }

        public Class getSshConnectorClass() {
            return SSHConnector.class;
        }

        /**
         * Delegates the help link to the {@link SSHConnector}.
         */
        @Override
        public String getHelpFile(String fieldName) {
            String n = super.getHelpFile(fieldName);
            if (n==null)
                n = Jenkins.getInstance().getDescriptor(SSHConnector.class).getHelpFile(fieldName);
            return n;
        }
    }
}