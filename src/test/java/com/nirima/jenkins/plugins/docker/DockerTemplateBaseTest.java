package com.nirima.jenkins.plugins.docker;

import static org.mockito.Matchers.anyListOf;
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
import com.github.dockerjava.api.model.BindOptions;
import com.github.dockerjava.api.model.BindPropagation;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.TmpfsOptions;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;

import java.util.Arrays;

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
    public void fillContainerConfigGivenMounts() {
        testFillContainerMount("volume", "source=aVolume,target=/aTarget",
                new Mount().withType(MountType.VOLUME).withSource("aVolume").withTarget("/aTarget"));
        testFillContainerMount("file", "source=aVolume,destination=aFile",
                new Mount().withType(MountType.VOLUME).withSource("aVolume").withTarget("aFile"));
        testFillContainerMount("readOnlyFile", "source=aVolume,destination=aFile,readonly",
                new Mount().withType(MountType.VOLUME).withSource("aVolume").withTarget("aFile").withReadOnly(true));

        testFillContainerMount("bind", "type=bind,source=/aSource,target=/aTarget",
                new Mount().withType(MountType.BIND).withSource("/aSource").withTarget("/aTarget"));
        testFillContainerMount("readOnlyBind", "type=bind,source=/aSource,target=/aTarget,readonly",
                new Mount().withType(MountType.BIND).withSource("/aSource").withTarget("/aTarget").withReadOnly(true));
        testFillContainerMount("bindWithPropagation", "type=bind,source=/aSource,target=/aTarget,bind-propagation=rslave",
                new Mount().withType(MountType.BIND).withSource("/aSource").withTarget("/aTarget").withBindOptions(new BindOptions().withPropagation(BindPropagation.R_SLAVE)));

        testFillContainerMount("tmpfs", "type=tmpfs,destination=/aTarget",
                new Mount().withType(MountType.TMPFS).withTarget("/aTarget"));
        testFillContainerMount("tmpfsWithOption", "type=tmpfs,destination=/aTarget,tmpfs-mode=1770",
                new Mount().withType(MountType.TMPFS).withTarget("/aTarget").withTmpfsOptions(new TmpfsOptions().withMode(1770)));
    }

    private static void testFillContainerMount(String imageName, String mountStringToSet, Mount... expectedMountsSet) {
        // Given
        final CreateContainerCmd mockCmd = mock(CreateContainerCmd.class);
        final HostConfig mockHostConfig = mock(HostConfig.class);
        when(mockCmd.getHostConfig()).thenReturn(mockHostConfig);
        final DockerTemplateBase instanceUnderTest = new DockerTemplateBase(imageName);
        instanceUnderTest.setMountsString(mountStringToSet);

        // When
        instanceUnderTest.fillContainerConfig(mockCmd);

        verify(mockHostConfig).withMounts(Arrays.asList(expectedMountsSet));
    }
}
