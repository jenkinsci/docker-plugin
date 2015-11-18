package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
public class DockerPluginConfiguration extends GlobalConfiguration {

    /**
     * Work around option.
     */
    public Boolean pullFix;

    /**
     * Returns this singleton instance.
     *
     * @return the singleton.
     */
    public static DockerPluginConfiguration get() {
        return GlobalConfiguration.all().get(DockerPluginConfiguration.class);
    }

    public DockerPluginConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return true;
    }

    public final boolean getPullFix() {
        if( pullFix == null )
            pullFix = true;
        return pullFix;
    }

    @DataBoundSetter
    public final void setPullFix(boolean pullFix) {
        this.pullFix = pullFix;
    }

}
