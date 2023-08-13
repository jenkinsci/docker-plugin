#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(
  // Run a JVM per core in tests
  forkCount: '1C',
  // Container agents won't work for testing this plugin
  useContainerAgent: false,
  // Show failures on all configurations
  failFast: false,
  // Test Java 11 with minimum Jenkins version, Java 17 with a more recent version
  configurations: [
    [platform: 'linux', jdk: 17],
    [platform: 'windows', jdk: 11],
])
