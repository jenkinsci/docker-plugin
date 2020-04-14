package com.nirima.jenkins.plugins.docker;

import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;

import java.util.Arrays;
import java.util.List;

public class DockerTemplateBaseTest {

    @BeforeClass
    public static void setUpClass() {
        JenkinsUtils.setTestInstanceId(DockerTemplateBaseTest.class.getSimpleName());
    }

    @AfterClass
    public static void tearDownClass() {
        JenkinsUtils.setTestInstanceId(null);
    }

    @Test // https://github.com/jenkinsci/docker-plugin/pull/643
    public void fillContainerConfigGivenShmSizeThenSetsShmSize() {
        // null/empty/negative values result in no value being set, so the
        // defaults are used
        testFillContainerShmSize("null", null, false, null);
        testFillContainerShmSize("negative", -1234, false, null);
        testFillContainerShmSize("zero", 0, false, null);
        // 1 means 1 megabyte = 1048576 bytes
        testFillContainerShmSize("one", 1, true, 1048576L);
        // value could be as high as Integer.MAX_VALUE, so ensure we cope
        // 2147483647 means 2147483647 megabytes = 2251799812636672 bytes
        testFillContainerShmSize("max", 2147483647, true, 2251799812636672L);
    }

    private static void testFillContainerShmSize(final String imageName, final Integer shmSizeToSet,
            final boolean shmSizeIsExpectedToBeSet, final Long expectedShmSizeSet) {
        // Given
        final CreateContainerCmd mockCmd = mock(CreateContainerCmd.class);
        final HostConfig mockHostConfig = mock(HostConfig.class);
        when(mockCmd.getHostConfig()).thenReturn(mockHostConfig);
        final DockerTemplateBase instanceUnderTest = new DockerTemplateBase(imageName);
        instanceUnderTest.setShmSize(shmSizeToSet);

        // When
        instanceUnderTest.fillContainerConfig(mockCmd);

        // Then
        if (shmSizeIsExpectedToBeSet) {
            verify(mockHostConfig, times(1)).withShmSize(expectedShmSizeSet);
        } else {
            verify(mockHostConfig, never()).withShmSize(anyLong());
        }
    }

    @Test // https://github.com/jenkinsci/docker-plugin/issues/642
    public void fillContainerConfigGivenEnvironmentThenSetsEnvs() {
        // null/empty values result in no value being set
        testFillContainerEnvironmentVariable("null", null, false);
        testFillContainerEnvironmentVariable("empty", "", false);
        // anything else, we should set things
        testFillContainerEnvironmentVariable("oneVar", "HOST_HOSTNAME=luu182d", true, "HOST_HOSTNAME=luu182d");
        testFillContainerEnvironmentVariable("multiple", "foo=bar\na=b\n\n", true, "foo=bar", "a=b");
    }

    private static void testFillContainerEnvironmentVariable(final String imageName,
            final String environmentStringToSet, final boolean envsIsExpectedToBeSet, final String... expectedEnvsSet) {
        // Given
        final CreateContainerCmd mockCmd = mock(CreateContainerCmd.class);
        final DockerTemplateBase instanceUnderTest = new DockerTemplateBase(imageName);
        instanceUnderTest.setEnvironmentsString(environmentStringToSet);

        // When
        instanceUnderTest.fillContainerConfig(mockCmd);

        // Then
        if (envsIsExpectedToBeSet) {
            verify(mockCmd, times(1)).withEnv(expectedEnvsSet);
            verify(mockCmd, never()).withEnv(anyListOf(String.class));
        } else {
            verify(mockCmd, never()).withEnv((String[]) anyVararg());
            verify(mockCmd, never()).withEnv(anyListOf(String.class));
        }
    }

    @Test
    public void fillContainerConfigGivenExtraGroupsThenSetsGroupAdd() {
        // null/empty values result in no value being set
        testFillContainerGroupAdd("null", null, false);
        testFillContainerGroupAdd("empty", "", false);
        testFillContainerGroupAdd("spaces", "\n\n", false);

        // anything else, we should set things
        testFillContainerGroupAdd("one", "foo", true, "foo");
        testFillContainerGroupAdd("two", "foo\nbar", true, "foo", "bar");
        testFillContainerGroupAdd("twospaced", "\nfoo\n\nbar\n\n", true, "foo", "bar");
    }

    private static void testFillContainerGroupAdd(
            String imageName,
            String extraGroupsStringToSet,
            boolean groupAddIsExpectedToBeSet,
            String... expectedGroupsSet) {
        // Given
        final CreateContainerCmd mockCmd = mock(CreateContainerCmd.class);
        final HostConfig mockHostConfig = mock(HostConfig.class);
        when(mockCmd.getHostConfig()).thenReturn(mockHostConfig);
        final DockerTemplateBase instanceUnderTest = new DockerTemplateBase(imageName);
        instanceUnderTest.setExtraGroupsString(extraGroupsStringToSet);

        // When
        instanceUnderTest.fillContainerConfig(mockCmd);

        // Then
        if (groupAddIsExpectedToBeSet) {
            verify(mockHostConfig, times(1)).withGroupAdd(Arrays.asList(expectedGroupsSet));
        } else {
            verify(mockHostConfig, never()).withGroupAdd(anyListOf(String.class));
        }
    }

    @Test
    public void fillContainerConfigGivenSecurityOptions() {
        testFillContainerSecurityOpts("null", null, false,
                null);
        String seccompSecurityOptUnconfined = "seccomp=unconfined";
        testFillContainerSecurityOpts("unconfined", Arrays.asList(seccompSecurityOptUnconfined), true,
                Arrays.asList(seccompSecurityOptUnconfined));
    }

    private static void testFillContainerSecurityOpts(final String imageName, final List<String> securityOptsToSet,
            final boolean securityOptsIsExpectedToBeSet, final List<String> expectedSecurityOpts) {
        // Given
        final CreateContainerCmd mockCmd = mock(CreateContainerCmd.class);
        final HostConfig mockHostConfig = mock(HostConfig.class);
        when(mockCmd.getHostConfig()).thenReturn(mockHostConfig);
        final DockerTemplateBase instanceUnderTest = new DockerTemplateBase(imageName);
        instanceUnderTest.setSecurityOpts(securityOptsToSet);

        // When
        instanceUnderTest.fillContainerConfig(mockCmd);

        // Then
        if (securityOptsIsExpectedToBeSet) {
            verify(mockHostConfig, times(1)).withSecurityOpts(expectedSecurityOpts);
        } else {
            verify(mockHostConfig, never()).withSecurityOpts(anyList());
        }
    }

    @Test
    public void fillContainerConfigGivenCapabilitiesToAdd() {
        testFillContainerCapabilitiesToAdd("null", null, false,
                null);
        String toAddInString = "AUDIT_CONTROL";
        Capability toAdd = Capability.AUDIT_CONTROL;
        testFillContainerCapabilitiesToAdd("toAdd", Arrays.asList(toAddInString), true,
                Arrays.asList(toAdd));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fillContainerConfigGivenCapabilitiesToAddWithException() {
        testFillContainerCapabilitiesToAdd("not existing", Arrays.asList("DUMMY"), false,
                null);
    }

    private static void testFillContainerCapabilitiesToAdd(final String imageName, final List<String> capabilitiesToSet,
            final boolean capabilitiesIsExpectedToBeSet, final List<Capability> expectedCapabilities) {
        // Given
        final CreateContainerCmd mockCmd = mock(CreateContainerCmd.class);
        final DockerTemplateBase instanceUnderTest = new DockerTemplateBase(imageName);
        instanceUnderTest.setCapabilitiesToAdd(capabilitiesToSet);

        // When
        instanceUnderTest.fillContainerConfig(mockCmd);

        // Then
        if (capabilitiesIsExpectedToBeSet) {
            verify(mockCmd, times(1)).withCapAdd(expectedCapabilities);
        } else {
            verify(mockCmd, never()).withCapAdd(anyList());
        }
    }

    @Test
    public void fillContainerConfigGivenCapabilitiesToDrop() {
        testFillContainerCapabilitiesToDrop("null", null, false, null);
        String toDropInString = "AUDIT_CONTROL";
        Capability toDrop = Capability.AUDIT_CONTROL;
        testFillContainerCapabilitiesToDrop("toDrop", Arrays.asList(toDropInString), true,
                Arrays.asList(toDrop));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fillContainerConfigGivenCapabilitiesToDropWithException() {
        testFillContainerCapabilitiesToDrop("not existing", Arrays.asList("DUMMY"), false,
                null);
    }

    private static void testFillContainerCapabilitiesToDrop(final String imageName, final List<String> capabilitiesToSet,
            final boolean capabilitiesIsExpectedToBeSet, final List<Capability> expectedCapabilities) {
        // Given
        final CreateContainerCmd mockCmd = mock(CreateContainerCmd.class);
        final DockerTemplateBase instanceUnderTest = new DockerTemplateBase(imageName);
        instanceUnderTest.setCapabilitiesToDrop(capabilitiesToSet);

        // When
        instanceUnderTest.fillContainerConfig(mockCmd);

        // Then
        if (capabilitiesIsExpectedToBeSet) {
            verify(mockCmd, times(1)).withCapDrop(expectedCapabilities);
        } else {
            verify(mockCmd, never()).withCapDrop(anyList());
        }
    }
}
