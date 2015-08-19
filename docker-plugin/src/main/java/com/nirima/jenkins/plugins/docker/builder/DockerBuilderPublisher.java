package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.EventStreamItem;
import com.github.dockerjava.api.model.Identifier;
import com.github.dockerjava.core.DockerClientConfig;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.action.DockerBuildImageAction;
import com.nirima.jenkins.plugins.docker.apidesc.DockerBuildDescribable;
import com.nirima.jenkins.plugins.docker.client.ClientBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.client.ClientConfigBuilderForPlugin;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import shaded.com.google.common.base.Joiner;
import shaded.com.google.common.base.Optional;
import shaded.com.google.common.base.Splitter;
import shaded.com.google.common.base.Throwables;

import javax.annotation.CheckForNull;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builder extension to build / publish an image from a Dockerfile.
 */
public class DockerBuilderPublisher extends Builder implements Serializable {
    private static final Pattern VALID_REPO_PATTERN = Pattern.compile("^([a-z0-9-_.]+)$");

    public final String dockerFileDirectory;

    /**
     * @deprecated use {@link #tags}
     */
    @Deprecated
    public String tag;

    @CheckForNull
    private List<String> tags;

    /**
     * push images
     */
    public final boolean pushOnSuccess;

    /**
     * force remove images with matching id.
     */
    public final boolean cleanImages;

    /**
     * {@see com.nirima.jenkins.plugins.docker.listener.DockerRunListener.class}
     */
    public final boolean cleanupWithJenkinsJobDelete;

    /**
     * Contains configuration for docker build api call
     */
    @CheckForNull
    private DockerBuildDescribable dockerBuild;

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

    public DockerBuildDescribable getDockerBuild() {
        return dockerBuild;
    }

    @DataBoundSetter
    public void setDockerBuild(DockerBuildDescribable dockerBuild) {
        this.dockerBuild = dockerBuild;
    }

    public boolean isCleanupWithJenkinsJobDelete() {
        return cleanupWithJenkinsJobDelete;
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
        return str == null ? null : Splitter.on("\n").omitEmptyStrings().trimResults().splitToList(str);
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
        final transient AbstractBuild build;
        final transient Launcher launcher;
        final BuildListener listener;

        FilePath fpChild;

        final List<String> tagsToUse;
        final String url;
        // Marshal the builder across the wire.
        private transient DockerClient _client;

        final DockerClientConfig clientConfig;

        Run(final AbstractBuild build, final Launcher launcher, final BuildListener listener) {
            this.build = build;
            this.launcher = launcher;
            this.listener = listener;

            fpChild = new FilePath(build.getWorkspace(), dockerFileDirectory);

            tagsToUse = expandTags(build, launcher, listener);
            url = getUrl(build);

            Optional<DockerCloud> cloudThatBuildRanOn = getCloudForBuild(build);

            if (cloudThatBuildRanOn.isPresent()) {

                // Don't build it yet. This may happen on a remote server.
                clientConfig = ClientConfigBuilderForPlugin.dockerClientConfig()
                        .forCloud(cloudThatBuildRanOn.get()).build();

            } else {
                clientConfig = null;
            }

        }

        /**
         * If the build was on a cloud, get the ID of that cloud.
         */
        public Optional<DockerCloud> getCloudForBuild(AbstractBuild build) {

            Node node = build.getBuiltOn();
            if (node instanceof DockerSlave) {
                DockerSlave slave = (DockerSlave) node;
                return Optional.of(slave.getCloud());
            }

            return Optional.absent();
        }

        private DockerClient getClient() {

            if (_client == null) {

                if (clientConfig == null)
                    throw new RuntimeException("Could not get client because we could not find the cloud that the " +
                            "project was built on. What this build run on Docker?");

                _client = ClientBuilderForPlugin.getInstance(clientConfig).build();
            }
            return _client;
        }

        boolean run() throws IOException, InterruptedException {
            listener.getLogger().println("Docker Build");

            String imageId = buildImage();

            // The ID of the image we just generated
            if (imageId == null) {
                return false;
            }

            listener.getLogger().println("Docker Build Response : " + imageId);

            build.addAction(new DockerBuildImageAction(url, imageId, tagsToUse, cleanupWithJenkinsJobDelete, pushOnSuccess));
            build.save();


            if (pushOnSuccess) {
                listener.getLogger().println("Pushing " + tagsToUse);
                String stringResponse = pushImages();
                listener.getLogger().println("Docker Push Response : " + stringResponse);
            }

            if (cleanImages) {
                // For some reason, docker delete doesn't delete all tagged
                // versions, despite force = true.
                // So, do it multiple times (protect against infinite looping).
                listener.getLogger().println("Cleaning local images [" + imageId + "]");

                try {
                    cleanImages(imageId);
                } catch (Exception ex) {
                    listener.getLogger().println("Error attempting to clean images");
                }
            }

            listener.getLogger().println("Docker Build Done");

            return true;
        }

        private void cleanImages(String id) {
            getClient().removeImageCmd(id)
                    .withForce()
                    .exec();
        }

        private String buildImage() throws IOException, InterruptedException {

            return fpChild.act(new FilePath.FileCallable<String>() {
                public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                    String imageId = null;
                    try {
                        listener.getLogger().println("Docker Build : build with tags " + tagsToUse.toString()
                                + " at path " + f.getAbsolutePath());

                        for (String tag : tagsToUse) {
                            listener.getLogger().println("Docker Build : building tag " + tag);

                            try {
                                final BuildImageCmd buildImageCmd = getClient().buildImageCmd(f);

                                if (getDockerBuild() != null) {
                                    getDockerBuild().fillBuildImageCmd(buildImageCmd);
                                }

                                Iterable<EventStreamItem> response = buildImageCmd
                                        .withTag(tag)
                                        .exec()
                                        .getItems();

                                for (EventStreamItem item : response) {
                                    String text = item.getStream();
                                    if (text != null) {
                                        listener.getLogger().append(text);
                                        if (text.startsWith("Successfully built ")) {
                                            imageId = StringUtils.substringBetween(text, "Successfully built ", "\n").trim();
                                        }
                                    }
                                }

                            } catch (Exception ex) {
                                listener.getLogger().println(ex.getMessage());
                                ex.printStackTrace(listener.getLogger());
                                listener.getLogger().println("Error attempting to tag " + tag + ". Continuing anyway.");
                            }
                        }

                        return imageId;
                    } catch (DockerException e) {
                        throw Throwables.propagate(e);
                    }
                }
            });
        }

        private String pushImages() throws IOException {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            for (String tagToUse : tagsToUse) {

                if (!tagToUse.toLowerCase().equals(tagToUse)) {
                    listener.getLogger().println("ERROR: Docker will refuse to push tag name "
                            + tagToUse + " because it uses upper case.");
                }

                Identifier identifier = Identifier.fromCompoundString(tagToUse);

                PushImageCmd pushImageCmd = getClient().pushImageCmd(identifier);

                InputStream pushResponse = pushImageCmd.exec();

                IOUtils.copy(pushResponse, byteArrayOutputStream);
            }

            return byteArrayOutputStream.toString();
        }
    }

    @Override
    public boolean perform(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        return new Run(build, launcher, listener).run();
    }

    private String getUrl(AbstractBuild build) {
        Node node = build.getBuiltOn();
        if (node instanceof DockerSlave) {
            DockerSlave slave = (DockerSlave) node;
            return slave.getCloud().serverUrl;
        }


        return null;
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
