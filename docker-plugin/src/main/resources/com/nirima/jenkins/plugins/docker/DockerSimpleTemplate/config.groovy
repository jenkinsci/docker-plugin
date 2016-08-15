package com.nirima.jenkins.plugins.docker.DockerTemplateBase

def f = namespace(lib.FormTagLib);

f.entry(title: _("Docker Image"), field: "image") {
    f.textbox()
}

f.entry(title: _("DNS"), field: "dnsString") {
    f.textbox()
}

f.entry(title: ("Network"), field: "network") {
    f.textbox()
}

f.entry(title: _("Port bindings"), field: "bindPorts") {
    f.textbox()
}

f.entry(title: _("Bind all declared ports"), field: "bindAllPorts") {
    f.checkbox()
}

f.entry(title: _("Hostname"), field: "hostname") {
    f.textbox()
}

f.advanced(title: _("Advanced..."), align: "right") {
    f.entry(title: _("Docker Command"), field: "dockerCommand") {
        f.textbox()
    }

    f.entry(title: _("LXC Conf Options"), field: "lxcConfString") {
        f.textbox()
    }

    f.entry(title: _("Volumes"), field: "volumesString") {
        f.expandableTextbox()
    }

    f.entry(title: _("Volumes From"), field: "volumesFrom") {
        f.expandableTextbox()
    }

    f.entry(title: _("Environment"), field: "environmentsString") {
        f.expandableTextbox()
    }
	
    f.entry(title: _("Run container privileged"), field: "privileged") {
        f.checkbox()
    }

    f.entry(title: _("Allocate a pseudo-TTY"), field: "tty") {
        f.checkbox()
    }

    f.entry(title: _("MAC address"), field: "macAddress") {
        f.textbox()
    }

}
