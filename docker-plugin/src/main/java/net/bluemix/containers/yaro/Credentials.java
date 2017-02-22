package net.bluemix.containers.yaro;

import java.io.File;
import java.io.FileReader;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Credentials {

	private static final Logger LOGGER = Logger.getLogger(Credentials.class.getName());

	private static Credentials _inst = null;
	private String space_guid = null;
	private String access_token = null;


	public static Credentials getInstance() {
		if (_inst == null) {
			_inst = new Credentials();
			_inst.initialize();
		}

		return _inst;
	}

	private Credentials() {

	}

	private void initialize() {
		//look up cf login info for current user in $HOME/.cf/config.json
		String user_home = System.getProperty("user.home");
		String cf_config =  user_home + "/.cf/config.json";
		LOGGER.log(Level.INFO, "Loading Bluemix credentials from " + cf_config);
		File f = new File(cf_config);
		if(!f.exists() || f.isDirectory()) { 
    		LOGGER.log(Level.SEVERE, "Can't locate cf credentials. Please use cf login command to initialize. ");
    		return;
		}

		JSONParser parser = new JSONParser();
 
        try {
 
            Object obj = parser.parse(new FileReader(cf_config));
 
            JSONObject jsonObject = (JSONObject) obj;
 
            this.access_token = (String) jsonObject.get("AccessToken");
            JSONObject spaceFields = (JSONObject) jsonObject.get("SpaceFields");
            this.space_guid = (String) spaceFields.get("Guid");
            String space_name = (String) spaceFields.get("Name");

            LOGGER.log(Level.INFO, "Found cf credentials for space " + space_name); 
  
        } catch (Exception e) {
            e.printStackTrace();
        }		

	}

	public String getSpaceGuid() {
		return space_guid;
	}

	public String getAccessToken() {
		return access_token;
	}	
	
}