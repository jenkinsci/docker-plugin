package io.jenkins.docker.pipeline

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.CheckoutScript
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript2
import org.jenkinsci.plugins.workflow.cps.CpsScript

class DockerAgentScript extends DeclarativeAgentScript2<DockerAgent> {

    DockerAgentScript(CpsScript s, DockerAgent a) {
        super(s, a)
    }

    @Override
    void run(Closure body) {
        try {
            script.dockerNode(describable.asArgs) {
                CheckoutScript.doCheckout2(script, describable, null, body)
            }
        } catch (Exception e) {
            script.getProperty("currentBuild").result = Utils.getResultFromException(e)
            throw e
        }
    }

}
