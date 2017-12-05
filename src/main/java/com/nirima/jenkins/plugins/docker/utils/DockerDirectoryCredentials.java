package com.nirima.jenkins.plugins.docker.utils;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.Messages;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.Secret;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;

/**
 * Created by magnayn on 17/11/2015.
 * @deprecated  use {@link org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials}
 */
@Deprecated
public class DockerDirectoryCredentials extends BaseStandardCredentials {

    String path;

    private DockerDirectoryCredentials(CredentialsScope scope, String id, String description) {
        super(scope, id, description);
    }


    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    private Object readResolve() throws IOException {
        final File f = new File(path);

        return new DockerServerCredentials(getScope(), getId(), getDescription(),
                FileUtils.readFileToString(new File(f, "key.pem")),
                FileUtils.readFileToString(new File(f, "cert.pem")),
                FileUtils.readFileToString(new File(f, "ca.pem")));
    }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {
        public DescriptorImpl() {
        }

        public String getDisplayName() {
            return "Docker Certificates Directory (Deprecated)";
        }

        @Override
        public boolean isApplicable(CredentialsProvider provider) {
            return false;
        }
    }
}
