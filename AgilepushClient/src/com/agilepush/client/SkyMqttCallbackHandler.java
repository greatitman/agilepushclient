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

import java.util.Calendar;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.skypush.demo.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat.Builder;

/**
 * Handles call backs from the MQTT Client
 * 
 */
public class SkyMqttCallbackHandler implements MqttCallback {

	/**
	 * {@link Context} for the application used to format and import external
	 * strings
	 **/
	private Context context;
	/**
	 * Client handle to reference the connection that this handler is attached
	 * to
	 **/
	private String clientHandle;

	/**
	 * The clientId of the client associated with this <code>Connection</code>
	 * object
	 **/
	private String clientId = null;
	/**
	 * The host that the {@link MqttAndroidClient} represented by this
	 * <code>Connection</code> is represented by
	 **/
	private String host = null;
	/** The port on the server this client is connecting to **/
	/** Message ID Counter **/
	private static int MessageID = 0;

	/**
	 * Creates an <code>MqttCallbackHandler</code> object
	 * 
	 * @param context
	 *            The application's context
	 * @param clientHandle
	 *            The handle to a {@link Connection} object
	 */
	public SkyMqttCallbackHandler(Context context, String clientHandle,
			String clientId, String server) {
		this.context = context;
		this.clientHandle = clientHandle;
		this.clientId = clientId;
		this.host = server;
	}

	/**
	 * Displays a notification in the notification area of the UI
	 * 
	 * @param context
	 *            Context from which to create the notification
	 * @param messageString
	 *            The string to display to the user as a message
	 * @param intent
	 *            The intent which will start the activity when the user clicks
	 *            the notification
	 * @param notificationTitle
	 *            The resource reference to the notification title
	 */
	private void notifcation(String messageString, Intent intent,
			int notificationTitle) {

		// Get the notification manage which we will use to display the
		// notification
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(ns);

		Calendar.getInstance().getTime().toString();

		long when = System.currentTimeMillis();

		// get the notification title from the application's strings.xml file
		CharSequence contentTitle = context.getString(notificationTitle);

		// the message that will be displayed as the ticker
		String ticker = contentTitle + " " + messageString;

		// build the pending intent that will start the appropriate activity
		PendingIntent pendingIntent = PendingIntent.getActivity(context,
				SkyMqttConstants.showHistory, intent, 0);

		// build the notification
		Builder notificationCompat = new Builder(context);
		notificationCompat.setAutoCancel(true).setContentTitle(contentTitle)
				.setContentIntent(pendingIntent).setContentText(messageString)
				.setTicker(ticker).setWhen(when)
				.setSmallIcon(R.drawable.ic_launcher);

		Notification notification = notificationCompat.build();
		// display the notification
		mNotificationManager.notify(MessageID, notification);
		MessageID++;

	}

	/**
	 * @see org.eclipse.paho.client.mqttv3.MqttCallback#connectionLost(java.lang.Throwable)
	 */
	@Override
	public void connectionLost(Throwable cause) {
		// cause.printStackTrace();
		if (cause != null) {
			// format string to use a notification text
			Object[] args = new Object[2];
			args[0] = this.clientId;
			args[1] = this.host;

			String message = context.getString(R.string.connection_lost, args);

			// build intent
			Intent intent = new Intent();
			intent.setClassName(context,
					"com.skypush.demo.PushActivity");
			intent.putExtra("handle", clientHandle);

			// notify the user
			notifcation(message, intent, R.string.notifyTitle_connectionLost);
		}
	}

	/**
	 * @see org.eclipse.paho.client.mqttv3.MqttCallback#messageArrived(java.lang.String,
	 *      org.eclipse.paho.client.mqttv3.MqttMessage)
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message)
			throws Exception {

		// create arguments to format message arrived notifcation string
		String[] args = new String[2];
		args[0] = new String(message.getPayload());
		args[1] = topic + ";qos:" + message.getQos() + ";retained:"
				+ message.isRetained();

		// get the string from strings.xml and format
		// String messageString = context.getString(R.string.messageRecieved,
		// (Object[]) args);

		// create intent to start activity
		Intent intent = new Intent();
		intent.setClassName(context,
				"com.skypush.demo.PushActivity");
		intent.putExtra("handle", clientHandle);

		// format string args
		Object[] notifyArgs = new String[3];
		notifyArgs[0] = clientId;
		notifyArgs[1] = new String(message.getPayload());
		notifyArgs[2] = topic;

		// notify the user
		notifcation(context.getString(R.string.notification, notifyArgs), intent,
				R.string.notifyTitle);

		// update client history
		// c.addAction(messageString);

	}

	/**
	 * @see org.eclipse.paho.client.mqttv3.MqttCallback#deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken)
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// Do nothing
	}

}
