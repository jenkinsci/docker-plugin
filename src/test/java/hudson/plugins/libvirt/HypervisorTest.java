/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.libvirt;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author mmornati
 */
public class HypervisorTest extends HudsonTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();

    }

    public void testDummy () {
    	// placeholder while there are no real tests
    }
    
    
    // this doesnt work for everyone ...
//    public void testCreation() {
//        Hypervisor hp = new Hypervisor("test", "localhost", 22, "default", "philipp");
//        assertEquals("Wrong Virtual Machines Size", 4, hp.getVirtualMachines().size());
//        //assertEquals("Wrong Virtual Machine Name", "test", hp.getVirtualMachines().get(0).getName());
//        assertEquals("Wrong Hypervisor", hp, hp.getVirtualMachines().get(0).getHypervisor());
//    }
}
