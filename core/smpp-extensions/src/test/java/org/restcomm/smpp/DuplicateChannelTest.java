package org.restcomm.smpp;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.restcomm.smpp.SmppManagement;
import org.restcomm.smpp.oam.SmppShellExecutor;
import org.testng.annotations.Test;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.PduAsyncResponse;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.SmppSessionHandler;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.BaseSm;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppBindException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;

/**
 *
 * @author Olena Radiboh
 * 
 */

public class DuplicateChannelTest {

    private int windowSize = 50000;

    private String peerAddress = "127.0.0.1";
    private int peerPort = 2776;
    private String systemId = "test";
    private String password = "test";
    
    protected class SmppSessionHandlerInterfaceImpl implements SmppSessionHandlerInterface {
        
        public SmppSessionHandlerInterfaceImpl() {
        }

        @Override
        public void destroySmppSessionHandler(Esme esme) {
        }

        @Override
        public SmppSessionHandler createNewSmppSessionHandler(Esme esme) {
            return new SmppSessionHandlerImpl(esme);
        }
    }
    
    protected class SmppSessionHandlerImpl implements SmppSessionHandler {
        private Esme esme;

        public SmppSessionHandlerImpl(Esme esme) {
            this.esme = esme;
        }
        
        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            PduResponse response = pduRequest.createResponse();
            return response;
        }
        
        @Override
        public String lookupResultMessage(int arg0) {
            return null;
        }

        @Override
        public String lookupTlvTagName(short arg0) {
            return null;
        }
        
        @Override
        public void fireChannelUnexpectedlyClosed() {
        }

        @Override
        public void fireExpectedPduResponseReceived(PduAsyncResponse pduAsyncResponse) {
        }

        @Override
        public void firePduRequestExpired(PduRequest pduRequest) {
        }

        @Override
        public void fireRecoverablePduException(RecoverablePduException recoverablePduException) {

        }

        @Override
        public void fireUnrecoverablePduException(UnrecoverablePduException unrecoverablePduException) {
        }

        @Override
        public void fireUnexpectedPduResponseReceived(PduResponse pduResponse) {
        }

        @Override
        public void fireUnknownThrowable(Throwable throwable) {
        }
        
    }
    
    protected class TestSmppClient extends DefaultSmppClient {
        DefaultSmppSession session = null;
        
        public SmppSession open2(SmppSessionConfiguration config, SmppSessionHandler sessionHandler) throws SmppTimeoutException, SmppChannelException, SmppBindException, UnrecoverablePduException, InterruptedException {
            
            try {
                // connect to the remote system and create the session
                System.out.println("Connecting to remote system " + config.getName() + " host " + config.getHost() + ":" + config.getPort());
                session = doOpen(config, sessionHandler);
   
            } catch(Exception exception) {
                exception.printStackTrace();
            }
            
            return session;
        }
        
        @Override
        public SmppSession bind(SmppSessionConfiguration config, SmppSessionHandler sessionHandler) throws SmppTimeoutException, SmppChannelException, SmppBindException, UnrecoverablePduException, InterruptedException {
            
            try {
                // try to bind to the remote system (may throw an exception)
                System.out.println("Binding to remote system " + config.getName());
                doBind(session, config, sessionHandler);
                
                System.out.println("Successfully bound to " + config.getName());
            } finally {
                // close the session if we weren't able to bind correctly
                if (session != null && !session.isBound()) {
                    // make sure that the resources are always cleaned up
                    try { 
                        System.out.println("Closing session - not able to bind to " + config.getName());
                        session.close(); 
                    } 
                    catch (Exception e) {
                        System.out.println("Exception while trying to close connection to " + config.getName());
                    }
                }
            }
            return session;
        }
    }
    
    protected class SmppClientConnection extends Thread {
        
        DefaultSmppSessionHandler sessionHandler;
        SmppSessionConfiguration config;
        TestSmppClient clientBootstrap;
        Semaphore semaphore;
        SmppSession session;
        
        public SmppClientConnection(DefaultSmppSessionHandler sessionHandler, SmppSessionConfiguration config,
                TestSmppClient clientBootstrap, Semaphore semaphore) {
            this.sessionHandler = sessionHandler;
            this.config = config;
            this.clientBootstrap = clientBootstrap;
            this.semaphore = semaphore;
        }
        
        public SmppSession getSession() {
            return session;
        }
        
        public void run() {
            try {
                semaphore.acquire();
            } catch (InterruptedException exception) {}
            
            try {
                
                session = clientBootstrap.bind(config, sessionHandler);
            } catch(Exception e) {
                System.out.println(e);
            }
        }
    }
    
//    @Test
    public void sendTwoMessages() {

        DefaultSmppSessionHandler sessionHandler = new DefaultSmppSessionHandler();
        
        SmppSessionConfiguration config1 = new SmppSessionConfiguration();
        config1.setWindowSize(windowSize);
        config1.setName("Esme1");
        config1.setType(SmppBindType.TRANSCEIVER);
        config1.setHost(peerAddress);
        config1.setPort(peerPort);
        config1.setConnectTimeout(10000);
        config1.setSystemId(systemId);
        config1.setPassword(password);
        config1.getLoggingOptions().setLogBytes(false);
        config1.setRequestExpiryTimeout(30000);
        config1.setWindowMonitorInterval(15000);
        config1.setCountersEnabled(true);
        
        SmppSessionConfiguration config2 = new SmppSessionConfiguration();
        config2.setWindowSize(windowSize);
        config2.setName("Esme2");
        config2.setType(SmppBindType.TRANSCEIVER);
        config2.setHost(peerAddress);
        config2.setPort(peerPort);
        config2.setConnectTimeout(10000);
        config2.setSystemId(systemId);
        config2.setPassword(password);
        config2.getLoggingOptions().setLogBytes(false);
        config2.setRequestExpiryTimeout(30000);
        config2.setWindowMonitorInterval(15000);
        config2.setCountersEnabled(true);
        
        int retriesCount = 20;
        
        for (int i = 0; i < retriesCount; i++) {
        
            TestSmppClient clientBootstrap = new TestSmppClient();
            TestSmppClient clientBootstrap2 = new TestSmppClient();
            
            try {
                clientBootstrap.open2(config1, sessionHandler);
                clientBootstrap2.open2(config2, sessionHandler);
            } catch(Exception e) {
                System.out.println(e);
            }
            Semaphore semaphore = new Semaphore(0);
            
            
            SmppClientConnection conn1 = new SmppClientConnection(sessionHandler, config1, clientBootstrap, semaphore);
            SmppClientConnection conn2 = new SmppClientConnection(sessionHandler, config2, clientBootstrap2, semaphore);
            conn1.start();
            conn2.start();
            
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {}
            
            semaphore.release(2);
            
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {}
    
            int count = 0;
            if (conn1.getSession() != null)
                count++;
            if (conn2.getSession() != null)
                count++;
            
            assertEquals(count, 1);
            
            if (conn1.getSession() != null) {
                conn1.getSession().unbind(1000);
                conn1.getSession().destroy();
                conn1.getSession().close();
            }
            
            if (conn2.getSession() != null) {
                conn2.getSession().unbind(1000);
                conn2.getSession().destroy();
                conn2.getSession().close();
            }
        
        }
    }
}
