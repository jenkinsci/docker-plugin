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
public interface DockerContainerLabelKeys {

    
    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link DockerTemplateBase#getJenkinsInstanceIdForContainerLabel()}, so that we
     * can recognize our own containers later.
     */
    static final String JENKINS_INSTANCE_ID = "JenkinsId";
    
    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link Jenkins#getRootUrl()}, so that we
     * can recognize our own containers later.
     */
    static final String JENKINS_URL = "JenkinsServerUrl";
    
    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link #getImage()}, so that we
     * can recognize our own containers later.
     */
    static final String CONTAINER_IMAGE = "JenkinsContainerImage";
    
    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link Node#getNodeName()}, so that we
     * can recognize our own containers later.
     */
    @Restricted(NoExternalUse.class) 
    static final String NODE_NAME = "JenkinsNodeName";
    
    /**
     * Name of the Docker "label" that we'll put into every container we start,
     * setting its value to our {@link #getName()}, so that we
     * can recognize our own containers later.
     */
    @Restricted(NoExternalUse.class) 
    static final String TEMPLATE_NAME = "JenkinsTemplateName";
    
}
