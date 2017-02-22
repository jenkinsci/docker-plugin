package net.bluemix.containers.yaro;


import java.util.logging.Level;
import java.util.logging.Logger;


public class ReleaseFloatingIpCmd {
	private static final Logger LOGGER = Logger.getLogger(ReleaseFloatingIpCmd.class.getName());

	private String ip = null;
	public ReleaseFloatingIpCmd(String _ip) {
		this.ip = _ip;
	}

	public String getFloatingIp() {
		return ip;
	}

	public void setFloatingIp(String _ip) {
		this.ip = _ip;
	}

	public void exec() throws BluemixException {

		LOGGER.log(Level.INFO, "Invoking Bluemix API to release a floating IP address " + ip);

		try {
			HttpClient c = new HttpClient();
			String url =  ApiEndpoint.getEndpoint()  + "containers/floating-ips/" + ip + "/release";
			c.sendPost(url, null);
			
			int responseCode = c.getResponseCode();

			if (responseCode == 401) {
				LOGGER.log(Level.SEVERE, "Authentication failed. The Access Token is invalid, or the Space ID could not be found");
				throw new BluemixException(responseCode);
			} else if (responseCode == 404) {
				LOGGER.log(Level.SEVERE, "Not found. The public IP address that you entered, could not be found. ");
				throw new BluemixException(responseCode);
			} else if (responseCode == 500) {
				LOGGER.log(Level.SEVERE, "Internal Server Error. The IBM Containers service is currently unavailable.");
				throw new BluemixException(responseCode);
			}  else if (responseCode > 300) {
				LOGGER.log(Level.SEVERE, "Unexpected response code. " +  responseCode);
				throw new BluemixException(responseCode);
			}

			
		}  catch (Exception e) {

			LOGGER.log(Level.SEVERE, "Unexpected error invoking Bluemix API POST /containers/floating-ips/request  Cause =   " + e.getCause());
			e.printStackTrace();

		}  

		LOGGER.log(Level.INFO, "Success. Floating IP address "+ip+ " has been released");

	}

}