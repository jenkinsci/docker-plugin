/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  useContainerAgent: false, // Set to `true` if Docker not required for containerized tests
  configurations: [
    [platform: 'linux', jdk: 25],
    [platform: 'windows', jdk: 21],
])
