package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;
import java.util.List;

@Extension
public class DockerPluginConfiguration extends GlobalConfiguration {

    /**
     * Work around option.
     */
    private Boolean pullFix;

    /**
     * List of registries
     */
    private List<DockerRegistry> registryList = Collections.emptyList();

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
        req.bindJSON(this,json);
        return true;
    }

    public final boolean getPullFix() {
        if( pullFix == null )
            pullFix = true;
        return pullFix;
    }

    public DockerRegistry getRegistryByName(String registryName) {
        for(DockerRegistry registry : registryList) {
            if( registry.registry.equalsIgnoreCase(registryName))
                return registry;
        }
        // Not found
        return null;
    }


    public final void setPullFix(boolean pullFix) {
        this.pullFix = pullFix;
        save();
    }

    public void setRegistryList(List<DockerRegistry> registryList) {
        this.registryList = registryList;
        save();
    }

    public List<DockerRegistry> getRegistryList() {
        return registryList;
    }
}
