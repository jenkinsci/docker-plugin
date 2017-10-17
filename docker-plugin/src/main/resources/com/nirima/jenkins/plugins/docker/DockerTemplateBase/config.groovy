package com.nirima.jenkins.plugins.docker.DockerTemplateBase

def f = namespace(lib.FormTagLib);
def c = namespace(lib.CredentialsTagLib);

f.entry(title: _("Docker Image"), field: "image") {
    f.textbox()
}

f.advanced(title: _("Registry Credentials")) {
    f.entry(title: _("Credentials"), field: "pullCredentialsId") {
        c.select()
    }
}

f.advanced(title: _("Container settings"), align: "left") {
    f.entry(title: _("Docker Command"), field: "dockerCommand") {
        f.textbox()
    }

    f.entry(title: _("LXC Conf Options"), field: "lxcConfString") {
        f.textbox()
    }

    f.entry(title: _("Hostname"), field: "hostname") {
        f.textbox()
    }

    f.entry(title: _("DNS"), field: "dnsString") {
        f.textbox()
    }
    
    f.entry(title: ("Network"), field: "network") {
        f.textbox()
    }

    f.entry(title: _("Volumes"), field: "volumesString") {
        f.expandableTextbox()
    }

    f.entry(title: _("Volumes From"), field: "volumesFromString") {
        f.expandableTextbox()
    }

    f.entry(title: _("Environment"), field: "environmentsString") {
        f.expandableTextbox()
    }

    f.entry(title: _("Port bindings"), field: "bindPorts") {
        f.textbox()
    }

    f.entry(title: _("Bind all declared ports"), field: "bindAllPorts") {
        f.checkbox()
    }

    f.entry(title: _("Memory Limit in MB"), field: "memoryLimit") {
        f.number(name: "memoryLimit", clazz: "positive-number", min: "4", step: "1")
    }
		
	f.entry(title: _("Swap Memory Limit in MB"), field: "memorySwap") {
        f.number(name: "memorySwap")
    }
	
    f.entry(title: _("CPU Shares"), field: "cpuShares") {
        f.number(name: "cpuShares", clazz: "positive-number", min: "0", step: "1")
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

    f.entry(title: _("Extra Hosts"), field: "extraHostsString") {
        f.expandableTextbox()
    }
}
