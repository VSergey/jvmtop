package com.jvmtop.openjdk.tools;

import javax.management.*;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

class SnapshotInvocationHandler implements InvocationHandler {
    private final MBeanServerConnection conn;
    private Map<ObjectName, NameValueMap> cachedValues = newMap();
    private Map<ObjectName, Set<String>> cachedNames = newMap();

    @SuppressWarnings("serial")
    private static final class NameValueMap extends HashMap<String, Object> {}

    SnapshotInvocationHandler(MBeanServerConnection conn) {
        this.conn = conn;
    }

    synchronized void flush() {
        cachedValues = newMap();
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final String methodName = method.getName();
        if (methodName.equals("getAttribute")) {
            return getAttribute((ObjectName) args[0], (String) args[1]);
        }
        if (methodName.equals("getAttributes")) {
            return getAttributes((ObjectName) args[0], (String[]) args[1]);
        }
        if (methodName.equals("flush")) {
            flush();
            return null;
        }
        try {
            return method.invoke(conn, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object getAttribute(ObjectName objName, String attrName) throws MBeanException, InstanceNotFoundException,
            AttributeNotFoundException, ReflectionException, IOException {
        final NameValueMap values = getCachedAttributes(objName, Collections.singleton(attrName));
        Object value = values.get(attrName);
        if (value != null || values.containsKey(attrName)) {
            return value;
        }
        // Not in cache, presumably because it was omitted from the
        // getAttributes result because of an exception.  Following
        // call will probably provoke the same exception.
        return conn.getAttribute(objName, attrName);
    }

    private AttributeList getAttributes(ObjectName objName, String[] attrNames) throws
            InstanceNotFoundException, ReflectionException, IOException {
        final NameValueMap values = getCachedAttributes(objName, new TreeSet<>(Arrays.asList(attrNames)));
        final AttributeList list = new AttributeList();
        for (String attrName : attrNames) {
            final Object value = values.get(attrName);
            if (value != null || values.containsKey(attrName)) {
                list.add(new Attribute(attrName, value));
            }
        }
        return list;
    }

    private synchronized NameValueMap getCachedAttributes(ObjectName objName, Set<String> attrNames) throws
            InstanceNotFoundException, ReflectionException, IOException {
        NameValueMap values = cachedValues.get(objName);
        if (values != null && values.keySet().containsAll(attrNames)) {
            return values;
        }
        attrNames = new TreeSet<>(attrNames);
        Set<String> oldNames = cachedNames.get(objName);
        if (oldNames != null) {
            attrNames.addAll(oldNames);
        }
        values = new NameValueMap();
        final AttributeList attrs = conn.getAttributes(objName, attrNames.toArray(new String[attrNames.size()]));
        for (Attribute attr : attrs.asList()) {
            values.put(attr.getName(), attr.getValue());
        }
        cachedValues.put(objName, values);
        cachedNames.put(objName, attrNames);
        return values;
    }

    // See http://www.artima.com/weblogs/viewpost.jsp?thread=79394
    private static <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }
}
