package org.restcomm.smpp.service;

import javolution.util.FastList;
import javolution.util.FastMap;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.logging.Logger;
import org.jboss.msc.service.*;
import org.jboss.msc.value.InjectedValue;
import org.mobicents.protocols.ss7.scheduler.DefaultClock;
import org.mobicents.protocols.ss7.scheduler.Scheduler;
import org.mobicents.ss7.management.console.ShellExecutor;
import org.mobicents.ss7.management.console.ShellServer;
import org.restcomm.smpp.SmppManagement;
import org.restcomm.smpp.oam.SmppShellExecutor;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.util.Map;

public class SmppService implements Service<SmppService> {

    private final Logger log = Logger.getLogger(SmppService.class);

    public static ServiceName getServiceName() {
        return ServiceName.of("restcomm","smpp-extensions");
    }

    private final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<PathManager>();
    public InjectedValue<PathManager> getPathManagerInjector() {
        return pathManagerInjector;
    }
    private final InjectedValue<MBeanServer> mbeanServer = new InjectedValue<MBeanServer>();
    public InjectedValue<MBeanServer> getMbeanServer() {
        return mbeanServer;
    }

    //private Map<String, Object> beanObjects = new FastMap<String, Object>();
    private static final String DATA_DIR = "jboss.server.data.dir";

    @Override
    public SmppService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
        log.info("Starting SmppExtension Service");

        String dataDir = pathManagerInjector.getValue().getPathEntry(DATA_DIR).resolvePath();

        // no props
        DefaultClock ss7Clock = null;
        StandardMBean ss7ClockMBean = null;
        try {
            ss7Clock = new DefaultClock();

            ss7ClockMBean = new StandardMBean(ss7Clock, org.mobicents.protocols.ss7.scheduler.Clock.class);
        } catch (Exception e) {
            log.warn("SS7Clock MBean creating is failed: "+e);
        }

        //this.beanObjects.put("SS7Clock", ss7Clock);
        registerMBean(ss7ClockMBean, "org.restcomm.smpp:name=SS7Clock");

        // no props
        Scheduler schedulerMBean = null;
        try {
            schedulerMBean = new Scheduler();
            schedulerMBean.setClock(ss7Clock);
            schedulerMBean.start();
        } catch (Exception e) {
            log.warn("SS7Scheduler MBean creating is failed: "+e);
        }

        //this.beanObjects.put("SS7Scheduler", schedulerMBean);
        registerMBean(schedulerMBean, "org.restcomm.smpp:name=SS7Scheduler");

        //
        SmppManagement smppManagementMBean = null;
        smppManagementMBean = SmppManagement.getInstance("SmppManagement");
        smppManagementMBean.setMbeanServer(getMbeanServer().getValue());
        smppManagementMBean.setPersistDir(dataDir);
        //try {
        //    smppManagementMBean.startSmppManagement();
        //} catch (Exception e) {
        //    e.printStackTrace();
        //}

        registerMBean(smppManagementMBean, "org.restcomm.smpp:name=SmppManagement");

        SmppShellExecutor smppShellExecutor = null;
        StandardMBean smppShellExecutorMBean = null;
        try {
            smppShellExecutor = new SmppShellExecutor();
            smppShellExecutor.setSmppManagement(smppManagementMBean);
            smppShellExecutor.start();

            smppShellExecutorMBean = new StandardMBean(smppShellExecutor, org.mobicents.ss7.management.console.ShellExecutor.class);
        } catch (Exception e) {
            log.warn("SccpExecutor MBean creating is failed: "+e);
        }

        registerMBean(smppShellExecutorMBean, "org.restcomm.smpp:name=SmppShellExecutor");

        // constructor: shellExecutors?
        ShellServer shellExecutorMBean = null;
        try {
            FastList<ShellExecutor> shellExecutors = new FastList<ShellExecutor>();
            shellExecutors.add(smppShellExecutor);

            shellExecutorMBean = new ShellServer(schedulerMBean, shellExecutors);
            shellExecutorMBean.setAddress("127.0.0.1");
            shellExecutorMBean.setPort(3435);
            //shellExecutorMBean.setSecurityDomain("java:/jaas/jmx-console");
            shellExecutorMBean.start();
        } catch (Exception e) {
            log.warn("ShellExecutor MBean creating is failed: "+e);
        }

        registerMBean(shellExecutorMBean, "org.restcomm.smpp:name=ShellExecutor");
    }

    @Override
    public void stop(StopContext context) {
        log.info("Stopping SmppExtension Service");
    }

    private void registerMBean(Object mBean, String name) throws StartException {
        try {
            getMbeanServer().getValue().registerMBean(mBean, new ObjectName(name));
        } catch (Throwable e) {
            throw new StartException(e);
        }
    }

    private void unregisterMBean(String name) {
        try {
            getMbeanServer().getValue().unregisterMBean(new ObjectName(name));
        } catch (Throwable e) {
            log.error("failed to unregister mbean", e);
        }
    }
}
