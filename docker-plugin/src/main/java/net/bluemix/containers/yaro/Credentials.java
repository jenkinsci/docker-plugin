package net.bluemix.containers.yaro;

import java.io.File;
import java.io.FileReader;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.List;
import java.util.Date;
import java.util.Calendar;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import jenkins.model.Jenkins;

public class Credentials {

	private static final Logger LOGGER = Logger.getLogger(Credentials.class.getName());

	private static final String BLUEMIX_CREDS_JENKINS_ID = "BLUEMIX";
	private static final int  DAYS_TO_CACHE_BLUEMIX_CREDS = 3;

	private static Credentials _inst = null;
	private String space_guid = null;
	private String access_token = null;

	private String org = null;
	private String space = null;

	private Date nextRefresh = null;
	private long tokenLastModified;


	public static Credentials getInstance() {
		if (_inst == null  || _inst.needToRefresh()) {
			_inst = new Credentials();
			_inst.initialize();
		} 
		
		_inst.loadAccessToken();
		return _inst;
	}

	private Credentials() {

	}

	private boolean needToRefresh() {

		//allow to test cred init
		String testOn = System.getProperty("forceLogin");
		if (testOn != null) {
			LOGGER.log(Level.INFO, "Detected testing mode: forceLogin=" + testOn + ". Will force full Bluemix login");
			return true;
		}

		Date now = new Date();
		if (now.after(nextRefresh)) {
			return true;
		} else {
			return false;
		}

	}

	private void initialize() {

		//using credentials in Jenkins
		List<StandardUsernamePasswordCredentials> standardCredentials = CredentialsProvider.lookupCredentials(
			StandardUsernamePasswordCredentials.class,
      		Jenkins.getInstance(),
      		null,
      		(com.cloudbees.plugins.credentials.domains.DomainRequirement)null);

		StandardUsernamePasswordCredentials bluemixCreds = null;

		for (StandardUsernamePasswordCredentials creds : standardCredentials) {
			//System.out.println("Credentials: ID:" + creds.getId() + " Desc:" + creds.getDescription() + " UserName:" + creds.getUsername() + " Password: " + hudson.util.Secret.toString(creds.getPassword()));
			if (creds.getId().equals(BLUEMIX_CREDS_JENKINS_ID)) {
				bluemixCreds = creds;
				LOGGER.log(Level.INFO, "Received " + BLUEMIX_CREDS_JENKINS_ID + " credentials from Jenkins: userName=" + bluemixCreds.getUsername() + "    description (org:space)="+ bluemixCreds.getDescription());
				break;
			}
		}
		if (bluemixCreds == null) {
			LOGGER.log(Level.SEVERE, "Can't find credentials in Jenkins with ID= " + BLUEMIX_CREDS_JENKINS_ID + "  Please configure in Jenkins console");
			return;
		}
		
		//extract org:space from description
		//description must contain org:space
		String description = bluemixCreds.getDescription();
		String [] result = description.split(":");
		if (result.length != 2) 
			LOGGER.log(Level.SEVERE, "Error parsing  " + BLUEMIX_CREDS_JENKINS_ID + "  credentials from Jenkins. Expect description=org:space, found description="+  description);
		this.org = result[0];
		this.space = result[1];
		LOGGER.log(Level.INFO, "Parsed  " + BLUEMIX_CREDS_JENKINS_ID + "  credentials from Jenkins: org=" + org + " space=" + space);


		//Run cf login
		LOGGER.log(Level.INFO, "Logging in Bluemix as " + bluemixCreds.getUsername() + "..");
		String userName = bluemixCreds.getUsername();
		String pwd = hudson.util.Secret.toString(bluemixCreds.getPassword());
		//String command = "cf login -u " + userName + " -p " + pwd + " -o " + this.org + " -s " + this.space;
		String[] command1 = {"cf", "login", "-u", userName, "-p", pwd, "-o", this.org, "-s", this.space};
		//LOGGER.log(Level.INFO, command);
 
 		String output = ExecuteShellCommand.executeCommand(command1);
 		LOGGER.log(Level.INFO, output);

 		LOGGER.log(Level.INFO, "Refreshing Docker certs from Bluemix..");
 		String[] command2 = {"cf", "ic", "init"};
 		output = ExecuteShellCommand.executeCommand(command2);
 		LOGGER.log(Level.INFO, output);

		
        //set next login refresh to 24 hrs from now
        Date now = new Date();
		Calendar cal = Calendar.getInstance();
	    cal.setTime(new Date());
	    cal.add(Calendar.DATE, DAYS_TO_CACHE_BLUEMIX_CREDS); //few days later
	    this.nextRefresh =  cal.getTime();
	    LOGGER.log(Level.INFO, "Success. Bluemix credentials will be cached until  " + this.nextRefresh);

	}


	private void loadAccessToken() {
		//look up cf login info for current user in $HOME/.cf/config.json
		String user_home = System.getProperty("user.home");
		String cf_config =  user_home + "/.cf/config.json";

		File f = new File(cf_config);
		if(!f.exists() || f.isDirectory()) { 
    		LOGGER.log(Level.SEVERE, "Can't locate " + cf_config);
    		return;
		}

		long lastModified = f.lastModified();
		if (lastModified <= tokenLastModified) {
			LOGGER.log(Level.INFO, "Using cached Bluemix credentials from " + cf_config);
			return;
		}
		tokenLastModified = lastModified;

		LOGGER.log(Level.INFO, "Loading Bluemix credentials from " + cf_config);

		JSONParser parser = new JSONParser(); 
        try {
 
            Object obj = parser.parse(new FileReader(cf_config));
 
            JSONObject jsonObject = (JSONObject) obj;
 
            this.access_token = (String) jsonObject.get("AccessToken");
            if (this.access_token == null) {
            	LOGGER.log(Level.SEVERE, "Can't find AccessToken in " + cf_config);
            	return;
            }
            JSONObject spaceFields = (JSONObject) jsonObject.get("SpaceFields");
            this.space_guid = (String) spaceFields.get("Guid");
            if ( this.space_guid == null)
            	this.space_guid = (String) spaceFields.get("GUID");	 //on Windows its all caps, on Mac mixed case
            String space_name = (String) spaceFields.get("Name");

            LOGGER.log(Level.INFO, "Found AccessToken for space " + space_name + " " + space_guid); 
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