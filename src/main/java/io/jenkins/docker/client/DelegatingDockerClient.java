package io.jenkins.docker.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.AttachContainerCmd;
import com.github.dockerjava.api.command.AuthCmd;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.CommitCmd;
import com.github.dockerjava.api.command.ConnectToNetworkCmd;
import com.github.dockerjava.api.command.ContainerDiffCmd;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CopyFileFromContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateImageCmd;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.command.CreateSecretCmd;
import com.github.dockerjava.api.command.CreateServiceCmd;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.DisconnectFromNetworkCmd;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.command.InitializeSwarmCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectNetworkCmd;
import com.github.dockerjava.api.command.ListSecretsCmd;
import com.github.dockerjava.api.command.InspectServiceCmd;
import com.github.dockerjava.api.command.InspectSwarmCmd;
import com.github.dockerjava.api.command.InspectVolumeCmd;
import com.github.dockerjava.api.command.JoinSwarmCmd;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.LeaveSwarmCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.ListNetworksCmd;
import com.github.dockerjava.api.command.ListServicesCmd;
import com.github.dockerjava.api.command.ListSwarmNodesCmd;
import com.github.dockerjava.api.command.ListTasksCmd;
import com.github.dockerjava.api.command.ListVolumesCmd;
import com.github.dockerjava.api.command.LoadImageCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.LogSwarmObjectCmd;
import com.github.dockerjava.api.command.PauseContainerCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.command.PruneCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.command.RemoveNetworkCmd;
import com.github.dockerjava.api.command.RemoveSecretCmd;
import com.github.dockerjava.api.command.RemoveServiceCmd;
import com.github.dockerjava.api.command.RemoveVolumeCmd;
import com.github.dockerjava.api.command.RenameContainerCmd;
import com.github.dockerjava.api.command.ResizeContainerCmd;
import com.github.dockerjava.api.command.ResizeExecCmd;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.command.SaveImageCmd;
import com.github.dockerjava.api.command.SaveImagesCmd;
import com.github.dockerjava.api.command.SearchImagesCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.command.TagImageCmd;
import com.github.dockerjava.api.command.TopContainerCmd;
import com.github.dockerjava.api.command.UnpauseContainerCmd;
import com.github.dockerjava.api.command.UpdateContainerCmd;
import com.github.dockerjava.api.command.UpdateServiceCmd;
import com.github.dockerjava.api.command.UpdateSwarmCmd;
import com.github.dockerjava.api.command.UpdateSwarmNodeCmd;
import com.github.dockerjava.api.command.VersionCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Identifier;
import com.github.dockerjava.api.model.PruneType;
import com.github.dockerjava.api.model.SecretSpec;
import com.github.dockerjava.api.model.ServiceSpec;
import com.github.dockerjava.api.model.SwarmSpec;

/**
 * Simple delegate class for the {@link DockerClient} interface. This makes it
 * easy for other classes to override specific methods without having to
 * implement all of them.
 */
public class DelegatingDockerClient implements DockerClient {

    private final DockerClient delegate;

    public DelegatingDockerClient(final DockerClient delegate) {
        this.delegate = delegate;
    }

    protected DockerClient getDelegate() {
        return delegate;
    }

    @Override
    public AttachContainerCmd attachContainerCmd(String arg0) {
        return getDelegate().attachContainerCmd(arg0);
    }

    @Override
    public AuthCmd authCmd() {
        return getDelegate().authCmd();
    }

    @Override
    public AuthConfig authConfig() throws DockerException {
        return getDelegate().authConfig();
    }

    @Override
    public BuildImageCmd buildImageCmd() {
        return getDelegate().buildImageCmd();
    }

    @Override
    public BuildImageCmd buildImageCmd(File arg0) {
        return getDelegate().buildImageCmd(arg0);
    }

    @Override
    public BuildImageCmd buildImageCmd(InputStream arg0) {
        return getDelegate().buildImageCmd(arg0);
    }

    @Override
    public void close() throws IOException {
        getDelegate().close();
    }

    @Override
    public CommitCmd commitCmd(String arg0) {
        return getDelegate().commitCmd(arg0);
    }

    @Override
    public ConnectToNetworkCmd connectToNetworkCmd() {
        return getDelegate().connectToNetworkCmd();
    }

    @Override
    public ContainerDiffCmd containerDiffCmd(String arg0) {
        return getDelegate().containerDiffCmd(arg0);
    }

    @Override
    public CopyArchiveFromContainerCmd copyArchiveFromContainerCmd(String arg0, String arg1) {
        return getDelegate().copyArchiveFromContainerCmd(arg0, arg1);
    }

    @Override
    public CopyArchiveToContainerCmd copyArchiveToContainerCmd(String arg0) {
        return getDelegate().copyArchiveToContainerCmd(arg0);
    }

    @Override
    public CopyFileFromContainerCmd copyFileFromContainerCmd(String arg0, String arg1) {
        return getDelegate().copyFileFromContainerCmd(arg0, arg1);
    }

    @Override
    public CreateContainerCmd createContainerCmd(String arg0) {
        return getDelegate().createContainerCmd(arg0);
    }

    @Override
    public CreateImageCmd createImageCmd(String arg0, InputStream arg1) {
        return getDelegate().createImageCmd(arg0, arg1);
    }

    @Override
    public CreateNetworkCmd createNetworkCmd() {
        return getDelegate().createNetworkCmd();
    }

    @Override
    public CreateVolumeCmd createVolumeCmd() {
        return getDelegate().createVolumeCmd();
    }

    @Override
    public DisconnectFromNetworkCmd disconnectFromNetworkCmd() {
        return getDelegate().disconnectFromNetworkCmd();
    }

    @Override
    public EventsCmd eventsCmd() {
        return getDelegate().eventsCmd();
    }

    @Override
    public ExecCreateCmd execCreateCmd(String arg0) {
        return getDelegate().execCreateCmd(arg0);
    }

    @Override
    public ExecStartCmd execStartCmd(String arg0) {
        return getDelegate().execStartCmd(arg0);
    }

    @Override
    public InfoCmd infoCmd() {
        return getDelegate().infoCmd();
    }

    @Override
    public InspectContainerCmd inspectContainerCmd(String arg0) {
        return getDelegate().inspectContainerCmd(arg0);
    }

    @Override
    public InspectExecCmd inspectExecCmd(String arg0) {
        return getDelegate().inspectExecCmd(arg0);
    }

    @Override
    public InspectImageCmd inspectImageCmd(String arg0) {
        return getDelegate().inspectImageCmd(arg0);
    }

    @Override
    public InspectNetworkCmd inspectNetworkCmd() {
        return getDelegate().inspectNetworkCmd();
    }

    @Override
    public InspectVolumeCmd inspectVolumeCmd(String arg0) {
        return getDelegate().inspectVolumeCmd(arg0);
    }

    @Override
    public KillContainerCmd killContainerCmd(String arg0) {
        return getDelegate().killContainerCmd(arg0);
    }

    @Override
    public ListContainersCmd listContainersCmd() {
        return getDelegate().listContainersCmd();
    }

    @Override
    public ListImagesCmd listImagesCmd() {
        return getDelegate().listImagesCmd();
    }

    @Override
    public ListNetworksCmd listNetworksCmd() {
        return getDelegate().listNetworksCmd();
    }

    @Override
    public ListVolumesCmd listVolumesCmd() {
        return getDelegate().listVolumesCmd();
    }

    @Override
    public LoadImageCmd loadImageCmd(InputStream arg0) {
        return getDelegate().loadImageCmd(arg0);
    }

    @Override
    public LogContainerCmd logContainerCmd(String arg0) {
        return getDelegate().logContainerCmd(arg0);
    }

    @Override
    public PauseContainerCmd pauseContainerCmd(String arg0) {
        return getDelegate().pauseContainerCmd(arg0);
    }

    @Override
    public PingCmd pingCmd() {
        return getDelegate().pingCmd();
    }

    @Override
    public PullImageCmd pullImageCmd(String arg0) {
        return getDelegate().pullImageCmd(arg0);
    }

    @Override
    public PushImageCmd pushImageCmd(String arg0) {
        return getDelegate().pushImageCmd(arg0);
    }

    @Override
    public PushImageCmd pushImageCmd(Identifier arg0) {
        return getDelegate().pushImageCmd(arg0);
    }

    @Override
    public RemoveContainerCmd removeContainerCmd(String arg0) {
        return getDelegate().removeContainerCmd(arg0);
    }

    @Override
    public RemoveImageCmd removeImageCmd(String arg0) {
        return getDelegate().removeImageCmd(arg0);
    }

    @Override
    public RemoveNetworkCmd removeNetworkCmd(String arg0) {
        return getDelegate().removeNetworkCmd(arg0);
    }

    @Override
    public RemoveVolumeCmd removeVolumeCmd(String arg0) {
        return getDelegate().removeVolumeCmd(arg0);
    }

    @Override
    public RenameContainerCmd renameContainerCmd(String arg0) {
        return getDelegate().renameContainerCmd(arg0);
    }

    @Override
    public ResizeContainerCmd resizeContainerCmd(String containerId) {
        return getDelegate().resizeContainerCmd(containerId);
    }

    @Override
    public ResizeExecCmd resizeExecCmd(String execId) {
        return getDelegate().resizeExecCmd(execId);
    }

    @Override
    public RestartContainerCmd restartContainerCmd(String arg0) {
        return getDelegate().restartContainerCmd(arg0);
    }

    @Override
    public SaveImageCmd saveImageCmd(String arg0) {
        return getDelegate().saveImageCmd(arg0);
    }

    @Override
    public SaveImagesCmd saveImagesCmd() {
        return getDelegate().saveImagesCmd();
    }

    @Override
    public SearchImagesCmd searchImagesCmd(String arg0) {
        return getDelegate().searchImagesCmd(arg0);
    }

    @Override
    public StartContainerCmd startContainerCmd(String arg0) {
        return getDelegate().startContainerCmd(arg0);
    }

    @Override
    public StatsCmd statsCmd(String arg0) {
        return getDelegate().statsCmd(arg0);
    }

    @Override
    public StopContainerCmd stopContainerCmd(String arg0) {
        return getDelegate().stopContainerCmd(arg0);
    }

    @Override
    public TagImageCmd tagImageCmd(String arg0, String arg1, String arg2) {
        return getDelegate().tagImageCmd(arg0, arg1, arg2);
    }

    @Override
    public TopContainerCmd topContainerCmd(String arg0) {
        return getDelegate().topContainerCmd(arg0);
    }

    @Override
    public UnpauseContainerCmd unpauseContainerCmd(String arg0) {
        return getDelegate().unpauseContainerCmd(arg0);
    }

    @Override
    public UpdateContainerCmd updateContainerCmd(String arg0) {
        return getDelegate().updateContainerCmd(arg0);
    }

    @Override
    public VersionCmd versionCmd() {
        return getDelegate().versionCmd();
    }

    @Override
    public WaitContainerCmd waitContainerCmd(String arg0) {
        return getDelegate().waitContainerCmd(arg0);
    }

    @Override
    public InitializeSwarmCmd initializeSwarmCmd(SwarmSpec swarmSpec) {
        return getDelegate().initializeSwarmCmd(swarmSpec);
    }

    @Override
    public InspectSwarmCmd inspectSwarmCmd() {
        return getDelegate().inspectSwarmCmd();
    }

    @Override
    public JoinSwarmCmd joinSwarmCmd() {
        return getDelegate().joinSwarmCmd();
    }

    @Override
    public LeaveSwarmCmd leaveSwarmCmd() {
        return getDelegate().leaveSwarmCmd();
    }

    @Override
    public UpdateSwarmCmd updateSwarmCmd(SwarmSpec swarmSpec) {
        return getDelegate().updateSwarmCmd(swarmSpec);
    }

    @Override
    public UpdateSwarmNodeCmd updateSwarmNodeCmd() {
        return getDelegate().updateSwarmNodeCmd();
    }

    @Override
    public ListSwarmNodesCmd listSwarmNodesCmd() {
        return getDelegate().listSwarmNodesCmd();
    }

    @Override
    public ListServicesCmd listServicesCmd() {
        return getDelegate().listServicesCmd();
    }

    @Override
    public CreateServiceCmd createServiceCmd(ServiceSpec serviceSpec) {
        return getDelegate().createServiceCmd(serviceSpec);
    }

    @Override
    public InspectServiceCmd inspectServiceCmd(String serviceId) {
        return getDelegate().inspectServiceCmd(serviceId);
    }

    @Override
    public UpdateServiceCmd updateServiceCmd(String serviceId, ServiceSpec serviceSpec) {
        return getDelegate().updateServiceCmd(serviceId, serviceSpec);
    }

    @Override
    public RemoveServiceCmd removeServiceCmd(String serviceId) {
        return getDelegate().removeServiceCmd(serviceId);
    }

    @Override
    public ListTasksCmd listTasksCmd() {
        return getDelegate().listTasksCmd();
    }

    @Override
    public LogSwarmObjectCmd logServiceCmd(String serviceId) {
        return getDelegate().logServiceCmd(serviceId);
    }

    @Override
    public LogSwarmObjectCmd logTaskCmd(String taskId) {
        return getDelegate().logTaskCmd(taskId);
    }

    @Override
    public PruneCmd pruneCmd(PruneType pruneType) {
        return getDelegate().pruneCmd(pruneType);
    }

    @Override
    public ListSecretsCmd listSecretsCmd() {
        return getDelegate().listSecretsCmd();
    }

    @Override
    public CreateSecretCmd createSecretCmd(SecretSpec secretSpec) {
        return getDelegate().createSecretCmd(secretSpec);
    }

    @Override
    public RemoveSecretCmd removeSecretCmd(String secretId) {
        return getDelegate().removeSecretCmd(secretId);
      }
}
