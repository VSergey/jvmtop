/*
 * jvmtop - java monitoring for the command-line
 *
 * Copyright (C) 2013 by Patric Rufflar. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.jvmtop.view;

import com.jvmtop.Config;
import com.jvmtop.monitor.VMInfo;
import com.jvmtop.openjdk.tools.LocalVirtualMachine;

import java.util.*;
import java.util.Map.Entry;

/**
 * "overview" view, providing the most-important metrics of all accessible jvms in a top-like manner.
 */
public class VMOverviewView extends AbstractConsoleView {
    private List<VMInfo> vmInfoList = new ArrayList<>();
    private Map<Integer, LocalVirtualMachine> vmMap = new HashMap<>();

    public VMOverviewView(Config p_config) {
        super(p_config);
    }

    public void printView() throws Exception
    {
        printHeader();
        //to reduce cpu effort, scan only every 5 iterations for new vms
        scanForNewVMs();
        updateVMs(vmInfoList);
        vmInfoList.sort(VMInfo.CPU_LOAD_COMPARATOR);

        for (VMInfo vmInfo : vmInfoList) {
            switch(vmInfo.getState()) {
                case ATTACHED:
                    printVM(vmInfo);
                    break;
                case ATTACHED_UPDATE_ERROR:
                    System.out.printf("%5d %-25.15s [ERROR: Could not fetch telemetries (Process DEAD?)] %n",
                            vmInfo.getId(), getEntryPointClass(vmInfo.getDisplayName()));
                    break;
                case ERROR_DURING_ATTACH:
                    System.out.printf("%5d %-25.15s [ERROR: Could not attach to VM] %n",
                            vmInfo.getId(), getEntryPointClass(vmInfo.getDisplayName()));
                    break;
                case CONNECTION_REFUSED:
                    System.out.printf("%5d %-25.15s [ERROR: Connection refused/access denied] %n",
                            vmInfo.getId(), getEntryPointClass(vmInfo.getDisplayName()));
                    break;
            }
        }
    }

    private String getEntryPointClass(String name) {
        if (name.indexOf(' ') > 0) {
            name = name.substring(0, name.indexOf(' '));
        }
        return rightStr(name, 15);
    }

    private void printVM(VMInfo vmInfo) throws Exception {
        String deadlockState = "";
        if (vmInfo.hasDeadlockThreads()) {
            deadlockState = "!D";
        }
        System.out.printf("%5d %-15.15s %5s %5s %5s %5s %5.2f%% %5.2f%% %-5.5s %8.8s %4d %2.2s%n",
                vmInfo.getId(), getEntryPointClass(vmInfo.getDisplayName()),
                toMB(vmInfo.getHeapUsed()), toMB(vmInfo.getHeapMax()),
                toMB(vmInfo.getNonHeapUsed()), toMB(vmInfo.getNonHeapMax()),
                vmInfo.getCpuLoad() * 100, vmInfo.getGcLoad() * 100,
                vmInfo.getVMVersion(), vmInfo.getOSUser(), vmInfo.getThreadCount(),
                deadlockState);
    }

    private void updateVMs(List<VMInfo> vmList) throws Exception {
        for (VMInfo vmInfo : vmList) {
            vmInfo.update();
        }
    }

    private void scanForNewVMs() {
        Map<Integer, LocalVirtualMachine> machines = LocalVirtualMachine.getNewVirtualMachines(vmMap);
        Set<Entry<Integer, LocalVirtualMachine>> set = machines.entrySet();

        for (Entry<Integer, LocalVirtualMachine> entry : set) {
            LocalVirtualMachine localvm = entry.getValue();
            int vmid = localvm.vmid();

            if (!vmMap.containsKey(vmid)) {
                VMInfo vmInfo = VMInfo.processNewVM(localvm, vmid);
                vmInfoList.add(vmInfo);
            }
        }
        vmMap = machines;
    }

    private void printHeader() {
        System.out.printf("%5s %-15.15s %5s %5s %5s %5s %6s %6s %5s %8s %4s %2s%n",
                "PID", "MAIN-CLASS", "HPCUR", "HPMAX", "NHCUR", "NHMAX", "CPU", "GC",
                "VM", "USERNAME", "#T", "DL");
    }

}
