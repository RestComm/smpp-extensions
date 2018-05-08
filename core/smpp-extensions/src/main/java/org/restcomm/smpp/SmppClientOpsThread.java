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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import javolution.util.FastList;
import javolution.util.FastMap;

import org.apache.log4j.Logger;

import com.cloudhopper.smpp.PduAsyncResponse;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSession.Type;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.SmppSessionHandler;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppSession;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.ssl.SslConfiguration;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;

/**
 * @author Amit Bhayani
 * 
 */
public class SmppClientOpsThread implements Runnable {

	private static final Logger logger = Logger.getLogger(SmppClientOpsThread.class);

	private static final long SCHEDULE_CONNECT_DELAY = 1000 * 30; // 30 sec
	private final SimpleDateFormat DATE_FORMAT =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"); 

	protected volatile boolean started = true;

	private ConcurrentLinkedQueue<ChangeRequest> futureSet = new ConcurrentLinkedQueue<ChangeRequest>();

	private Object waitObject = new Object();

	private final DefaultSmppClient clientBootstrap;
	private final SmppSessionHandlerInterface smppSessionHandlerInterface;
	private final EsmeManagement esmeManagement;

	/**
	 * 
	 */
	public SmppClientOpsThread(DefaultSmppClient clientBootstrap,
			SmppSessionHandlerInterface smppSessionHandlerInterface, EsmeManagement esmeManagement) {
		this.clientBootstrap = clientBootstrap;
        this.smppSessionHandlerInterface = smppSessionHandlerInterface;
        this.esmeManagement = esmeManagement;
	}

	/**
	 * @param started
	 *            the started to set
	 */
	protected void setStarted(boolean started) {
		this.started = started;

		synchronized (this.waitObject) {
			this.waitObject.notify();
		}
	}

	protected void scheduleConnect(Esme esme) {

        logger.debug("Scheduling a Client SMPP connection for ESME: " + esme.getName() 
                + " systemId=" + esme.getSystemId());
		
        long executionTime = System.currentTimeMillis() + SCHEDULE_CONNECT_DELAY;
        if (esme.getInConnectingQueue().compareAndSet(false, true)) {
            this.futureSet.offer(new ChangeRequest(esme, ChangeRequest.CONNECT, executionTime));
    		logger.debug("Pending change request CONNECT has been added for esme: " + esme.getName() 
    		        + " with scheduled execution time on " + DATE_FORMAT.format(new Date(executionTime)));
        } else {
            logger.debug("Pending change request CONNECT has NOT been added for esme: " + esme.getName() 
            + " because it is already in queue");
        }
		synchronized (this.waitObject) {
			this.waitObject.notify();
		}

	}

	protected void scheduleEnquireLink(Esme esme) {
		this.futureSet.offer(new ChangeRequest(esme, ChangeRequest.ENQUIRE_LINK, System.currentTimeMillis()
					+ esme.getEnquireLinkDelay()));

		synchronized (this.waitObject) {
			this.waitObject.notify();
		}
	}

	@Override
	public void run() {
        FastMap<String, Long> startedClosedTime = new FastMap<String, Long>();

		if (logger.isInfoEnabled()) {
			logger.info("SmppClientOpsThread started.");
		}
		
		while (this.started) {
		    FastList<ChangeRequest> newSet = new FastList<ChangeRequest>();
		    FastList<Esme> pendingList = new FastList<Esme>();

            ChangeRequest change = futureSet.poll();
            while (change != null) {
                switch (change.getType()) {
                    case ChangeRequest.CONNECT:
                        if (!change.getEsme().isStarted()) {
                            change.getEsme().getInConnectingQueue().set(false);
                            logger.warn("ESME " + change.getEsme().getName() + " is stopped. Removing change request.");
                        } else {
                            if (change.getExecutionTime() <= System.currentTimeMillis()) {
                                change.getEsme().getInConnectingQueue().set(false);
                                initiateConnection(change.getEsme());
                            } else {
                                newSet.add(change);
                                logger.debug("Change request for ESME " + change.getEsme().getName()
                                        + " is scheduled for later: " + DATE_FORMAT.format(new Date(change.getExecutionTime())));
                            }
                        }
                        break;
                    case ChangeRequest.ENQUIRE_LINK:
                        if (change.getEsme().isStarted()) {
                            if (change.getEsme().getEnquireClientEnabled()) {
                                if (change.getExecutionTime() <= System.currentTimeMillis()) {
                                    pendingList.add(change.getEsme());
                                } else {
                                    newSet.add(change);
                                }
                            }
                        }
                        break;
                }

                change = futureSet.poll();
            }

            // Putting change requests into a queue
            Iterator<ChangeRequest> newChanges = newSet.iterator();
            while (newChanges.hasNext()) {
                ChangeRequest changeRequest = newChanges.next();
                futureSet.offer(changeRequest);
            }

            // Sending Enquire messages
            Iterator<Esme> pendingChanges = pendingList.iterator();
            while (pendingChanges.hasNext()) {
                Esme esme = pendingChanges.next();
                this.enquireLink(esme);
            }

            try {
                synchronized (this.waitObject) {
                    this.waitObject.wait(5000);
                }
    
                // checking of ESME CLOSED state
                try {
                    long curTimeStamp = System.currentTimeMillis();
                    for (FastList.Node<Esme> n = this.esmeManagement.esmes.head(), end = this.esmeManagement.esmes.tail(); (n = n
                            .getNext()) != end;) {
                        Esme esme = n.getValue();
                        if (esme.getSmppSessionType() == Type.CLIENT) {
                            if (esme.isStarted() && esme.isClosed()) {
                                Long stTime = startedClosedTime.get(esme.getName());
                                if (stTime == null) {
                                    startedClosedTime.put(esme.getName(), curTimeStamp);
                                } else {
                                    long stTimeV = stTime;
                                    // checking if a disconnection time > 5 min == 300 sec
                                    if (curTimeStamp - stTimeV > 300000) {
                                        startedClosedTime.remove(esme.getName());
                                        logger.warn("Client ESME is not connected for 5 minutes :" + esme.getName());
                                    }
                                }
                            } else {
                                startedClosedTime.remove(esme.getName());
                            }
                        }
                    }
                } catch (Throwable e) {
                }
            } catch (InterruptedException e) {
                logger.error("Error while looping SmppClientOpsThread thread", e);
            }
	            
		}// while

		if (logger.isInfoEnabled()) {
			logger.info("SmppClientOpsThread for stopped.");
		}
	}

	private void enquireLink(Esme esme) {
		SmppSession smppSession = esme.getSmppSession();

		if (!esme.isStarted()) {
			return;
		}

		if (smppSession != null && smppSession.isBound()) {
			try {
				smppSession.enquireLink(new EnquireLink(), 10000);

				// all ok lets schedule another ENQUIRE_LINK
				this.scheduleEnquireLink(esme);
				return;

			} catch (RecoverablePduException e) {
				logger.warn(String.format("RecoverablePduException while sending the ENQURE_LINK for ESME SystemId=%s",
						esme.getSystemId()), e);

				// Recoverabel exception is ok
				// all ok lets schedule another ENQUIRE_LINK
				this.scheduleEnquireLink(esme);
				return;

			} catch (Exception e) {

				logger.error(
						String.format("Exception while trying to send ENQUIRE_LINK for ESME SystemId=%s",
								esme.getSystemId()), e);
				// For all other exceptions lets close session and re-try
				// connect
				try {
					smppSession.close();
				} catch (Exception ex) {
					logger.error(String.format("Failed to close smpp client session for %s.",
							smppSession.getConfiguration().getName()));
				}
				this.scheduleConnect(esme);
			}

		} else {
			// This should never happen
			logger.warn(String.format("Sending ENQURE_LINK failed for ESME SystemId=%s as SmppSession is =%s !",
					esme.getSystemId(), (smppSession == null ? null : smppSession.getStateName())));

			if (smppSession != null) {
				try {
					smppSession.close();
				} catch (Exception e) {
					logger.error(String.format("Failed to close smpp client session for %s.",
							smppSession.getConfiguration().getName()));
				}
			}
			
			this.scheduleConnect(esme);
		}
	}

	private void initiateConnection(Esme esme) {
	    logger.debug("Initiating connection for esme " + esme.getName() + " is started.");
	    
		// If Esme is stopped, don't try to initiate connect
		if (!esme.isStarted()) {
		    logger.warn("ESME: " + esme.getName() + " is stopped. Will not try to initiate connection.");
			return;
		}

		SmppSession smppSession = esme.getSmppSession();
		if ((smppSession != null && smppSession.isBound()) || (smppSession != null && smppSession.isBinding())) {
			// If process has already begun lets not do it again
		    logger.debug("SMPP session is already bound or binding for ESME: " + esme.getName() + ". Will not try to initiate connection");
			return;
		}

		SmppSession session0 = null;
		try {
		    logger.debug("Creating SMPP Session with ESME " + esme.getName() + " started.");

			SmppSessionConfiguration config0 = new SmppSessionConfiguration();
			config0.setWindowSize(esme.getWindowSize());
			config0.setName(esme.getName());
			config0.setType(esme.getSmppBindType());
			config0.setBindTimeout(esme.getClientBindTimeout());
			config0.setHost(esme.getHost());
			config0.setPort(esme.getPort());
			config0.setConnectTimeout(esme.getConnectTimeout());
			config0.setSystemId(esme.getSystemId());
			config0.setPassword(esme.getPassword());
			config0.setSystemType(esme.getSystemType());
			config0.getLoggingOptions().setLogBytes(true);
			// to enable monitoring (request expiration)
			config0.setRequestExpiryTimeout(esme.getRequestExpiryTimeout());
			config0.setWindowMonitorInterval(esme.getWindowMonitorInterval());
			config0.setCountersEnabled(esme.isCountersEnabled());
			config0.setWriteTimeout(SmppManagement.getInstance().getSmppServerManagement().getWriteTimeout());

			int addressTon = esme.getEsmeTon();
			int addressNpi = esme.getEsmeNpi();
			String addressRange = esme.getEsmeAddressRange();
			
			Address addressRangeObj = new Address();
			if(addressTon!=-1){
				addressRangeObj.setTon((byte)addressTon);
			}
			
			if(addressNpi != -1){
				addressRangeObj.setNpi((byte)addressNpi);
			}
			
			if(addressRange != null){
				addressRangeObj.setAddress(addressRange);
			}
			
			config0.setAddressRange(addressRangeObj);

			SmppSessionHandler sessionHandler = new ClientSmppSessionHandler(esme,
					this.smppSessionHandlerInterface.createNewSmppSessionHandler(esme));
			
			// SSL settings
			if (esme.isUseSsl()) {
				logger.info(String.format("%s ESME will use SSL Configuration", esme.getName()));
				SslConfiguration sslConfiguration = esme.getWrappedSslConfig();

				config0.setUseSsl(true);
				config0.setSslConfiguration(sslConfiguration);
			}

			logger.debug("Binding with ESME " + esme.getName() + " systemId=" + esme.getSystemId());
			esme.nextLocalSessionId();
			session0 = clientBootstrap.bind(config0, sessionHandler);

			// Set in ESME
			logger.debug("SMPP session has been created for ESME: " + esme.getName());
			esme.setSmppSession((DefaultSmppSession) session0);

			// Finally set Enquire Link schedule
			this.scheduleEnquireLink(esme);
		} catch (Throwable e) {
			logger.error(
					String.format("Exception when trying to bind client SMPP connection for ESME systemId=%s",
							esme.getSystemId()) + " name = " + esme.getName(), e);
			if (session0 != null) {
				session0.close();
			}
			this.scheduleConnect(esme);
		}
	}

	protected class ClientSmppSessionHandler implements SmppSessionHandler {

		private final Esme esme;
		private final SmppSessionHandler wrappedSmppSessionHandler;

		/**
		 * @param esme
		 */
		public ClientSmppSessionHandler(Esme esme, SmppSessionHandler wrappedSmppSessionHandler) {
			super();
			this.esme = esme;
			this.wrappedSmppSessionHandler = wrappedSmppSessionHandler;
		}

		@Override
		public String lookupResultMessage(int arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String lookupTlvTagName(short arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void fireChannelUnexpectedlyClosed() {
			this.wrappedSmppSessionHandler.fireChannelUnexpectedlyClosed();

			if (this.esme.getSmppSession() != null) {
                this.esme.getSmppSession().close();
            }

			// Schedule the connection again
			scheduleConnect(this.esme);
		}

		@Override
		public void fireExpectedPduResponseReceived(PduAsyncResponse pduAsyncResponse) {
			this.wrappedSmppSessionHandler.fireExpectedPduResponseReceived(pduAsyncResponse);
		}

		@Override
		public void firePduRequestExpired(PduRequest pduRequest) {
			this.wrappedSmppSessionHandler.firePduRequestExpired(pduRequest);
		}

		@Override
		public PduResponse firePduRequestReceived(PduRequest pduRequest) {
			return this.wrappedSmppSessionHandler.firePduRequestReceived(pduRequest);
		}

		@Override
		public void fireRecoverablePduException(RecoverablePduException e) {
			this.wrappedSmppSessionHandler.fireRecoverablePduException(e);
		}

		@Override
		public void fireUnexpectedPduResponseReceived(PduResponse pduResponse) {
			this.wrappedSmppSessionHandler.fireUnexpectedPduResponseReceived(pduResponse);
		}

		@Override
		public void fireUnknownThrowable(Throwable e) {
			this.wrappedSmppSessionHandler.fireUnknownThrowable(e);
			// TODO is this ok?

			if (this.esme.getSmppSession() != null) {
			    this.esme.getSmppSession().close();
			}

			// Schedule the connection again
			scheduleConnect(this.esme);

		}

		@Override
		public void fireUnrecoverablePduException(UnrecoverablePduException e) {
			// TODO shall we call wrapped?
			this.wrappedSmppSessionHandler.fireUnrecoverablePduException(e);

			this.esme.getSmppSession().close();

			// Schedule the connection again
			scheduleConnect(this.esme);
		}

	}

}
