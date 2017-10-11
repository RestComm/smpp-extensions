package org.restcomm.smpp.oam;

public class SessionKey {
    
    private String esmeName;
    private Long sessionId;
    
    public String getEsmeName() {
        return esmeName;
    }
    public void setEsmeName(String esmeName) {
        this.esmeName = esmeName;
    }
    public Long getSessionId() {
        return sessionId;
    }
    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
    public SessionKey(String esmeName, Long sessionId) {
        super();
        this.esmeName = esmeName;
        this.sessionId = sessionId;
    }
    
    public String getSessionKeyName() {
        if (sessionId == null) {
            return esmeName + "-";
        }
        return esmeName + "-" + sessionId;
    }
}
