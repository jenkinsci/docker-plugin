package com.nirima.jenkins.plugins.docker;

import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Node;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Kanstantsin Shautsou
 */
@SuppressWarnings("deprecation")
public class DockerTemplate2Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    @Issue("jenkinsci/docker-plugin/issues/263")
    public void load080Config() {
        final DockerCloud dock = (DockerCloud) j.getInstance().getCloud("someCloud");
        assertThat(dock.getTemplates(), hasSize(6));

        final DockerTemplate template = dock.getTemplate("somedomain.com:5000/dockerbuild");
        assertThat(template, notNullValue());

        assertThat(template.getLabelString(), equalTo("dockerbuild-local"));
        assertThat(template.remoteFs, equalTo("/home/jenkins"));

        assertNotNull("Must appear default value", template.getPullStrategy());
        assertThat("Default after migration must be PULL_LATEST",
                template.getPullStrategy(), equalTo(DockerImagePullStrategy.PULL_LATEST));

        assertNotNull("Must appear default value", template.getMode());
        assertThat("Changeable mode appeared in some version", template.getMode(), equalTo(Node.Mode.NORMAL));

        assertNotNull("Must appear default value", template.getRetentionStrategy());
        assertTrue(template.getRetentionStrategy() instanceof DockerOnceRetentionStrategy);

        final DockerTemplateBase tBase = template.getDockerTemplateBase();
        assertThat(tBase, notNullValue());

        assertThat(tBase.getDockerCommandArray()[0], equalTo("/usr/bin/start-jenkins-slave.sh"));
        assertThat(tBase.getDnsString(), equalTo("8.8.8.8"));

        assertThat(tBase.getVolumesString(), equalTo("/dev/log:/dev/log"));
        assertThat(asList(tBase.getVolumes()), hasSize(1));
        assertThat(tBase.getVolumes()[0], equalTo("/dev/log:/dev/log"));

        assertFalse(tBase.bindAllPorts);
        assertTrue(tBase.privileged);
    }

    @Test
    @LocalData
    public void shouldLoad090rc1Config() {
        final DockerCloud dock = (DockerCloud) j.getInstance().getCloud("dock");
        final DockerTemplate template = dock.getTemplate("image:b25");
        final DockerTemplateBase dockerTemplateBase = template.getDockerTemplateBase();
        
        final String[] volumes = dockerTemplateBase.getVolumes();
        assertThat("volumes", asList(volumes), hasSize(2));
        assertThat(volumes[0], equalTo("/host/path:/container/path:ro"));
        assertThat(volumes[1], equalTo("/host/path2:/container/path2:ro"));

        final String[] volumesFrom2 = dockerTemplateBase.getVolumesFrom2();
        assertThat("volumesFrom2", asList(volumesFrom2), hasSize(1));
        assertThat(volumesFrom2[0], equalTo("otherContainer:ro"));
        assertThat("volumesFrom", dockerTemplateBase.getVolumesFrom(), nullValue());

        assertThat(dockerTemplateBase.getImage(), equalTo("image:b25"));
    }

    @Test
    @LocalData
    public void shouldLoadEmptyVolumesFrom() {
        final DockerCloud dock = (DockerCloud) j.getInstance().getCloud("dock");
        final DockerTemplate template = dock.getTemplate("jenkins-ubuntu-slave");
        final String[] volumes = template.getDockerTemplateBase().getVolumes();
        final String[] volumesFrom2 = template.getDockerTemplateBase().getVolumesFrom2();

        assertThat("volumes", asList(volumes), hasSize(0));
        assertThat("volumesFrom2", asList(volumesFrom2), hasSize(0));
        assertThat("volumesFrom", template.getDockerTemplateBase().getVolumesFrom(), nullValue());
    }
}
