package com.nirima.jenkins.plugins.docker.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.dockerjava.core.DockerClientConfig;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ItemGroup;
import org.acegisecurity.Authentication;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WithoutJenkins;

import java.util.Collections;
import java.util.List;

import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static com.nirima.jenkins.plugins.docker.client.ClientBuilderForPlugin.dockerClient;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class ClientBuilderForPluginTest {

    public static final String HTTP_SERVER_URL = "http://server.url/";
    public static final String DOCKER_API_VER = "test-ver12";
    public static final String CLOUD_NAME = "cloud-name";
    public static final int READ_TIMEOUT = 10;
    public static final String EMPTY_CREDS = "";
    public static final int CONNECT_TIMEOUT = 0;
    public static final String EMPTY_CONTAINER_CAP = "";

    public static final String ID_OF_CREDS = "idcreds";
    public static final String USERNAME = "usrname";
    public static final String PASSWORD = "pwd021";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    @WithoutJenkins
    public void shouldGetUriVersionReadTimeoutSettingsFromCloud() throws Exception {
        DockerCloud cloud = new DockerCloud(CLOUD_NAME,
                Collections.<DockerTemplate>emptyList(),
                HTTP_SERVER_URL,
                EMPTY_CONTAINER_CAP,
                CONNECT_TIMEOUT, READ_TIMEOUT, EMPTY_CREDS, DOCKER_API_VER);
        ClientBuilderForPlugin builder = dockerClient();
        builder.forCloud(cloud);

        DockerClientConfig config = builder.config().build();
        assertThat("server", config.getUri().toString(), equalTo(HTTP_SERVER_URL));
        assertThat("version", config.getVersion(), equalTo(DOCKER_API_VER));
        assertThat("read TO", config.getReadTimeout(), equalTo((int) SECONDS.toMillis(READ_TIMEOUT)));
    }

    @Test
    public void shouldFindPasswordCredsFromJenkins() throws Exception {
        ClientBuilderForPlugin builder = dockerClient();
        builder.forServer(HTTP_SERVER_URL, DOCKER_API_VER).withCredentials(ID_OF_CREDS);

        DockerClientConfig config = builder.config().build();
        assertThat("login", config.getUsername(), equalTo(USERNAME));
        assertThat("pwd", config.getPassword(), equalTo(PASSWORD));
    }


    @TestExtension
    public static class TestCreds extends CredentialsProvider {
        @NonNull
        @Override
        public <C extends Credentials> List<C> getCredentials(Class<C> type,
                                                              ItemGroup itemGroup,
                                                              Authentication authentication) {
            return asList((C) new UsernamePasswordCredentialsImpl(GLOBAL, ID_OF_CREDS, null, USERNAME, PASSWORD));
        }
    }
}
