package net.bluemix.containers.yaro;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class HttpClient {

	private String responseBody = null;
	private int responseCode = 0;
	private Credentials creds = null;
	private final String USER_AGENT = "Mozilla/5.0";
	private final String CONTENT_TYPE = "application/json";

	public HttpClient() {
		creds = Credentials.getInstance();

	}

	public String getResponseBody(){
		return responseBody;
	}

	public int getResponseCode(){
		return responseCode;
	}

	// HTTP GET request
	public void sendGet(String url) throws Exception {

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();

		// set request method
		con.setRequestMethod("GET");

		//add request headers
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Content-Type", CONTENT_TYPE);
		con.setRequestProperty("Authorization", creds.getAccessToken());
		con.setRequestProperty("X-Auth-Project-Id", creds.getSpaceGuid());


		this.responseCode = con.getResponseCode();

		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		//result
		this.responseBody = response.toString();

	}

    // HTTP POST request
	public void sendPost(String url, String payload) throws Exception {

		URL obj = new URL(url);
		HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

		// set request method
		con.setRequestMethod("POST");

		//add request headers
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Content-Type", CONTENT_TYPE);
		con.setRequestProperty("Authorization", creds.getAccessToken());
		con.setRequestProperty("X-Auth-Project-Id", creds.getSpaceGuid());


		if (payload ==null) {
			con.setDoOutput(false);
		} else {
			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(payload);
			wr.flush();
			wr.close();
		}

		this.responseCode = con.getResponseCode();


		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		//result
		this.responseBody = response.toString();

	}


}