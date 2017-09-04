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
package com.jvmtop.monitor;

import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.rmi.ConnectException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvmtop.openjdk.tools.ConnectionState;
import com.jvmtop.openjdk.tools.LocalVirtualMachine;
import com.jvmtop.openjdk.tools.ProxyClient;
import com.sun.tools.attach.AttachNotSupportedException;

/**
 * VMInfo retrieves or updates the metrics for a specific remote jvm,
 * using ProxyClient.
 *
 * TODO: refactor this class, seperate:
 * - connect / attach code
 * - updating metrics
 * - model
 */
public class VMInfo {
    public static final Comparator<VMInfo> USED_HEAP_COMPARATOR = new UsedHeapComparator();
    public static final Comparator<VMInfo> CPU_LOAD_COMPARATOR  = new CPULoadComparator();

    private VMInfoState state = VMInfoState.INIT;
    private ProxyClient proxyClient;
    private Collection<java.lang.management.GarbageCollectorMXBean> gcMXBeans;
    private OperatingSystemMXBean osBean;
    private RuntimeMXBean runtimeMXBean;
    private ClassLoadingMXBean classLoadingMXBean;
    private MemoryMXBean memoryMXBean;
    private ThreadMXBean threadMXBean;

    private MemoryUsage heapMemoryUsage;
    private MemoryUsage nonHeapMemoryUsage;
    private int vmId;

    private long lastGcTime;
    private long lastUpTime = -1;
    private long lastCPUTime = -1;
    private long gcCount;
    private long deltaUptime;
    private long deltaCpuTime;
    private long deltaGcTime;
    private long totalLoadedClassCount;
    private long threadCount;

    private double cpuLoad;
    private double gcLoad;
    private int updateErrorCount = 0;
    private boolean deadlocksDetected;

    private LocalVirtualMachine localVm;
    private String vmVersion;
    private String osUser;
    private Map<String, String> systemProperties;

    /**
     * Comparator providing ordering of VMInfo objects by the current heap usage of their monitored jvms
     */
    private static final class UsedHeapComparator implements Comparator<VMInfo> {
        public int compare(VMInfo o1, VMInfo o2) {
            return Long.compare(o1.getHeapUsed(), o2.getHeapUsed());
        }
    }

    /**
     * Comparator providing ordering of VMInfo objects by the current CPU usage of their monitored jvms
     */
    private static final class CPULoadComparator implements Comparator<VMInfo> {
        public int compare(VMInfo o1, VMInfo o2) {
            return Double.compare(o2.getCpuLoad(), o1.getCpuLoad());
        }
    }

    private VMInfo(int p_vmid, LocalVirtualMachine p_localVm, VMInfoState p_state) {
        vmId = p_vmid;
        localVm = p_localVm;
        state = p_state;
    }

    private VMInfo(int p_vmid, ProxyClient p_proxyClient, LocalVirtualMachine p_localVm) throws Exception {
        vmId = p_vmid;
        proxyClient = p_proxyClient;
        localVm = p_localVm;
        state = VMInfoState.ATTACHED;
        update();
    }

    public static VMInfo processNewVM(LocalVirtualMachine localvm, int vmid) {
        try {
            if (localvm == null || !localvm.isAttachable()) {
                Logger.getLogger("jvmtop").log(Level.FINE, "jvm is not attachable (PID=" + vmid + ")");
                return VMInfo.createDeadVM(vmid, localvm);
            }
            return attachToVM(localvm, vmid);
        } catch (Exception e) {
            Logger.getLogger("jvmtop").log(Level.FINE, "error during attach (PID=" + vmid + ")", e);
            return VMInfo.createDeadVM(vmid, localvm);
        }
    }

    /**
     *
     * Creates a new VMInfo which is attached to a given LocalVirtualMachine
     */
    private static VMInfo attachToVM(LocalVirtualMachine localvm, int vmid) throws Exception {
        //VirtualMachine vm = VirtualMachine.attach("" + vmid);
        try {
            ProxyClient proxyClient = ProxyClient.getProxyClient(localvm);
            proxyClient.connect();
            if (proxyClient.getConnectionState() == ConnectionState.DISCONNECTED) {
                Logger.getLogger("jvmtop").log(Level.FINE, "connection refused (PID=" + vmid + ")");
                return createDeadVM(vmid, localvm);
            }
            return new VMInfo(vmid, proxyClient, localvm);
        } catch (ConnectException rmiE) {
            if (rmiE.getMessage().contains("refused")) {
                Logger.getLogger("jvmtop").log(Level.FINE, "connection refused (PID=" + vmid + ")", rmiE);
                return createDeadVM(vmid, localvm, VMInfoState.CONNECTION_REFUSED);
            }
            rmiE.printStackTrace(System.err);
        } catch (IOException e) {
            if ((e.getCause() != null
                    && e.getCause() instanceof AttachNotSupportedException)
                    || e.getMessage().contains("Permission denied"))
            {
                Logger.getLogger("jvmtop").log(Level.FINE, "could not attach (PID=" + vmid + ")", e);
                return createDeadVM(vmid, localvm, VMInfoState.CONNECTION_REFUSED);
            }
            e.printStackTrace(System.err);
        } catch (Exception e) {
            Logger.getLogger("jvmtop").log(Level.WARNING, "could not attach (PID=" + vmid + ")", e);
        }
        return createDeadVM(vmid, localvm);
    }

    /**
     * Creates a dead VMInfo, representing a jvm which cannot be attached or other monitoring issues occurred.
     */
    private static VMInfo createDeadVM(int vmid, LocalVirtualMachine localVm) {
        return createDeadVM(vmid, localVm, VMInfoState.ERROR_DURING_ATTACH);
    }

    /**
     * Creates a dead VMInfo, representing a jvm in a given state
     * which cannot be attached or other monitoring issues occurred.
     */
    private static VMInfo createDeadVM(int vmid, LocalVirtualMachine localVm, VMInfoState state) {
        return new VMInfo(vmid, localVm, state);
    }

    /**
     * @return the state
     */
    public VMInfoState getState()
    {
        return state;
    }

    /**
     * Updates all jvm metrics to the most recent remote values
     */
    public void update() throws Exception {
        switch(state) {
            case ERROR_DURING_ATTACH:
            case DETACHED:
            case CONNECTION_REFUSED:
                return;
        }
        if (proxyClient.isDead()) {
            state = VMInfoState.DETACHED;
            return;
        }
        try {
            proxyClient.flush();

            osBean = proxyClient.getSunOperatingSystemMXBean();
            runtimeMXBean = proxyClient.getRuntimeMXBean();
            gcMXBeans = proxyClient.getGarbageCollectorMXBeans();
            classLoadingMXBean = proxyClient.getClassLoadingMXBean();
            memoryMXBean = proxyClient.getMemoryMXBean();
            heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
            nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
            threadMXBean = proxyClient.getThreadMXBean();

            //TODO: fetch jvm-constant data only once
            systemProperties = runtimeMXBean.getSystemProperties();
            vmVersion = extractShortVer();
            osUser = systemProperties.get("user.name");
            updateInternal();

            deadlocksDetected = threadMXBean.findDeadlockedThreads() != null
                    || threadMXBean.findMonitorDeadlockedThreads() != null;

        } catch (Throwable e) {
            Logger.getLogger("jvmtop").log(Level.FINE, "error during update", e);
            updateErrorCount++;
            if (updateErrorCount > 10) {
                state = VMInfoState.DETACHED;
            } else {
                state = VMInfoState.ATTACHED_UPDATE_ERROR;
            }
        }
    }

    /**
     * calculates internal delta metrics
     */
    private void updateInternal() throws Exception {
        long uptime = runtimeMXBean.getUptime();
        long cpuTime = proxyClient.getProcessCpuTime();
        //long cpuTime = osBean.getProcessCpuTime();
        long gcTime = sumGCTimes();
        gcCount = sumGCCount();
        if (lastUpTime > 0 && lastCPUTime > 0 && gcTime > 0) {
            deltaUptime = uptime - lastUpTime;
            deltaCpuTime = (cpuTime - lastCPUTime) / 1000000;
            deltaGcTime = gcTime - lastGcTime;
            gcLoad = calcLoad(deltaCpuTime, deltaGcTime);
            cpuLoad = calcLoad(deltaUptime, deltaCpuTime);
        }
        lastUpTime = uptime;
        lastCPUTime = cpuTime;
        lastGcTime = gcTime;
        totalLoadedClassCount = classLoadingMXBean.getTotalLoadedClassCount();
        threadCount = threadMXBean.getThreadCount();
    }

    /**
     * calculates a "load", given on two deltas
     */
    private double calcLoad(double deltaUptime, double deltaTime) {
        if (deltaTime <= 0 || deltaUptime == 0) {
            return 0.0;
        }
        return Math.min(99.0, deltaTime / (deltaUptime * osBean.getAvailableProcessors()));
    }

    /**
     * Returns the sum of all GC times
     */
    private long sumGCTimes() {
        long sum = 0;
        for (java.lang.management.GarbageCollectorMXBean mxBean : gcMXBeans) {
            sum += mxBean.getCollectionTime();
        }
        return sum;
    }

    /**
     * Returns the sum of all GC invocations
     */
    private long sumGCCount() {
        long sum = 0;
        for (java.lang.management.GarbageCollectorMXBean mxBean : gcMXBeans) {
            sum += mxBean.getCollectionCount();
        }
        return sum;
    }

    public long getHeapSize() { return heapMemoryUsage.getCommitted(); }
    public long getHeapUsed() { return heapMemoryUsage.getUsed(); }
    public long getHeapMax() { return heapMemoryUsage.getMax(); }
    public long getNonHeapUsed() { return nonHeapMemoryUsage.getUsed(); }
    public long getNonHeapMax() { return nonHeapMemoryUsage.getMax(); }

    public long getTotalLoadedClassCount() { return totalLoadedClassCount; }
    public boolean hasDeadlockThreads() { return deadlocksDetected; }
    public long getThreadCount() { return threadCount; }
    public double getCpuLoad() { return cpuLoad; }
    public double getGcLoad() { return gcLoad; }
    public ProxyClient getProxyClient() { return proxyClient; }
    public String getDisplayName() { return localVm.displayName(); }
    public Integer getId() { return localVm.vmid(); }
    public int getVMId() { return vmId; }
    public long getGcCount() { return gcCount; }
    public String getVMVersion() { return vmVersion; }
    public String getOSUser() { return osUser; }
    public long getGcTime() { return lastGcTime; }
    public long getDeltaUptime() { return deltaUptime; }
    public long getDeltaCpuTime() { return deltaCpuTime; }
    public long getDeltaGcTime() { return deltaGcTime; }
    public Map<String, String> getSystemProperties() { return systemProperties; }

    public RuntimeMXBean getRuntimeMXBean() { return runtimeMXBean; }
    public Collection<java.lang.management.GarbageCollectorMXBean> getGcMXBeans() { return gcMXBeans; }
    public MemoryMXBean getMemoryMXBean()  { return memoryMXBean; }
    public ThreadMXBean getThreadMXBean() { return threadMXBean; }
    public OperatingSystemMXBean getOSBean() { return osBean; }

    /**
     * Extracts the jvmtop "short version" out of different properties
     */
    private String extractShortVer() {
        String vmVer = systemProperties.get("java.runtime.version");
        String vmVendor = systemProperties.get("java.vendor");
        Pattern pattern = Pattern.compile("[0-9]\\.([0-9])\\.0_([0-9]+)-.*");
        Matcher matcher = pattern.matcher(vmVer);
        if (matcher.matches()) {
            return vmVendor.charAt(0) + matcher.group(1) + "U" + matcher.group(2);
        } else {
            pattern = Pattern.compile(".*-(.*)_.*");
            matcher = pattern.matcher(vmVer);
            if (matcher.matches()) {
                return vmVendor.charAt(0) + matcher.group(1).substring(2, 6);
            }
            return vmVer;
        }
    }
}
