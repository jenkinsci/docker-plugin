#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(
  // Container agents won't work for testing this plugin
  useContainerAgent: false,
  // Show failures on all configurations
  failFast: false,
  // Test Java 11, 17, and 21
  configurations: [
    [platform: 'linux', jdk: 17],
    [platform: 'linux',   jdk: '21', jenkins: '2.401.3'],
    [platform: 'windows', jdk: 11],
])
