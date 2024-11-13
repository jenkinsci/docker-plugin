package io.jenkins.docker;

import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;
import hudson.Extension;
import hudson.ExtensionList;

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
