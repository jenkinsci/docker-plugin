# Docker plugin for Jenkins

This plugin allows containers to be dynamically provisioned as Jenkins agents using Docker.
It is a Jenkins Cloud plugin for Docker.

This plugin allows a [Docker](https://docs.docker.com/) host to dynamically provision a container as a Jenkins agent node,
lets that run a single build,
then removes that node,
without the build process or Jenkins job definition
requiring any awareness of Docker.

The Jenkins administrator configures Jenkins with
Docker hosts,
one or more "templates"
that describe
the labels/tags provided by the template,
the Docker image,
how to start it,
etc)
and Jenkins can creates agents on-demand using those Docker containers.

## See also
* [Changelog](https://plugins.jenkins.io/docker-plugin/releases/) and [archive](https://github.com/jenkinsci/docker-plugin/blob/docker-plugin-1.3.0/CHANGELOG.md)
* Support and [contribution guide](CONTRIBUTING.md)
* [Software license](LICENSE)

----

**Note:** There is _more than one Docker plugin_ for Jenkins.
While this can be confusing for end-users, it's even more confusing when end users report bugs in the wrong place.
For example, if you are using Jenkins
[Pipeline](https://jenkins.io/doc/book/pipeline/docker/)
builds with code including terms like
`docker.withDockerRegistry`
or
`docker.image`
then you're using the
[`docker-workflow`](https://plugins.jenkins.io/docker-workflow)
plugin and should go to its repository instead of this one.

----

**Note:** This plugin does not use the OS's native Docker client;
it uses [docker-java](https://github.com/docker-java/docker-java).
You do not need to install a Docker client on Jenkins or on your agents to use this plugin.

----

**Note:** This plugin does not _provide_ a Docker daemon; it allows Jenkins to _use_ a Docker daemon.
i.e. Once you've installed Docker somewhere, this plugin will allow Jenkins to make use of it.

----

## Setup

A quick setup is :

1. get a Docker environment running
1. follow the instructions for creating a Docker image that can be used
as a Jenkins Agent
_or_ use one of the pre-built images
like the [jenkins/inbound-agent](https://hub.docker.com/r/jenkins/inbound-agent/)

### Docker Environment

Follow the installation steps on [the Docker website](https://docs.docker.com/).

If your Jenkins instance is not on the same OS as the Docker install,
you will need to open the Docker TCP port so that Jenkins can communicate with the Docker daemon.
This can be achieved by editing the Docker config file and setting (for example)

```sh
DOCKER_OPTS="-H tcp://0.0.0.0:2376 -H unix:///var/run/docker.sock"
```

The Docker configuration file location will depend your system, but it
is likely to be
`/etc/init/docker.conf`
,
`/etc/default/docker`
or
`/etc/default/docker.io`.

### Multiple Docker Hosts

If you want to use more than just one physical node to run containers, you can define multiple Docker clouds.
The [Docker engine swarm mode API](https://docs.docker.com/engine/swarm/) is not supported.
Enhancement contributions would be welcomed.

### Jenkins Configuration

Docker plugin is a "Cloud" implementation.
You'll need to edit Jenkins system configuration
(Jenkins -> Manage -> System configuration)
and add a new Cloud of type "Docker".

![](docs/images/add-new-docker-cloud.png)

Configure Docker API URL with required credentials.
The test button lets you check the connection.

Then configure Agent templates,
assigning them labels that you can use so your jobs select the appropriate template,
and set the Docker container to be run with whatever container settings you require.

### Running self-made local Docker images

By default the Jenkins Docker plugin will download ('pull') the latest version of the image.
This will fail with custom images that are not on Docker Hub.
You will see logs like:

> com.nirima.jenkins.plugins.docker.DockerTemplate pullImage
> Pulling image '..'. This may take awhile...

> com.github.dockerjava.api.exception.NotFoundException: Status 404: {"message":"pull access denied for .., repository does not exist or may require 'docker login': denied: requested access to the resource is denied"}

On the _Docker Agent template_ set the **Pull strategy** to **Never pull**, Name, Docker Image, Remote File System Root, and checkbox Enabled.

If you have Docker only on your local Jenkins machine, configure the  **Docker Host URI** to `unix:///var/run/docker.sock`.

### Creating a Docker image

You need a Docker image that can be used to run Jenkins agent.
Depending on the launch method you select, there are some prerequisites
for the Docker image to be used:

## Launch via SSH

-   [sshd](https://linux.die.net/man/8/sshd) server and a JDK installed.
    You can use
    [jenkins/ssh-agent](https://hub.docker.com/r/jenkins/ssh-agent/)
    as a basis for a custom image.
-   an SSH key (based on the unique Jenkins instance identity) can be
    injected in container on startup, you don't need any credential set
    as long as you use standard openssl sshd.
    ![](docs/images/connect-with-ssh.png)
    When using the `jenkins/ssh-agent` Docker image, ensure that the user
    is set to `jenkins`.
    For backward compatibility *or* non-standard sshd packaged in your
    Docker image, you also have option to provide manually configured
    ssh credentials
-   **Note:** If the Docker container's host SSH key is not trusted by
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

Avoid overriding the Docker command, as the SSH Launcher relies on it.

You _can_ use an Entrypoint to run some side service inside your build agent container before the agent runtime starts and establish a connection
... but you MUST ensure your entrypoint eventually runs the passed command:

    exec "$@"

## Further information

More information can be obtained from the online help built into the Jenkins web UI.
Most configurable fields have explanatory text.
This, combined with knowledge of [Docker itself](https://docs.docker.com/), should answer most questions.

## Configuration as code

Jenkins and the Docker plugin can be configured as code  using the [configuration as code plugin](https://plugins.jenkins.io/configuration-as-code/).
It can also be configured from the Groovy script

If you're unsure which method to use, use the configuration as code plugin.

### Configuration as Code plugin

Install the [configuration-as-code plugin](https://plugins.jenkins.io/configuration-as-code/) and follow [its example](https://github.com/jenkinsci/configuration-as-code-plugin/tree/master/demos/docker).

As another alternative, a Docker daemon can listen to requests from remote hosts by following the [Docker documentation](https://docs.docker.com/engine/daemon/remote-access/).
The following configuration as code example creates a cloud named "my-docker-cloud" that uses the docker daemon at port 2375 on dockerhost.example.com to run up to 3 containerized agents at a time.
Agents run the [Jenkins Alpine inbound agent container image](https://hub.docker.com/r/jenkins/inbound-agent) with Java 21.
They use an inbound connection and run as the user ID 1000 with the home directory "/home/jenkins/agent".

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
could be run automatically during
[Jenkins post-initialization](https://www.jenkins.io/doc/book/managing/groovy-hook-scripts/)
or through the
[Jenkins script console](https://www.jenkins.io/doc/book/managing/script-console/).
This script configures the plugin to look for a Docker daemon running within the same OS as the Jenkins controller
(connecting to Docker service through `unix:///var/run/docker.sock`)
and with the containers connecting to Jenkins using the "attach" method.
