package org.restcomm.smpp;

import org.restcomm.smpp.oam.SessionKey;

public interface SmppStateListener {
    void esmeStarted(String esmeName, String clusterName);
    void esmeStopped(String esmeName, String clusterName, Long sessionId);

    void sessionCreated(SessionKey key);
    void sessionClosed(SessionKey key);
}
