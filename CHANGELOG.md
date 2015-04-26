# Changelog


## Next dev version

*

## 0.9.0-rc1

* Handle exception inspecting newly created container
* Added experimental feature for choosing retention strategies and number of executors
* Temp fix for tagging. Fixes container stop.
* More help files
* DockerJobProperty optional in job configuration
* Fix connection test. Print error instead broken page
* Use host IP from container binding
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
