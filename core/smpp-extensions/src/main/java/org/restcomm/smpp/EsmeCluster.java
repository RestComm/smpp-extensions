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

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;

/**
 * 
 * @author Amit Bhayani
 * 
 */
public class EsmeCluster {
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
	synchronized Esme getNextEsme() {
		// TODO synchronized is correct here?
		for (int i = 0; i < this.esmesToSendPdu.size(); i++) {
			this.index++;
			if (this.index >= this.esmesToSendPdu.size()) {
				this.index = 0;
			}

			Esme esme = this.esmesToSendPdu.get(this.index);
			if (esme.isBound()) {
				return esme;
			}
		}

		return null;
	}

	boolean hasMoreEsmes() {
		return (esmes.size() > 0);
	}
	
    /**
     * Checks if is OK for given request parameters.
     *
     * @param aTon the TON
     * @param anNpi the NPI
     * @param anAddress the address
     * @param aName the a name
     * @return true, if is OK for give parameters
     */
    public boolean isOkFor(final int aTon, final int anNpi, final String anAddress, final String aName) {
        for (FastList.Node<Esme> n = esmesToSendPdu.head(), end = esmesToSendPdu.tail(); (n = n.getNext()) != end;) {
            final Esme esme = n.getValue();
            if (esme.getName().equals(aName)) {
                continue;
            }
            if (esme.isRoutingAddressMatching(aTon, anNpi, anAddress)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        final int count = esmes.size();
        final StringBuilder sb = new StringBuilder();
        sb.append(clusterName).append("[");
        for (int i = 0; i < count; i++) {
            sb.append(esmes.get(i).getName());
            if (i < count - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
