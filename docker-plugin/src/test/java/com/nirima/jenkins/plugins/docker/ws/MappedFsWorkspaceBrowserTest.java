package com.nirima.jenkins.plugins.docker.ws;

import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author lanwen (Merkushev Kirill)
 */
@RunWith(MockitoJUnitRunner.class)
public class MappedFsWorkspaceBrowserTest {

    public static final String FS_MAPPING = "/mapping";
    public static final String JOB_NAME = "job-name";

    @Mock
    private Job job;

    @Mock
    private Run run;

    @Test
    public void shouldReturnWsMappingOnFieldInAction() throws Exception {
        MappedFsWorkspaceBrowser mappedFsWorkspaceBrowser = new MappedFsWorkspaceBrowser();

        when(job.getLastBuild()).thenReturn(run);
        when(job.getName()).thenReturn(JOB_NAME);
        when(run.getAction(DockerBuildAction.class))
                .thenReturn(new DockerBuildAction(
                        randomAlphabetic(10),
                        randomAlphabetic(10),
                        randomAlphabetic(10), FS_MAPPING));

        FilePath workspace = mappedFsWorkspaceBrowser.getWorkspace(job);

        assertThat(workspace.getRemote(),
                both(startsWith(FS_MAPPING))
                        .and(endsWith(JOB_NAME))
                        .and(containsString("workspace")));
    }

    @Test
    public void shouldReturnNullOnNullLastBuild() throws Exception {
        MappedFsWorkspaceBrowser mappedFsWorkspaceBrowser = new MappedFsWorkspaceBrowser();

        assertThat(mappedFsWorkspaceBrowser.getWorkspace(job), nullValue());
    }

    @Test
    public void shouldReturnNullOnNullAction() throws Exception {
        MappedFsWorkspaceBrowser mappedFsWorkspaceBrowser = new MappedFsWorkspaceBrowser();
        when(job.getLastBuild()).thenReturn(run);
        assertThat(mappedFsWorkspaceBrowser.getWorkspace(job), nullValue());
    }

    @Test
    public void shouldReturnNullOnEmptyWsMappingInAction() throws Exception {
        MappedFsWorkspaceBrowser mappedFsWorkspaceBrowser = new MappedFsWorkspaceBrowser();
        when(job.getLastBuild()).thenReturn(run);
        when(run.getAction(DockerBuildAction.class))
                .thenReturn(new DockerBuildAction(
                        randomAlphabetic(10),
                        randomAlphabetic(10),
                        randomAlphabetic(10), ""));

        assertThat(mappedFsWorkspaceBrowser.getWorkspace(job), nullValue());
    }
}
