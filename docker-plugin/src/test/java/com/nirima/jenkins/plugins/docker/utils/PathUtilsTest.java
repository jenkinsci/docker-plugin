package com.nirima.jenkins.plugins.docker.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class PathUtilsTest {
	private static final String SLAVE_HOME = "/home/slave";
	private static final String MASTER_HOME = "/home/master";
	private static final String RELATIVE_SIMPLE = "jobname";
	private static final String RELATIVE_NESTED = "some/nested/jobname";

	private static final String SLAVE_JOBDIR_SIMPLE = SLAVE_HOME + "/workspace/" + RELATIVE_SIMPLE;
	private static final String SLAVE_JOBDIR_NESTED = SLAVE_HOME + "/workspace/" + RELATIVE_NESTED;
	private static final String MASTER_JOBDIR_SIMPLE = MASTER_HOME + "/workspace/" + RELATIVE_SIMPLE;
	private static final String MASTER_JOBDIR_NESTED = MASTER_HOME + "/workspace/" + RELATIVE_NESTED;


	@Test
	public void mapDirectoryToOtherRootSimple() {
		mapDirectoryToOtherRoot(SLAVE_JOBDIR_SIMPLE, MASTER_JOBDIR_SIMPLE);
	}

	@Test
	public void mapDirectoryToOtherRootNested() {
		mapDirectoryToOtherRoot(SLAVE_JOBDIR_NESTED, MASTER_JOBDIR_NESTED);
	}

	private void mapDirectoryToOtherRoot(String slaveJobDir, String expectedMasterJobDir) {
		Path slaveHome = Paths.get(SLAVE_HOME);
		Path masterHome = Paths.get(MASTER_HOME);

		Path slaveJobDirectory = Paths.get(slaveJobDir);
		Path exectedMasterJobDirectory = Paths.get(expectedMasterJobDir);

		Path masterJobDirectory = PathUtils.mapDirectoryToOtherRoot(slaveJobDirectory, slaveHome, masterHome);
		assertNotNull("could not map slave job directory to the master", masterJobDirectory);
		assertEquals(exectedMasterJobDirectory, masterJobDirectory);
	}

}
