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

import javolution.util.FastList;

/**
 * 
 * @author Amit Bhayani
 * @author sergey vetyutnev
 * 
 */
public interface EsmeManagementMBean {
    FastList<Esme> getEsmes();

	Esme getEsmeByName(String esmeName);

	Esme getEsmeByClusterName(String esmeClusterName);

    Esme createEsme(String name, String systemId, String password, String host, int port, boolean chargingEnabled,
            String smppBindType, String systemType, String smppIntVersion, byte ton, byte npi, String address,
            String smppSessionType, int windowSize, long connectTimeout, long requestExpiryTimeout, long clientBindTimeout,
            long windowMonitorInterval, long windowWaitTimeout, String clusterName, boolean countersEnabled,
            int enquireLinkDelay, int enquireLinkDelayServer, long linkDropServer, int sourceTon, int sourceNpi,
            String sourceAddressRange, int routingTon, int routingNpi, String routingAddressRange, int networkId,
            boolean splitLongMessages, long rateLimitPerSecond, long rateLimitPerMinute, long rateLimitPerHour,
            long rateLimitPerDay, int nationalLanguageSingleShift, int nationalLanguageLockingShift, int destAddrSendLimit, int minMessageLength,
            int maxMessageLength) throws Exception;

	Esme destroyEsme(String esmeName) throws Exception;

	void startEsme(String esmeName) throws Exception;

	void stopEsme(String esmeName) throws Exception;

}
