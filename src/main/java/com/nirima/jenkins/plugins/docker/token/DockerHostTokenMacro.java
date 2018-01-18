package com.nirima.jenkins.plugins.docker.token;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.TaskListener;
import io.jenkins.docker.DockerTransientNode;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;

/**
 * Created by magnayn on 30/01/2014.
 */
@Extension(optional=true)
public class DockerHostTokenMacro extends DataBoundTokenMacro {
    @Override
    public String evaluate(AbstractBuild<?, ?> abstractBuild, TaskListener taskListener, String s) throws MacroEvaluationException, IOException, InterruptedException {
        Node node = abstractBuild.getBuiltOn();
        if( node instanceof DockerTransientNode) {
            return ((DockerTransientNode) node).getContainerId();
        }

        return null;
    }

    @Override
    public boolean acceptsMacroName(String macroName) {
        return macroName.equals("DOCKER_CONTAINERID");
    }
}
