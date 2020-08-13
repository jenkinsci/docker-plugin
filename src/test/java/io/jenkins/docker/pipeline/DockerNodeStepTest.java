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

import com.google.common.collect.ImmutableSet;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerContainerWatchdog;
import com.nirima.jenkins.plugins.docker.TestableDockerContainerWatchdog;

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
import jenkins.model.Jenkins;
import org.apache.commons.lang3.SystemUtils;
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
import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;

public class DockerNodeStepTest {

    @Before
    public void before() {
        // FIXME on CI windows nodes don't have Docker4Windows
        Assume.assumeFalse(SystemUtils.IS_OS_WINDOWS);
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

    private static String dockerNode(final String dockerHostUri, final String dockerImage,
            final String remoteFsInImage) {
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

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    private void doCleanUp() {
        try {
            // remove all nodes
            final JenkinsRule j = story.j;
            final Jenkins jenkins = j.jenkins;
            final List<Node> nodes = jenkins.getNodes();
            for (final Node node : nodes) {
                jenkins.removeNode(node);
            }
            // now trigger the docker cleanup, telling it it's long overdue.
            final DockerContainerWatchdog cleaner = jenkins.getExtensionList(DockerContainerWatchdog.class).get(0);
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
    public void simpleProvision() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "simpleProvision");
                j.setDefinition(new CpsFlowDefinition(dockerNodeJenkinsAgent() + " {\n" +
                        "  sh 'echo \"hello there\"'\n" +
                        "}\n", true));
                WorkflowRun r = story.j.buildAndAssertSuccess(j);
                story.j.assertLogContains("hello there", r);
            }
        });
    }

    @Test
    public void withinNode() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "withinNode");
                j.setDefinition(new CpsFlowDefinition("node {\n" +
                        "  " + dockerNodeJenkinsAgent() + " {\n" +
                        "    sh 'echo \"hello there\"'\n" +
                        "  }\n" +
                        "}\n", true));
                WorkflowRun r = story.j.buildAndAssertSuccess(j);
                story.j.assertLogContains("hello there", r);
            }
        });
    }

    @Issue("JENKINS-36913")
    @Test
    public void toolInstall() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                // I feel like there's a way to do this without downloading the JSON, but at the moment I can't figure it out.
                DownloadService.Downloadable mvnDl = DownloadService.Downloadable.get("hudson.tasks.Maven.MavenInstaller");
                mvnDl.updateNow();
                DownloadFromUrlInstaller.Installable ins = story.j.get(Maven.MavenInstaller.DescriptorImpl.class).getInstallables().get(0);
                Maven.MavenInstaller installer = new Maven.MavenInstaller(ins.id);

                InstallSourceProperty mvnIsp = new InstallSourceProperty(Collections.singletonList(installer));

                Maven.MavenInstallation mvnInst = new Maven.MavenInstallation("myMaven", null, Collections.singletonList(mvnIsp));
                story.j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mvnInst);
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "toolInstall");
                j.setDefinition(new CpsFlowDefinition(dockerNodeJenkinsAgent() + " {\n" +
                        "  def mvnHome = tool id: 'maven', name: 'myMaven'\n" +
                        "  assert fileExists(mvnHome + '/bin/mvn')\n" +
                        "}\n", true));
                story.j.buildAndAssertSuccess(j);
            }
        });
    }

    @Issue("JENKINS-33510")
    @Test
    public void changeDir() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "changeDir");
                j.setDefinition(new CpsFlowDefinition(dockerNodeJenkinsAgent() + " {\n" +
                        "  echo \"dir is '${pwd()}'\"\n" +
                        "  dir('subdir') {\n" +
                        "    echo \"dir now is '${pwd()}'\"\n" +
                        "  }\n" +
                        "}\n", true));
                WorkflowRun r = story.j.buildAndAssertSuccess(j);
                story.j.assertLogContains("dir is '/home/jenkins/workspace'", r);
                story.j.assertLogContains("dir now is '/home/jenkins/workspace/subdir'", r);
            }
        });
    }

    @Issue("JENKINS-41894")
    @Test
    public void deleteDir() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "deleteDir");
                j.setDefinition(new CpsFlowDefinition(dockerNodeJenkinsAgent() + " {\n" +
                        "  sh 'mkdir -p subdir'\n" +
                        "  assert fileExists('subdir')\n" +
                        "  dir('subdir') {\n" +
                        "    echo \"dir now is '${pwd()}'\"\n" +
                        "    deleteDir()\n" +
                        "  }\n" +
                        "  assert !fileExists('subdir')\n" +
                        "}\n", true));
                WorkflowRun r = story.j.buildAndAssertSuccess(j);
                story.j.assertLogContains("dir now is '/home/jenkins/workspace/subdir'", r);
            }
        });
    }

    @Issue("JENKINS-46831")
    @Test
    public void nodeWithinDockerNodeWithinNode() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                Slave s1 = story.j.createOnlineSlave();
                s1.setLabelString("first-agent");
                s1.setMode(Node.Mode.EXCLUSIVE);
                s1.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                        new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first")));

                Slave s2 = story.j.createOnlineSlave();
                s2.setLabelString("other-agent");
                s2.setMode(Node.Mode.EXCLUSIVE);
                s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                        new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second")));

                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "nodeWithinDockerNode");
                j.setDefinition(new CpsFlowDefinition("node('first-agent') {\n" +
                        "  sh 'echo \"FIRST: WHICH_AGENT=|$WHICH_AGENT|\"'\n" +
                        "  " + dockerNodeJenkinsAgent() + " {\n" +
                        "    sh 'echo \"DOCKER: WHICH_AGENT=|$WHICH_AGENT|\"'\n" +
                        "    node('other-agent') {\n" +
                        "      sh 'echo \"SECOND: WHICH_AGENT=|$WHICH_AGENT|\"'\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n", true));
                WorkflowRun r = story.j.buildAndAssertSuccess(j);
                story.j.assertLogContains("FIRST: WHICH_AGENT=|first|", r);
                story.j.assertLogContains("SECOND: WHICH_AGENT=|second|", r);
                story.j.assertLogContains("DOCKER: WHICH_AGENT=||", r);
            }
        });
    }

    @Test
    public void defaults() {
        story.then(r -> {
            DockerNodeStep s = new DockerNodeStep("foo");
            s.setCredentialsId("");
            s.setDockerHost("");
            s.setRemoteFs("");
            UninstantiatedDescribable uninstantiated = new DescribableModel<>(DockerNodeStep.class).uninstantiate2(s);
            assertEquals(uninstantiated.toString(), Collections.singleton("image"), uninstantiated.getArguments().keySet());
            r.jenkins.clouds.add(new DockerCloud("whatever", new DockerAPI(new DockerServerEndpoint("unix:///var/run/docker.sock", null)), Collections.emptyList()));
            WorkflowJob j = r.createProject(WorkflowJob.class, "p");
            j.setDefinition(new CpsFlowDefinition(
                dockerNodeWithImage("openjdk:8") + " {\n" +
                "  sh 'java -version && whoami && pwd && touch stuff && ls -lat . ..'\n" +
                "}\n", true));
            r.buildAndAssertSuccess(j);
        });
    }

    @Issue("JENKINS-47805")
    @Test
    public void pathModification() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "pathModification");
                j.setDefinition(new CpsFlowDefinition(dockerNodeJenkinsAgent() + " {\n" +
                        "  echo \"Original PATH: ${env.PATH}\"\n" +
                        "  def origPath = env.PATH\n" +
                        "  pathModifier('/some/fake/path') {\n" +
                        "    echo \"Modified PATH: ${env.PATH}\"\n" +
                        "    assert env.PATH == '/some/fake/path:' + origPath" +
                        "  }\n" +
                        "}\n", true));
                story.j.buildAndAssertSuccess(j);
            }
        });
    }

    @Test
    public void dockerBuilderPublisher() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "dockerBuilderPublisher");
                j.setDefinition(new CpsFlowDefinition(dockerNodeJenkinsAgent() + " {\n" +
                        "  writeFile(file: 'Dockerfile', text: 'FROM jenkins/agent')\n" +
                        "  step([$class: 'DockerBuilderPublisher', dockerFileDirectory: ''])\n" +
                        "}\n", true));
                WorkflowRun r = story.j.buildAndAssertSuccess(j);
                story.j.assertLogContains("Successfully built", r);
            }
        });
    }

    @Test
    public void getAcceptableConnectorDescriptors() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runTest();
                } finally {
                    doCleanUp();
                }
            }

            private void runTest() throws Throwable {
                // Given
                final Jenkins jenkins = story.j.getInstance();
                final Descriptor ourDesc = jenkins.getDescriptor(DockerNodeStep.class);
                final Descriptor expectedDesc = jenkins.getDescriptor(DockerComputerAttachConnector.class);
                final DockerNodeStep.DescriptorImpl ourInstance = (DockerNodeStep.DescriptorImpl) ourDesc;
                final DockerComputerAttachConnector.DescriptorImpl attachDescriptor = (DockerComputerAttachConnector.DescriptorImpl) expectedDesc;
                final List<Descriptor<? extends DockerComputerConnector>> expected = new ArrayList<>();
                expected.add(attachDescriptor);

                // When
                final List<Descriptor<? extends DockerComputerConnector>> actual = ourInstance.getAcceptableConnectorDescriptors();

                // Then
                assertEquals(expected, actual);
            }
        });
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
                return ImmutableSet.of(TaskListener.class, FilePath.class);
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
            EnvironmentExpander envEx = EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class),
                    EnvironmentExpander.constant(Collections.singletonMap("PATH+MODIFIER", step.getElement())));

            body = getContext().newBodyInvoker()
                    .withContext(envEx)
                    // Could use a dedicated BodyExecutionCallback here if we wished to print a message at the end ("Returning to ${cwd}"):
                    .withCallback(BodyExecutionCallback.wrap(getContext()))
                    .start();
            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            if (body!=null)
                body.cancel(cause);
        }
    }
}
