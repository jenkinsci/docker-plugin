package io.jenkins.docker.connector;

import com.github.dockerjava.api.model.Ports;
import java.io.Serializable;
import java.util.Comparator;

public class HostPortComparator implements Comparator<Ports.Binding>, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public int compare(Ports.Binding binding1, Ports.Binding binding2) {
        String hostIp1 = binding1.getHostIp();
        if (hostIp1 == null) {
            hostIp1 = "0.0.0.0";
        }
        String hostIp2 = binding2.getHostIp();
        if (hostIp2 == null) {
            hostIp2 = "0.0.0.0";
        }
        if (!isLocalhost(hostIp1)) {
            if (isLocalhost(hostIp2)) {
                return -1;
            }
        } else if (!isLocalhost(hostIp2)) {
            return 1;
        }
        return hostIp1.compareTo(hostIp2);
    }

    private static boolean isLocalhost(String hostIp) {
        return "0.0.0.0".equals(hostIp) || "::".equals(hostIp);
    }
}
