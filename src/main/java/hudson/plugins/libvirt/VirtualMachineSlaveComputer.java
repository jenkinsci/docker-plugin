/**
 *  Copyright (C) 2010, Byte-Code srl <http://www.byte-code.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Date: Mar 04, 2010
 * Author: Marco Mornati<mmornati@byte-code.com>
 */

package hudson.plugins.libvirt;

import hudson.slaves.SlaveComputer;
import hudson.model.Slave;
import org.libvirt.Connect;

public class VirtualMachineSlaveComputer extends SlaveComputer {

    /**
     * Cached connection to the virtaul datacenter. Lazily fetched.
     */
    private volatile Connect hypervisorConnection;

    public VirtualMachineSlaveComputer(Slave slave) {
        super(slave);
    }
}
