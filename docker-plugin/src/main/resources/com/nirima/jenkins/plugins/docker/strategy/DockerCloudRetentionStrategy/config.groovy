package com.nirima.jenkins.plugins.docker.strategy.DockerCloudRetentionStrategy

def f = namespace(lib.FormTagLib);

f.entry(title: "Idle timeout", field: "idleMinutes") {
    f.number(default: 0)
}
