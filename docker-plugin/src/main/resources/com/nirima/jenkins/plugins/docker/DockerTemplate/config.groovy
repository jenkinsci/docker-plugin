package com.nirima.jenkins.plugins.docker.DockerTemplate

import com.nirima.jenkins.plugins.docker.DockerTemplate
import com.nirima.jenkins.plugins.docker.utils.DockerFunctions

def f = namespace(lib.FormTagLib)
def st = namespace("jelly:stapler")

// when added to heteroList
if (instance == null) {
    instance = new DockerTemplate();
}

f.entry(title: _("Labels"), field: "labelString",
        help: "/descriptor/com.nirima.jenkins.plugins.docker.DockerSlave/help/labelString") {
    f.textbox()
}

f.property(field: "dockerTemplateBase")

f.entry(title: _("Instance Capacity"), field: "instanceCapStr") {
    f.textbox()
}

f.entry(title: _("Remote Filing System Root"), field: "remoteFs") {
    f.textbox()
}

f.slave_mode(name: "mode", node: instance)

f.advanced(title: _("Experimental Options"), align: "left") {
    f.dropdownList(name: "retentionStrategy", title: _("Availability"),
            help: "/descriptor/com.nirima.jenkins.plugins.docker.DockerTemplate/help/retentionStrategy") {
        DockerFunctions.dockerRetentionStrategyDescriptors.each { sd ->
            if (sd != null) {
                def prefix = sd.displayName.equals("Docker Once Retention Strategy") ? "" : "Experimental: "

                f.dropdownListBlock(value: sd.clazz.name, name: sd.displayName,
                        selected: instance.retentionStrategy == null ?
                                false : instance.retentionStrategy.descriptor.equals(sd),
                        title: prefix + sd.displayName) {
                    descriptor = sd
                    if (instance.retentionStrategy != null && instance.retentionStrategy.descriptor.equals(sd)) {
                        instance = instance.retentionStrategy
                    }
                    f.invisibleEntry() {
                        input(type: "hidden", name: "stapler-class", value: sd.clazz.name)
                    }
                    st.include(from: sd, page: sd.configPage, optional: "true")
                }
            }
        }
    }

    f.entry(title: _("# of executors"), field: "numExecutors") {
        f.number(default: "1")
    }
}

f.dropdownDescriptorSelector(field: "connector", title: _("Connect method"),
        descriptors: DockerFunctions.dockerComputerConnectorDescriptors)

f.entry(title: _("Remote FS Root Mapping"), field: "remoteFsMapping") {
    f.textbox()
}

f.entry(title: _("Remove volumes"), field: "removeVolumes") {
    f.checkbox()
}

f.entry(title: _("Pull strategy"), field: "pullStrategy") {
    f.enum() {
        text(my.description)
    }
}

f.descriptorList(title: _("Node Properties"), descriptors: descriptor.getNodePropertyDescriptors(), field: "nodeProperties") {
}
