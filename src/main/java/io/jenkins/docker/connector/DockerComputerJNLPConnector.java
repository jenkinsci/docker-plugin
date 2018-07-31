package io.jenkins.docker.connector;

import static com.nirima.jenkins.plugins.docker.DockerTemplateBase.splitAndFilterEmpty;

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
import hudson.slaves.NodeProperty;
import hudson.util.LogTaskListener;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerJNLPConnector extends DockerComputerConnector {
    private static final Logger LOGGER = Logger.getLogger(DockerComputerJNLPConnector.class.getCanonicalName());
    private static final TaskListener LOGGER_LISTENER = new LogTaskListener(LOGGER, Level.FINER);

    private String user;
    private final JNLPLauncher jnlpLauncher;
    private String jenkinsUrl;
    private String[] entryPointArguments;

    @DataBoundConstructor
    public DockerComputerJNLPConnector(JNLPLauncher jnlpLauncher) {
        this.jnlpLauncher = jnlpLauncher;
    }


    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    public String getJenkinsUrl(){ return jenkinsUrl; }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl){ this.jenkinsUrl = jenkinsUrl; }

    @CheckForNull
    public String[] getEntryPointArguments(){
        return entryPointArguments;
    }

    @CheckForNull
    public String getEntryPointArgumentsString() {
        if (entryPointArguments == null) return null;
        return Joiner.on("\n").join(entryPointArguments);
    }

    @DataBoundSetter
    public void setEntryPointArgumentsString(String entryPointArgumentsString) {
        setEntryPointArguments(splitAndFilterEmpty(entryPointArgumentsString, "\n"));
    }

    public void setEntryPointArguments(String[] entryPointArguments) {
        if (entryPointArguments == null || entryPointArguments.length == 0) {
            this.entryPointArguments = null;
        } else {
            this.entryPointArguments = entryPointArguments;
        }
    }

    public DockerComputerJNLPConnector withUser(String user) {
        this.user = user;
        return this;
    }

    public DockerComputerJNLPConnector withJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
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
    protected ComputerLauncher createLauncher(final DockerAPI api, final String workdir, final InspectContainerResponse inspect, TaskListener listener) throws IOException, InterruptedException {
        return new JNLPLauncher();
    }

    @Restricted(NoExternalUse.class)
    public enum ArgumentVariables {
        NodeName("NODE_NAME", "The name assigned to this node"), //
        Secret("JNLP_SECRET",
                "The secret that must be passed to slave.jar's -secret argument to pass JNLP authentication."), //
        JenkinsUrl("JENKINS_URL", "The Jenkins root URL."), //
        TunnelArgument("TUNNEL_ARG",
                "If a JNLP tunnel has been specified then this evaluates to '-tunnel', otherwise it evaluates to the empty string"), //
        TunnelValue("TUNNEL", "The JNLP tunnel value");
        private final String name;
        private final String description;

        private ArgumentVariables(String name, String description) {
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

    @Override
    public void afterContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {
    }

    private EnvVars calculateVariablesForVariableSubstitution(final String nodeName, final String secret,
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

    private static void addEnvVars(final EnvVars vars, final Iterable<? extends NodeProperty<?>> nodeProperties)
            throws IOException, InterruptedException {
        if (nodeProperties != null) {
            for (final NodeProperty<?> nodeProperty : nodeProperties) {
                nodeProperty.buildEnvVars(vars, LOGGER_LISTENER);
            }
        }
    }

    private static void addEnvVar(final EnvVars vars, final String name, final Object valueOrNull) {
        vars.put(name, valueOrNull == null ? "" : valueOrNull.toString());
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
