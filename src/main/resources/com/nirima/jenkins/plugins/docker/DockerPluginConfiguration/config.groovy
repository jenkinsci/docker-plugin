package com.nirima.jenkins.plugins.docker.DockerPluginConfiguration;

def f=namespace(lib.FormTagLib)

f.section(title:"Docker Plugin") {

    f.entry(title: _("Ignore Pull Return (Workaround)"), field: "pullFix") {
        f.checkbox()
    }

    f.entry(title: _("Registry Credentials")) {
        f.repeatableHeteroProperty( field: "registryList", addCaption:"Add Registry", deleteCaption:"Remove Registry");
    }
}
