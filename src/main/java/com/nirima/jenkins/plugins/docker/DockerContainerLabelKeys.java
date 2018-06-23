package com.nirima.jenkins.plugins.docker;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.model.Node;
import jenkins.model.Jenkins;

/**
 * This constant interface defines the identifiers of label keys used at containers, which
 * are generated using this plugin.
 * 
 * @author eaglerainbow
 *
 */
public final class DockerContainerLabelKeys {

    /**
     * As requested by https://docs.docker.com/config/labels-custom-metadata/, keys of labels used
     * in docker shall be prefixed by a namespace using the reverse DNS notation.
     * All label keys of this plugin shall use this namespace as a prefix. 
     * 
     * Label keys defined in this interface already have this namespace prefixed.
     */
    private static final String PLUGIN_LABEL_KEY_NAMESPACE = DockerContainerLabelKeys.class.getPackage().getName()+".";

    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link DockerTemplateBase#getJenkinsInstanceIdForContainerLabel()}, so that we
     * can recognize our own containers later.
     */
    static final String JENKINS_INSTANCE_ID = PLUGIN_LABEL_KEY_NAMESPACE + "JenkinsId";

    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link Jenkins#getRootUrl()}, so that we
     * can recognize our own containers later.
     */
    static final String JENKINS_URL = PLUGIN_LABEL_KEY_NAMESPACE + "JenkinsServerUrl";

    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to the value of {@link DockerTemplateBase#getImage()}, so that we
     * can recognize our own containers later.
     */
    static final String CONTAINER_IMAGE = PLUGIN_LABEL_KEY_NAMESPACE + "JenkinsContainerImage";

    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link Node#getNodeName()}, so that we
     * can recognize our own containers later.
     */
    @Restricted(NoExternalUse.class) 
    static final String NODE_NAME = PLUGIN_LABEL_KEY_NAMESPACE + "JenkinsNodeName";

    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to the value of {@link DockerTemplate#getName()}, so that we
     * can recognize our own containers later.
     */
    @Restricted(NoExternalUse.class) 
    static final String TEMPLATE_NAME = PLUGIN_LABEL_KEY_NAMESPACE + "JenkinsTemplateName";

    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link Node#isRemoveVolumes()}, so that we
     * can recognize our own containers later.
     */
    @Restricted(NoExternalUse.class)
    static final String REMOVE_VOLUMES = PLUGIN_LABEL_KEY_NAMESPACE + "JenkinsRemoveVolumes";

}
