package net.bluemix.containers.yaro;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;


public class FloatingIpManager{
	private static final Logger LOGGER = Logger.getLogger(FloatingIpManager.class.getName());

	public static String getIpByContainerId(String containerId) {
		return IDtoIP.get(containerId);
	}

	public static String getIpByHostName(String hostName) {
		String containerId = HOSTtoID.get(hostName);
		return getIpByContainerId(containerId);
	}

	public static void mapContainerIdtoIp(String containerId, String ip) {
		LOGGER.fine("mapping " + containerId + " --> " + ip);
		IDtoIP.put(containerId, ip);
		
	}

	public static void mapHostNameToContainerId(String hostName, String containerId) {
		LOGGER.fine("mapping " + hostName + "->" + containerId);
		HOSTtoID.put(hostName, containerId);
		
	}

	public static void cleanupByContainerId(String containerId) {

		String hostName = null;
		String ip = getIpByContainerId(containerId);
		for (Map.Entry<String, String> entry : HOSTtoID.entrySet()) {
		    String _hostName = entry.getKey();
		    String _containerId = entry.getValue();
		    if (_containerId.equals(containerId)) {
		    	hostName = _hostName;
		    	break;
		    }
		}
		HOSTtoID.remove(hostName);
		IDtoIP.remove(containerId);
		LOGGER.fine("unmapping " + hostName + " --> " + containerId+ " and " + containerId + " --> " + ip);
    }

		

	private static HashMap<String, String> IDtoIP = new HashMap<>();
	private static HashMap<String, String> HOSTtoID = new HashMap<>();
}