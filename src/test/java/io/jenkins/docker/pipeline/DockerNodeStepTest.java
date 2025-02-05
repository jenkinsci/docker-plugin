/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.docker.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerContainerWatchdog;
import com.nirima.jenkins.plugins.docker.TestableDockerContainerWatchdog;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.model.DownloadService;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Maven;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.InstallSourceProperty;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerComputerAttachConnector;
import io.jenkins.docker.connector.DockerComputerConnector;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.testcontainers.DockerClientFactory;

@WithJenkins
class DockerNodeStepTest {

    @BeforeAll
    static void before() {
        // FIXME on CI windows nodes don't have Docker4Windows
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
    }

    private static String dockerNodeJenkinsAgent() {
        final String dockerHostUri = SystemUtils.IS_OS_WINDOWS ? "tcp://localhost:2375" : "unix:///var/run/docker.sock";
        final String dockerImage = "jenkins/agent";
        final String remoteFsInImage = "/home/jenkins";
        return dockerNode(dockerHostUri, dockerImage, remoteFsInImage);
    }

    private static String dockerNodeWithImage(final String dockerImage) {
        final String dockerHostUri = null;
        final String remoteFsInImage = null;
        return dockerNode(dockerHostUri, dockerImage, remoteFsInImage);
    }

    private static String dockerNode(
            final String dockerHostUri, final String dockerImage, final String remoteFsInImage) {
        final StringBuilder s = new StringBuilder();
        s.append("dockerNode(");
        s.append("connector: attach(jvmArgsString: '-Xmx250m')");
        if (dockerHostUri != null && !dockerHostUri.isEmpty()) {
            s.append(", ");
            s.append("dockerHost: '").append(dockerHostUri).append("'");
        }
        if (dockerImage != null && !dockerImage.isEmpty()) {
            s.append(", ");
            s.append("image: '" + dockerImage + "'");
        }
        if (remoteFsInImage != null && !remoteFsInImage.isEmpty()) {
            s.append(", ");
            s.append("remoteFs: '" + remoteFsInImage + "'");
        }
        s.append(")");
        return s.toString();
    }

    private void doCleanUp(JenkinsRule rule) {
        try {
            // remove all nodes
            final Jenkins jenkins = rule.jenkins;
            final List<Node> nodes = jenkins.getNodes();
            for (final Node node : nodes) {
                jenkins.removeNode(node);
            }
            // now trigger the docker cleanup, telling it it's long overdue.
            final DockerContainerWatchdog cleaner =
                    jenkins.getExtensionList(DockerContainerWatchdog.class).get(0);
            final String cleanerThreadName = cleaner.name;
            final Clock now = Clock.systemUTC();
            final Clock future = Clock.offset(now, Duration.ofMinutes(60));
            waitUntilNoThreadRunning(cleanerThreadName);
            TestableDockerContainerWatchdog.setClockOn(cleaner, future);
            cleaner.doRun();
            waitUntilNoThreadRunning(cleanerThreadName);
            TestableDockerContainerWatchdog.setClockOn(cleaner, now);
        } catch (Throwable loggedAndSuppressed) {
            loggedAndSuppressed.printStackTrace();
        }
    }

    private static void waitUntilNoThreadRunning(String name) throws InterruptedException {
        while (isThreadRunning(name)) {
            Thread.sleep(1000L);
        }
    }

    private static boolean isThreadRunning(String name) {
        final Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        for (final Thread t : all.keySet()) {
            final String tName = t.getName();
            if (tName.contains(name) && t.isAlive()) {
                return true;
            }
        }
        return false;
    }

    @Test
    void simpleProvision(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        try {
            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "simpleProvision");
            j.setDefinition(new CpsFlowDefinition(
                    dockerNodeJenkinsAgent() + " {\n" + "  sh 'echo \"hello there\"'\n" + "}\n", true));
            WorkflowRun r = rule.buildAndAssertSuccess(j);
            rule.assertLogContains("hello there", r);
        } finally {
            doCleanUp(rule);
        }
    }

    @Test
    void withinNode(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        try {
            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "withinNode");
            j.setDefinition(new CpsFlowDefinition(
                    "node {\n" + "  "
                            + dockerNodeJenkinsAgent() + " {\n" + "    sh 'echo \"hello there\"'\n"
                            + "  }\n"
                            + "}\n",
                    true));
            WorkflowRun r = rule.buildAndAssertSuccess(j);
            rule.assertLogContains("hello there", r);
        } finally {
            doCleanUp(rule);
        }
    }

    @Issue("JENKINS-36913")
    @Test
    void toolInstall(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        try {
            // I feel like there's a way to do this without downloading the JSON, but at the moment I can't figure
            // it out.
            DownloadService.Downloadable mvnDl = DownloadService.Downloadable.get("hudson.tasks.Maven.MavenInstaller");
            mvnDl.updateNow();
            DownloadFromUrlInstaller.Installable ins = rule.get(Maven.MavenInstaller.DescriptorImpl.class)
                    .getInstallables()
                    .get(0);
            Maven.MavenInstaller installer = new Maven.MavenInstaller(ins.id);

            InstallSourceProperty mvnIsp = new InstallSourceProperty(List.of(installer));

            Maven.MavenInstallation mvnInst = new Maven.MavenInstallation("myMaven", null, List.of(mvnIsp));
            rule.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mvnInst);
            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "toolInstall");
            j.setDefinition(new CpsFlowDefinition(
                    dockerNodeJenkinsAgent() + " {\n" + "  def mvnHome = tool name: 'myMaven'\n"
                            + "  assert fileExists(mvnHome + '/bin/mvn')\n"
                            + "}\n",
                    true));
            rule.buildAndAssertSuccess(j);
        } finally {
            doCleanUp(rule);
        }
    }

    @Issue("JENKINS-33510")
    @Test
    void changeDir(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        try {
            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "changeDir");
            j.setDefinition(new CpsFlowDefinition(
                    dockerNodeJenkinsAgent() + " {\n" + "  echo \"dir is '${pwd()}'\"\n"
                            + "  dir('subdir') {\n"
                            + "    echo \"dir now is '${pwd()}'\"\n"
                            + "  }\n"
                            + "}\n",
                    true));
            WorkflowRun r = rule.buildAndAssertSuccess(j);
            rule.assertLogContains("dir is '/home/jenkins/workspace'", r);
            rule.assertLogContains("dir now is '/home/jenkins/workspace/subdir'", r);
        } finally {
            doCleanUp(rule);
        }
    }

    @Issue("JENKINS-41894")
    @Test
    void deleteDir(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        try {
            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "deleteDir");
            j.setDefinition(new CpsFlowDefinition(
                    dockerNodeJenkinsAgent() + " {\n" + "  sh 'mkdir -p subdir'\n"
                            + "  assert fileExists('subdir')\n"
                            + "  dir('subdir') {\n"
                            + "    echo \"dir now is '${pwd()}'\"\n"
                            + "    deleteDir()\n"
                            + "  }\n"
                            + "  assert !fileExists('subdir')\n"
                            + "}\n",
                    true));
            WorkflowRun r = rule.buildAndAssertSuccess(j);
            rule.assertLogContains("dir now is '/home/jenkins/workspace/subdir'", r);
        } finally {
            doCleanUp(rule);
        }
    }

    @Issue("JENKINS-46831")
    @Test
    void nodeWithinDockerNodeWithinNode(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        try {
            Slave s1 = rule.createOnlineSlave();
            s1.setLabelString("first-agent");
            s1.setMode(Node.Mode.EXCLUSIVE);
            s1.getNodeProperties()
                    .add(new EnvironmentVariablesNodeProperty(
                            new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                            new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first")));

            Slave s2 = rule.createOnlineSlave();
            s2.setLabelString("other-agent");
            s2.setMode(Node.Mode.EXCLUSIVE);
            s2.getNodeProperties()
                    .add(new EnvironmentVariablesNodeProperty(
                            new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                            new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second")));

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "nodeWithinDockerNode");
            j.setDefinition(new CpsFlowDefinition(
                    "node('first-agent') {\n" + "  sh 'echo \"FIRST: WHICH_AGENT=|$WHICH_AGENT|\"'\n"
                            + "  "
                            + dockerNodeJenkinsAgent() + " {\n"
                            + "    sh 'echo \"DOCKER: WHICH_AGENT=|$WHICH_AGENT|\"'\n"
                            + "    node('other-agent') {\n"
                            + "      sh 'echo \"SECOND: WHICH_AGENT=|$WHICH_AGENT|\"'\n"
                            + "    }\n"
                            + "  }\n"
                            + "}\n",
                    true));
            WorkflowRun r = rule.buildAndAssertSuccess(j);
            rule.assertLogContains("FIRST: WHICH_AGENT=|first|", r);
            rule.assertLogContains("SECOND: WHICH_AGENT=|second|", r);
            rule.assertLogContains("DOCKER: WHICH_AGENT=||", r);
        } finally {
            doCleanUp(rule);
        }
    }

    @Test
    void defaults(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        DockerNodeStep s = new DockerNodeStep("foo");
        s.setCredentialsId("");
        s.setDockerHost("");
        s.setRemoteFs("");
        UninstantiatedDescribable uninstantiated = new DescribableModel<>(DockerNodeStep.class).uninstantiate2(s);
        assertEquals(Set.of("image"), uninstantiated.getArguments().keySet(), uninstantiated.toString());
        rule.jenkins.clouds.add(new DockerCloud(
                "whatever",
                new DockerAPI(new DockerServerEndpoint("unix:///var/run/docker.sock", null)),
                Collections.emptyList()));
        WorkflowJob j = rule.createProject(WorkflowJob.class, "p");
        j.setDefinition(new CpsFlowDefinition(
                dockerNodeWithImage("eclipse-temurin:17-jre") + " {\n"
                        + "  sh 'java -version && whoami && pwd && touch stuff && ls -lat . ..'\n"
                        + "}\n",
                true));
        rule.buildAndAssertSuccess(j);
    }

    @Issue("JENKINS-47805")
    @Test
    void pathModification(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        try {
            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "pathModification");
            j.setDefinition(new CpsFlowDefinition(
                    dockerNodeJenkinsAgent() + " {\n" + "  echo \"Original PATH: ${env.PATH}\"\n"
                            + "  def origPath = env.PATH\n"
                            + "  pathModifier('/some/fake/path') {\n"
                            + "    echo \"Modified PATH: ${env.PATH}\"\n"
                            + "    assert env.PATH == '/some/fake/path:' + origPath"
                            + "  }\n"
                            + "}\n",
                    true));
            rule.buildAndAssertSuccess(j);
        } finally {
            doCleanUp(rule);
        }
    }

    @Test
    void dockerBuilderPublisher(JenkinsRule rule) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        try {
            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "dockerBuilderPublisher");
            j.setDefinition(new CpsFlowDefinition(
                    dockerNodeJenkinsAgent() + " {\n"
                            + "  writeFile(file: 'Dockerfile', text: 'FROM jenkins/agent')\n"
                            + "  step([$class: 'DockerBuilderPublisher', dockerFileDirectory: ''])\n"
                            + "}\n",
                    true));
            WorkflowRun r = rule.buildAndAssertSuccess(j);
            rule.assertLogContains("Successfully built", r);
        } finally {
            doCleanUp(rule);
        }
    }

    @Test
    void getAcceptableConnectorDescriptors(JenkinsRule rule) throws Exception {
        try {
            // Given
            final Jenkins jenkins = rule.getInstance();
            final Descriptor ourDesc = jenkins.getDescriptor(DockerNodeStep.class);
            final Descriptor expectedDesc = jenkins.getDescriptor(DockerComputerAttachConnector.class);
            final DockerNodeStep.DescriptorImpl ourInstance = (DockerNodeStep.DescriptorImpl) ourDesc;
            final DockerComputerAttachConnector.DescriptorImpl attachDescriptor =
                    (DockerComputerAttachConnector.DescriptorImpl) expectedDesc;
            final List<Descriptor<? extends DockerComputerConnector>> expected = new ArrayList<>();
            expected.add(attachDescriptor);

            // When
            final List<Descriptor<? extends DockerComputerConnector>> actual =
                    ourInstance.getAcceptableConnectorDescriptors();

            // Then
            assertEquals(expected, actual);
        } finally {
            doCleanUp(rule);
        }
    }

    public static class PathModifierStep extends Step {
        private String element;

        @DataBoundConstructor
        public PathModifierStep(String element) {
            this.element = element;
        }

        public String getElement() {
            return element;
        }

        @Override
        public StepExecution start(StepContext context) throws Exception {
            return new PathModifierStepExecution(context, this);
        }

        @TestExtension("pathModification")
        public static class PathModifierStepDescriptor extends StepDescriptor {
            @Override
            public String getFunctionName() {
                return "pathModifier";
            }

            @Override
            public String getDisplayName() {
                return "Add element to PATH";
            }

            @Override
            public boolean takesImplicitBlockArgument() {
                return true;
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of(TaskListener.class, FilePath.class);
            }
        }
    }

    public static class PathModifierStepExecution extends StepExecution {
        private transient PathModifierStep step;
        private transient BodyExecution body;

        public PathModifierStepExecution(StepContext context, PathModifierStep step) throws Exception {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() throws Exception {
            EnvironmentExpander envEx = EnvironmentExpander.merge(
                    getContext().get(EnvironmentExpander.class),
                    EnvironmentExpander.constant(Map.of("PATH+MODIFIER", step.getElement())));

            body = getContext()
                    .newBodyInvoker()
                    .withContext(envEx)
                    // Could use a dedicated BodyExecutionCallback here if we wished to print a message at the end
                    // ("Returning to ${cwd}"):
                    .withCallback(BodyExecutionCallback.wrap(getContext()))
                    .start();
            return false;
        }

        @Override
        public void stop(@NonNull Throwable cause) throws Exception {
            if (body != null) {
                body.cancel(cause);
            }
        }
    }
}
