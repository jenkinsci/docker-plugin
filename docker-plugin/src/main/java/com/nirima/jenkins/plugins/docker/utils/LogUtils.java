package com.nirima.jenkins.plugins.docker.utils;

import com.github.dockerjava.api.model.ResponseItem;
import hudson.model.BuildListener;
import hudson.model.TaskListener;

/**
 * @author Kanstantsin Shautsou
 */
public class LogUtils {
    private LogUtils() {
    }

    public static void printResponseItemToListener(TaskListener listener, ResponseItem item) {

        if (item != null && item.getStatus() != null) {
            if (item.getError() != null) {
                listener.error(item.getError());
            }

            final StringBuilder stringBuffer = new StringBuilder();

            if (item.getId() != null) {
                stringBuffer.append(item.getId()).append(": "); // Doesn't exist before "Digest"
            }

            stringBuffer.append(item.getStatus());

            if (item.getProgress() != null) {
                stringBuffer.append(" ").append(item.getProgress());
            }

            listener.getLogger().println(stringBuffer.toString());
        }
    }
}
