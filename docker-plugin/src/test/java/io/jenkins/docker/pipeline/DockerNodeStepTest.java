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

import hudson.model.DownloadService;
import hudson.tasks.Maven;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.InstallSourceProperty;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.Collections;

public class DockerNodeStepTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void simpleProvision() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "simpleProvision");
                j.setDefinition(new CpsFlowDefinition("dockerNode(dockerHost: 'unix:///var/run/docker.sock', image: 'jenkins/slave', remoteFs: '/home/jenkins') {\n" +
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
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "withinNode");
                j.setDefinition(new CpsFlowDefinition("node {\n" +
                        "  dockerNode(dockerHost: 'unix:///var/run/docker.sock', image: 'jenkins/slave', remoteFs: '/home/jenkins') {\n" +
                        "    sh 'echo \"hello there\"'\n" +
                        "  }\n" +
                        "}\n", true));
                WorkflowRun r = story.j.buildAndAssertSuccess(j);
                story.j.assertLogContains("hello there", r);
            }
        });
    }

    @Test
    public void toolInstall() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // I feel like there's a way to do this without downloading the JSON, but at the moment I can't figure it out.
                DownloadService.Downloadable mvnDl = DownloadService.Downloadable.get("hudson.tasks.Maven.MavenInstaller");
                mvnDl.updateNow();
                DownloadFromUrlInstaller.Installable ins = story.j.get(Maven.MavenInstaller.DescriptorImpl.class).getInstallables().get(0);
                Maven.MavenInstaller installer = new Maven.MavenInstaller(ins.id);

                InstallSourceProperty mvnIsp = new InstallSourceProperty(Collections.singletonList(installer));

                Maven.MavenInstallation mvnInst = new Maven.MavenInstallation("myMaven", null, Collections.singletonList(mvnIsp));
                story.j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mvnInst);
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "toolInstall");
                j.setDefinition(new CpsFlowDefinition("dockerNode(dockerHost: 'unix:///var/run/docker.sock', image: 'jenkins/slave', remoteFs: '/home/jenkins') {\n" +
                        "  def mvnHome = tool id: 'maven', name: 'myMaven'\n" +
                        "  assert fileExists(mvnHome + '/bin/mvn')\n" +
                        "}\n", true));
                WorkflowRun r = story.j.buildAndAssertSuccess(j);
            }
        });
    }

    @Test
    public void changeDir() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob j = story.j.jenkins.createProject(WorkflowJob.class, "changeDir");
                j.setDefinition(new CpsFlowDefinition("dockerNode(dockerHost: 'unix:///var/run/docker.sock', image: 'jenkins/slave', remoteFs: '/home/jenkins') {\n" +
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
}
