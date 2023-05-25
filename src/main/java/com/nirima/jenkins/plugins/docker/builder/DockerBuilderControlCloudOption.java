package com.nirima.jenkins.plugins.docker.builder;

import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.Run;
import java.util.Optional;
import jenkins.model.Jenkins;

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

    protected @NonNull DockerCloud getCloud(Run<?, ?> build, Launcher launcher) {
        // Did we specify?
        if (!Strings.isNullOrEmpty(cloudName)) {
            DockerCloud specifiedCloud = (DockerCloud) Jenkins.get().getCloud(cloudName);
            if (specifiedCloud == null) {
                throw new IllegalStateException("Could not find a cloud named " + cloudName);
            }
            return specifiedCloud;
        }

        // Otherwise default to where we ran
        Optional<DockerCloud> cloud = JenkinsUtils.getCloudThatWeBuiltOn(build, launcher);

        if (cloud.isEmpty()) {
            throw new IllegalStateException("Cannot list cloud for docker action");
        }

        return cloud.get();
    }
}
