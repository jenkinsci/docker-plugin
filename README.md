docker-plugin
=============

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/docker-plugin/master)](https://ci.jenkins.io/job/Plugins/job/docker-plugin/job/master/)

Jenkins Cloud Plugin for Docker

The aim of the docker plugin is to be able to use a docker host to
dynamically provision a slave, run a single build, then tear-down
that slave.

The Jenkins administrator configures Jenkins with
knowledge of one or more docker hosts (or swarms),
knowledge of one or more "templates"
(which describe
the labels/tags that this template provides,
the docker image,
how to start it,
etc)
and Jenkins can then run docker containers to provide Jenkins (slave) Nodes on which Jenkins can run builds.

More documentation available on the Jenkins wiki: https://plugins.jenkins.io/docker-plugin

Note: There is more than one docker plugin for Jenkins and if you are using Jenkins [pipeline / workflow / Jenkinsfile](https://jenkins.io/doc/book/pipeline/docker/) builds with code including terms like `docker.withDockerRegistry` or `docker.image` etc as provided by the [`docker-workflow`](https://plugins.jenkins.io/docker-workflow) plugin then _this is not the repository you are looking for_.
