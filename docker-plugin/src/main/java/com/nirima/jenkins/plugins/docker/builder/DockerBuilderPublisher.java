package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.core.DockerClientConfig;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.client.ClientBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.client.ClientConfigBuilderForPlugin;
import shaded.com.google.common.base.Optional;
import shaded.com.google.common.base.Splitter;
import shaded.com.google.common.base.Throwables;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;

import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.Identifier;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.action.DockerBuildImageAction;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;

/**
 * Builder extension to build / publish an image from a Dockerfile.
 */
public class DockerBuilderPublisher extends Builder implements Serializable {

    public final String dockerFileDirectory;
    public final String tag;
    public final boolean pushOnSuccess;
    public final boolean cleanImages;
    public final boolean cleanupWithJenkinsJobDelete;

    @DataBoundConstructor
    public DockerBuilderPublisher(String dockerFileDirectory, String tag, boolean pushOnSuccess, boolean cleanImages, boolean cleanupWithJenkinsJobDelete) {
        this.dockerFileDirectory = dockerFileDirectory;
        this.tag = tag;
        this.pushOnSuccess = pushOnSuccess;
        this.cleanImages = cleanImages;
        this.cleanupWithJenkinsJobDelete = cleanupWithJenkinsJobDelete;
    }

    class Run implements Serializable {
        final transient AbstractBuild build;
        final transient Launcher launcher;
        final BuildListener listener;

        FilePath fpChild;

        final String tagsToUse;
        final String url;
        // Marshal the builder across the wire.
        private transient DockerClient _client;

        final DockerClientConfig clientConfig;

        Run(final AbstractBuild build, final Launcher launcher, final BuildListener listener) {
            this.build = build;
            this.launcher = launcher;
            this.listener = listener;

            fpChild = new FilePath(build.getWorkspace(), dockerFileDirectory);

            tagsToUse = getTag(build, launcher, listener);
            url = getUrl(build);

            Optional<DockerCloud> cloudThatBuildRanOn = getCloudForBuild(build);

            if( cloudThatBuildRanOn.isPresent() ) {

                // Don't build it yet. This may happen on a remote server.
                clientConfig = ClientConfigBuilderForPlugin.dockerClientConfig()
                        .forCloud(cloudThatBuildRanOn.get()).build();

            }else{
                clientConfig = null;
            }

        }

        protected Iterable<String> getTagsToUse() {
            return Splitter.on(" ").omitEmptyStrings().trimResults().split(tagsToUse);
        }


        /**
         * If the build was on a cloud, get the ID of that cloud.
         * @param build
         * @return
         */
        public Optional<DockerCloud> getCloudForBuild(AbstractBuild build) {

            Node node = build.getBuiltOn();
            if( node instanceof DockerSlave ) {
                DockerSlave slave = (DockerSlave)node;
                return Optional.of(slave.getCloud());
            }

            return Optional.absent();
        }

        private DockerClient getClient() {

            if( _client == null ) {

                if( clientConfig == null )
                    throw new RuntimeException("Could not get client because we could not find the cloud that the project was built on. What this build run on Docker?");

                _client = ClientBuilderForPlugin.getInstance(clientConfig).build();
            }
            return _client;
        }

        boolean run() throws IOException, InterruptedException {
            listener.getLogger().println("Docker Build");

            String response = buildImage();

            listener.getLogger().println("Docker Build Response : " + response);

            // The ID of the image we just generated
            Optional<String> id = getImageId(response);
            if( !id.isPresent() )
                return false;

            build.addAction( new DockerBuildImageAction(url, id.get(), tagsToUse, cleanupWithJenkinsJobDelete, pushOnSuccess) );
            build.save();



            if( pushOnSuccess ) {

                listener.getLogger().println("Pushing " + tagsToUse);
                String stringResponse = pushImage();
                listener.getLogger().println("Docker Push Response : " + stringResponse);
            }

            if (cleanImages) {

                // For some reason, docker delete doesn't delete all tagged
                // versions, despite force = true.
                // So, do it multiple times (protect against infinite looping).
                listener.getLogger().println("Cleaning local images [" + id.get() + "]");

                try {
                    cleanImages(id.get());
                } catch(Exception ex) {
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
                    try {
                      listener.getLogger().println("Docker Build : build with tags " + tagsToUse + " at path " + f.getAbsolutePath());

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();

                        for(String tag : getTagsToUse() ) {
                            listener.getLogger().println("Docker Build : building tag " + tag);

                            try {
                                InputStream is = getClient().buildImageCmd(f)
                                        .withTag(tag)
                                        .exec();

                                IOUtils.copy(is, baos);
                            } catch(Exception ex) {
                                listener.getLogger().println(ex.getMessage());
                                ex.printStackTrace( listener.getLogger());
                                listener.getLogger().println("Error attempting to tag " + tag + ". Continuing anyway.");
                            }
                        }



                        return baos.toString();

                    } catch (DockerException e) {
                        throw Throwables.propagate(e);
                    }

                }
            });
        }

        private String pushImage() throws IOException {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            for(String tagToUse : getTagsToUse()) {

                if (!tagToUse.toLowerCase().equals(tagToUse)) {
                    listener.getLogger().println("ERROR: Docker will refuse to push tag name " + tagToUse + " because it uses upper case.");
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

    private Optional<String> getImageId(String response) {
        for(String item : response.split("\n") ) {
            if (item.contains("Successfully built")) {
                String id =  StringUtils.substringAfterLast(item, "Successfully built ").trim();
                // Seem to have an additional \n in the stream.
                id = id.substring(0,12);
                return Optional.of(id);
            }
        }

        return Optional.absent();
    }



    private String getUrl(AbstractBuild build) {
        Node node = build.getBuiltOn();
        if( node instanceof DockerSlave ) {
            DockerSlave slave = (DockerSlave)node;
            return slave.getCloud().serverUrl;
        }


        return null;
    }

    private String getTag(AbstractBuild build, Launcher launcher, BuildListener listener) {
        try {
            return TokenMacro.expandAll(build, listener, tag);
        }catch(Exception ex) {
            listener.getLogger().println("Couldn't macro expand tag " + tag);
        }
        return tag;

    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

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
