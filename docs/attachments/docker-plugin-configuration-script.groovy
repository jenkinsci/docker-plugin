import io.jenkins.docker.client.DockerAPI
import com.nirima.jenkins.plugins.docker.DockerCloud
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint
import com.nirima.jenkins.plugins.docker.DockerTemplate
import com.nirima.jenkins.plugins.docker.DockerTemplateBase
import com.nirima.jenkins.plugins.docker.launcher.AttachedDockerComputerLauncher
import io.jenkins.docker.connector.DockerComputerAttachConnector
import jenkins.model.Jenkins

// parameters
def dockerTemplateBaseParameters = [
  image:              'jenkinsci/slave:latest',
  pullCredentialsId:  '',
  dnsString:          '',
  network:            '',
  dockerCommand:      '',
  volumesString:      '',
  volumesFromString:  '',
  environmentsString: '',
  hostname:           '',
  user:               '',
  extraGroupsString:  '',
  memoryLimit:        null,
  memorySwap:         null,
  cpuShares:          null,
  shmSize:            null,
  bindPorts:          '',
  bindAllPorts:       false,
  privileged:         false,
  tty:                true,
  macAddress:         '',
  extraHostsString:   ''
]

def DockerTemplateParameters = [
  instanceCapStr: '4',
  labelString:    'docker.local.jenkins.slave',
  remoteFs:       ''
]

def dockerCloudParameters = [
  connectTimeout:   3,
  containerCapStr:  '4',
  credentialsId:    '',
  dockerHostname:   '',
  name:             'docker.local',
  readTimeout:      60,
  serverUrl:        'unix:///var/run/docker.sock',
  version:          ''
]

DockerTemplateBase dockerTemplateBase = new DockerTemplateBase(dockerTemplateBaseParameters.image)
dockerTemplateBaseParameters.findAll{ it.key != "image" }.each {
  k, v ->
    dockerTemplateBase."$k" = v
}

DockerTemplate dockerTemplate = new DockerTemplate(
  dockerTemplateBase,
  new DockerComputerAttachConnector(),
  DockerTemplateParameters.labelString,
  DockerTemplateParameters.remoteFs,
  DockerTemplateParameters.instanceCapStr
)

dockerApi = new DockerAPI(new DockerServerEndpoint(dockerCloudParameters.serverUrl, dockerCloudParameters.credentialsId))
dockerApi.with {
  connectTimeout = dockerCloudParameters.connectTimeout
  readTimeout = dockerCloudParameters.readTimeout
  apiVersion = dockerCloudParameters.version
  hostname = dockerCloudParameters.dockerHostname
}

DockerCloud dockerCloud = new DockerCloud(
  dockerCloudParameters.name,
  dockerApi,
  [dockerTemplate]
)

// get Jenkins instance
Jenkins jenkins = Jenkins.getInstance()

// add cloud configuration to Jenkins
jenkins.clouds.add(dockerCloud)

// save current Jenkins state to disk
jenkins.save()
