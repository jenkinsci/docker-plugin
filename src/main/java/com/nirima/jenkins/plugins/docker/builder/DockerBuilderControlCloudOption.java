package com.nirima.jenkins.plugins.docker.builder;

import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.Launcher;
import hudson.model.Run;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.util.Optional;


/**
 * Abstract class for cloud based container "control" actions
 *
 * @author magnayn
 */
public abstract class DockerBuilderControlCloudOption extends DockerBuilderControlOption {
    public final String cloudName;

    protected DockerBuilderControlCloudOption(String cloudName) {
        this.cloudName = cloudName;
    }
    
    public String getCloudName() {
        return cloudName;
    }

    protected @Nonnull DockerCloud getCloud(Run<?, ?> build, Launcher launcher) {

        // Did we specify?
        if (!Strings.isNullOrEmpty(cloudName)) {
            DockerCloud specifiedCloud = (DockerCloud)Jenkins.getInstance().getCloud(cloudName);
            if( specifiedCloud == null )
                throw new IllegalStateException("Could not find a cloud named " + cloudName);
            return specifiedCloud;
        }

        // Otherwise default to where we ran
        Optional<DockerCloud> cloud = JenkinsUtils.getCloudThatWeBuiltOn(build, launcher);

        if (!cloud.isPresent()) {
            throw new IllegalStateException("Cannot list cloud for docker action");
        }

        return cloud.get();
    }

}
