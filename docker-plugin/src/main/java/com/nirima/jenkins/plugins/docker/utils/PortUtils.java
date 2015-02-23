package com.nirima.jenkins.plugins.docker.utils;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PortUtils {

    private static final int RETRIES = 10;
    private static final int WAIT_TIME_MS = 2000;

    public static boolean isPortAvailable(String host, int port) {
        Socket socket = null;
        boolean available = false;
        try {
            socket = new Socket(host, port);
            available = true;
        } catch (IOException e) {
            // no-op
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // no-op
                }
            }
        }
        return available;
    }

    public static boolean waitForPort(String host, int port) {
        for (int i = 0; i < RETRIES; i++) {
            if(isPortAvailable(host, port))
                return true;
                        
            try {
                Thread.sleep(WAIT_TIME_MS);
            } catch (InterruptedException e) {
                // no-op
            }
        }
        return false;
    }

    public static Map<String, List<Integer>> parsePorts(String waitPorts) throws IllegalArgumentException,
            NumberFormatException {
        Map<String, List<Integer>> containers = new HashMap<String, List<Integer>>();
        String[] containerPorts = waitPorts.split(System.getProperty("line.separator"));
        for (String container : containerPorts) {
            String[] idPorts = container.split(" ", 2);
            if (idPorts.length < 2)
                throw new IllegalArgumentException("Cannot parse " + idPorts + " as '[conainerId] [port1],[port2],...'");
            String containerId = idPorts[0].trim();
            String portsStr = idPorts[1].trim();

            List<Integer> ports = new ArrayList<Integer>();
            for (String port : portsStr.split(",")) {
                ports.add(new Integer(port));
            }
            containers.put(containerId, ports);
        }
        return containers;
    }

}
