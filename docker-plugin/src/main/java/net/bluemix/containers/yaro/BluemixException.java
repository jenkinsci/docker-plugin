package net.bluemix.containers.yaro;

public class BluemixException extends Exception {
	private int statusCode = 0;
	public BluemixException(int code) {
		statusCode = code;
	}

	public int getStatusCode() {
		return statusCode;
	}

}