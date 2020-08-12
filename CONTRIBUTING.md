# General

- First, and most importantly, make sure you're in the right place.
There's more than one docker plugin for Jenkins and raising an issue about one plugin in the issues area for another plugin causes a lot of confusion.
See the [README](README.md) file for details.
- Please do not use the issue tracker to ask questions.
This facility is for reporting (and fixing) bugs in the code.
If you're not 100% sure it's a bug in the code then please seek help elsewhere.
e.g. the [jenkins-users google group](https://groups.google.com/forum/#!forum/jenkinsci-users).
- [RTFM](https://en.wikipedia.org/wiki/RTFM).
The Jenkins UI pages include help that should explain things.
The
[plugin documentation](README.md)
gives additional information.
Both of these assume a basic working understanding docker itself, so make sure you read [the docker documentation](https://docs.docker.com/) too.
- Be helpful and make no demands.
  * This code is Free Open-Source Software - nobody is obliged to make things work for you *but* you have legal permission to fix things yourself.
  * If you're reporting and fixing an issue yourself then you only need to explain what problem you're fixing in enough detail that the maintainer(s) can understand why your changes are in the public interest.
  * If you're relying on someone else to fix a problem then you should to make it as easy as possible for others to fix it for you, and you should test any fixes provided.

# Legal conditions

- Any contributions (code, information etc) submitted will be subject to the same [license](LICENSE) as the rest of the code.
No new restrictions/conditions are permitted.
- As a contributor, you MUST have the legal right to grant permission for your contribution to be used under these conditions.

# Reporting a new issue

- Provide a full description of the issue you are facing.
  * What are you trying to do?
  * How is this failing?
  * What should happen instead?
- Provide step-by-step instructions for how to reproduce the issue.
  * Try to avoid relying on custom docker images or for the repro case. Ideally, reproduce with a `jenkins/(ssh-agent|inbound-agent|agent)` image with a dumb freestyle job, as that makes life easier for everyone.
- Specify the Jenkins core & plugin version (of all docker-related plugins) that you're seeing the issue with.
- Check `Manage Jenkins` -> `Manage Old Data` for out of date configuration data and provide this info.
- Check and provide errors from system jenkins.log and errors from `Manage Jenkins` -> `System Log`.
  * If that log is too verbose, create a `docker-plugin` log that only logs `com.nirima.jenkins.plugin.docker` and `io.jenkins.docker` (e.g. at level "ALL") and grab what's logged when you reproduce the issue.
  * Exceptions and stacktraces are *especially* useful, so don't omit those...
  * ...except please note that exceptions logged by the docker-java library are "expected behaviour" (include those, but don't worry about them).
  * Note that sensitive information may be visible in logs, so take care to redact logs where necessary.
- Provide a copy/paste of the `Cloud` section from $JENKINS_HOME/config.xml file (redacted where neccessary).
- Describe what your docker infrastructure looks like, e.g. single vs multiple, hosts vs swarms, where Jenkins is in relation to that etc.
- Provide docker host/api version.
- Ensure that any code or log examples are surrounded with [the right markdown](https://help.github.com/articles/github-flavored-markdown/) otherwise it'll be unreadable.

# Submitting pull requests

- A PR's description must EITHER refer to an existing issue (either in github or on Jenkins JIRA) OR include a full description as for "Creating new issue".
- A single PR should EITHER be making functional changes OR making (non-functional) cosmetic/refactoring changes to the code.
Please do not do both in the same PR as this makes life difficult for anyone else merging your changes.
- For functional-change PRs, keep changes to a minimum (to make merges easier).
- Coding style:
  * Try to fit in.
  Not all of the code within this plugin is written in the same "style".
  Any changes you make must fit in with the existing style that is prevalent within the area in which you are working.
  i.e. code in the same style that the existing method/class/package uses.
  * If you can't tolerate inconsistencies, submit a cosmetic PR that applies the formatting/whitespace/non-functional changes that you want made.
  * If in doubt, use 4 spaces for indentation, avoid using tabs, avoid trailing whitespace, use unix end-of-line codes (LF, not CR/LF), and make sure every file ends with a newline.
- Unit tests:
  * Any new functionality must be unit tested.
  * PRs that add unit-tests for existing functionality will be very welcome too.
  * Unit tests should be as fast as possible but *must* be reliable (Tests that behave inconsistently cause trouble for everyone else trying to work on the code).
- Clean build & test:
  * Any submitted PRs should automatically get built and tested; PRs will not be considered for merger until they are showing a clean build.
  If you believe that the build failed for reasons unconnected to your changes, close your PR, wait 10 minutes, then re-open it (just re-open the same PR; don't create a new one) to trigger a rebuild.
  Repeat until it builds clean, changing your code if necessary.
  * Please provide unit-tests for your contribution.
  * Don't give findbugs, the compiler or any IDEs anything new to complain about.
- Preserve existing functionality by default:
  * Where possible, ensure that existing users don't find that their functionality has changed after they've upgraded from an old version of the plugin to a version of the plugin that contains your changes.
  * When adding new functionality, try to keep the defaults are unchanged so that nobody finds new, unexpected, things happening after upgrading.
  * Ensure any breaking changes are well documented in the PR description.
- Configuration data changes:
  * If the PR adds a new field to an existing (or new) data structure, make sure you've written good help text for it that explains what it's for, why it's useful, and an example.
  Implement corresponding `doCheck` methods to provide validation when users are entering this data from the WebUI.
  * If the PR changes an existing field, make sure that the code copes with reading in data from older versions of the plugin.

# Links

- https://plugins.jenkins.io/docker-plugin/
- https://jenkins.io/participate/code/
