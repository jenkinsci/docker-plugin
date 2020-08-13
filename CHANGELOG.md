# Changelog

## Unreleased
A pre-release can be downloaded from https://ci.jenkins.io/job/Plugins/job/docker-plugin/job/master/
* Enhancement: container stop timeout now configurable [#732](https://github.com/jenkinsci/docker-plugin/issues/732)
* Fix possible resource leak [#786](https://github.com/jenkinsci/docker-plugin/issues/786)
* Enhancement: can now add/drop docker capabilites [#696](https://github.com/jenkinsci/docker-plugin/issues/696)
* Enhancement: can now customise "attach" connections [#790](https://github.com/jenkinsci/docker-plugin/issues/790)
* QA: Made SpotBugs mandatory [#793](https://github.com/jenkinsci/docker-plugin/issues/793), [#794](https://github.com/jenkinsci/docker-plugin/issues/794), [#798](https://github.com/jenkinsci/docker-plugin/issues/798)
* Improve errors when folks specify cloud names we don't have [#796](https://github.com/jenkinsci/docker-plugin/issues/796)
* Update terminology and reference non-deprecated image names [#802](https://github.com/jenkinsci/docker-plugin/issues/802), [#811](https://github.com/jenkinsci/docker-plugin/issues/811)
* Enhancement: templates can now specify cpu period and cpu quota [#795](https://github.com/jenkinsci/docker-plugin/issues/795)

## 1.2.0
_2020-04-02_
* Fix "ClassNotFoundException: com.github.dockerjava.netty.WebTarget" after upgrading to docker-java-api-plugin v 3.1.5 [#782](https://github.com/jenkinsci/docker-plugin/issues/782)
* Fix DockerBuilderPublisher to work within dockerNode() [#756](https://github.com/jenkinsci/docker-plugin/issues/756)
* Enhancement: Add "Security Options" field to docker templates [#744](https://github.com/jenkinsci/docker-plugin/issues/744)
* Enhancement: Add "User" field to docker templates [#729](https://github.com/jenkinsci/docker-plugin/issues/729)
* Enhancement: Improve docs for memory/instance constraints [#773](https://github.com/jenkinsci/docker-plugin/issues/773)
* Enhancement: Add "Extra Groups" field to docker templates [#777](https://github.com/jenkinsci/docker-plugin/issues/777)
* Moved plugin documentation from Jenkins wiki to GitHub [#774](https://github.com/jenkinsci/docker-plugin/issues/774)

:warning: Earlier versions require `docker-java-api-plugin` version 3.0.14.  Later versions require `docker-java-api-plugin` version 3.1.5

## 1.1.9
_2019-11-22_
* Fix "SSH service hadn't started after 0 seconds" [#751](https://github.com/jenkinsci/docker-plugin/issues/751)
* Fix SSH connection-timeout handling [JENKINS-59764](https://issues.jenkins-ci.org/browse/JENKINS-59764), [#755](https://github.com/jenkinsci/docker-plugin/pull/755)
* Fix "Null value not allowed" in DockerComputer.getEnvironment [#759](https://github.com/jenkinsci/docker-plugin/issues/759)
* Enhancement: Improve help text for docker-attach connection method [#761](https://github.com/jenkinsci/docker-plugin/pull/761)
* Enhancement: Support --pull and --no-cache in build/publish image build step [#758](https://github.com/jenkinsci/docker-plugin/pull/758)

## 1.1.8
_2019-09-24_
* Enhancement: Improve JCasC support [#726](https://github.com/jenkinsci/docker-plugin/pull/726)
* Enhancement: Allow extra docker labels on containers [#747](https://github.com/jenkinsci/docker-plugin/pull/747)
* Fix NodeProperties handling [#748](https://github.com/jenkinsci/docker-plugin/pull/748)
* Fix handling of "retries" field [#718](https://github.com/jenkinsci/docker-plugin/pull/718)
* Made it clearer that the plugin is under MIT license [#749](https://github.com/jenkinsci/docker-plugin/pull/749)

## 1.1.7
_2019-07-11_
* Security fixes [SECURITY-1010 and SECURITY-1400](https://jenkins.io/security/advisory/2019-07-11/).

## 1.1.6
_2019-02-07_
* Enhancement: Incrementals CI builds [#680](https://github.com/jenkinsci/docker-plugin/issues/680)
* Fix SSH retry count and wait config from being ignored [#677](https://github.com/jenkinsci/docker-plugin/issues/677), [#717](https://github.com/jenkinsci/docker-plugin/issues/717)
* Enhancement: Make it easier to run dockerNode [#679](https://github.com/jenkinsci/docker-plugin/issues/679)
* Enhancement: Add devices flag support [#685](https://github.com/jenkinsci/docker-plugin/issues/685), [#686](https://github.com/jenkinsci/docker-plugin/issues/686)
* Convert DockerComputerConnector$1 to non anymous class [JENKINS-53785](https://issues.jenkins-ci.org/browse/JENKINS-53785), [#688](https://github.com/jenkinsci/docker-plugin/issues/688)
* Fix DockerMultiplexedInputStream [#693](https://github.com/jenkinsci/docker-plugin/issues/693)
* Fix ugly exception in log [JENKINS-48460](https://issues.jenkins-ci.org/browse/JENKINS-48460), [#700](https://github.com/jenkinsci/docker-plugin/issues/700)
* Fix ugly exception when removing a container that was already being removed [#702](https://github.com/jenkinsci/docker-plugin/issues/702)
* Enhancement: Cloud/Template help improvements [#711](https://github.com/jenkinsci/docker-plugin/issues/711)

## 1.1.5
_2018-08-08_
* :warning: Docker container labels now follow [docker recommendations](https://docs.docker.com/config/labels-custom-metadata/) [#660](https://github.com/jenkinsci/docker-plugin/pull/660)
* Fix JAVA_OPT env var when using JNLP [#642](https://github.com/jenkinsci/docker-plugin/issues/642)
* Fix ugly NPE in log [#603](https://github.com/jenkinsci/docker-plugin/issues/603)
* Enhancement: Shared memory size [#467](https://github.com/jenkinsci/docker-plugin/issues/467)
* Enhancement: JNLP custom entry points [#654](https://github.com/jenkinsci/docker-plugin/issues/654), fixes [#635](https://github.com/jenkinsci/docker-plugin/issues/635)
* Enhancement: Docker container/node garbage collection [#658](https://github.com/jenkinsci/docker-plugin/pull/658)
* Enhancement: Robustness and configurability enhancements [#644](https://github.com/jenkinsci/docker-plugin/issues/644), [#651](https://github.com/jenkinsci/docker-plugin/issues/651)

## 1.1.4
_2018-04-19_
* Automatically avoid using broken clouds/templates [#626](https://github.com/jenkinsci/docker-plugin/issues/626)
* Improved "read timeout" handling and added template "pull timeout" [#624](https://github.com/jenkinsci/docker-plugin/issues/624)
* Fix NPE when there are no templates in a cloud [#603](https://github.com/jenkinsci/docker-plugin/issues/603)
* Improved cleanup of defunct containers when provisioning fails [#630](https://github.com/jenkinsci/docker-plugin/pull/630)
* Fix but whereby retention "idleMinutes" can be zero when upgrading from earlier releases. [#623](https://github.com/jenkinsci/docker-plugin/pull/623)
* Prevent over-provisioning [#622](https://github.com/jenkinsci/docker-plugin/pull/622)
* Online help improvements.
* :construction: More [JENKINS-48050](https://issues.jenkins-ci.org/browse/JENKINS-48050) enhancements [#636](https://github.com/jenkinsci/docker-plugin/pull/636)

## 1.1.3
_2018-02-13_
* :warning: Container cap now only counts containers our Jenkins started.  [#616](https://github.com/jenkinsci/docker-plugin/pull/616)
* Fix test-connection fd leak [#615](https://github.com/jenkinsci/docker-plugin/issues/615)
* :construction: Added docker cloud "read timeout" [#610](https://github.com/jenkinsci/docker-plugin/pull/610)
* Improved JNLP support [#596](https://github.com/jenkinsci/docker-plugin/pull/596)
* Improved SSH support [#598](https://github.com/jenkinsci/docker-plugin/pull/598)

## 1.1.2
_2017-12-15_
* Attach DockerBuildAction to the build to document container used to run the build
* workaround inconsistent delays provisioning a new node when a job waits in queue.

## 1.1.1
_2017-12-11_
Regression fix release
* Fix SSH connector with standalone swarm
* Restore multi-line control for container setting
* fix configuration conversion from legacy DockerCloudRetentionStrategy 

## 1.1
_2017-12-05_
* :warning: Require Jenkins 2.60+ and Java 8
* :construction: Introduce experimental Pipeline support with `dockerNode` [JENKINS-48050](https://issues.jenkins-ci.org/browse/JENKINS-48050)
* Huge improvement in "attached" launcher performances
* reviewed SSH external IP detection
* introduce support for SSH host key verification
* added support for variables in tags and Dockerfile directory
* removed "mappedFsWorkspace" option, which only make sense for a local docker host.
* refactoring
* fix `-tunnel` option for JNLP agents
* fix UI data-binding issues

## 1.0.4
_2017-10-27_
* fix support for binded ports
* fix SSH command Prefix / Suffix
* fix JNLP agent provisionning
* disable Matrix-autorisation node property [JENKINS-47697](https://issues.jenkins-ci.org/browse/JENKINS-47697)

## 1.0.3
_2017-10-25_
* fix configuration lost when upgrading from 0.x to 1.0.2

## 1.0.2
_2017-10-20_
* fix credential management to access a private docker registry
* log in debug diagnostic information on created container
* re-implemented UI for SSH connector with explicit SSH key strategies
* use configured user for JNLP launcher
* wait for ssh service to be up before trying to connect
* refactored launchers for extensibility and pipeline compatibility (reconnect agent after restart) 

## 1.0.1
_2017-10-17_
* upgrade docker-java API client to 3.0.14
* fix credential management to access a private docker registry
* fix JNLP launcher for master with required authentication
* option to disable SSH key injection (backward compatibility)

## 1.0.0
_2017-10-16_
* fix missuse of obsolete serverUrl
* removed some obsolete code
* fix serialization issue with DockerBuildPublisher on a remote agent
* implemented credentials migrattion to docker-commons
* minor UI fixes
* fix registry authentication (username/password)

## 0.18.0
_2017-10-11_
* Token Macro is actually a required plugin dependency
* Template sections in cloud configuration is now collapsible
* Fix a regression in SSH launcher
* Fix swarm standalone pull status detection
* Use non infinite default timeout

## 0.17.0
_2017-10-09_
* Move to [docker-java](http://wiki.jenkins-ci.org/display/JENKINS/Docker+Java+API+Plugin) 3.0.13
* Adopted [docker-commons](https://wiki.jenkins.io/display/JENKINS/Docker+Commons+Plugin) for docker API and registry credentials
* Refactored computer launcher for more flexibility
* SSH launcher now inject dedicated ssh key pair
* introduce experimental interactive launcher

## 0.16.1,2
_2016-09-13_
* Move to docker-java 3.x (3.0.6 + fixes for SDC/Triton)
* Re-instate setting API version as some versions of docker break compatibility
* Allow setting of registry credentials (build, push, pull)
* Documentation clarifications

## 0.16.0
_2015-11-26_
* Workflow support for build steps (publish, start/stop containers)
* Enable the JNLP agent support (Experimental). 
* Add a credential type to allow TLS connections.
* Work-around for pull status failures

## 0.15.0
_2015-09-27_
* Provide 100 as default capacity
* Remove API version field from user configuration
* Added build variables `DOCKER_CONTAINER_ID`, `JENKINS_CLOUD_ID` and `DOCKER_HOST` that allows creating simple `--volumes-from` bindings for additionally run containers
* Small code clean-up

## 0.14.0
_2015-09-20_
* Require 1.609.3 [#328](https://github.com/jenkinsci/docker-plugin/pull/328)
* Fixed "Environment" variables data binding
* Update docker-java to 2.1.1. Fixes JENKINS-30422
* Minimise retry delay for ssh launcher to 2 seconds.

## 0.13.0
_2015-08-31_
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
_2015-08-18_
* Fix NPE in Builder when build fails

## 0.12.0
_2015-08-18_
* Human readable console output for docker Builder
* Fix UI checkbox for bindAllPorts

## 0.11.0
_2015-08-06_
* Implement pull strategy
* UI don't use String for container capacity, make 100 as default
* Fix data migration update

## 0.10.2
_2015-07-22_
* Fix UI dropdown selector (show saved value right).

## 0.10.1
_2015-07-21_
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
_2015-07-06_
* Unbunble launchers
* Improve provisioning
* Change '@' delimiter in agent name to '-'
* Fix not removed "suspended" agents introduced in 0.9.4

## 0.9.4
_2015-07-03_
* Fixes termination errors. Sync OnceRetentionStrategy implementation.  

## 0.9.3
_2015-06-14_
* Hide Docker strategies for non-docker agents.

## 0.9.2
_2015-05-30_
* Don't use deprecated(?) api. Fixes not applied container configuration options. 

## 0.9.1
_2015-05-30_
* Temp fix: VolumeFrom now works with the first entry (multiple will work after upstream fix in docker-java library)

## 0.9.0
_2015-05-27_
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
_2015-04-26_
* More help files
* Fix executor value validation

## 0.9.0-beta2
_2015-04-24_
* Handle exception inspecting newly created container
* Added experimental feature for choosing retention strategies and number of executors
* Allow configure agent Mode: exclusive/inclusive
* Temp fix for tagging. Fixes container stop.
* More help files
* DockerJobProperty optional in job configuration
* Fix connection test. Print error instead broken page
* Use host IP from container binding

## 0.9-beta1
_2015-03-19_
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
* Wait for SSH port to be available on docker agent
* Be graceful on stop if container has already stopped

## 0.8
_2014-10-03_
* Expand token macros when running containers
* Use a standardized “one-shot” cloud retention strategy
* Use identifier to get image by tag
* Add port bindings capability
* Added mapped remote filesystem support for workspace browsing
* Adding support for using lxc conf options

## 0.7
_2014-07-22_
* Feature to delete images from repository when jenkins culls the job
* Fixed #64 - storing of cloudName and templateId variables
* Add timeout for a agent that gets provisioned but then has no work
* Add a new feature that allows you to add a build step of constructing a docker image from a Dockerfile, and optionally push that image to a registry
* Added 'volumes-from' functionality
* Pull the image if we do not find it
* Proper parsing of empty dnsHosts string
* When the SSH connection fails, back off and retry.

# 0.6.2
_2014-06-18_
* Allow configuration of an instance cap
* Allow configuration of image hostname

# 0.6.1
_2014-06-16_
* Fix for DockerTemplate volumes param

# 0.6
_2014-06-14_
* :warning: Docker 1.0 has been released, but has non-backwards compatible changes. Upgrade your hosts to docker >= 1.0.0.
* Restore 1.6 compat, Docker 1.0 compat (jDocker 1.4)
* volumes parameter in template
* wiki link in readme

## 0.3.4
_2014-04-09_
* Various fixes; jDocker to released version in maven central.

## 0.3
* Change client library to jDocker
* Management UI to list running containers and images, and to stop running ones.

## 0.2
* Various bugfixes

## 0.1
* Initial release. Probably many bugs!
