/*
 * TeleStax, Open Source Cloud Communications  
 * Copyright 2012, Telestax Inc and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.smpp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import javolution.text.TextBuilder;
import javolution.util.FastList;
import javolution.util.FastMap;
import javolution.xml.XMLBinding;
import javolution.xml.XMLObjectReader;
import javolution.xml.XMLObjectWriter;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.jboss.mx.util.MBeanServerLocator;
import org.restcomm.smpp.oam.SmppOamMessages;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppSession;

/**
 *
 * @author amit bhayani
 * @author sergey vetyutnev
 *
 */
public class EsmeManagement implements EsmeManagementMBean {

	private static final Logger logger = Logger.getLogger(EsmeManagement.class);

	private static final String ESME_LIST = "esmeList";
	private static final String TAB_INDENT = "\t";
	private static final String CLASS_ATTRIBUTE = "type";
	private static final XMLBinding binding = new XMLBinding();
	private static final String PERSIST_FILE_NAME = "esme.xml";

	private final String name;

	private String persistDir = null;

	protected FastList<Esme> esmes = new FastList<Esme>();

	protected FastMap<String, Long> esmesServer = new FastMap<String, Long>();

	protected FastMap<String, EsmeCluster> esmeClusters = new FastMap<String, EsmeCluster>();

	private final TextBuilder persistFile = TextBuilder.newInstance();

	private SmppClientManagement smppClient = null;

    private MBeanServer mbeanServer = null;

    private Timer timer;
    private TimerTask timerTask;

	private static EsmeManagement instance = null;

	protected EsmeManagement(String name) {
		this.name = name;

		binding.setClassAttribute(CLASS_ATTRIBUTE);
		binding.setAlias(Esme.class, "esme");
	}

    protected static EsmeManagement getInstance(String name) {
        if (instance == null) {
            instance = new EsmeManagement(name);
        }
        return instance;
    }

    protected static void setInstance(EsmeManagement instance) {
        EsmeManagement.instance = instance;
    }

	public static EsmeManagement getInstance() {
		return instance;
	}

	public String getName() {
		return name;
	}

	public String getPersistDir() {
		return persistDir;
	}

	public void setPersistDir(String persistDir) {
		this.persistDir = persistDir;
	}

	/**
	 * @param smppClient
	 *            the smppClient to set
	 */
	protected void setSmppClient(SmppClientManagement smppClient) {
		this.smppClient = smppClient;
	}

    @Override
    public FastList<Esme> getEsmes() {
        return esmes;
    }

	@Override
	public Esme getEsmeByName(String esmeName) {
		for (FastList.Node<Esme> n = esmes.head(), end = esmes.tail(); (n = n.getNext()) != end;) {
			Esme esme = n.getValue();
			if (esme.getName().equals(esmeName)) {
				return esme;
			}
		}
		return null;
	}

	@Override
	public Esme getEsmeByClusterName(String esmeClusterName) {
		EsmeCluster esmeCluster = this.esmeClusters.get(esmeClusterName);
		if (esmeCluster != null) {
			return esmeCluster.getNextEsme();
		}
		return null;
	}

	protected Esme getEsmeByPrimaryKey(String SystemId, String host, int port, SmppBindType smppBindType) {

		// Check for actual SystemId, host and port
		for (FastList.Node<Esme> n = esmes.head(), end = esmes.tail(); (n = n.getNext()) != end;) {
			Esme esme = n.getValue();

			if (esme.getSystemId().equals(SystemId) && esme.getSmppBindType() == smppBindType) {
				//discoveredEsme = esme;
				if (esme.getHost().equals(host) && esme.getPort() == port) {
					// exact match found
					return esme;
				}

				if (esme.getHost().equals(host) && esme.getPort() == -1) {
					// Hosts match but port is any
					if (esme.getStateName().equals(com.cloudhopper.smpp.SmppSession.STATES[SmppSession.STATE_CLOSED])) {
						return esme;
					}
				}

				if (esme.getHost().equals("-1") && esme.getPort() == port) {
					// Host is any but port matches
					if (esme.getStateName().equals(com.cloudhopper.smpp.SmppSession.STATES[SmppSession.STATE_CLOSED])) {
						return esme;
					}
				}

				if (esme.getHost().equals("-1") && esme.getPort() == -1) {
					// Host is any and port is also any
					if (esme.getStateName().equals(com.cloudhopper.smpp.SmppSession.STATES[SmppSession.STATE_CLOSED])) {
						return esme;
					}
				}

			}// esme.getSystemId().equals(SystemId) && esme.getSmppBindType() ==
				// smppBindType
		}

		return null;
	}

    public Esme createEsme(String name, String systemId, String password, String host, int port, boolean chargingEnabled,
            String smppBindType, String systemType, String smppIntVersion, byte ton, byte npi, String address,
            String smppSessionType, int windowSize, long connectTimeout, long requestExpiryTimeout, long clientBindTimeout,
            long windowMonitorInterval, long windowWaitTimeout, String clusterName, boolean countersEnabled,
            int enquireLinkDelay, int enquireLinkDelayServer, long linkDropServer, int sourceTon, int sourceNpi,
            String sourceAddressRange, int routingTon, int routingNpi, String routingAddressRange, int networkId,
            boolean splitLongMessages, long rateLimitPerSecond, long rateLimitPerMinute, long rateLimitPerHour,
            long rateLimitPerDay, int nationalLanguageSingleShift, int nationalLanguageLockingShift, int destAddrSendLimit, int minMessageLength,
            int maxMessageLength) throws Exception {

		SmppBindType smppBindTypeOb = SmppBindType.valueOf(smppBindType);

		if (smppBindTypeOb == null) {
			throw new Exception("SmppBindType must be either of TRANSCEIVER, TRANSMITTER or RECEIVER. Passed is "
					+ smppBindType);
		}

		SmppSession.Type smppSessionTypeObj = SmppSession.Type.valueOf(smppSessionType);
		if (smppSessionTypeObj == null) {
			throw new Exception("SmppSession.Type must be either of SERVER or CLIENT. Passed is " + smppSessionType);
		}

		SmppInterfaceVersionType smppInterfaceVersionTypeObj = SmppInterfaceVersionType
				.getInterfaceVersionType(smppIntVersion);

		if (smppInterfaceVersionTypeObj == null) {
			smppInterfaceVersionTypeObj = SmppInterfaceVersionType.SMPP34;
		}

		if (smppSessionTypeObj == SmppSession.Type.CLIENT) {
			if (port < 1) {
				throw new Exception(SmppOamMessages.CREATE_EMSE_FAIL_PORT_CANNOT_BE_LESS_THAN_ZERO);
			}

			if (host == null || host.equals("-1")) {
				throw new Exception(SmppOamMessages.CREATE_EMSE_FAIL_HOST_CANNOT_BE_ANONYMOUS);
			}
		}

		for (FastList.Node<Esme> n = esmes.head(), end = esmes.tail(); (n = n.getNext()) != end;) {
			Esme esme = n.getValue();

			// Name should be unique
			if (esme.getName().equals(name)) {
				throw new Exception(String.format(SmppOamMessages.CREATE_EMSE_FAIL_ALREADY_EXIST, name));
			}

			// SystemId:IP:Port:SmppBindType combination should be unique for
			// CLIENT. For SERVER it accepts multiple incoming binds as far as
			// host is anonymous (-1) and/or port is -1
//			String primaryKey = systemId + smppBindType;
//			String existingPrimaryKey = esme.getSystemId() + esme.getSmppBindType().name();
//
//			if (smppSessionTypeObj == SmppSession.Type.SERVER) {
//				if (!host.equals("-1") && port != -1) {
//					primaryKey = primaryKey + host + port;
//					existingPrimaryKey = existingPrimaryKey + esme.getHost() + esme.getPort();
//				} else {
//					// Let the ESME be created
//					primaryKey = "X";
//					existingPrimaryKey = "Y";
//				}
//			} else {
//				primaryKey = primaryKey + host + port;
//				existingPrimaryKey = existingPrimaryKey + esme.getHost() + esme.getPort();
//			}
//
//			if (primaryKey.equals(existingPrimaryKey)) {
//				throw new Exception(String.format(SmppOamMessages.CREATE_EMSE_FAIL_PRIMARY_KEY_ALREADY_EXIST, systemId,
//						host, port, smppBindType));
//			}
		}// for loop

		EsmeCluster esmeCluster = this.esmeClusters.get(clusterName);
        if (esmeCluster != null) {
            if (esmeCluster.getNetworkId() != networkId) {
                throw new Exception(String.format(SmppOamMessages.CREATE_EMSE_FAIL_WRONG_NETWORKID_IN_ESMECLUSTER, esmeCluster.getNetworkId(), networkId));
            }
        }

		if (clusterName == null) {
			clusterName = name;
		}

        Esme esme = new Esme(name, systemId, password, host, port, chargingEnabled, systemType, smppInterfaceVersionTypeObj,
                ton, npi, address, smppBindTypeOb, smppSessionTypeObj, windowSize, connectTimeout, requestExpiryTimeout,
                clientBindTimeout, windowMonitorInterval, windowWaitTimeout, clusterName, countersEnabled, enquireLinkDelay,
                enquireLinkDelayServer, linkDropServer, sourceTon, sourceNpi, sourceAddressRange, routingTon, routingNpi,
                routingAddressRange, networkId, splitLongMessages, rateLimitPerSecond, rateLimitPerMinute, rateLimitPerHour,
                rateLimitPerDay, nationalLanguageSingleShift, nationalLanguageLockingShift, destAddrSendLimit, minMessageLength, maxMessageLength);

		esme.esmeManagement = this;

		esmes.add(esme);

		if (esmeCluster == null) {
			esmeCluster = new EsmeCluster(clusterName, networkId);
			this.esmeClusters.put(clusterName, esmeCluster);
		}

		esmeCluster.addEsme(esme);

		this.store();

		this.registerEsmeMbean(esme);

		return esme;
	}

	public Esme destroyEsme(String esmeName) throws Exception {
		Esme esme = this.getEsmeByName(esmeName);
		if (esme == null) {
			throw new Exception(String.format(SmppOamMessages.DELETE_ESME_FAILED_NO_ESME_FOUND, esmeName));
		}

		if (esme.isStarted()) {
			throw new Exception(String.format(SmppOamMessages.DELETE_ESME_FAILED_ESME_STARTED));
		}

		esmes.remove(esme);

		EsmeCluster esmeCluster = this.esmeClusters.get(esme.getClusterName());
		esmeCluster.removeEsme(esme);

		if (!esmeCluster.hasMoreEsmes()) {
			this.esmeClusters.remove(esme.getClusterName());
		}

		this.store();

		this.unregisterEsmeMbean(esme.getName());

		return esme;
	}

	@Override
	public void startEsme(String esmeName) throws Exception {
		Esme esme = this.getEsmeByName(esmeName);
		if (esme == null) {
			throw new Exception(String.format(SmppOamMessages.DELETE_ESME_FAILED_NO_ESME_FOUND, esmeName));
		}

		if (esme.isStarted()) {
			throw new Exception(String.format(SmppOamMessages.START_ESME_FAILED_ALREADY_STARTED, esmeName));
		}

		esme.setStarted(true);
		this.store();

		if (esme.getSmppSessionType().equals(SmppSession.Type.CLIENT)) {
			this.smppClient.startSmppClientSession(esme);
		}

	}

	@Override
	public void stopEsme(String esmeName) throws Exception {
		Esme esme = this.getEsmeByName(esmeName);
		if (esme == null) {
			throw new Exception(String.format(SmppOamMessages.DELETE_ESME_FAILED_NO_ESME_FOUND, esmeName));
		}

		esme.setStarted(false);

		if (esme.getLinkDropServerEnabled()) {
			esme.resetLinkRecvMessage();
		}

		this.store();

		this.stopWrappedSession(esme);
	}

	private void stopWrappedSession(Esme esme) {
		if (esme.getSmppSessionType().equals(SmppSession.Type.SERVER)) {
			DefaultSmppSession smppSession = esme.getSmppSession();

			if (smppSession != null) {
				// TODO can server side send UNBIND?
				// smppSession.unbind(5000);
				try {
					smppSession.close();
				} catch (Exception e) {
					logger.error(String.format("Failed to close smpp session for %s.",
							smppSession.getConfiguration().getName()));
				}

//                // firing of onPduRequestTimeout() for sent messages for which we do not have responses
//                Window<Integer, PduRequest, PduResponse> wind = smppSession.getSendWindow();
//                Map<Integer, WindowFuture<Integer, PduRequest, PduResponse>> futures = wind.createSortedSnapshot();
//                for (WindowFuture<Integer, PduRequest, PduResponse> future : futures.values()) {
//                    this.logger.warn("Firing of onPduRequestTimeout from EsmeManagement.stopWrappedSession() - 1: "
//                            + future.getRequest().toString());
//                    smppSession.expired(future);
//                }

                smppSession.destroy();
			}
		} else {
			if (this.smppClient != null) {
				this.smppClient.stopSmppClientSession(esme);
			}
		}
	}

	public void start() throws Exception {

        try {
            this.mbeanServer = MBeanServerLocator.locateJBoss();
        } catch (Exception e) {
        }

		this.persistFile.clear();

		if (persistDir != null) {
			this.persistFile.append(persistDir).append(File.separator).append(this.name).append("_")
					.append(PERSIST_FILE_NAME);
		} else {
			persistFile
					.append(System.getProperty(SmppManagement.SMSC_PERSIST_DIR_KEY,
							System.getProperty(SmppManagement.USER_DIR_KEY))).append(File.separator).append(this.name)
					.append("_").append(PERSIST_FILE_NAME);
		}

		logger.info(String.format("Loading ESME configuration from %s", persistFile.toString()));

		try {
			this.load();
		} catch (FileNotFoundException e) {
			logger.warn(String.format("Failed to load the ESME configuration file. \n%s", e.getMessage()));
		}

		for (FastList.Node<Esme> n = esmes.head(), end = esmes.tail(); (n = n.getNext()) != end;) {
			Esme esme = n.getValue();
			this.registerEsmeMbean(esme);
		}

		// setting a timer for cleaning of 
        this.clearMessageClearTimer();
        this.timer = new Timer();
        this.timerTask = new MessageCleanerTimerTask();
        this.timer.scheduleAtFixedRate(timerTask, 0, 1000);
	}

	public void stop() throws Exception {
        this.clearMessageClearTimer();

        this.store();

		for (FastList.Node<Esme> n = esmes.head(), end = esmes.tail(); (n = n.getNext()) != end;) {
			Esme esme = n.getValue();
			this.stopWrappedSession(esme);
			this.unregisterEsmeMbean(esme.getName());
		}
	}

    private void clearMessageClearTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

	/**
	 * Persist
	 */
	public void store() {

		// TODO : Should we keep reference to Objects rather than recreating
		// everytime?
		try {
			XMLObjectWriter writer = XMLObjectWriter.newInstance(new FileOutputStream(persistFile.toString()));
			writer.setBinding(binding);
			// Enables cross-references.
			// writer.setReferenceResolver(new XMLReferenceResolver());
			writer.setIndentation(TAB_INDENT);
			writer.write(esmes, ESME_LIST, FastList.class);

			writer.close();
		} catch (Exception e) {
			logger.error("Error while persisting the Rule state in file", e);
		}
	}

	/**
	 * Load and create LinkSets and Link from persisted file
	 * 
	 * @throws Exception
	 */
	public void load() throws FileNotFoundException {

	    // backward compatibility: rename old file name to new one
        if (persistDir != null) {
            TextBuilder oldFileName = new TextBuilder();
            oldFileName.append(persistDir).append(File.separator).append("SmscManagement").append("_").append(PERSIST_FILE_NAME);

            Path oldPath = FileSystems.getDefault().getPath(oldFileName.toString());
            Path newPath = FileSystems.getDefault().getPath(persistFile.toString());

            if (Files.exists(oldPath) && Files.notExists(newPath)) {
                try {
                    Files.move(oldPath, newPath);
                } catch (IOException e) {
                    logger.warn("Exception when trying to rename old config file", e);
                }
            }
        }

		XMLObjectReader reader = null;
		try {
			reader = XMLObjectReader.newInstance(new FileInputStream(persistFile.toString()));

			reader.setBinding(binding);
			this.esmes = reader.read(ESME_LIST, FastList.class);

			// Populate cluster
			for (FastList.Node<Esme> n = this.esmes.head(), end = this.esmes.tail(); (n = n.getNext()) != end;) {
				Esme esme = n.getValue();
				
				esme.esmeManagement = this;
				
				String esmeClusterName = esme.getClusterName();
				EsmeCluster esmeCluster = this.esmeClusters.get(esmeClusterName);
				if (esmeCluster == null) {
					esmeCluster = new EsmeCluster(esmeClusterName, esme.getNetworkId());
					this.esmeClusters.put(esmeClusterName, esmeCluster);
                } else {
                    esme.setNetworkId(esmeCluster.getNetworkId());
                }
                esmeCluster.addEsme(esme);
			}

			reader.close();
		} catch (XMLStreamException ex) {
			// this.logger.info(
			// "Error while re-creating Linksets from persisted file", ex);
		}
	}

	private void registerEsmeMbean(Esme esme) {
		try {
			ObjectName esmeObjNname = new ObjectName(SmppManagement.JMX_DOMAIN + ":layer=Esme,name=" + esme.getName());
			StandardMBean esmeMxBean = new StandardMBean(esme, EsmeMBean.class, true);

            if (this.mbeanServer != null)
                this.mbeanServer.registerMBean(esmeMxBean, esmeObjNname);
		} catch (InstanceAlreadyExistsException e) {
			logger.error(String.format("Error while registering MBean for ESME %s", esme.getName()), e);
		} catch (MBeanRegistrationException e) {
			logger.error(String.format("Error while registering MBean for ESME %s", esme.getName()), e);
		} catch (NotCompliantMBeanException e) {
			logger.error(String.format("Error while registering MBean for ESME %s", esme.getName()), e);
		} catch (MalformedObjectNameException e) {
			logger.error(String.format("Error while registering MBean for ESME %s", esme.getName()), e);
		}
	}

	private void unregisterEsmeMbean(String esmeName) {

		try {
			ObjectName esmeObjNname = new ObjectName(SmppManagement.JMX_DOMAIN + ":layer=Esme,name=" + esmeName);
            if (this.mbeanServer != null)
                this.mbeanServer.unregisterMBean(esmeObjNname);
		} catch (MBeanRegistrationException e) {
			logger.error(String.format("Error while unregistering MBean for ESME %s", esmeName), e);
		} catch (InstanceNotFoundException e) {
			logger.error(String.format("Error while unregistering MBean for ESME %s", esmeName), e);
		} catch (MalformedObjectNameException e) {
			logger.error(String.format("Error while unregistering MBean for ESME %s", esmeName), e);
		}
	}

    private class MessageCleanerTimerTask extends TimerTask {

        private int lastDay = -1;
        private int lastHour = -1;
        private int lastMinute = -1;

        @Override
        public void run() {
            boolean needDay = false;
            boolean needHour = false;
            boolean needMinute = false;
            Date tm = new Date();
            if (lastDay != tm.getDay()) {
                lastDay = tm.getDay();
                needDay = true;
            }
            if (lastHour != tm.getHours()) {
                lastHour = tm.getHours();
                needHour = true;
            }
            if (lastMinute != tm.getMinutes()) {
                lastMinute = tm.getMinutes();
                needMinute = true;
            }

            for (FastList.Node<Esme> n = esmes.head(), end = esmes.tail(); (n = n.getNext()) != end;) {
                Esme esme = n.getValue();
                if (needDay) {
                    esme.clearDayMsgCounter();
                } else if (needHour) {
                    esme.clearHourMsgCounter();
                } else if (needMinute) {
                    esme.clearMinuteMsgCounter();
                } else {
                    esme.clearSecondMsgCounter();
                }
            }
        }

    }
}
