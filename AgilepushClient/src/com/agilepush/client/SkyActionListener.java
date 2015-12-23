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

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

import com.skypush.demo.R;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * This Class handles receiving information from the
 * {@link SkyMqttAndroidClient} and updating the {@link Connection} associated
 * with the action
 */
class SkyActionListener implements IMqttActionListener {

	/**
	 * Actions that can be performed Asynchronously <strong>and</strong>
	 * associated with a {@link SkyActionListener} object
	 * 
	 */
	enum Action {
		/** Connect Action **/
		CONNECT,
		/** Disconnect Action **/
		DISCONNECT,
		/** Subscribe Action **/
		SUBSCRIBE,
		/** Publish Action **/
		PUBLISH
	}

	/**
	 * The {@link Action} that is associated with this instance of
	 * <code>ActionListener</code>
	 **/
	private Action action;
	/** The arguments passed to be used for formatting strings **/
	private String[] additionalArgs;
	/** {@link Context} for performing various operations **/
	private Context context;

	/**
	 * Creates a generic action listener for actions performed form any activity
	 * 
	 * @param context
	 *            The application context
	 * @param action
	 *            The action that is being performed
	 * @param clientHandle
	 *            The handle for the client which the action is being performed
	 *            on
	 * @param additionalArgs
	 *            Used for as arguments for string formating
	 */
	public SkyActionListener(Context context, Action action,
			String clientHandle, String... additionalArgs) {
		this.context = context;
		this.action = action;
		this.additionalArgs = additionalArgs;
	}

	/**
	 * The action associated with this listener has been successful.
	 * 
	 * @param asyncActionToken
	 *            This argument is not used
	 */
	@Override
	public void onSuccess(IMqttToken asyncActionToken) {
		Log.e("lxs","action success "+ action);
		switch (action) {
		case CONNECT:
			Log.e("lxs","connect success");
			break;
		case DISCONNECT:
			Log.e("lxs","disconnect success");
			break;
		case SUBSCRIBE:
			Log.e("lxs","subscribe success");
			subscribe();
			break;
		case PUBLISH:
			publish();
			break;
		}

	}

	/**
	 * A publish action has been successfully completed, update connection
	 * object associated with the client this action belongs to, then notify the
	 * user of success
	 */
	private void publish() {

		// Connection c =
		// Connections.getInstance(context).getConnection(clientHandle);
		String actionTaken = context.getString(R.string.toast_pub_success,
				(Object[]) additionalArgs);
		// c.addAction(actionTaken);
		toast(context, actionTaken, Toast.LENGTH_SHORT);
	}

	/**
	 * A subscribe action has been successfully completed, update the connection
	 * object associated with the client this action belongs to and then notify
	 * the user of success
	 */
	private void subscribe() {
		// Connection c =
		// Connections.getInstance(context).getConnection(clientHandle);
		String actionTaken = context.getString(R.string.toast_sub_success,
				(Object[]) additionalArgs);
		// c.addAction(actionTaken);
		toast(context, actionTaken, Toast.LENGTH_SHORT);

	}


	/**
	 * The action associated with the object was a failure
	 * 
	 * @param token
	 *            This argument is not used
	 * @param exception
	 *            The exception which indicates why the action failed
	 */
	@Override
	public void onFailure(IMqttToken token, Throwable exception) {
		Log.e("lxs","action failure "+ action);
		switch (action) {
		case CONNECT:
			Log.e("lxs","connect exception");
			break;
		case DISCONNECT:
			Log.e("lxs","disconnect exception");
			break;
		case SUBSCRIBE:
			subscribe(exception);
			break;
		case PUBLISH:
			publish(exception);
			break;
		}

	}

	/**
	 * A publish action was unsuccessful, notify user and update client history
	 * 
	 * @param exception
	 *            This argument is not used
	 */
	private void publish(Throwable exception) {
		// Connection c =
		// Connections.getInstance(context).getConnection(clientHandle);
		String action = context.getString(R.string.toast_pub_failed,
				(Object[]) additionalArgs);
		// c.addAction(action);
		toast(context, action, Toast.LENGTH_SHORT);
	}

	/**
	 * A subscribe action was unsuccessful, notify user and update client
	 * history
	 * 
	 * @param exception
	 *            This argument is not used
	 */
	private void subscribe(Throwable exception) {
		// Connection c =
		// Connections.getInstance(context).getConnection(clientHandle);
		String action = context.getString(R.string.toast_sub_failed,
				(Object[]) additionalArgs);
		// c.addAction(action);
		toast(context, action, Toast.LENGTH_SHORT);

	}



	/**
	 * Display a toast notification to the user
	 * 
	 * @param context
	 *            Context from which to create a notification
	 * @param text
	 *            The text the toast should display
	 * @param duration
	 *            The amount of time for the toast to appear to the user
	 */
	private void toast(Context context, CharSequence text, int duration) {
		Toast toast = Toast.makeText(context, text, duration);
		toast.show();
	}

}