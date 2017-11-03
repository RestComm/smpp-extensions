package org.restcomm.smpp.service;

import javolution.util.FastList;

import org.jboss.as.controller.services.path.PathManager;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.*;
import org.jboss.msc.value.InjectedValue;
import org.mobicents.protocols.ss7.scheduler.DefaultClock;
import org.mobicents.protocols.ss7.scheduler.Scheduler;
import org.mobicents.ss7.management.console.ShellExecutor;
import org.mobicents.ss7.management.console.ShellServer;
import org.mobicents.ss7.management.console.ShellServerWildFly;
import org.restcomm.smpp.SmppManagement;
import org.restcomm.smpp.oam.SmppShellExecutor;

import javax.management.MBeanServer;
import javax.management.ObjectName;

public class SmppService implements SmppServiceInterface,Service<SmppServiceInterface> {

    public static final SmppService INSTANCE = new SmppService();

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

    private static final String DATA_DIR = "jboss.server.data.dir";

    private ModelNode fullModel;
    
    private Scheduler schedulerMBean = null;
    private SmppManagement smppManagementMBean = null;
    private SmppShellExecutor smppShellExecutor = null;
    private ShellServer shellExecutorMBean = null;

    public void setModel(ModelNode model) {
        this.fullModel = model;
    }

    private ModelNode peek(ModelNode node, String... args) {
        for (String arg : args) {
            if (!node.hasDefined(arg)) {
                return null;
            }
            node = node.get(arg);
        }
        return node;
    }

    private String getPropertyString(String mbeanName, String propertyName, String defaultValue) {
        String result = defaultValue;
        ModelNode propertyNode = peek(fullModel, "mbean", mbeanName, "property", propertyName);
        if (propertyNode != null && propertyNode.isDefined()) {
            // log.debug("propertyNode: "+propertyNode);
            // todo: test TYPE?
            result = propertyNode.get("value").asString();
        }
        return (result == null) ? defaultValue : result;
    }

    private int getPropertyInt(String mbeanName, String propertyName, int defaultValue) {
        int result = defaultValue;
        ModelNode propertyNode = peek(fullModel, "mbean", mbeanName, "property", propertyName);
        if (propertyNode != null && propertyNode.isDefined()) {
            // log.debug("propertyNode: "+propertyNode);
            // todo: test TYPE?
            result = propertyNode.get("value").asInt();
        }
        return result;
    }

    private boolean getPropertyBoolean(String mbeanName, String propertyName, boolean defaultValue) {
        boolean result = defaultValue;
        ModelNode propertyNode = peek(fullModel, "mbean", mbeanName, "property", propertyName);
        if (propertyNode != null && propertyNode.isDefined()) {
            result = propertyNode.get("value").asBoolean();
        }
        return result;
    }

    @Override
    public SmppService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
        log.info("Starting SmppExtension Service");

        String dataDir = pathManagerInjector.getValue().getPathEntry(DATA_DIR).resolvePath();
        
        // smppManagementMBean
        smppManagementMBean = SmppManagement.getInstance("SmppManagement");
        smppManagementMBean.setMbeanServer(getMbeanServer().getValue());
        smppManagementMBean.setPersistDir(dataDir);
        smppManagementMBean.start();
        registerMBean(smppManagementMBean, "org.restcomm.smpp:name=SmppManagement");
        
        smppShellExecutor = null;
        try {
            smppShellExecutor = new SmppShellExecutor();
            smppShellExecutor.setSmppManagement(smppManagementMBean);
        } catch (Exception e) {
            log.warn("SccpExecutor MBean creating is failed: " + e);
        }
        
        System.out.println("SMPP shellExecutorExists():" + shellExecutorExists());
        
        if(shellExecutorExists()) {
        	// ss7Clock
            DefaultClock ss7Clock = null;
            try {
                ss7Clock = new DefaultClock();
            } catch (Exception e) {
                log.warn("SS7Clock MBean creating is failed: " + e);
            }

            // schedulerMBean
            schedulerMBean = null;
            try {
                schedulerMBean = new Scheduler();
                schedulerMBean.setClock(ss7Clock);
            } catch (Exception e) {
                log.warn("SS7Scheduler MBean creating is failed: " + e);
            }

            shellExecutorMBean = null;
            try {
                FastList<ShellExecutor> shellExecutors = new FastList<ShellExecutor>();
                shellExecutors.add(smppShellExecutor);

                String address = getPropertyString("ShellExecutor", "address", "127.0.0.1");
                int port = getPropertyInt("ShellExecutor", "port", 3436);
                String securityDomain = getPropertyString("ShellExecutor", "securityDomain", "jmx-console");

                shellExecutorMBean = new ShellServerWildFly(schedulerMBean, shellExecutors);
                shellExecutorMBean.setAddress(address);
                shellExecutorMBean.setPort(port);
                shellExecutorMBean.setSecurityDomain(securityDomain);
            } catch (Exception e) {
                throw new StartException("ShellExecutor MBean creating is failed: " + e.getMessage(), e);
            }

            // starting
            try {
                schedulerMBean.start();
                shellExecutorMBean.start();
            } catch (Exception e) {
                throw new StartException("MBeans starting is failed: " + e.getMessage(), e);
            }
        }
        
        try {
            smppShellExecutor.start();
        } catch (Exception e) {
            throw new StartException("MBeans starting is failed: " + e.getMessage(), e);
        }
    }

    private boolean shellExecutorExists() {
        ModelNode shellExecutorNode = peek(fullModel, "mbean", "ShellExecutor");
        return shellExecutorNode != null;
    }
    
    @Override
    public void stop(StopContext context) {
        log.info("Stopping SmppExtension Service");

        // scheduler - stop
        try {
            if (shellExecutorMBean != null)
                shellExecutorMBean.stop();
            if (schedulerMBean != null)
                schedulerMBean.stop();
        } catch (Exception e) {
            log.warn("MBean stopping is failed: " + e);
        }
    }

    private void registerMBean(Object mBean, String name) throws StartException {
        try {
            getMbeanServer().getValue().registerMBean(mBean, new ObjectName(name));
        } catch (Throwable e) {
            throw new StartException(e);
        }
    }

    @SuppressWarnings("unused")
	private void unregisterMBean(String name) {
        try {
            getMbeanServer().getValue().unregisterMBean(new ObjectName(name));
        } catch (Throwable e) {
            log.error("failed to unregister mbean", e);
        }
    }

	public SmppShellExecutor getSmppShellExecutor() {
		return smppShellExecutor;
	}
    
	public SmppManagement getSmppManagementMBean() {
		return smppManagementMBean;
	}
}
