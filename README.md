# Docker plugin for Jenkins

Note: This plugin is officially ["up for adoption"](https://www.jenkins.io/doc/developer/plugin-governance/adopt-a-plugin/#faq).
It would benefit from having a new maintainer who uses it "for real work"
and is thus able to test things "for real" before release
instead of relying purely on the unit tests.

## Overview

This plugin allows containers to be dynamically provisioned as Jenkins nodes using Docker.
It is a Jenkins Cloud plugin for Docker.

The aim of this docker plugin is to be able to use a
[Docker](https://docs.docker.com/)
host to dynamically provision a docker container as a Jenkins agent node,
let that run a single build,
then tear-down that node,
without the build process (or Jenkins job definition)
requiring any awareness of docker.

The Jenkins administrator configures Jenkins with
knowledge of one or more docker hosts (or swarms),
knowledge of one or more "templates"
(which describe
the labels/tags that this template provides,
the docker image,
how to start it,
etc)
and Jenkins can then run docker containers to provide Jenkins (agent) Nodes on which Jenkins can run builds.

## See also
* [Software licence](LICENSE)
* Support and [contribution guide](CONTRIBUTING.md)
* [Changelog](CHANGELOG.md)

----

**Note:** There is _more than one docker plugin_ for Jenkins.
While this can be confusing for end-users, it's even more confusing when end users report bugs in the wrong place.
e.g. if you are using Jenkins
[pipeline / workflow / Jenkinsfile](https://jenkins.io/doc/book/pipeline/docker/)
builds with code including terms like
`docker.withDockerRegistry`
or
`docker.image`
etc then you're using the
[`docker-workflow`](https://plugins.jenkins.io/docker-workflow)
plugin and should go to its repository instead of this one.

----

**Note:** This plugin does not use the OS's native docker client;
it uses [docker-java](https://github.com/docker-java/docker-java).
You do not need to install a docker client on Jenkins or on your agents to use this plugin.

----

**Note:** This plugin does not _provide_ a Docker daemon; it allows Jenkins to _use_ a docker daemon.
i.e. Once you've installed docker somewhere, this plugin will allow Jenkins to make use of it.

----

## Setup

A quick setup is :

1. get a docker environment running
1. follow the instructions for creating a docker image that can be used
as a Jenkins Agent
_or_ use one of the pre-built images
e.g. [jenkins/inbound-agent](https://hub.docker.com/r/jenkins/inbound-agent/)

### Docker Environment

Follow the installation steps on [the docker website](https://docs.docker.com/).

If your Jenkins instance is not on the same OS as the docker install,
you will need to open the docker TCP port so that Jenkins can communicate with the docker daemon.
This can be achieved by editing the docker config file and setting (for example)

```sh
DOCKER_OPTS="-H tcp://0.0.0.0:2376 -H unix:///var/run/docker.sock"
```

The docker configuration file location will depend your system, but it
is likely to be
`/etc/init/docker.conf`
,
`/etc/default/docker`
or
`/etc/default/docker.io`.

### Multiple Docker Hosts

If you want to use more than just one physical node to run containers,
you can use
[Docker Swarm Standalone](https://github.com/docker/swarm)
or you can define more than one docker "cloud".
The docker engine swarm mode API is not supported
(at present; enhancement contributions would be welcomed).

To use the standalone swarm,
follow docker swarm standalone instructions and configure Jenkins with the swarm's API endpoint.

### Jenkins Configuration

Docker plugin is a "Cloud" implementation.
You'll need to edit Jenkins system configuration
(Jenkins -> Manage -> System configuration)
and add a new Cloud of type "Docker".

![](docs/images/add-new-docker-cloud.png)

Configure Docker (or Swarm standalone) API URL with required credentials.
The test button lets you check the connection.

Then configure Agent templates,
assigning them labels that you can use so your jobs select the appropriate template,
and set the docker container to be run with whatever container settings you require.

### Running self-made local Docker images

By default the Jenkins Docker plugin will try to download ('pull') the latest version of the image. This will fail with custom images that are not on Docker Hub. You will see logs like:

> com.nirima.jenkins.plugins.docker.DockerTemplate pullImage    
> Pulling image '..'. This may take awhile...

> com.github.dockerjava.api.exception.NotFoundException: Status 404: {"message":"pull access denied for .., repository does not exist or may require 'docker login': denied: requested access to the resource is denied"}

On the _Docker Agent template_ set **Pull strategy** to **Never pull**, Name, Docker Image, Remote File System Root, and checkbox Enabled.

If you have docker only on your local Jenkins machine, set on _Docker Cloud details_ the textbox **Docker Host URI** to `unix:///var/run/docker.sock`, and checkbox Enabled.

### Creating a docker image

You need a docker image that can be used to run Jenkins agent runtime.
Depending on the launch method you select, there's some prerequisites
for the Docker image to be used:

## Launch via SSH

-   [sshd](https://linux.die.net/man/8/sshd) server and a JDK installed.
    You can use
    [jenkins/ssh-agent](https://hub.docker.com/r/jenkins/ssh-agent/)
    as a basis for a custom image.
-   a SSH key (based on the unique Jenkins instance identity) can be
    injected in container on startup, you don't need any credential set
    as long as you use standard openssl sshd.
    ![](docs/images/connect-with-ssh.png)
    When using the `jenkins/ssh-agent` Docker image, ensure that the user
    is set to `jenkins`.
    For backward compatibility *or* non-standard sshd packaged in your
    docker image, you also have option to provide manually configured
    ssh credentials
-   **Note:** If the docker container's host SSH key is not trusted by
    Jenkins (usually the case) then you'll need to set the SSH host key
    verification method to "non-verifying".

## Launch via JNLP

-   a JDK installed.
    You can use
    [jenkins/inbound-agent](https://hub.docker.com/r/jenkins/inbound-agent/)
    as a basis for a custom image.
-   Jenkins controller URL has to be reachable from container.
-   container will be configured automatically with agent's name and
    secret, so you don't need any special configuration of the container.

## Launch attached

-   a JDK installed.
    You can use
    [jenkins/agent](https://hub.docker.com/r/jenkins/agent/)
    as a basis for a custom image.

To create a custom image and bundle your favorite tools,
create a `Dockerfile` with the `FROM` to point to one of the
jenkins/\*-agent
reference images,
and install everything needed for your own usage, e.g.

```
FROM jenkins/inbound-agent
RUN apt-get update && apt-get install XXX
COPY your-favorite-tool-here
```

## Note on ENTRYPOINT

Avoid overriding the docker command, as the SSH Launcher relies on it.

You _can_ use an Entrypoint to run some side service inside your build agent container before the agent runtime starts and establish a connection
... but you MUST ensure your entrypoint eventually runs the passed command:

    exec "$@"

## Further information

More information can be obtained from the online help built into the Jenkins web UI.
Most configurable fields have explanatory text.
This,
combined with knowledge of [docker itself](https://docs.docker.com/),
should answer most questions.

## Configuration as code

Jenkins and the docker-plugin can be configured using Groovy code
and/or using the [JCasC plugin](https://plugins.jenkins.io/configuration-as-code/).

If you're unsure which method to use, use JCasC.

### JCasC plugin

Install the [configuration-as-code plugin](https://plugins.jenkins.io/configuration-as-code/) and follow [its example](https://github.com/jenkinsci/configuration-as-code-plugin/tree/master/demos/docker).

As another alternative, a Docker daemon can listen to requests from remote hosts by following the [Docker documentation](https://docs.docker.com/config/daemon/remote-access/).
The following configuration as code example creates a cloud named "my-docker-cloud" that uses the docker daemon at port 2375 on dockerhost.example.com to run up to 3 containerized agents at a time.
Agents run the [Jenkins Alpine inbound agent container image](https://hub.docker.com/r/jenkins/inbound-agent) with Java 21.
Thay use an inbound connection and run as the user ID 1000 with the home directory "/home/jenkins/agent".

```yaml
jenkins:
  clouds:
  - docker:
      containerCap: 3
      dockerApi:
        connectTimeout: 23
        dockerHost:
          uri: "tcp://dockerhost.example.com:2375"
        readTimeout: 43
      errorDuration: 313
      name: "my-docker-cloud"
      templates:
      - connector:
          jnlp:
            jenkinsUrl: "https://jenkins.example.com/"
            user: "1000"
        dockerTemplateBase:
          cpuPeriod: 0
          cpuQuota: 0
          image: "jenkins/inbound-agent:latest-alpine-jdk21"
        labelString: "alpine jdk21 alpine-jdk21 git-2.43"
        name: "alpine-jdk21"
        pullTimeout: 171
        remoteFs: "/home/jenkins/agent"
```

### Groovy script

For example, this
[configuration script](docs/attachments/docker-plugin-configuration-script.groovy)
could be run automatically upon
[Jenkins post-initialization](https://www.jenkins.io/doc/book/managing/groovy-hook-scripts/)
or through the
[Jenkins script console](https://www.jenkins.io/doc/book/managing/script-console/).
If run,
this script will configure the docker-plugin to look for a docker daemon running within the same OS as the Jenkins controller
(connecting to Docker service through `unix:///var/run/docker.sock`)
and with the containers connecting to Jenkins using the "attach" method.
