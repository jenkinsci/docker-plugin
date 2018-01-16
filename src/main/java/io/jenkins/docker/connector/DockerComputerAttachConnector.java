package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.client.DockerMultiplexedInputStream;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.Socket;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerAttachConnector extends DockerComputerConnector implements Serializable {


    private String user;

    @DataBoundConstructor
    public DockerComputerAttachConnector() {
    }

    public DockerComputerAttachConnector(String user) {
        this.user = user;
    }

    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }


    @Override
    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {
        ensureWaiting(cmd);
    }

    @Override
    public void afterContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {
        final DockerClient client = api.getClient();
        injectRemotingJar(containerId, workdir, client);
    }

    @Override
    protected ComputerLauncher createLauncher(DockerAPI api, String workdir, InspectContainerResponse inspect, TaskListener listener) throws IOException, InterruptedException {
        return new DockerAttachLauncher(api, inspect.getId(), user, workdir);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerComputerAttachConnector that = (DockerComputerAttachConnector) o;

        return user != null ? user.equals(that.user) : that.user == null;
    }

    @Extension(ordinal = 100) @Symbol("attach")
    public static class DescriptorImpl extends Descriptor<DockerComputerConnector> {

        @Override
        public String getDisplayName() {
            return "Attach Docker container";
        }
    }

    private static class DockerAttachLauncher extends ComputerLauncher {

        private final DockerAPI api;
        private final String containerId;
        private final String user;
        private final String remoteFs;

        private DockerAttachLauncher(DockerAPI api, String containerId, String user, String remoteFs) {
            this.api = api;
            this.containerId = containerId;
            this.user = user;
            this.remoteFs = remoteFs;
        }

        public void launch(final SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
            final DockerClient client = api.getClient();

            final PrintStream logger = computer.getListener().getLogger();
            logger.println("Connecting to docker container "+containerId);

            final ExecCreateCmd cmd = client.execCreateCmd(containerId)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(false)
                    .withCmd("java", "-jar", remoteFs + '/' + remoting.getName(), "-noReconnect", "-noKeepAlive", "-slaveLog", remoteFs + "/agent.log");

            if (StringUtils.isNotBlank(user)) {
                cmd.withUser(user);
            }

            final ExecCreateCmdResponse exec = cmd.exec();

            String js = "{ \"Detach\": false, \"Tty\": false }";

            Socket socket = api.getSocket();

            final OutputStream out = socket.getOutputStream();
            final InputStream in = socket.getInputStream();

            final PrintWriter w = new PrintWriter(out);
            w.println("POST /v1.32/exec/" + exec.getId() + "/start HTTP/1.1");
            w.println("Host: docker.sock");
            w.println("Content-Type: application/json");
            w.println("Upgrade: tcp");
            w.println("Connection: Upgrade");
            w.println("Content-Length: " + js.length());
            w.println();
            w.println(js);
            w.flush();

            // read HTTP response headers
            String line = readLine(in);
            logger.println(line);
            if (! line.startsWith("HTTP/1.1 101 ")) {   // Switching Protocols
                throw new IOException("Unexpected HTTP response status line " + line);
            }

            // Skip HTTP header
            while ((line = readLine(in)).length() > 0) {
                if (line.length() == 0) break; // end of header
                logger.println(line);
            }

            final InputStream demux = new DockerMultiplexedInputStream(in);

            computer.setChannel(demux, out, listener, new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    // Bye!
                }
            });

        }

        private String readLine(InputStream in) throws IOException {
            StringBuilder s = new StringBuilder();
            int c;
            while((c = in.read()) > 0) {
                if (c == '\r') break; // EOL
                s.append((char) c);
            }
            in.read(); // \n
            return s.toString();
        }

    }

}
