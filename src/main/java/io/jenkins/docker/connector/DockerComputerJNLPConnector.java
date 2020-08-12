package io.jenkins.docker.connector;

import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.bldToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.endToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.fixEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndFilterEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.startToString;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.google.common.base.Joiner;
import com.nirima.jenkins.plugins.docker.DockerTemplate;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.client.DockerEnvUtils;
import jenkins.model.Jenkins;
import jenkins.slaves.JnlpSlaveAgentProtocol;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerJNLPConnector extends DockerComputerConnector {

    @CheckForNull
    private String user;
    private final JNLPLauncher jnlpLauncher;
    @CheckForNull
    private String jenkinsUrl;
    @CheckForNull
    private String[] entryPointArguments;

    @Restricted(NoExternalUse.class)
    public DockerComputerJNLPConnector() {
        this(new JNLPLauncher());
    }

    @DataBoundConstructor
    public DockerComputerJNLPConnector(JNLPLauncher jnlpLauncher) {
        this.jnlpLauncher = jnlpLauncher;
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
    public String getJenkinsUrl() {
        return Util.fixEmptyAndTrim(jenkinsUrl);
    }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = Util.fixEmptyAndTrim(jenkinsUrl);
    }

    @Nonnull
    public String getEntryPointArgumentsString() {
        if (entryPointArguments == null) return "";
        return Joiner.on("\n").join(entryPointArguments);
    }

    @DataBoundSetter
    public void setEntryPointArgumentsString(String entryPointArgumentsString) {
        setEntryPointArguments(splitAndFilterEmpty(entryPointArgumentsString, "\n"));
    }

    private void setEntryPointArguments(String[] entryPointArguments) {
        this.entryPointArguments = fixEmpty(entryPointArguments);
    }

    public DockerComputerJNLPConnector withUser(String user) {
        setUser(user);
        return this;
    }

    public DockerComputerJNLPConnector withJenkinsUrl(String jenkinsUrl) {
        setJenkinsUrl(jenkinsUrl);
        return this;
    }

    public DockerComputerJNLPConnector withEntryPointArguments(String... args) {
        setEntryPointArguments(args);
        return this;
    }

    public JNLPLauncher getJnlpLauncher() {
        return jnlpLauncher;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(entryPointArguments);
        result = prime * result + Objects.hash(jenkinsUrl, jnlpLauncher, user);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        DockerComputerJNLPConnector other = (DockerComputerJNLPConnector) obj;
        return Arrays.equals(entryPointArguments, other.entryPointArguments)
                && Objects.equals(jenkinsUrl, other.jenkinsUrl) && Objects.equals(jnlpLauncher, other.jnlpLauncher)
                && Objects.equals(user, other.user);
    }

    @Override
    public String toString() {
        final StringBuilder sb = startToString(this);
        bldToString(sb, "user", user);
        bldToString(sb, "jnlpLauncher", jnlpLauncher);
        bldToString(sb, "jenkinsUrl", jenkinsUrl);
        bldToString(sb, "entryPointArguments", entryPointArguments);
        endToString(sb);
        return sb.toString();
    }

    @Override
    protected ComputerLauncher createLauncher(final DockerAPI api, final String workdir, final InspectContainerResponse inspect, TaskListener listener) throws IOException, InterruptedException {
        return new JNLPLauncher();
    }

    @Restricted(NoExternalUse.class)
    enum ArgumentVariables {
        NodeName("NODE_NAME", "The name assigned to this node"), //
        Secret("JNLP_SECRET",
                "The secret that must be passed to agent.jar's -secret argument to pass JNLP authentication."), //
        JenkinsUrl("JENKINS_URL", "The Jenkins root URL."), //
        TunnelArgument("TUNNEL_ARG",
                "If a JNLP tunnel has been specified then this evaluates to '-tunnel', otherwise it evaluates to the empty string"), //
        TunnelValue("TUNNEL", "The JNLP tunnel value");
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

    private static final String DEFAULT_ENTRY_POINT_ARGUMENTS = "${" + ArgumentVariables.TunnelArgument.getName()
            + "}\n${" + ArgumentVariables.TunnelValue.getName() + "}\n-url\n${" + ArgumentVariables.JenkinsUrl.getName()
            + "}\n${" + ArgumentVariables.Secret.getName() + "}\n${" + ArgumentVariables.NodeName.getName() + "}";

    @Override
    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {
        final String effectiveJenkinsUrl = StringUtils.isEmpty(jenkinsUrl) ? Jenkins.getInstance().getRootUrl() : jenkinsUrl;
        final String nodeName = DockerTemplate.getNodeNameFromContainerConfig(cmd);
        final String secret = JnlpSlaveAgentProtocol.SLAVE_SECRET.mac(nodeName);
        final EnvVars knownVariables = calculateVariablesForVariableSubstitution(nodeName, secret, jnlpLauncher.tunnel, effectiveJenkinsUrl);
        final String configuredArgString = getEntryPointArgumentsString();
        final String effectiveConfiguredArgString = StringUtils.isNotBlank(configuredArgString) ? configuredArgString : DEFAULT_ENTRY_POINT_ARGUMENTS;
        final String resolvedArgString = Util.replaceMacro(effectiveConfiguredArgString, knownVariables);
        final String[] resolvedArgs = splitAndFilterEmpty(resolvedArgString, "\n");

        cmd.withCmd(resolvedArgs);
        String vmargs = jnlpLauncher.vmargs;
        if (StringUtils.isNotBlank(vmargs)) {
            DockerEnvUtils.addEnvToCmd("JAVA_OPT", vmargs.trim(), cmd);
        }
        if (StringUtils.isNotBlank(user)) {
            cmd.withUser(user);
        }
    }

    private static EnvVars calculateVariablesForVariableSubstitution(final String nodeName, final String secret,
            final String jnlpTunnel, final String jenkinsUrl) throws IOException, InterruptedException {
        final EnvVars knownVariables = new EnvVars();
        final Jenkins j = Jenkins.getInstance();
        addEnvVars(knownVariables, j.getGlobalNodeProperties());
        for (final ArgumentVariables v : ArgumentVariables.values()) {
            // This switch statement MUST handle all possible
            // values of v.
            final String argValue;
            switch (v) {
                case JenkinsUrl :
                    argValue = jenkinsUrl;
                    break;
                case TunnelArgument :
                    argValue = StringUtils.isNotBlank(jnlpTunnel) ? "-tunnel" : "";
                    break;
                case TunnelValue :
                    argValue = jnlpTunnel;
                    break;
                case Secret :
                    argValue = secret;
                    break;
                case NodeName :
                    argValue = nodeName;
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

    @Extension @Symbol("jnlp")
    public static final class DescriptorImpl extends Descriptor<DockerComputerConnector> {

        public Collection<ArgumentVariables> getEntryPointArgumentVariables() {
            return Arrays.asList(ArgumentVariables.values());
        }

        public Collection<String> getDefaultEntryPointArguments() {
            final String[] args = splitAndFilterEmpty(DEFAULT_ENTRY_POINT_ARGUMENTS, "\n");
            return Arrays.asList(args);
        }

        @Override
        public String getDisplayName() {
            return "Connect with JNLP";
        }
    }
}
