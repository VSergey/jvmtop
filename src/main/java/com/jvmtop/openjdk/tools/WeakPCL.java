package com.jvmtop.openjdk.tools;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;

/**
 * The PropertyChangeListener is handled via a WeakReference
 * so as not to pin down the listener.
 */
class WeakPCL extends WeakReference<PropertyChangeListener> implements PropertyChangeListener {
    private final ProxyClient proxyClient;

    WeakPCL(ProxyClient p_proxyClient, PropertyChangeListener referent) {
        super(referent);
        proxyClient = p_proxyClient;
    }

    public void propertyChange(PropertyChangeEvent pce) {
        PropertyChangeListener pcl = get();

        if (pcl == null) {
            // The referent listener was GC'ed, we're no longer
            // interested in PropertyChanges, remove the listener.
            dispose();
        } else {
            pcl.propertyChange(pce);
        }
    }

    private void dispose() {
        proxyClient.removePropertyChangeListener(this);
    }
}
