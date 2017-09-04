package com.jvmtop.openjdk.tools;

import javax.management.MBeanServerConnection;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

class Snapshot {

    private Snapshot() {
    }

    static ProxyClient.SnapshotMBeanServerConnection newSnapshot(MBeanServerConnection mbsc) {
        final InvocationHandler ih = new SnapshotInvocationHandler(mbsc);
        return (ProxyClient.SnapshotMBeanServerConnection) Proxy.newProxyInstance(
                Snapshot.class.getClassLoader(),
                new Class[] {ProxyClient.SnapshotMBeanServerConnection.class},
                ih);
    }
}
