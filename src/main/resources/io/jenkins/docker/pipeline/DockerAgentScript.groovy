package io.jenkins.docker.pipeline

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.CheckoutScript
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript
import org.jenkinsci.plugins.workflow.cps.CpsScript

class DockerAgentScript extends DeclarativeAgentScript<DockerAgent> {

    DockerAgentScript(CpsScript s, DockerAgent a) {
        super(s, a)
    }

    @Override
    Closure run(Closure body) {
        return {
            try {
                script.dockerNode(describable.asArgs) {
                    CheckoutScript.doCheckout(script, describable, null, body).call()
                }
            } catch (Exception e) {
                script.getProperty("currentBuild").result = Utils.getResultFromException(e)
                throw e
            }
        }
    }

}
