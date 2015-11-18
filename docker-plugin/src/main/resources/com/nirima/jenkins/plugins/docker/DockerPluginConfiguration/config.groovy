package com.nirima.jenkins.plugins.docker.DockerPluginConfiguration;

def f=namespace(lib.FormTagLib)

f.section(title:"Docker Plugin") {

    f.entry(title: _("Ignore Pull Return (Workaround)"), field: "pullFix") {
        f.checkbox()
    }

}
