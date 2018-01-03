package org.restcomm.smpp.service;

import org.restcomm.smpp.SmppManagement;
import org.restcomm.smpp.oam.SmppShellExecutor;

public interface SmppServiceInterface 
{
	public SmppShellExecutor getSmppShellExecutor();	
	public SmppManagement getSmppManagementMBean();		
}
