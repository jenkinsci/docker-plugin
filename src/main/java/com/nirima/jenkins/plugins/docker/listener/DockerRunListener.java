package com.nirima.jenkins.plugins.docker.listener;

import com.nirima.jenkins.plugins.docker.action.DockerBuildImageAction;
import com.nirima.jenkins.plugins.docker.publisher.DockerPublisherControl;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.listeners.RunListener;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listen for builds being deleted, and optionally clean up resources
 * (docker images) when this happens.
 *
 */
@Extension
public class DockerRunListener extends RunListener<Run<?,?>> {
    private static final Logger LOGGER = Logger.getLogger(DockerRunListener.class.getName());

    @Override
    public void onDeleted(Run<?, ?> run) {
        super.onDeleted(run);
        List<DockerBuildImageAction> actions = run.getActions(DockerBuildImageAction.class);

        for(DockerBuildImageAction action : actions) {
            if( action.cleanupWithJenkinsJobDelete ) {
                LOGGER.info("Attempting to clean up docker image for " + run);


                if( action.pushOnSuccess ) {

                    // TODO:

                    /*
                    DockerRegistryClient registryClient;

                    try {

                        Identifier identifier = Identifier.fromCompoundString(action.taggedId);

                        registryClient = DockerRegistryClient.builder()
                                .withUrl(identifier.repository.getURL())
                                .build();

                        registryClient.registryApi().deleteRepositoryTag("library",
                                identifier.repository.getPath(),
                                identifier.tag.orNull());



                    } catch (Exception ex) {

                        LOGGER.log(Level.WARNING, "Failed to clean up", ex);
                    }
                          */
                }
            }
        }

    }
}
