jenkins-plugin-bluemix
=======================

Jenkins Cloud Plugin for Bluemix


The aim of the docker plugin is to be able to use a Bluemix Containers to
dynamically provision a slave, run a single build, then tear-down
that slave.

This repo is based on a fork of docker-plugin

More documentation available on the Jenkins wiki:

https://wiki.jenkins-ci.org/display/JENKINS/Docker+Plugin


It also uses as submodule a fork of docker-java


Because docker-java is a submodule, use --reqursive flag with git clone e.g.

git clone --recursive https://github.com/dunchych/jenkins-plugin-bluemix.git
