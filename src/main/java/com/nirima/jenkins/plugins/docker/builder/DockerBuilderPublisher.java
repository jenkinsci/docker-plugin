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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.action.DockerBuildImageAction;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.VirtualChannel;
import hudson.slaves.Cloud;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.docker.client.DockerAPI;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.Validate;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.nirima.jenkins.plugins.docker.utils.LogUtils.printResponseItemToListener;
import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Builder extension to build / publish an image from a Dockerfile.
 * TODO automatic migration to https://wiki.jenkins.io/display/JENKINS/CloudBees+Docker+Build+and+Publish+plugin
 */
public class DockerBuilderPublisher extends Builder implements Serializable, SimpleBuildStep {

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

    private String pullCredentialsId;
    private transient DockerRegistryEndpoint fromRegistry;

    /**
     * @deprecated use {@link #tags}
     */
    @Deprecated
    public String tag;

    @CheckForNull
    private List<String> tags;

    public final boolean pushOnSuccess;

    private String pushCredentialsId;
    private transient DockerRegistryEndpoint registry;

    public final boolean cleanImages;
    public final boolean cleanupWithJenkinsJobDelete;

    public final String cloud;

    @DataBoundConstructor
    public DockerBuilderPublisher(String dockerFileDirectory,
                                  String pullCredentialsId,
                                  String cloud,
                                  String tagsString,
                                  boolean pushOnSuccess,
                                  String pushCredentialsId,
                                  boolean cleanImages,
                                  boolean cleanupWithJenkinsJobDelete) {
        this.dockerFileDirectory = dockerFileDirectory;
        this.pullCredentialsId = pullCredentialsId;
        setTagsString(tagsString);
        this.tag = null;
        this.cloud = cloud;
        this.pushOnSuccess = pushOnSuccess;
        this.pushCredentialsId = pushCredentialsId;
        this.cleanImages = cleanImages;
        this.cleanupWithJenkinsJobDelete = cleanupWithJenkinsJobDelete;
    }

    public DockerRegistryEndpoint getRegistry() {
        if (registry == null) {
            registry = new DockerRegistryEndpoint(null, pushCredentialsId);
        }
        return registry;
    }

    public String getPullCredentialsId() {
        return pullCredentialsId;
    }

    public String getPushCredentialsId() {
        return pushCredentialsId;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getTagsString() {
        return getTags() == null ? "" : Joiner.on("\n").join(getTags());
    }

    public void setTagsString(String tagsString) {
        setTags(filterStringToList(tagsString));
    }

    public DockerRegistryEndpoint getFromRegistry() {
        if (fromRegistry == null) {
            fromRegistry = new DockerRegistryEndpoint(null, pullCredentialsId);
        }
        return fromRegistry;
    }

    public static List<String> filterStringToList(String str) {
        if (str == null) return Collections.<String>emptyList();

        List<String> result = new ArrayList<String>();
        for (String o : Splitter.on("\n").omitEmptyStrings().trimResults().split(str)) {
            result.add(o);
        }
        return result;
    }

    public static void verifyTags(String tagsString) {
        final List<String> verifyTags = filterStringToList(tagsString);
        for (String verifyTag : verifyTags) {
            // Our strings are subjected to variable substitution before they are used, so ${foo} might be valid.
            // So we do some fake substitution to help prevent incorrect complaints.
            final String expandedTag = verifyTag.replaceAll("\\$\\{[^}]*NUMBER\\}", "1234").replaceAll("\\$\\{[^}]*\\}", "xyz");
            if (!VALID_REPO_PATTERN.matcher(expandedTag).matches()) {
                throw new IllegalArgumentException("Tag " + verifyTag + " doesn't match " + VALID_REPO_REGEX);
            }
        }
    }

    protected DockerCloud getCloud(Launcher launcher) {

        DockerCloud theCloud;

        if (!Strings.isNullOrEmpty(cloud)) {
            theCloud = JenkinsUtils.getServer(cloud);
        } else {

            Optional<DockerCloud> cloud = JenkinsUtils.getCloudForChannel(launcher.getChannel());
            if (!cloud.isPresent())
                throw new RuntimeException("Could not find the cloud this project was built on");

            theCloud = cloud.get();
        }

        // Triton can't do docker build. Ensure we're not trying to do that.
        if (theCloud.isTriton()) {
            LOGGER.warn("Selected cloud for build does not support this feature. Finding an alternative");

            for (DockerCloud dc : JenkinsUtils.getServers()) {
                if (!dc.isTriton()) {
                    LOGGER.warn("Picked {} cloud instead", dc.getDisplayName());
                    return dc;
                }
            }

        }

        return theCloud;
    }

    class Run implements Serializable {

        final transient Launcher launcher;
        final TaskListener listener;

        final FilePath fpChild;

        final List<String> tagsToUse;

        private final DockerAPI dockerApi;

        // Marshal the builder across the wire.
        private transient DockerClient _client;

        final transient hudson.model.Run<?, ?> run;

        private Run(hudson.model.Run<?, ?> run, final Launcher launcher, final TaskListener listener, FilePath fpChild, List<String> tagsToUse, DockerCloud dockerCloud) {

            this.run = run;
            this.launcher = launcher;
            this.listener = listener;
            this.fpChild = fpChild;
            this.tagsToUse = tagsToUse;
            this.dockerApi = dockerCloud.getDockerApi();

        }

//        public Run(hudson.model.Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, DockerCloud cloud) {
//            this(run, launcher, listener,new FilePath(workspace, dockerFileDirectory), expandTags(run, launcher, listener), cloud);
//        }

        private DockerClient getClient() {
            if (_client == null) {
                Validate.notNull(dockerApi, "Could not get client because we could not find the cloud that the " +
                        "project was built on. Was this build run on Docker?");

                _client = dockerApi.getClient();
            }

            return _client;
        }

        boolean run() throws IOException, InterruptedException {
            log("Docker Build");

            String imageId = buildImage();

            // The ID of the image we just generated
            if (imageId == null) {
                return false;
            }

            log("Docker Build Response : " + imageId);

            // Add an action to the build
            run.addAction(new DockerBuildImageAction(dockerApi.getDockerHost().getUri(), imageId, tagsToUse, cleanupWithJenkinsJobDelete, pushOnSuccess));
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
                    log("Error attempting to clean images");
                }
            }

            log("Docker Build Done");

            return true;
        }

        private void cleanImages(String id) {
            getClient().removeImageCmd(id)
                    .withForce(true)
                    .exec();
        }

        private String buildImage() throws IOException, InterruptedException {
            final AuthConfigurations auths = new AuthConfigurations();
            final DockerRegistryEndpoint pullRegistry = getFromRegistry();
            if (pullRegistry != null && pullRegistry.getCredentialsId() != null) {
                auths.addConfig(DockerCloud.getAuthConfig(pullRegistry, run.getParent().getParent()));
            }

            final DockerClient client = getClient();

            log("Docker Build: building image at path " + fpChild.getRemote());
            final InputStream tar = fpChild.act(new DockerBuildCallable());


            BuildImageResultCallback resultCallback = new BuildImageResultCallback() {
                public void onNext(BuildResponseItem item) {
                    String text = item.getStream();
                    if (text != null) {
                        listener.getLogger().println(text);
                    }
                    super.onNext(item);
                }
            };
            String imageId = client.buildImageCmd(tar)
                    .withBuildAuthConfigs(auths)
                    .exec(resultCallback)
                    .awaitImageId();

            if (imageId == null) {
                throw new AbortException("Built image id is null. Some error accured");
            }

            // tag built image with tags
            for (String tag : tagsToUse) {
                final NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(tag);
                final String commitTag = isEmpty(reposTag.tag) ? "latest" : reposTag.tag;
                log("Tagging built image with " + reposTag.repos + ":" + commitTag);
                client.tagImageCmd(imageId, reposTag.repos, commitTag).withForce().exec();
            }

            return imageId;
        }

        protected void log(String s) {
            listener.getLogger().println(s);
        }


        private void pushImages() throws IOException {
            for (String tagToUse : tagsToUse) {
                Identifier identifier = Identifier.fromCompoundString(tagToUse);
                PushImageResultCallback resultCallback = new PushImageResultCallback() {
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
                try {
                    PushImageCmd cmd = getClient().pushImageCmd(identifier);
                    DockerCloud.setRegistryAuthentication(cmd, getRegistry(), run.getParent().getParent());
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

        public InputStream invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return new RemoteInputStream(new Dockerfile(new File(f, "Dockerfile"), f).parse().buildDockerFolderTar(), RemoteInputStream.Flag.GREEDY);
        }

    }

    @Override
    public void perform(hudson.model.Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        List<String> expandedTags;

        expandedTags = expandTags(run, workspace, launcher, listener);
        String expandedDockerFileDirectory = dockerFileDirectory;
        try {
            expandedDockerFileDirectory = TokenMacro.expandAll(run, workspace, listener, this.dockerFileDirectory);
        } catch (MacroEvaluationException e) {
            listener.getLogger().println("Couldn't macro expand docker file directory " + dockerFileDirectory);
        }
        new Run(run, launcher, listener, new FilePath(workspace, expandedDockerFileDirectory), expandedTags, getCloud(launcher)).run();

    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }


    private List<String> expandTags(hudson.model.Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        List<String> eTags = new ArrayList<>(tags.size());
        for (String tag : tags) {
            try {
                eTags.add(TokenMacro.expandAll(build, workspace, listener, tag));
            } catch (MacroEvaluationException | IOException | InterruptedException e) {
                listener.getLogger().println("Couldn't macro expand tag " + tag);
            }
        }
        return eTags;
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
        if (pullCredentialsId == null && fromRegistry != null) {
            pullCredentialsId = fromRegistry.getCredentialsId();
        }
        return this;
    }

}


