# Changelog

## 1.0.4

* fix support for binded ports
* fix SSH command Prefix / Suffix
* fix JNLP agent provisionning
* disable Matrix-autorisation node property (JENKINS-47697)

## 1.0.3

* fix configuration lost when upgrading from 0.x to 1.0.2

## 1.0.2

* fix credential management to access a private docker registry
* log in debug diagnostic information on created container
* re-implemented UI for SSH connector with explicit SSH key strategies
* use configured user for JNLP launcher
* wait for ssh service to be up before trying to connect
* refactored launchers for extensibility and pipeline compatibility (reconnect slave after restart) 

## 1.0.1

* upgrade docker-java API client to 3.0.14
* fix credential management to access a private docker registry
* fix JNLP launcher for master with required authentication
* option to disable SSH key injection (backward compatibility)

## 1.0.0

* fix missuse of obsolete serverUrl
* removed some obsolete code
* fix serialization issue with DockerBuildPublisher on a remote agent
* implemented credentials migrattion to docker-commons
* minor UI fixes
* fix registry authentication (username/password)

## 0.18.0
* Token Macro is actually a required plugin dependency
* Template sections in cloud configuration is now collapsible
* Fix a regression in SSH launcher
* Fix swarm standalone pull status detection
* Use non infinite default timeout

## 0.17.0

* Move to [docker-java](http://wiki.jenkins-ci.org/display/JENKINS/Docker+Java+API+Plugin) 3.0.13
* Adopted [docker-commons](https://wiki.jenkins.io/display/JENKINS/Docker+Commons+Plugin) for docker API and registry credentials
* Refactored computer launcher for more flexibility
* SSH launcher now inject dedicated ssh key pair
* introduce experimental interactive launcher

## 0.16.1,2

* Move to docker-java 3.x (3.0.6 + fixes for SDC/Triton)
* Re-instate setting API version as some versions of docker break compatibility
* Allow setting of registry credentials (build, push, pull)
* Documentation clarifications

## 0.16.0

* Workflow support for build steps (publish, start/stop containers)
* Enable the JNLP slave support (Experimental). 
* Add a credential type to allow TLS connections.
* Work-around for pull status failures

## [0.15.0](https://github.com/jenkinsci/docker-plugin/issues?utf8=%E2%9C%93&q=milestone%3A0.15.0)

* Provide 100 as default capacity
* Remove API version field from user configuration
* Added build variables `DOCKER_CONTAINER_ID`, `JENKINS_CLOUD_ID` and `DOCKER_HOST` that allows creating simple `--volumes-from` bindings for additionally run containers
* Small code clean-up

## [0.14.0](https://github.com/jenkinsci/docker-plugin/issues?utf8=%E2%9C%93&q=milestone%3A0.14.0)

* Require 1.609.3 [#328](https://github.com/jenkinsci/docker-plugin/pull/328)
* Fixed "Environment" variables data binding
* Update docker-java to 2.1.1. Fixes JENKINS-30422
* Minimise retry delay for ssh launcher to 2 seconds.

## 0.13.0

* Small fix in logging
* Update to docker-java 2.1.0
* Progressive logging during build, push
* Print output into job
* restore connectionTimeout usage
* make timeout defaults as in jaxrs (library) 0 = infinite
* Remove wrong check for image name on push
* Pretty print push output in job log 
* Rework build/tag logic

## 0.12.1

* Fix NPE in Builder when build fails

## 0.12.0 

* Human readable console output for docker Builder
* Fix UI checkbox for bindAllPorts

## 0.11.0 

* Implement pull strategy
* UI don't use String for container capacity, make 100 as default
* Fix data migration update

## 0.10.2

* Fix UI dropdown selector (show saved value right).

## 0.10.1

* Set DockerOnceRetentionStrategy default timeout to 10 minutes. 
* Update docker-java library to 1.4.0
* Enhance help page #265
* Fix 0.8 config data loading
* Fix DockerBuilderPublisher
* Allow multiple tags in DockerBuilderPublisher
* Add removeVolumes option for Docker Template config.
* Add ExtraHosts template configuration
* Fix not shown availability drop-down
* Convert some UIs from jelly to groovy

## 0.10.0

* Unbunble launchers
* Improve provisioning
* Change '@' delimiter in slave name to '-'
* Fix not removed "suspended" slaves introduced in 0.9.4

## 0.9.4

* Fixes termination errors. Sync OnceRetentionStrategy implementation.  

## 0.9.3

* Hide Docker strategies for non-docker slaves.

## 0.9.2

* Don't use deprecated(?) api. Fixes not applied container configuration options. 

## 0.9.1

* Temp fix: VolumeFrom now works with the first entry (multiple will work after upstream fix in docker-java library)

## 0.9.0

* Fixed maven-release-plugin, shaded jar with sources now should be available
* Add MAC address configuration
* Filter image name from configuration more carefully
* Fix idleMinutes shadowing for DockerOnceRetentionStrategy
* Form validation for volume configuration
* Support multiple volumesFrom
* Fix volume binding and volume creation
* hide empty commit in action
* Show plugin url and description in Jenkins update center

## 0.9.0-rc1

* More help files
* Fix executor value validation

## 0.9.0-beta2

* Handle exception inspecting newly created container
* Added experimental feature for choosing retention strategies and number of executors
* Allow configure slave Mode: exclusive/inclusive
* Temp fix for tagging. Fixes container stop.
* More help files
* DockerJobProperty optional in job configuration
* Fix connection test. Print error instead broken page
* Use host IP from container binding

## 0.9-beta1

* java 1.7 required
* Support allocating a pseudo-TTY
* Support CPU and memory constraints
* Shade libraries to exclude jenkins classloading issues
* Don't run FlyweightTask on Docker container
* Fixed ignored total container if ami limit was zero
* Logging improvements for provisioning
* Fix checking if a image is present on the server
* Handle Queue with lock to
* Support 1.565.3 LTS
* Split lxc-conf on CSV instead of space
* Pass environment additions to docker container
* Сonfigurable SSH Launch timeout
* Migration to java-docker library
* Credentials support for docker connection
* Fixed a race that may cause commit and push to fail
* Wait for SSH port to be available on docker slave
* Be graceful on stop if container has already stopped

## 0.8

- Expand token macros when running containers
- Use a standardized “one-shot” cloud retention strategy
- Use identifier to get image by tag
- Add port bindings capability
- Added mapped remote filesystem support for workspace browsing
- Adding support for using lxc conf options

## 0.7

- Feature to delete images from repository when jenkins culls the job
- Fixed #64 - storing of cloudName and templateId variables
- Add timeout for a slave that gets provisioned but then has no work
- Add a new feature that allows you to add a build step of constructing a docker image from a Dockerfile, and optionally push that image to a registry
- Added 'volumes-from' functionality
- Pull the image if we do not find it
- Proper parsing of empty dnsHosts string
- When the SSH connection fails, back off and retry.

# 0.6.2
- Allow configuration of an instance cap
- Allow configuration of image hostname

# 0.6.1
- Fix for DockerTemplate volumes param

# 0.6
Docker 1.0 has been released, but has non-backwards compatible changes. Upgrade your hosts to docker >= 1.0.0.

- Restore 1.6 compat, Docker 1.0 compat (jDocker 1.4)
- volumes parameter in template
- wiki link in readme

## 0.3.4

- Various fixes; jDocker to released version in maven central.

## 0.3

- Change client library to jDocker
- Management UI to list running containers and images, and to stop running ones.

## 0.2

- Various bugfixes

## 0.1
- Initial release. Probably many bugs!
