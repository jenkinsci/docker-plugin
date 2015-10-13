package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerClientException;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Identifier;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.action.DockerBuildImageAction;
import com.nirima.jenkins.plugins.docker.client.ClientBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.client.ClientConfigBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.client.DockerCmdExecConfig;
import com.nirima.jenkins.plugins.docker.client.DockerCmdExecConfigBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.Validate;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import shaded.com.google.common.base.Joiner;
import shaded.com.google.common.base.Optional;
import shaded.com.google.common.base.Splitter;

import javax.annotation.CheckForNull;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.nirima.jenkins.plugins.docker.utils.LogUtils.printResponseItemToListener;
import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Builder extension to build / publish an image from a Dockerfile.
 */
public class DockerBuilderPublisher extends Builder implements Serializable, SimpleBuildStep {
    private static final Pattern VALID_REPO_PATTERN = Pattern.compile("^([a-z0-9-_.]+)$");

    public final String dockerFileDirectory;

    /**
     * @deprecated use {@link #tags}
     */
    @Deprecated
    public String tag;

    @CheckForNull
    private List<String> tags;

    public final boolean pushOnSuccess;
    public final boolean cleanImages;
    public final boolean cleanupWithJenkinsJobDelete;

    @DataBoundConstructor
    public DockerBuilderPublisher(String dockerFileDirectory,
                                  String tagsString,
                                  boolean pushOnSuccess,
                                  boolean cleanImages,
                                  boolean cleanupWithJenkinsJobDelete) {
        this.dockerFileDirectory = dockerFileDirectory;
        setTagsString(tagsString);
        this.tag = null;
        this.pushOnSuccess = pushOnSuccess;
        this.cleanImages = cleanImages;
        this.cleanupWithJenkinsJobDelete = cleanupWithJenkinsJobDelete;
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

    public static List<String> filterStringToList(String str) {
        return str == null ? Collections.<String>emptyList() : Splitter.on("\n").omitEmptyStrings().trimResults().splitToList(str);
    }

    public static void verifyTags(String tagsString) {
        final List<String> verifyTags = filterStringToList(tagsString);
        for (String verifyTag : verifyTags) {
            if (!VALID_REPO_PATTERN.matcher(verifyTag).matches()) {
                throw new IllegalArgumentException("Tag " + verifyTag + " doesn't match ^([a-z0-9-_.]+)$");
            }
        }
    }

    class Run implements Serializable {

        final transient Launcher launcher;
        final TaskListener listener;

        final FilePath fpChild;

        final List<String> tagsToUse;

        // Marshal the builder across the wire.
        private transient DockerClient _client;

        final DockerClientConfig clientConfig;
        final DockerCmdExecConfig dockerCmdExecConfig;

        final transient hudson.model.Run<?,?> run;

        final String url;


        private Run(hudson.model.Run<?, ?> run, final Launcher launcher, final TaskListener listener, FilePath fpChild, List<String> tagsToUse, Optional<DockerCloud> cloudThatBuildRanOn) {

            this.run = run;
            this.launcher = launcher;
            this.listener = listener;
            this.fpChild = fpChild;
            this.tagsToUse = tagsToUse;

            if (cloudThatBuildRanOn.isPresent()) {

                // Don't build it yet. This may happen on a remote server.
                clientConfig = ClientConfigBuilderForPlugin.dockerClientConfig()
                       .forCloud(cloudThatBuildRanOn.get()).build();
                dockerCmdExecConfig = DockerCmdExecConfigBuilderForPlugin.builder()
                        .forCloud(cloudThatBuildRanOn.get()).build();

                url = cloudThatBuildRanOn.get().serverUrl;

            } else {
                clientConfig = null;
                dockerCmdExecConfig = null;
                url = null;
            }

        }

        public Run(AbstractBuild build, Launcher launcher, BuildListener listener) {
            //super(launcher, listener);
            this(build, launcher, listener, new FilePath(build.getWorkspace(), dockerFileDirectory), expandTags(build, launcher, listener), JenkinsUtils.getCloudForBuild(build));
        }

        public Run(hudson.model.Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) {
            this(run, launcher, listener,new FilePath(workspace, dockerFileDirectory), tags, JenkinsUtils.getCloudForChannel(launcher.getChannel()));
        }

        private DockerClient getClient() {
            if (_client == null) {
                Validate.notNull(clientConfig, "Could not get client because we could not find the cloud that the " +
                        "project was built on. What this build run on Docker?");

                _client = ClientBuilderForPlugin.builder()
                        .withDockerCmdExecConfig(dockerCmdExecConfig)
                        .withDockerClientConfig(clientConfig)
                        .build();
            }

            return _client;
        }

        boolean run() throws IOException, InterruptedException {
            final PrintStream llog = listener.getLogger();
            llog.println("Docker Build");

            String imageId = buildImage();

            // The ID of the image we just generated
            if (imageId == null) {
                return false;
            }

            llog.println("Docker Build Response : " + imageId);

            // Add an action to the build
            run.addAction(new DockerBuildImageAction(url, imageId, tagsToUse, cleanupWithJenkinsJobDelete, pushOnSuccess));
            run.save();

            if (pushOnSuccess) {
                llog.println("Pushing " + tagsToUse);
                pushImages();
            }

            if (cleanImages) {
                // For some reason, docker delete doesn't delete all tagged
                // versions, despite force = true.
                // So, do it multiple times (protect against infinite looping).
                llog.println("Cleaning local images [" + imageId + "]");

                try {
                    cleanImages(imageId);
                } catch (Exception ex) {
                    llog.println("Error attempting to clean images");
                }
            }

            llog.println("Docker Build Done");

            return true;
        }

        private void cleanImages(String id) {
            getClient().removeImageCmd(id)
                    .withForce()
                    .exec();
        }

        private String buildImage() throws IOException, InterruptedException {
            return fpChild.act(new MasterToSlaveFileCallable<String>() {
                public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {


                    log("Docker Build: building image at path " + f.getAbsolutePath());
                    BuildImageResultCallback resultCallback = new BuildImageResultCallback() {
                        public void onNext(BuildResponseItem item) {
                            String text = item.getStream();
                            if (text != null) {
                                log(text);
                            }
                            super.onNext(item);
                        }
                    };
                    String imageId = getClient().buildImageCmd(f)
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
                        getClient().tagImageCmd(imageId, reposTag.repos, commitTag).withForce().exec();
                    }

                    return imageId;
                }
            });
        }

        protected void log(String s)
        {
            final PrintStream llog = listener.getLogger();
            llog.println(s);
        }


        private void pushImages() {
            for (String tagToUse : tagsToUse) {
                Identifier identifier = Identifier.fromCompoundString(tagToUse);

                PushImageResultCallback resultCallback = new PushImageResultCallback() {
                    public void onNext(PushResponseItem item) {
                        if( item == null ) {
                            // docker-java not happy if you pass it nulls.
                            log("Received NULL Push Response. Ignoring");
                            return;
                        }
                        printResponseItemToListener(listener, item);
                        super.onNext(item);
                    }
                };
                try {
                    getClient().pushImageCmd(identifier).exec(resultCallback).awaitSuccess();
                } catch(DockerClientException ex) {
                    // Private Docker registries fall over regularly. Tell the user so they
                    // have some clue as to what to do as the exception gives no hint.
                    log("Exception pushing docker image. Check that the destination registry is running.");
                    throw ex;
                }
            }
        }
    }

    /**
     * Traditional build.
     */
    @Override
    public boolean perform(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        return new Run(build, launcher, listener).run();
    }

    /**
     * Workflow
     */
    @Override
    public void perform(hudson.model.Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        new Run(run, workspace, launcher, listener).run();
    }

    private List<String> expandTags(AbstractBuild build, Launcher launcher, BuildListener listener) {
        List<String> eTags = new ArrayList<>(tags.size());
        for (String tag : tags) {
            try {
                eTags.add(TokenMacro.expandAll(build, listener, tag));
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

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Build / Publish Docker Containers";
        }
    }
}
