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

import org.apache.log4j.Logger;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;

import javolution.util.FastList;

/**
 * 
 * @author Amit Bhayani
 * 
 */
public class EsmeCluster {
    
    private final Logger LOG = Logger.getLogger(EsmeCluster.class);

	private final String clusterName;
	private final FastList<Esme> esmes = new FastList<Esme>();
    private final int networkId;

	// These are the ESME's that will be used to transmit PDU to remote side
	private final FastList<Esme> esmesToSendPdu = new FastList<Esme>();

	private volatile int index = 0;

	protected EsmeCluster(String clusterName, int networkId) {
        this.clusterName = clusterName;
        this.networkId = networkId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public int getNetworkId() {
        return networkId;
    }

	void addEsme(Esme esme) {
		synchronized (this.esmes) {
			this.esmes.add(esme);
		}

		synchronized (this.esmesToSendPdu) {
			if (esme.getSmppBindType() == SmppBindType.TRANSCEIVER
					|| (esme.getSmppBindType() == SmppBindType.RECEIVER && esme.getSmppSessionType() == SmppSession.Type.SERVER)
					|| (esme.getSmppBindType() == SmppBindType.TRANSMITTER && esme.getSmppSessionType() == SmppSession.Type.CLIENT)) {
				this.esmesToSendPdu.add(esme);
			}
		}
	}

	void removeEsme(Esme esme) {
		synchronized (this.esmes) {
			this.esmes.remove(esme);
		}

		synchronized (this.esmesToSendPdu) {
			this.esmesToSendPdu.remove(esme);
		}
	}

	/**
	 * This method is to find the correct ESME to send the SMS
	 * 
	 * @return
	 */
	synchronized Esme getNextEsme(final boolean anUpdateIndex) {
	    int idx = index;
	    if (LOG.isDebugEnabled()) {
	        LOG.debug("Index: " + idx + ". Getting next ESME.");
	    }
		// TODO synchronized is correct here?
		for (int i = 0; i < this.esmesToSendPdu.size(); i++) {
			idx++;
			if (idx >= this.esmesToSendPdu.size()) {
				idx = 0;
			}

			Esme esme = this.esmesToSendPdu.get(idx);
			if (esme.isBound()) {
		        if (LOG.isDebugEnabled()) {
		            LOG.debug("Index: " + idx + ". Using ESME: " + esme.getName() + ".");
		        }
		        if (anUpdateIndex) {
		            index = idx;
		        }
				return esme;
			}
		}
        if (LOG.isDebugEnabled()) {
            LOG.debug("Index: " + idx + ". Next ESME not selected.");
        }
        if (anUpdateIndex) {
            index = idx;
        }
		return null;
	}


	boolean hasMoreEsmes() {
		return (esmes.size() > 0);
	}

    @Override
    public String toString() {
        final int countAll = esmes.size();
        final int countEsmesForSending = esmesToSendPdu.size();
        final StringBuilder sb = new StringBuilder();
        sb.append(clusterName).append("[");
        for (int i = 0; i < countAll; i++) {
            sb.append(esmes.get(i).getName());
            if (i < countAll - 1) {
                sb.append(",");
            }
        }
        sb.append("][");
        for (int i = 0; i < countEsmesForSending; i++) {
            sb.append(esmesToSendPdu.get(i).getName());
            if (i < countEsmesForSending - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
