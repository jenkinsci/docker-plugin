package com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy

def f = namespace(lib.FormTagLib);

f.entry(title: "Idle timeout", field: "idleMinutes") {
    f.number(default: 10)
}
