package com.nirima.jenkins.plugins.docker;

import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;

import javax.annotation.Nonnull;
import java.io.IOException;


/**
 * @deprecated use {@link DockerTransientNode}
 */
@Deprecated
public class DockerSlave extends Slave {

    private transient  DockerTemplate dockerTemplate;

    private transient String containerId;

    private transient String cloudId;

    private transient  DockerAPI dockerAPI;

    private DockerSlave(@Nonnull String name, String remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super(name, remoteFS, launcher);
    }

    protected Object readResolve() {
        try {
            return new DockerTransientNode(containerId, containerId, dockerTemplate.remoteFs, getLauncher());
        } catch (Descriptor.FormException | IOException e) {
            throw new RuntimeException("Failed to migrate DockerSlave", e);
        }
    }



    /* FIXME better move this to a io.jenkins.docker.DockerTransientNode.Callback
    private void slaveShutdown(final TaskListener listener) throws DockerException, IOException {

        // The slave has stopped. Should we commit / tag / push ?

        if (!getJobProperty().tagOnCompletion) {
            addJenkinsAction(null);
            return;
        }

        DockerClient client = getClient();


        // Commit
        String tag_image = client.commitCmd(containerId)
                .withRepository(theRun.getParent().getDisplayName())
                .withTag(theRun.getDisplayName().replace("#", "b")) // allowed only ([a-zA-Z_][a-zA-Z0-9_]*)
                .withAuthor("Jenkins")
                .exec();

        // Tag it with the jenkins name
        addJenkinsAction(tag_image);

        // SHould we add additional tags?
        try {
            String tagToken = getAdditionalTag(listener);

            if (!Strings.isNullOrEmpty(tagToken)) {


                final NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(tagToken);
                final String commitTag = isEmpty(reposTag.tag) ? "latest" : reposTag.tag;

                getClient().tagImageCmd(tag_image, reposTag.repos, commitTag).withForce().exec();

                addJenkinsAction(tagToken);

                if (getJobProperty().pushOnSuccess) {
                    Identifier identifier = Identifier.fromCompoundString(tagToken);

                    PushImageResultCallback resultCallback = new PushImageResultCallback() {
                        public void onNext(PushResponseItem item) {
                            printResponseItemToListener(listener, item);
                            super.onNext(item);
                        }
                    };
                    try {

                        PushImageCmd cmd = getClient().pushImageCmd(identifier);
                        final DockerRegistryEndpoint registry = dockerTemplate.getRegistry();
                        DockerCloud.setRegistryAuthentication(cmd, registry, Jenkins.getInstance());
                        cmd.exec(resultCallback).awaitSuccess();

                    } catch(DockerException ex) {

                        LOGGER.log(Level.SEVERE, "Exception pushing docker image. Check that the destination registry is running.", ex);
                        throw ex;
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not add additional tags", ex);
        }

        if (getJobProperty().cleanImages) {

            client.removeImageCmd(tag_image)
                    .withForce(true)
                    .exec();
        }

    }

    private String getAdditionalTag(TaskListener listener) {
        // Do a macro expansion on the addJenkinsAction token

        // Job property
        String tagToken = getJobProperty().additionalTag;

        // Do any macro expansions
        try {
            if (!Strings.isNullOrEmpty(tagToken))
                tagToken = TokenMacro.expandAll((AbstractBuild) theRun, listener, tagToken);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "can't expand macroses", e);
        }
        return tagToken;
    }

    private DockerJobProperty getJobProperty() {

        try {
            DockerJobProperty p = (DockerJobProperty) ((AbstractBuild) theRun).getProject().getProperty(DockerJobProperty.class);

            if (p != null)
                return p;
        } catch (Exception ex) {
            // Don't care.
        }
        // Safe default
        return new DockerJobProperty(false, null, false, true, null);
    }


    / **
     * Add a built on docker action.
     * /
    private void addJenkinsAction(String tag_image) throws IOException {
        theRun.addAction(new DockerBuildAction(getCloud().getDockerHost().getUri(), containerId, tag_image, dockerTemplate.remoteFsMapping));
        theRun.save();
    }
    */

}
