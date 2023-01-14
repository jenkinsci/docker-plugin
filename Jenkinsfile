#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(
  // Container agents won't work for testing this plugin
  useContainerAgent: false,
  // Show failures on all configurations
  failFast: false,
  // Test Java 11 with minimum Jenkins version, Java 17 with a more recent version
  configurations: [
    [platform: 'windows', jdk: '17'],
    [platform: 'linux',   jdk: '11'],
  ]
)
