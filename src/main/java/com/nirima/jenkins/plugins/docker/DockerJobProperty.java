package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Identifier;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.TaskListener;
import io.jenkins.docker.DockerTransientNode;
import jenkins.model.Jenkins;
import jenkins.model.OptionalJobProperty;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.export.Exported;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.nirima.jenkins.plugins.docker.utils.LogUtils.printResponseItemToListener;


public class DockerJobProperty extends OptionalJobProperty<AbstractProject<?, ?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerJobProperty.class.getName());


    public final String additionalTag;

    private boolean pushOnSuccess;

    private DockerRegistryEndpoint registry;

    public final boolean cleanImages;

    @Deprecated
    private DockerJobTemplateProperty dockerJobTemplate;

    @DataBoundConstructor
    public DockerJobProperty(
            String additionalTag,
            boolean cleanImages) {
        this.additionalTag = additionalTag;
        this.cleanImages = cleanImages;
    }

    @Exported
    public String getAdditionalTag() {
        return additionalTag;
    }

    @Exported
    public boolean isPushOnSuccess() {
        return pushOnSuccess;
    }

    @Exported
    public boolean isCleanImages() {
        return cleanImages;
    }
    
    public DockerRegistryEndpoint getRegistry() {
        return registry;
    }

    @DataBoundSetter
    public void setRegistry(DockerRegistryEndpoint registry) {
        this.pushOnSuccess = true;
        this.registry = registry;
    }

    @Exported
    public DockerJobTemplateProperty getDockerJobTemplate() {
        return dockerJobTemplate;
    }


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final Node node = build.getBuiltOn();
        if (!(node instanceof DockerTransientNode)) {
            return true;
        }
        DockerTransientNode dockerNode = (DockerTransientNode) node;

        final String containerId = dockerNode.getContainerId();
        final DockerClient client = dockerNode.getDockerAPI().getClient();
        final String dockerHost = dockerNode.getDockerAPI().getDockerHost().getUri();

        // Commit
        String tag_image = client.commitCmd(containerId)
                .withRepository(build.getParent().getDisplayName())
                .withTag(build.getDisplayName().replace("#", "b")) // allowed only ([a-zA-Z_][a-zA-Z0-9_]*)
                .withAuthor("Jenkins")
                .withMessage(build.getFullDisplayName())
                .exec();

        // Tag it with the jenkins name
        addJenkinsAction(build, dockerHost, containerId, tag_image);

        // Should we add additional tags?
        try {
            String tagToken = getAdditionalTag(build, listener);

            if (!Strings.isNullOrEmpty(tagToken)) {


                final NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(tagToken);
                final String commitTag = StringUtils.isEmpty(reposTag.tag) ? "latest" : reposTag.tag;

                client.tagImageCmd(tag_image, reposTag.repos, commitTag).withForce().exec();

                addJenkinsAction(build, dockerHost, containerId, tagToken);

                if (pushOnSuccess) {
                    Identifier identifier = Identifier.fromCompoundString(tagToken);

                    PushImageResultCallback resultCallback = new PushImageResultCallback() {
                        public void onNext(PushResponseItem item) {
                            printResponseItemToListener(listener, item);
                            super.onNext(item);
                        }
                    };
                    try {

                        PushImageCmd cmd = client.pushImageCmd(identifier);
                        DockerCloud.setRegistryAuthentication(cmd, registry, Jenkins.getInstance());
                        cmd.exec(resultCallback).awaitSuccess();

                    } catch(DockerException ex) {

                        LOGGER.error("Exception pushing docker image. Check that the destination registry is buildning.", ex);
                        throw ex;
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Could not add additional tags", ex);
        }

        if (cleanImages) {

            client.removeImageCmd(tag_image)
                    .withForce(true)
                    .exec();
        }

        return true;
    }

    private String getAdditionalTag(AbstractBuild build, TaskListener listener) {
        // Do a macro expansion on the addJenkinsAction token

        // Job property
        String tagToken = additionalTag;

        // Do any macro expansions
        try {
            if (!Strings.isNullOrEmpty(tagToken))
                tagToken = TokenMacro.expandAll(build, listener, tagToken);
        } catch (Exception e) {
            LOGGER.warn("can't expand macro", e);
        }
        return tagToken;
    }

    private void addJenkinsAction(AbstractBuild build, String dockerHost, String containerId, String tag_image) throws IOException {
        build.addAction(new DockerBuildAction(dockerHost, containerId, tag_image));
        build.save();
    }

    @Extension
    public static final class DescriptorImpl extends OptionalJobPropertyDescriptor {
        public String getDisplayName() {
            return "Commit agent's Docker container";
        }
    }

    private Object readResolve() {
        if (pushOnSuccess && registry == null) {
            registry = new DockerRegistryEndpoint(null, null);
        }
        return this;
    }
}
