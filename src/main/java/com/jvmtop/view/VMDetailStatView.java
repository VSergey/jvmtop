/*
 * jvmtop - java monitoring for the command-line
 *
 * Copyright (C) 2013 by Patric Rufflar. All rights reserved. DO NOT ALTER OR
 * REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 only, as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package com.jvmtop.view;

import com.jvmtop.Config;
import com.jvmtop.monitor.VMInfo;
import com.jvmtop.monitor.VMInfoState;
import com.jvmtop.openjdk.tools.LocalVirtualMachine;

/*
 * "detail" view, printing detail metrics of a specific jvm in a vmstat manner.
 */
public class VMDetailStatView extends AbstractConsoleView {
    private final VMInfo vmInfo;

    public VMDetailStatView(Config p_config) throws Exception {
        super(p_config);
        Integer pid = p_config.getPid();
        LocalVirtualMachine localVirtualMachine = LocalVirtualMachine.getLocalVirtualMachine(pid);
        vmInfo = VMInfo.processNewVM(localVirtualMachine, pid);
    }

    public void printView() throws Exception {
        vmInfo.update();

        if (vmInfo.getState() == VMInfoState.ATTACHED_UPDATE_ERROR) {
            System.out.println("ERROR: Could not fetch telemetries - Process terminated.");
            exit();
        } else if (vmInfo.getState() != VMInfoState.ATTACHED) {
            System.out.println("ERROR: Could not attach to process.");
            exit();
        } else {
            printVM(vmInfo);
        }
    }

    private void printVM(VMInfo vmInfo) throws Exception {
        String deadlockState = vmInfo.hasDeadlockThreads()? "!D" : "";

        System.out.printf("%5d %5s %5.2f%% %5.2f%% %4d %2.2s%n",
                vmInfo.getId(),
                toMB(vmInfo.getHeapUsed()),
                vmInfo.getCpuLoad() * 100,
                vmInfo.getGcLoad() * 100,
                vmInfo.getThreadCount(),
                deadlockState);
    }

    @Override
    public boolean isTopBarRequired() { return false; }

    @Override
    public boolean isClearingRequired() { return false; }
}
