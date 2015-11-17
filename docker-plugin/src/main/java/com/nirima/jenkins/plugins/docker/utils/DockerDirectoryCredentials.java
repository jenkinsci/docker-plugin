package com.nirima.jenkins.plugins.docker.utils;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.Messages;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by magnayn on 17/11/2015.
 */
public class DockerDirectoryCredentials extends BaseStandardCredentials {

    String path;

    @DataBoundConstructor
    public DockerDirectoryCredentials(@CheckForNull CredentialsScope scope, @CheckForNull String id, @CheckForNull String description, @CheckForNull String path) {
        super(scope, id, description);
        this.path = Util.fixNull(path);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {
        public DescriptorImpl() {
        }

        public String getDisplayName() {
            return "Docker Certificates Directory";
        }
    }
}
