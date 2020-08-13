package io.jenkins.docker.connector;

import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.bldToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.endToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.fixEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndFilterEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.startToString;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.google.common.base.Joiner;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.client.DockerMultiplexedInputStream;
import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerAttachConnector extends DockerComputerConnector implements Serializable {

    @CheckForNull
    private String user;
    @CheckForNull
    private String javaExe;
    @CheckForNull
    private String[] jvmArgs;
    @CheckForNull
    private String[] entryPointCmd;

    @DataBoundConstructor
    public DockerComputerAttachConnector() {
    }

    public DockerComputerAttachConnector(String user) {
        this.user = user;
    }

    @CheckForNull
    public String getUser() {
        return Util.fixEmptyAndTrim(user);
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = Util.fixEmptyAndTrim(user);
    }

    @CheckForNull
    public String getJavaExe() {
        return Util.fixEmptyAndTrim(javaExe);
    }

    @DataBoundSetter
    public void setJavaExe(String javaExe) {
        this.javaExe = Util.fixEmptyAndTrim(javaExe);
    }

    @Nonnull
    public String getEntryPointCmdString() {
        if (entryPointCmd == null) return "";
        return Joiner.on("\n").join(entryPointCmd);
    }

    @DataBoundSetter
    public void setEntryPointCmdString(String entryPointCmdString) {
        setEntryPointCmd(splitAndFilterEmpty(entryPointCmdString, "\n"));
    }

    private void setEntryPointCmd(String[] entryPointCmd) {
        this.entryPointCmd = fixEmpty(entryPointCmd);
    }

    @Nonnull
    public String getJvmArgsString() {
        if (jvmArgs == null) return "";
        return Joiner.on("\n").join(jvmArgs);
    }

    @DataBoundSetter
    public void setJvmArgsString(String jvmArgsString) {
        setJvmArgs(splitAndFilterEmpty(jvmArgsString, "\n"));
    }

    private void setJvmArgs(String[] jvmArgs) {
        this.jvmArgs = fixEmpty(jvmArgs);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(entryPointCmd);
        result = prime * result + Arrays.hashCode(jvmArgs);
        result = prime * result + Objects.hash(javaExe, user);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DockerComputerAttachConnector other = (DockerComputerAttachConnector) obj;
        return Arrays.equals(entryPointCmd, other.entryPointCmd) && Objects.equals(javaExe, other.javaExe)
                && Arrays.equals(jvmArgs, other.jvmArgs) && Objects.equals(user, other.user);
    }

    @Override
    public String toString() {
        final StringBuilder sb = startToString(this);
        bldToString(sb, "user", user);
        bldToString(sb, "javaExe", javaExe);
        bldToString(sb, "jvmArgs", jvmArgs);
        bldToString(sb, "entryPointCmd", entryPointCmd);
        endToString(sb);
        return sb.toString();
    }

    @Override
    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {
        ensureWaiting(cmd);
    }

    @Override
    public void afterContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {
        try(final DockerClient client = api.getClient()) {
            injectRemotingJar(containerId, workdir, client);
        }
    }

    @Restricted(NoExternalUse.class)
    enum ArgumentVariables {
        JavaExe("JAVA_EXE", "The Java Executable, e.g. java, /usr/bin/java etc."), //
        JvmArgs("JVM_ARGS", "Any arguments for the JVM itself, e.g. -Xmx250m."), //
        JarName("JAR_NAME", "The name of the jar file the node must run, e.g. agent.jar."), //
        RemoteFs("FS_DIR",
                "The filesystem folder in which the agent process is to be run."), //
        JenkinsUrl("JENKINS_URL", "The Jenkins root URL.");
        private final String name;
        private final String description;

        ArgumentVariables(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final String DEFAULT_JAVA_EXE = "java";
    private static final String DEFAULT_JVM_ARGS = "";
    private static final String DEFAULT_ENTRY_POINT_CMD_STRING = "${" + ArgumentVariables.JavaExe.getName() + "}\n"
            + "${" + ArgumentVariables.JvmArgs.getName() + "}\n"
            + "-jar\n"
            + "${" + ArgumentVariables.RemoteFs.getName() + "}/${" + ArgumentVariables.JarName.getName() + "}\n"
            + "-noReconnect\n"
            + "-noKeepAlive\n"
            + "-slaveLog\n"
            + "${" + ArgumentVariables.RemoteFs.getName() + "}/agent.log";

    @Override
    protected ComputerLauncher createLauncher(DockerAPI api, String workdir, InspectContainerResponse inspect, TaskListener listener) throws IOException, InterruptedException {
        return new DockerAttachLauncher(api, inspect.getId(), getUser(), workdir, getJavaExe(), getJvmArgsString(), getEntryPointCmdString());
    }

    @Extension(ordinal = 100) @Symbol("attach")
    public static class DescriptorImpl extends Descriptor<DockerComputerConnector> {

        public String getDefaultJavaExe() {
            return DEFAULT_JAVA_EXE;
        }

        public String getJavaExeVariableName() {
            return ArgumentVariables.JavaExe.getName();
        }

        public String getDefaultJvmArgs() {
            return DEFAULT_JVM_ARGS;
        }

        public String getJvmArgsVariableName() {
            return ArgumentVariables.JvmArgs.getName();
        }

        public Collection<ArgumentVariables> getEntryPointCmdVariables() {
            return Arrays.asList(ArgumentVariables.values());
        }

        public Collection<String> getDefaultEntryPointCmd() {
            final String[] args = splitAndFilterEmpty(DEFAULT_ENTRY_POINT_CMD_STRING, "\n");
            return Arrays.asList(args);
        }

        @Override
        public String getDisplayName() {
            return "Attach Docker container";
        }
    }

    private static class DockerAttachLauncher extends ComputerLauncher {
        private final DockerAPI api;
        private final String containerId;
        private final String userOrNull;
        private final String remoteFs;
        private final String javaExeOrNull;
        private final String jvmArgsOrEmpty;
        private final String entryPointCmdOrEmpty;

        private DockerAttachLauncher(DockerAPI api, String containerId, String user, String remoteFs, String javaExe, String jvmArgs, String entryPointCmd) {
            this.api = api;
            this.containerId = containerId;
            this.userOrNull = user;
            this.remoteFs = remoteFs;
            this.javaExeOrNull = javaExe;
            this.jvmArgsOrEmpty = jvmArgs;
            this.entryPointCmdOrEmpty = entryPointCmd;
        }

        @Override
        public void launch(final SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
            final PrintStream logger = computer.getListener().getLogger();
            final String jenkinsUrl = Jenkins.getInstance().getRootUrl();
            final String effectiveJavaExe = StringUtils.isNotBlank(javaExeOrNull) ? javaExeOrNull : DEFAULT_JAVA_EXE;
            final String effectiveJvmArgs = StringUtils.isNotBlank(jvmArgsOrEmpty) ? jvmArgsOrEmpty : DEFAULT_JVM_ARGS ;
            final EnvVars knownVariables = calculateVariablesForVariableSubstitution(effectiveJavaExe, effectiveJvmArgs, remoting.getName(), remoteFs, jenkinsUrl);
            final String effectiveEntryPointCmdString = StringUtils.isNotBlank(entryPointCmdOrEmpty) ? entryPointCmdOrEmpty : DEFAULT_ENTRY_POINT_CMD_STRING;
            final String resolvedEntryPointCmdString = Util.replaceMacro(effectiveEntryPointCmdString, knownVariables);
            final String[] resolvedEntryPointCmd = splitAndFilterEmpty(resolvedEntryPointCmdString, "\n");
            logger.println("Connecting to docker container " + containerId + ", running command " + Joiner.on(" ").join(resolvedEntryPointCmd));

            final String execId;
            try(final DockerClient client = api.getClient()) {
                final ExecCreateCmd cmd = client.execCreateCmd(containerId)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .withTty(false)
                        .withCmd(resolvedEntryPointCmd);
                if (StringUtils.isNotBlank(userOrNull)) {
                    cmd.withUser(userOrNull);
                }
                final ExecCreateCmdResponse exec = cmd.exec();
                execId = exec.getId();
            }
            final String js = "{ \"Detach\": false, \"Tty\": false }";
            final Socket socket = api.getSocket();
            final OutputStream out = socket.getOutputStream();
            final InputStream in = socket.getInputStream();
            final PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.US_ASCII));
            w.println("POST /v1.32/exec/" + execId + "/start HTTP/1.1");
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

        private static EnvVars calculateVariablesForVariableSubstitution(@Nonnull final String javaExe,
                @Nonnull final String jvmArgs, @Nonnull final String jarName, @Nonnull final String remoteFs,
                @Nonnull final String jenkinsUrl) throws IOException, InterruptedException {
            final EnvVars knownVariables = new EnvVars();
            final Jenkins j = Jenkins.getInstance();
            addEnvVars(knownVariables, j.getGlobalNodeProperties());
            for (final ArgumentVariables v : ArgumentVariables.values()) {
                // This switch statement MUST handle all possible
                // values of v.
                final String argValue;
                switch (v) {
                    case JavaExe :
                        argValue = javaExe;
                        break;
                    case JvmArgs :
                        argValue = jvmArgs;
                        break;
                    case JarName :
                        argValue = jarName;
                        break;
                    case RemoteFs :
                        argValue = remoteFs;
                        break;
                    case JenkinsUrl :
                        argValue = jenkinsUrl;
                        break;
                    default :
                        final String msg = "Internal code error: Switch statement is missing \"case " + v.name()
                                + " : argValue = ... ; break;\" code.";
                        // If this line throws an exception then it's because
                        // someone has added a new variable to the enum without
                        // adding code above to handle it.
                        // The two have to be kept in step in order to
                        // ensure that the help text stays in step.
                        throw new RuntimeException(msg);
                }
                addEnvVar(knownVariables, v.getName(), argValue);
            }
            return knownVariables;
        }

        private static String readLine(InputStream in) throws IOException {
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
