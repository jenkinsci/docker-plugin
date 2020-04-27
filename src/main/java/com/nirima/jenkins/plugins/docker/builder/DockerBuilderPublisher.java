package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.AuthConfigurations;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Identifier;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.github.dockerjava.core.dockerfile.Dockerfile;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.action.DockerBuildImageAction;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.VirtualChannel;
import hudson.remoting.Channel;
import hudson.slaves.Cloud;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.DockerTransientNode;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.Validate;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.fixEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.splitAndTrimFilterEmptyList;
import static com.nirima.jenkins.plugins.docker.utils.LogUtils.printResponseItemToListener;
import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Builder extension to build / publish an image from a Dockerfile.
 * TODO automatic migration to https://wiki.jenkins.io/display/JENKINS/CloudBees+Docker+Build+and+Publish+plugin
 */
public class DockerBuilderPublisher extends Builder implements SimpleBuildStep {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerBuilderPublisher.class);

    /**
     * The docker spec says "<i>A tag name may contain lowercase and uppercase
     * characters, digits, underscores, periods and dashes. A tag name may not
     * start with a period or a dash and may contain a maximum of 128
     * characters</i>". This is a simplified version of that specification.
     */
    private static final String TAG_REGEX = "[a-zA-Z0-9-_.]+";
    /**
     * The docker spec says "<i>Name components may contain lowercase
     * characters, digits and separators. A separator is defined as a period,
     * one or two underscores, or one or more dashes. A name component may not
     * start or end with a separator</i>". This is a simplified version of that
     * specification.
     */
    private static final String NAME_COMPONENT_REGEX = "[a-z0-9-_.]+";
    /**
     * The docker spec says "<i>The (registry) hostname must comply with
     * standard DNS rules, but may not contain underscores. If a hostname is
     * present, it may optionally be followed by a port number in the format
     * :8080</i>". This is a simplified version of that specification.
     */
    private static final String REGISTRY_HOSTNAME_REGEX = "[a-zA-Z0-9-.]+(:[0-9]+)?";
    /**
     * The docker spec says "<i>An image name is made up of slash-separated name
     * components, optionally prefixed by a registry hostname</i>".
     */
    private static final String IMAGE_NAME_REGEX = "(" + REGISTRY_HOSTNAME_REGEX + "/)?" + NAME_COMPONENT_REGEX + "(/"
            + NAME_COMPONENT_REGEX + ")*";
    /**
     * A regex matching IMAGE[:TAG] (from the "docker tag" command) where IMAGE
     * matches {@link #IMAGE_NAME_REGEX} and TAG matches {@link #TAG_REGEX}.
     */
    private static final String VALID_REPO_REGEX = "^" + IMAGE_NAME_REGEX + "(:" + TAG_REGEX + ")?$";
    /** Compiled version of {@link #VALID_REPO_REGEX}. */
    private static final Pattern VALID_REPO_PATTERN = Pattern.compile(VALID_REPO_REGEX);

    public final String dockerFileDirectory;

    /** @deprecated use {@link #fromRegistry} instead */
    @Deprecated
    private transient String pullCredentialsId;
    @CheckForNull
    private DockerRegistryEndpoint fromRegistry;

    /**
     * @deprecated use {@link #setTags(List)} and/or {@link #getTags()}
     */
    @Deprecated
    public String tag;

    @CheckForNull
    private List<String> tags;

    public final boolean pushOnSuccess;

    @CheckForNull
    private String pushCredentialsId;
    /** @deprecated use {@link #pushCredentialsId} instead */
    @Deprecated
    private transient DockerRegistryEndpoint registry;

    public final boolean cleanImages;
    public final boolean cleanupWithJenkinsJobDelete;

    @CheckForNull
    public final String cloud;
    public /* almost final */ boolean noCache;
    public /* almost final */ boolean pull;

    @DataBoundConstructor
    public DockerBuilderPublisher(String dockerFileDirectory,
                                  @Nullable DockerRegistryEndpoint fromRegistry,
                                  @Nullable String cloud,
                                  @Nullable String tagsString,
                                  boolean pushOnSuccess,
                                  @Nullable String pushCredentialsId,
                                  boolean cleanImages,
                                  boolean cleanupWithJenkinsJobDelete) {
        this.dockerFileDirectory = dockerFileDirectory;
        this.fromRegistry = fromRegistry;
        setTagsString(tagsString);
        this.tag = null;
        this.cloud = cloud;
        this.pushOnSuccess = pushOnSuccess;
        this.pushCredentialsId = pushCredentialsId;
        this.cleanImages = cleanImages;
        this.cleanupWithJenkinsJobDelete = cleanupWithJenkinsJobDelete;
    }

    @SuppressWarnings("unused")
    @Deprecated
    public DockerRegistryEndpoint getRegistry(Identifier identifier) {
        if (registry == null) {
            registry = new DockerRegistryEndpoint(null, pushCredentialsId);
        }
        return registry;
    }

    /** @deprecated See {@link #getFromRegistry()} */
    @Deprecated
    @CheckForNull
    public String getPullCredentialsId() {
        return pullCredentialsId;
    }

    @CheckForNull
    public String getPushCredentialsId() {
        return pushCredentialsId;
    }

    @CheckForNull
    public List<String> getTags() {
        return fixEmpty(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = fixEmpty(tags);
    }

    @Nonnull
    public String getTagsString() {
        final List<String> tagsOrNull = getTags();
        return tagsOrNull == null ? "" : Joiner.on("\n").join(tagsOrNull);
    }

    public void setTagsString(String tagsString) {
        setTags(splitAndTrimFilterEmptyList(tagsString, "\n"));
    }

    @CheckForNull
    public DockerRegistryEndpoint getFromRegistry() {
        return fromRegistry;
    }

    public boolean isNoCache() {
        return noCache;
    }

    @DataBoundSetter
    public void setNoCache(boolean noCache) {
        this.noCache = noCache;
    }

    public boolean isPull() {
        return pull;
    }

    @DataBoundSetter
    public void setPull(boolean pull) {
        this.pull = pull;
    }

    // package access for test purposes
    @Restricted(NoExternalUse.class)
    static void verifyTags(String tagsString) {
        final List<String> verifyTags = splitAndTrimFilterEmptyList(tagsString, "\n");
        for (String verifyTag : verifyTags) {
            // Our strings are subjected to variable substitution before they are used, so ${foo} might be valid.
            // So we do some fake substitution to help prevent incorrect complaints.
            final String expandedTag = verifyTag.replaceAll("\\$\\{[^}]*NUMBER\\}", "1234").replaceAll("\\$\\{[^}]*\\}", "xyz");
            if (!VALID_REPO_PATTERN.matcher(expandedTag).matches()) {
                throw new IllegalArgumentException("Tag " + verifyTag + " doesn't match " + VALID_REPO_REGEX);
            }
        }
    }

    protected DockerAPI getDockerAPI(Launcher launcher) {
        DockerCloud theCloud;
        final VirtualChannel channel = launcher.getChannel();
        if (!Strings.isNullOrEmpty(cloud)) {
            theCloud = JenkinsUtils.getCloudByNameOrThrow(cloud);
        } else {
            if(channel instanceof Channel) {
                final Node node = Jenkins.getInstance().getNode(((Channel)channel).getName() );
                if (node instanceof DockerTransientNode) {
                    return ((DockerTransientNode) node).getDockerAPI();
                }
            }
            final Optional<DockerCloud> cloudForChannel = JenkinsUtils.getCloudForChannel(channel);
            if (!cloudForChannel.isPresent())
                throw new RuntimeException("Could not find the cloud this project was built on");
            theCloud = cloudForChannel.get();
        }

        // Triton can't do docker build. Ensure we're not trying to do that.
        if (theCloud.isTriton()) {
            LOGGER.warn("Selected cloud for build does not support this feature. Finding an alternative");
            for (DockerCloud dc : DockerCloud.instances()) {
                if (!dc.isTriton()) {
                    LOGGER.warn("Picked {} cloud instead", dc.getDisplayName());
                    theCloud = dc;
                    break;
                }
            }
        }
        return theCloud.getDockerApi();
    }

    private class Run {
        private final TaskListener listener;
        private final FilePath fpChild;
        private final List<String> tagsToUse;
        private final DockerAPI dockerApi;
        private final hudson.model.Run<?, ?> run;

        private Run(hudson.model.Run<?, ?> run, final TaskListener listener, FilePath fpChild, List<String> tagsToUse, DockerAPI dockerApi) {
            this.run = run;
            this.listener = listener;
            this.fpChild = fpChild;
            this.tagsToUse = tagsToUse;
            this.dockerApi = dockerApi;
        }

        private DockerAPI getDockerAPI() {
            Validate.notNull(dockerApi, "Could not get client because we could not find the cloud that the " +
                    "project was built on. Was this build run on Docker?");
            return dockerApi;
        }

        private DockerClient getClientWithNoTimeout() {
            // use a connection without an activity timeout
            return getDockerAPI().getClient(0);
        }

        private DockerClient getClient() {
            // use a connection with an activity timeout
            return getDockerAPI().getClient();
        }

        boolean run() throws IOException, InterruptedException {
            log("Docker Build");
            final String imageId = buildImage();
            log("Docker Build Response : " + imageId);
            // Add an action to the build
            Action action = new DockerBuildImageAction(dockerApi.getDockerHost().getUri(), imageId, tagsToUse, cleanupWithJenkinsJobDelete, pushOnSuccess, noCache, pull);
            run.addAction(action);
            run.save();
            if (pushOnSuccess) {
                log("Pushing " + tagsToUse);
                pushImages();
            }
            if (cleanImages) {
                // For some reason, docker delete doesn't delete all tagged
                // versions, despite force = true.
                // So, do it multiple times (protect against infinite looping).
                log("Cleaning local images [" + imageId + "]");

                try {
                    cleanImages(imageId);
                } catch (Exception ex) {
                    log("Error attempting to clean images", ex);
                }
            }
            log("Docker Build Done");
            return true;
        }

        private void cleanImages(String id) throws IOException {
            try(final DockerClient client = getClient()) {
                client.removeImageCmd(id)
                    .withForce(true)
                    .exec();
            }
        }

        @Nonnull
        private String buildImage() throws IOException, InterruptedException {
            final AuthConfigurations auths = new AuthConfigurations();
            final DockerRegistryEndpoint pullRegistry = getFromRegistry();
            if (pullRegistry != null && pullRegistry.getCredentialsId() != null) {
                auths.addConfig(DockerCloud.getAuthConfig(pullRegistry, run.getParent().getParent()));
            }
            log("Docker Build: building image at path " + fpChild.getRemote());
            final InputStream tar = fpChild.act(new DockerBuildCallable());
            BuildImageResultCallback resultCallback = new BuildImageResultCallback() {
                @Override
                public void onNext(BuildResponseItem item) {
                    String text = item.getStream();
                    if (text != null) {
                        listener.getLogger().println(text);
                    }
                    super.onNext(item);
                }
            };
            final String imageId;
            try(final DockerClient client = getClientWithNoTimeout()) {
                imageId = client.buildImageCmd(tar)
                        .withNoCache(noCache)
                        .withPull(pull)
                        .withBuildAuthConfigs(auths)
                        .exec(resultCallback)
                        .awaitImageId();
                if (imageId == null) {
                    throw new AbortException("Built image id is null. Some error occured");
                }
                // tag built image with tags
                for (String thisTag : tagsToUse) {
                    final NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(thisTag);
                    final String commitTag = isEmpty(reposTag.tag) ? "latest" : reposTag.tag;
                    log("Tagging built image with " + reposTag.repos + ":" + commitTag);
                    client.tagImageCmd(imageId, reposTag.repos, commitTag).withForce().exec();
                }
            }
            return imageId;
        }

        protected void log(String s) {
            listener.getLogger().println(s);
        }

        protected void log(Throwable ex) {
            ex.printStackTrace(listener.getLogger());
        }

        protected void log(String s, Throwable ex) {
            log(s);
            log(ex);
        }

        private void pushImages() throws IOException {
            for (String tagToUse : tagsToUse) {
                Identifier identifier = Identifier.fromCompoundString(tagToUse);
                PushImageResultCallback resultCallback = new PushImageResultCallback() {
                    @Override
                    public void onNext(PushResponseItem item) {
                        if (item == null) {
                            // docker-java not happy if you pass it nulls.
                            log("Received NULL Push Response. Ignoring");
                            return;
                        }
                        printResponseItemToListener(listener, item);
                        super.onNext(item);
                    }
                };
                try(final DockerClient client = getClientWithNoTimeout()) {
                    PushImageCmd cmd = client.pushImageCmd(identifier);

                    int i = identifier.repository.name.indexOf('/');
                    String regName = i >= 0 ?
                            identifier.repository.name.substring(0,i) : null;

                    DockerCloud.setRegistryAuthentication(cmd,
                            new DockerRegistryEndpoint(regName, getPushCredentialsId()),
                            run.getParent().getParent());
                    cmd.exec(resultCallback).awaitSuccess();
                } catch (DockerException ex) {
                    // Private Docker registries fall over regularly. Tell the user so they
                    // have some clue as to what to do as the exception gives no hint.
                    log("Exception pushing docker image. Check that the destination registry is running.");
                    throw ex;
                }
            }
        }
    }

    private static class DockerBuildCallable extends MasterToSlaveFileCallable<InputStream> {
        @Override
        public InputStream invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return new RemoteInputStream(new Dockerfile(new File(f, "Dockerfile"), f).parse().buildDockerFolderTar(), RemoteInputStream.Flag.GREEDY);
        }
    }

    @Override
    public void perform(hudson.model.Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        final List<String> expandedTags = expandTags(run, workspace, listener);
        String expandedDockerFileDirectory = dockerFileDirectory;
        try {
            expandedDockerFileDirectory = TokenMacro.expandAll(run, workspace, listener, this.dockerFileDirectory);
        } catch (MacroEvaluationException e) {
            listener.getLogger().println("Couldn't macro expand docker file directory " + dockerFileDirectory);
            e.printStackTrace(listener.getLogger());
        }
        new Run(run, listener, new FilePath(workspace, expandedDockerFileDirectory), expandedTags, getDockerAPI(launcher)).run();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private List<String> expandTags(hudson.model.Run<?, ?> build, FilePath workspace, TaskListener listener) {
        final List<String> tagsOrNull = getTags();
        final List<String> result;
        if (tagsOrNull == null) {
            result = new ArrayList<>(0);
        } else {
            result = new ArrayList<>(tagsOrNull.size());
            for (final String thisTag : tagsOrNull) {
                try {
                    final String expandedTag = TokenMacro.expandAll(build, workspace, listener, thisTag);
                    result.add(expandedTag);
                } catch (MacroEvaluationException | IOException | InterruptedException e) {
                    listener.getLogger().println("Couldn't macro expand tag " + thisTag);
                    e.printStackTrace(listener.getLogger());
                }
            }
        }
        return result;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckTagsString(@QueryParameter String tagsString) {
            try {
                verifyTags(tagsString);
            } catch (Throwable t) {
                return FormValidation.error(t.getMessage());
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillPullCredentialsIdItems(@AncestorInPath Item item) {
            return doFillRegistryCredentialsIdItems(item);
        }

        public ListBoxModel doFillPushCredentialsIdItems(@AncestorInPath Item item) {
            return doFillRegistryCredentialsIdItems(item);
        }

        private ListBoxModel doFillRegistryCredentialsIdItems(@AncestorInPath Item item) {
            final DockerRegistryEndpoint.DescriptorImpl descriptor =
                    (DockerRegistryEndpoint.DescriptorImpl)
                    Jenkins.getInstance().getDescriptorOrDie(DockerRegistryEndpoint.class);
            return descriptor.doFillCredentialsIdItems(item);
        }

        public ListBoxModel doFillCloudItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Cloud this build is running on", "");
            for (Cloud cloud : DockerCloud.instances()) {
                model.add(cloud.name);
            }
            return model;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Build / Publish Docker Image";
        }
    }

    private Object readResolve() {
        if (pushCredentialsId == null && registry != null) {
            pushCredentialsId = registry.getCredentialsId();
        }
        if (pullCredentialsId != null) {
            fromRegistry = new DockerRegistryEndpoint(null, pullCredentialsId);
        }
        return this;
    }
}
