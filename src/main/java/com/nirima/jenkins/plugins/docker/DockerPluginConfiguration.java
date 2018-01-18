package com.nirima.jenkins.plugins.docker;

import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;
import java.util.List;

@Deprecated
public class DockerPluginConfiguration extends GlobalConfiguration {

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

    public DockerRegistry getRegistryByName(String registryName) {
        for(DockerRegistry registry : registryList) {
            if( registry.registry.equalsIgnoreCase(registryName))
                return registry;
        }
        // Not found
        return null;
    }



    public void setRegistryList(List<DockerRegistry> registryList) {
        this.registryList = registryList;
        save();
    }

    public List<DockerRegistry> getRegistryList() {
        return registryList;
    }



    // --- obsolete code goes here. kept for backward compatibility

    public final boolean getPullFix() {
        return false;
    }

    public final void setPullFix(boolean pullFix) {
    }

}
