package net.bluemix.containers.yaro;

import java.util.logging.Level;
import java.util.logging.Logger;


public class RequestFloatingIpCmd {
	private static final Logger LOGGER = Logger.getLogger(RequestFloatingIpCmd.class.getName());

	private String ip = null;

	public RequestFloatingIpCmd() {}

	public String getFloatingIp() {
		return ip;
	}

	public void exec() throws BluemixException {

		LOGGER.log(Level.INFO, "Invoking Bluemix API to request new floating IP address");

		try {
			HttpClient c = new HttpClient();
			String url = ApiEndpoint.URL + "containers/floating-ips/request";
			c.sendPost(url, null);
			
			int responseCode = c.getResponseCode();
			if (responseCode == 401) {
				LOGGER.log(Level.SEVERE, "Authentication failed. The Access Token is invalid, or the Space ID could not be found");
				throw new BluemixException(responseCode);
			} else if (responseCode == 402) {
				LOGGER.log(Level.SEVERE, "Payment required. This request exceeds the quota that is allocated to the space. ");
				throw new BluemixException(responseCode);
			} else if (responseCode == 409) {
				LOGGER.log(Level.SEVERE, "Conflict. This request exceeds the quota that is allocated to the space ");
				throw new BluemixException(responseCode);
			} else if (responseCode == 500) {
				LOGGER.log(Level.SEVERE, "Internal Server Error. The IBM Containers service is currently unavailable.");
				throw new BluemixException(responseCode);
			} else if (responseCode > 300) {
				LOGGER.log(Level.SEVERE, "Unexpected response code. " +  responseCode);
				throw new BluemixException(responseCode);
			}

			ip = c.getResponseBody();
			//trim quotes in "1.2.3.4"
			ip=ip.replace("\"", "");
		}  catch (Exception e) {

			LOGGER.log(Level.SEVERE, "Unexpected error invoking Bluemix API POST /containers/floating-ips/request  Cause =   " + e.getCause());
			e.printStackTrace();

		}  

		LOGGER.log(Level.INFO, "Success. Allocated floating IP address "+ip);

	}

}