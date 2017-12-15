package io.jenkins.docker;

import hudson.model.Descriptor.FormException;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DockerTransientNodeTest {

  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Test
  public void checkNodeNameStartWithCloudName()
  {
    try {
      DockerTransientNode node = new DockerTransientNode("1234567890123456", "/home/jenkins",null);
      node.setCloudId("CloudNode");
      Assert.assertTrue(node.getNodeName().startsWith("CloudNode"));
    } catch (FormException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
