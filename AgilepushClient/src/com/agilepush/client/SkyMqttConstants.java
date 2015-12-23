/*******************************************************************************
 * Copyright (c) 1999, 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 */
package com.agilepush.client;

/**
 * Various strings used to identify operations or data in the Android MQTT
 * service, mainly used in Intents passed between Activities and the Service.
 */
interface SkyMqttConstants {

	/*
	 * Version information
	 */

	static final String VERSION = "v0";

	/*
	 * Attributes of messages <p> Used for the column names in the database
	 */
	static final String DUPLICATE = "duplicate";
	static final String RETAINED = "retained";
	static final String QOS = "qos";
	static final String PAYLOAD = "payload";
	static final String DESTINATION_NAME = "destinationName";
	static final String CLIENT_HANDLE = "clientHandle";
	static final String MESSAGE_ID = "messageId";

	/* Tags for actions passed between the Activity and the Service */
	static final String SEND_ACTION = "send";
	static final String UNSUBSCRIBE_ACTION = "unsubscribe";
	static final String SUBSCRIBE_ACTION = "subscribe";
	static final String DISCONNECT_ACTION = "disconnect";
	static final String CONNECT_ACTION = "connect";
	static final String MESSAGE_ARRIVED_ACTION = "messageArrived";
	static final String MESSAGE_DELIVERED_ACTION = "messageDelivered";
	static final String ON_CONNECTION_LOST_ACTION = "onConnectionLost";
	static final String TRACE_ACTION = "trace";

	/* Identifies an Intent which calls back to the Activity */
	static final String CALLBACK_TO_ACTIVITY = SkyMqttService.TAG
			+ ".callbackToActivity" + "." + VERSION;

	/* Identifiers for extra data on Intents broadcast to the Activity */
	static final String CALLBACK_ACTION = SkyMqttService.TAG
			+ ".callbackAction";
	static final String CALLBACK_STATUS = SkyMqttService.TAG
			+ ".callbackStatus";
	static final String CALLBACK_CLIENT_HANDLE = SkyMqttService.TAG + "."
			+ CLIENT_HANDLE;
	static final String CALLBACK_ERROR_MESSAGE = SkyMqttService.TAG
			+ ".errorMessage";
	static final String CALLBACK_EXCEPTION_STACK = SkyMqttService.TAG
			+ ".exceptionStack";
	static final String CALLBACK_INVOCATION_CONTEXT = SkyMqttService.TAG + "."
			+ "invocationContext";
	static final String CALLBACK_ACTIVITY_TOKEN = SkyMqttService.TAG + "."
			+ "activityToken";
	static final String CALLBACK_DESTINATION_NAME = SkyMqttService.TAG + '.'
			+ DESTINATION_NAME;
	static final String CALLBACK_MESSAGE_ID = SkyMqttService.TAG + '.'
			+ MESSAGE_ID;
	static final String CALLBACK_MESSAGE_PARCEL = SkyMqttService.TAG
			+ ".PARCEL";
	static final String CALLBACK_TRACE_SEVERITY = SkyMqttService.TAG
			+ ".traceSeverity";
	static final String CALLBACK_TRACE_TAG = SkyMqttService.TAG + ".traceTag";
	static final String CALLBACK_TRACE_ID = SkyMqttService.TAG + ".traceId";
	static final String CALLBACK_ERROR_NUMBER = SkyMqttService.TAG
			+ ".ERROR_NUMBER";

	static final String CALLBACK_EXCEPTION = SkyMqttService.TAG + ".exception";

	// Intent prefix for Ping sender.
	static final String PING_SENDER = SkyMqttService.TAG + ".pingSender.";

	// Constant for wakelock
	static final String PING_WAKELOCK = SkyMqttService.TAG + ".client.";
	static final String WAKELOCK_NETWORK_INTENT = SkyMqttService.TAG + "";

	// Trace severity levels
	static final String TRACE_ERROR = "error";
	static final String TRACE_DEBUG = "debug";
	static final String TRACE_EXCEPTION = "exception";

	// exception code for non MqttExceptions
	static final int NON_MQTT_EXCEPTION = -1;
	/** Show History Request Code **/
	static final int showHistory = 3;
	static final String empty = new String();

	enum Status {
		/**
		 * Indicates that the operation succeeded
		 */
		OK,

		/**
		 * Indicates that the operation failed
		 */
		ERROR,

		/**
		 * Indicates that the operation's result may be returned asynchronously
		 */
		NO_RESULT
	}

}