package com.nirima.jenkins.plugins.docker.utils;

import java.nio.file.Path;

public class PathUtils {
	/**
	 * Translates an absolute directory, that is a child of fromRoot, to a second directory toRoot.
	 * So basically the part of directory that is relative to fromRoot is applied to toRoot and
	 * that absolute path is returned.
	 * @param directory the directory to map/translate
	 * @param fromRoot the directory that is a sub directory of fromRoot
	 * @param toRoot the directory to which the relative part of directory shall be mapped
	 * @return the mapped directory or <code>null</code> if directory is not a child of fromRoot
	 */
    public static Path mapDirectoryToOtherRoot(Path directory, Path fromRoot, Path toRoot) {
		if (directory.startsWith(fromRoot)) {
			Path jenkinsHomeRelativeJobPath = fromRoot.relativize(directory);
			if (jenkinsHomeRelativeJobPath.getNameCount() > 0) {
				Path localJobDir = toRoot.resolve(jenkinsHomeRelativeJobPath);
				return localJobDir;
			}
		}
		return null;
    }
}
