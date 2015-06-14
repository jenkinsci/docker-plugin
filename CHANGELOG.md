# Changelog


## Next version

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
