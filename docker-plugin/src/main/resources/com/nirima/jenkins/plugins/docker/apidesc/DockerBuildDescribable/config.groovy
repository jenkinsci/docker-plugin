package com.nirima.jenkins.plugins.docker.apidesc.DockerBuildDescribable

def f = namespace(lib.FormTagLib);

f.entry(title: _("Quite"), field: "q") {
    f.checkbox()
}

f.entry(title: _("No cache"), field: "nocache"){
    f.checkbox()
}

f.entry(title: _("Pull"), field: "pull") {
    f.checkbox()
}

f.entry(title: _("Remove"), field: "rm"){
    f.checkbox(default: "true")
}
