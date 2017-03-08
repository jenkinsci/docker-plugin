package net.bluemix.containers.yaro;

import java.io.File;
import java.io.FileReader;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ApiEndpoint {
	private static final Logger LOGGER = Logger.getLogger(ApiEndpoint.class.getName());


	private static String URL = "https://containers-api.ng.bluemix.net:8443/v3/";
	private static boolean isInitialized = false;

	public static String getEndpoint() {
		if (!isInitialized) {
			initialize();
			isInitialized = true;
		}
		return URL;

	}


	private static void initialize() {

	//deduct API endpoint from  $HOME/.cf/config.json
		String user_home = System.getProperty("user.home");
		String cf_config =  user_home + "/.cf/config.json";
		File f = new File(cf_config);
		if(!f.exists() || f.isDirectory()) { 
    		LOGGER.log(Level.SEVERE, "Can't locate $HOME/.cf/config.json.  Will default to public endpoint ");
    		return;
		}

		JSONParser parser = new JSONParser();
		String target = null;
 
        try {
 
            Object obj = parser.parse(new FileReader(cf_config));
 
            JSONObject jsonObject = (JSONObject) obj;
 
            target = (String) jsonObject.get("Target");

          } catch (Exception e) {
            e.printStackTrace();
          }	

          //deduct Container API endpoint from CF API endpoint
          //e.g. "http://api.ng.bluemix.net" ---> "https://containers-api.ng.bluemix.net"
          // or  "https://api.x.y.bluemix.net" ----> "https://containers-api.x.y.bluemix.net"

		  int i1 = target.indexOf("api");
          int i2= target.indexOf("bluemix.net");
          String url = "https://containers-api" + target.substring (i1+3,i2) + "bluemix.net";

          url = url + ":8443/v3/";
          URL=url;
          LOGGER.log(Level.INFO, "Setting Containers API endpoint to " + URL);

     }       


}

