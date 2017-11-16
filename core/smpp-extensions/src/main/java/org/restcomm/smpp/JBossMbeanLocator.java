package org.restcomm.smpp;

import java.util.Iterator;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

public class JBossMbeanLocator {

    private static MBeanServer instance = null;

    public static MBeanServer locateJBoss() {
        synchronized (JBossMbeanLocator.class) {
            if (instance != null) {
                return instance;
            }
        }
        for (Iterator i = MBeanServerFactory.findMBeanServer(null).iterator(); i.hasNext();) {
            MBeanServer server = (MBeanServer) i.next();
            if (server.getDefaultDomain().equals("jboss")) {
                return server;
            }
        }
        throw new IllegalStateException("No 'jboss' MBeanServer found!");
    }
}
