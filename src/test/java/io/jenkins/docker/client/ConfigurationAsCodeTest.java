package io.jenkins.docker.client;

import static io.jenkins.plugins.casc.misc.Util.getJenkinsRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import io.jenkins.docker.connector.DockerComputerAttachConnector;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;

public class ConfigurationAsCodeTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code_current.yml")
    public void should_support_current_configuration() throws Exception {
        DockerCloud cloud = (DockerCloud) r.jenkins.clouds.get(0);
        assertThat(cloud.getDisplayName(), is("docker"));
        assertThat(cloud.getDockerApi().getDockerHost().getUri(), is("unix:///var/run/docker.sock"));
        assertThat(cloud.getTemplates(), hasSize(1));

        DockerTemplate template = cloud.getTemplates().get(0);
        assertThat(template.getLabelString(), is("docker-agent"));
        assertThat(template.getImage(), is("jenkins/agent"));
        assertThat(
                template.getMounts(),
                arrayContaining(
                        "type=tmpfs,destination=/run",
                        "type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock",
                        "type=volume,src=hello,dst=/world"));
        assertThat(template.getEnvironmentsString(), is("hello=world\nfoo=bar"));
        assertThat(template.getRemoteFs(), is("/home/jenkins/agent"));
        assertThat(template.getConnector(), instanceOf(DockerComputerAttachConnector.class));
        assertThat(((DockerComputerAttachConnector) template.getConnector()).getUser(), is("jenkins"));
        assertThat(template.getInstanceCapStr(), is("10"));
        assertThat(template.getRetentionStrategy(), instanceOf(DockerOnceRetentionStrategy.class));
        assertThat(((DockerOnceRetentionStrategy) template.getRetentionStrategy()).getIdleMinutes(), is(1));

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(
                getJenkinsRoot(context).get("clouds").asSequence().get(0).asMapping());
        String expected = toStringFromYamlFile(this, "expected_output_current.yml");

        assertThat(exported, is(expected));
    }

    @Test
    @ConfiguredWithCode("configuration-as-code_old.yml")
    public void should_support_old_configuration() throws Exception {
        DockerCloud cloud = (DockerCloud) r.jenkins.clouds.get(0);
        assertThat(cloud.getDisplayName(), is("docker"));
        assertThat(cloud.getDockerApi().getDockerHost().getUri(), is("unix:///var/run/docker.sock"));
        assertThat(cloud.getTemplates(), hasSize(1));

        DockerTemplate template = cloud.getTemplates().get(0);
        assertThat(template.getLabelString(), is("docker-agent"));
        assertThat(template.getImage(), is("jenkins/agent"));
        assertThat(
                template.getMounts(),
                arrayContaining(
                        "type=volume,source=hello,destination=/hello", "type=volume,source=world,destination=/world"));
        assertThat(template.getEnvironmentsString(), is("hello=world\nfoo=bar"));
        assertThat(template.getRemoteFs(), is("/home/jenkins/agent"));
        assertThat(template.getConnector(), instanceOf(DockerComputerAttachConnector.class));
        assertThat(((DockerComputerAttachConnector) template.getConnector()).getUser(), is("jenkins"));
        assertThat(template.getInstanceCapStr(), is("10"));
        assertThat(template.getRetentionStrategy(), instanceOf(DockerOnceRetentionStrategy.class));
        assertThat(((DockerOnceRetentionStrategy) template.getRetentionStrategy()).getIdleMinutes(), is(1));

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        String exported = toYamlString(
                getJenkinsRoot(context).get("clouds").asSequence().get(0).asMapping());
        String expected = toStringFromYamlFile(this, "expected_output_old.yml");

        assertThat(exported, is(expected));
    }
}
