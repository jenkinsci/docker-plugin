package net.bluemix.containers.yaro;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Test {
	private static final Logger LOGGER = Logger.getLogger(ReleaseFloatingIpCmd.class.getName());

	public static void main(String [ ] args) throws Exception {
		RequestFloatingIpCmd cmd1 = new RequestFloatingIpCmd();
		cmd1.exec();
		String ip = cmd1.getFloatingIp();
		ReleaseFloatingIpCmd cmd2 = new ReleaseFloatingIpCmd(ip);
		cmd2.exec();
     
	}
}