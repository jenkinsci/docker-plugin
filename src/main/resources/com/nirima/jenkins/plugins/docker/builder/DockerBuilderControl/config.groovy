package com.nirima.jenkins.plugins.docker.builder.DockerBuilderControl;

def f = namespace(lib.FormTagLib);

f.entry() {
    f.dropdownDescriptorSelector(title:_("Action to choose"), field:"option",
            descriptors: descriptor.getOptionList())
}