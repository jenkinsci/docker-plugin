package io.jenkins.docker;

import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
public class DockerGlobalConfiguration extends GlobalConfiguration {
    public static DockerGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(DockerGlobalConfiguration.class);
    }

    private boolean randomizeCloudsOrder = false;

    public DockerGlobalConfiguration() {
        load();
    }

    public boolean getRandomizeCloudsOrder() {
        return randomizeCloudsOrder;
    }

    @DataBoundSetter
    public void setRandomizeCloudsOrder(boolean value) {
        randomizeCloudsOrder = value;
        save();
    }
}
