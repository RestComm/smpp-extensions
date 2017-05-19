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

package org.restcomm.smpp.oam;

/**
 * 
 * @author Amit Bhayani
 * @author sergey vetyutnev
 * 
 */
public interface SmppOamMessages {

    /**
     * Pre defined messages
     */
    public static final String INVALID_COMMAND = "Invalid Command";

    public static final String ILLEGAL_ARGUMENT = "Illegal argument %s: %s";


    public static final String NEW_LINE = "\n";


    public static final String ESME_START_SUCCESSFULL = "Successfully started ESME name=%s";

    public static final String ESME_STOP_SUCCESSFULL = "Successfully stopped ESME name=%s";

    public static final String CREATE_ESME_SUCCESSFULL = "Successfully created ESME name=%s";
    
    public static final String MODIFY_ESME_SUCCESSFULL = "Successfully modified ESME name=%s";

    public static final String DELETE_ESME_SUCCESSFUL = "Successfully deleted Esme with given name %s";

    public static final String NO_ESME_DEFINED_YET = "No ESME defined yet";

    public static final String SMPP_SERVER_PARAMETER_SUCCESSFULLY_SET = "Parameter has been successfully set. The changed value will take effect after SmppServer is restarted";

    public static final String INVALID_SMPP_BIND_TYPE = "Invalid SMPP Bind Type %s. Allowed are TRANSCEIVER, TRANSMITTER or RECEIVER";


    public static final String CREATE_EMSE_FAIL_PORT_CANNOT_BE_LESS_THAN_ZERO = "Creation of EMSE failed. Port cannot be less than 0 for CLIENT Sessions";

    public static final String CREATE_EMSE_FAIL_HOST_CANNOT_BE_ANONYMOUS = "Creation of EMSE failed. Host cannot be anonymous (-1) for CLIENT Sessions";

    public static final String CREATE_EMSE_FAIL_ALREADY_EXIST = "Creation of EMSE failed. Other ESME with name=%s already exist";

    public static final String CREATE_EMSE_FAIL_PRIMARY_KEY_ALREADY_EXIST = "Creation of EMSE failed. Other ESME with same SystemId=%s host=%s port=%d and SmppBindType=%s already exist";

    public static final String CREATE_EMSE_FAIL_WRONG_NETWORKID_IN_ESMECLUSTER = "Creation of EMSE failed. NetworkId of the ESME cluster is %d and not equal inserted NetworkId for a new ESME %d";

    public static final String DELETE_ESME_FAILED_NO_ESME_FOUND = "No Esme found with given name %s";

    public static final String DELETE_ESME_FAILED_ESME_STARTED = "Cannot remove ESME. Please stop ESME before removing";

    public static final String START_ESME_FAILED_ALREADY_STARTED = "Esme with given name %s is already started";


    public static final String SHOW_ESME_NAME = "ESME name=";

    public static final String SHOW_ESME_SYSTEM_ID = " systemId=";

    public static final String SHOW_ESME_STATE = " state=";

    public static final String SHOW_ESME_PASSWORD = " password=";

    public static final String SHOW_ESME_HOST = " host=";

    public static final String SHOW_ESME_PORT = " port=";

    public static final String SHOW_NETWORK_ID = " networkId=";

    public static final String SPLIT_LONG_MESSAGES = " split-long-messages=";

    public static final String CHARGING_ENABLED = " chargingEnabled=";

    public static final String SHOW_ESME_BIND_TYPE = " bindType=";

    public static final String SHOW_ESME_SYSTEM_TYPE = " systemType=";

    public static final String SHOW_ESME_INTERFACE_VERSION = " smppInterfaceVersion=";

    public static final String SHOW_ADDRESS = " address=";

    public static final String SHOW_ADDRESS_TON = " ton=";

    public static final String SHOW_ADDRESS_NPI = " npi=";

    public static final String SHOW_CLUSTER_NAME = " clusterName=";

    public static final String SHOW_SOURCE_ADDRESS_TON = " sourceTon=";

    public static final String SHOW_SOURCE_ADDRESS_NPI = " sourceNpi=";

    public static final String SHOW_SOURCE_ADDRESS = " sourceAddress=";

    public static final String SHOW_ROUTING_ADDRESS_TON = " routingTon=";

    public static final String SHOW_ROUTING_ADDRESS_NPI = " routingNpi=";

    public static final String SHOW_ROUTING_ADDRESS = " routingAddress=";

    public static final String SHOW_RATE_LIMIT_PER_SECOND = " rateLimitPerSecond=";

    public static final String SHOW_RATE_LIMIT_PER_MINUTE = " rateLimitPerMinute=";

    public static final String SHOW_RATE_LIMIT_PER_HOUR = " rateLimitPerHour=";

    public static final String SHOW_RATE_LIMIT_PER_DAY = " rateLimitPerDay=";

    public static final String SHOW_SECOND_RECEIVED_MSG_COUNT = " secondReceivedMsgCount=";

    public static final String SHOW_MINUTE_RECEIVED_MSG_COUNT = " minuteReceivedMsgCount=";

    public static final String SHOW_HOUR_RECEIVED_MSG_COUNT = " hourReceivedMsgCount=";

    public static final String SHOW_DAY_RECEIVED_MSG_COUNT = " dayReceivedMsgCount=";

    public static final String SHOW_NATIONAL_LANGUAGE_SINGLE_SHIFT = " nationalLanguageSingleShift=";

    public static final String SHOW_NATIONAL_LANGUAGE_LOCKING_SHIFT = " nationalLanguageLockingShift=";

    public static final String SHOW_DEST_ADDR_SEND_LIMIT = " destAddrSendLimit=";
    
    public static final String MIN_MESSAGE_LENGTH = " minMessageLength=";

    public static final String MAX_MESSAGE_LENGTH = " maxMessageLength=";

    public static final String ENQUIRE_LINK_DELAY_SERVER = " enquireLinkDelayServer=";

	public static final String LINK_DROP_SERVER = " linkDropServer=";

}
