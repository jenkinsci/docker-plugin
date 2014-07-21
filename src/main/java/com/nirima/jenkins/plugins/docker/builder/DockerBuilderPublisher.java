package com.nirima.jenkins.plugins.docker.builder;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.nirima.docker.client.DockerClient;
import com.nirima.docker.client.DockerException;
import com.nirima.docker.client.command.BuildCommandResponse;
import com.nirima.docker.client.command.PushCommandResponse;
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
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Builder extension to build / publish an image.
 */
public class DockerBuilderPublisher extends Builder implements Serializable {

    public final String dockerFileDirectory;
    public final String tag;
    public final boolean pushOnSuccess;
    public final boolean cleanImages;

    @DataBoundConstructor
    public DockerBuilderPublisher(String dockerFileDirectory, String tag, boolean pushOnSuccess, boolean cleanImages) {
        this.dockerFileDirectory = dockerFileDirectory;
        this.tag = tag;
        this.pushOnSuccess = pushOnSuccess;
        this.cleanImages = cleanImages;
    }

    @Override
    public boolean perform(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {

        listener.getLogger().println("Docker Build");

        FilePath fpChild = new FilePath(build.getWorkspace(), dockerFileDirectory);

        final String tagToUse = getTag(build, launcher, listener);
        final String url = getUrl(build);
        // Marshal the builder across the wire.
        DockerClient client = getDockerClient(build);
        final DockerClient.Builder builder = DockerClient.builder().fromClient(client);

        BuildCommandResponse response = fpChild.act(new FilePath.FileCallable<BuildCommandResponse>() {
            public BuildCommandResponse invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                try {
                    listener.getLogger().println("Docker Build : build with tag " + tagToUse + " at path " + f.getAbsolutePath());
                    DockerClient client = builder
                            .readTimeout(3600000).build();

                    File dockerFile;

                    // Be lenient and allow the user to just specify the path.
                    if( f.isFile() )
                        dockerFile = f;
                    else
                        dockerFile = new File(f, "Dockerfile");

                    return client.createBuildCommand()
                            .dockerFile(dockerFile)
                            .tag(tagToUse)
                            .execute();

                } catch (DockerException e) {
                    throw Throwables.propagate(e);
                }

            }
        });


        listener.getLogger().println("Docker Build Response : " + response);

        Optional<String> id = response.imageId();
        if( !id.isPresent() )
           return false;

        build.addAction( new DockerBuildImageAction(url, id.get(), tagToUse) );
        build.save();


        if( pushOnSuccess ) {

            listener.getLogger().println("Pushing " + tagToUse);
            if( !tagToUse.toLowerCase().equals(tagToUse) ) {
                listener.getLogger().println("ERROR: Docker will refuse to push tag name " + tagToUse + " because it uses upper case.");
            }

            String repositoryName = getRepositoryName(tagToUse);

            PushCommandResponse pushResponse = client.createPushCommand()
                    .name(repositoryName)
                    .execute();

            listener.getLogger().println("Docker Push Response : " + pushResponse);
        }

        if (cleanImages) {

            // For some reason, docker delete doesn't delete all tagged
            // versions, despite force = true.
            // So, do it multiple times (protect against infinite looping).
            listener.getLogger().println("Cleaning local images");

            int delete = 100;
            while (delete != 0) {
                int count = client.image(id.get()).removeCommand()
                        .force(true)
                        .execute().size();
                if (count == 0)
                    delete = 0;
                else
                    delete--;
            }
        }



        listener.getLogger().println("Docker Build Done");

        return true;
    }

    private String getRepositoryName(String tagToUse) {
        // fred/jim     --> fred/jim
        // fred/jim:123 --> fred/jim
        // fred:123/jim:123 --> fred:123/jim

        String[] parts = tagToUse.split("/");
        if( parts.length != 2 )
            return tagToUse;

        String[] rhs = parts[1].split(":");
        if( rhs.length != 2 )
            return tagToUse;

        return parts[0] + "/" + rhs[0];
    }

    private DockerClient getDockerClient(AbstractBuild build) {


        Node node = build.getBuiltOn();
        if( node instanceof DockerSlave ) {
            DockerSlave slave = (DockerSlave)node;
            return slave.getCloud().connect();
        }


        return null;
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
