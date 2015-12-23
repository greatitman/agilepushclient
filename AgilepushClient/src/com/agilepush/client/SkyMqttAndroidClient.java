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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.MqttToken;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.util.SparseArray;

/**
 * Enables an android application to communicate with an MQTT server using
 * non-blocking methods.
 * <p>
 * Implementation of the MQTT asynchronous client interface
 * {@link IMqttAsyncClient} , using the MQTT android service to actually
 * interface with MQTT server. It provides android applications a simple
 * programming interface to all features of the MQTT version 3.1 specification
 * including:
 * <ul>
 * <li>connect
 * <li>publish
 * <li>subscribe
 * <li>unsubscribe
 * <li>disconnect
 * </ul>
 * </p>
 */
public class SkyMqttAndroidClient implements IMqttAsyncClient, MqttCallback {

	/**
	 * 
	 * The Acknowledgment mode for messages received from
	 * {@link MqttCallback#messageArrived(String, MqttMessage)}
	 * 
	 */
	public enum Ack {
		/**
		 * As soon as the
		 * {@link MqttCallback#messageArrived(String, MqttMessage)} returns, the
		 * message has been acknowledged as received .
		 */
		AUTO_ACK,
		/**
		 * When {@link MqttCallback#messageArrived(String, MqttMessage)}
		 * returns, the message will not be acknowledged as received, the
		 * application will have to make an acknowledgment call to
		 * {@link MqttAndroidClient} using
		 * {@link MqttAndroidClient#acknowledgeMessage(String)}
		 */
		MANUAL_ACK
	}

	// private static final String SERVICE_NAME =
	// "com.skypush.demo.MqttService";
	//
	// private static final int BIND_SERVICE_FLAG = 0;
	//
	// private static ExecutorService pool = Executors.newCachedThreadPool();

	private static final String TAG = "SkyMqttAndroidClient";

	private volatile boolean disconnected = true;
	// store connect ActivityToken for reconnect
	private String reconnectActivityToken = null;

	private WakeLock wakelock = null;
	// Saved sent messages and their corresponding Topics, activityTokens and
	// invocationContexts, so we can handle "deliveryComplete" callbacks
	// from the mqttClient
	private Map<IMqttDeliveryToken, String /* Topic */> savedTopics = new HashMap<IMqttDeliveryToken, String>();
	private Map<IMqttDeliveryToken, MqttMessage> savedSentMessages = new HashMap<IMqttDeliveryToken, MqttMessage>();
	private Map<IMqttDeliveryToken, String> savedActivityTokens = new HashMap<IMqttDeliveryToken, String>();
	private Map<IMqttDeliveryToken, String> savedInvocationContexts = new HashMap<IMqttDeliveryToken, String>();

	// The Android Service which will process our mqtt calls
	private SkyMqttService mqttService;
	// our client object - instantiated on connect
	private MqttAsyncClient myClient = null;

	Context myContext;

	// We hold the various tokens in a collection and pass identifiers for them
	// to the service
	private SparseArray<IMqttToken> tokenMap = new SparseArray<IMqttToken>();
	private int tokenNumber = 0;

	// Connection data
	private String serverURI;
	private String clientId;
	private MqttClientPersistence persistence = null;
	private MqttConnectOptions connectOptions;
	private IMqttToken connectToken;
	// Indicate this connection is connecting or not.
	// This variable uses to avoid reconnect multiple times.
	private volatile boolean isConnecting = false;

	// The MqttCallback provided by the application
	private MqttCallback callback;

	// The acknowledgment that a message has been processed by the application
	private Ack messageAck;

	// private volatile boolean bindedService = false;

	private String wakeLockTag = null;

	/**
	 * Constructor - create an MqttAndroidClient that can be used to communicate
	 * with an MQTT server on android
	 * 
	 * @param context
	 *            object used to pass context to the callback.
	 * @param serverURI
	 *            specifies the protocol, host name and port to be used to
	 *            connect to an MQTT server
	 * @param clientId
	 *            specifies the name by which this connection should be
	 *            identified to the server
	 */
	public SkyMqttAndroidClient(SkyMqttService mqttService, String serverURI,
			String clientId) {
		this(mqttService, serverURI, clientId, null, Ack.AUTO_ACK);
	}

	/**
	 * Constructor - create an MqttAndroidClient that can be used to communicate
	 * with an MQTT server on android
	 * 
	 * @param ctx
	 *            Application's context
	 * @param serverURI
	 *            specifies the protocol, host name and port to be used to
	 *            connect to an MQTT server
	 * @param clientId
	 *            specifies the name by which this connection should be
	 *            identified to the server
	 * @param ackType
	 *            how the application wishes to acknowledge a message has been
	 *            processed
	 */
	public SkyMqttAndroidClient(SkyMqttService mqttService, String serverURI,
			String clientId, Ack ackType) {
		this(mqttService, serverURI, clientId, null, ackType);
	}

	/**
	 * Constructor - create an MqttAndroidClient that can be used to communicate
	 * with an MQTT server on android
	 * 
	 * @param ctx
	 *            Application's context
	 * @param serverURI
	 *            specifies the protocol, host name and port to be used to
	 *            connect to an MQTT server
	 * @param clientId
	 *            specifies the name by which this connection should be
	 *            identified to the server
	 * @param persistence
	 *            The object to use to store persisted data
	 */
	public SkyMqttAndroidClient(SkyMqttService mqttService, String serverURI,
			String clientId, MqttClientPersistence persistence) {
		this(mqttService, serverURI, clientId, persistence, Ack.AUTO_ACK);
	}

	/**
	 * Constructor- create an MqttAndroidClient that can be used to communicate
	 * with an MQTT server on android
	 * 
	 * @param context
	 *            used to pass context to the callback.
	 * @param serverURI
	 *            specifies the protocol, host name and port to be used to
	 *            connect to an MQTT server
	 * @param clientId
	 *            specifies the name by which this connection should be
	 *            identified to the server
	 * @param persistence
	 *            the persistence class to use to store in-flight message. If
	 *            null then the default persistence mechanism is used
	 * @param ackType
	 *            how the application wishes to acknowledge a message has been
	 *            processed.
	 */
	public SkyMqttAndroidClient(SkyMqttService mqttService, String serverURI,
			String clientId, MqttClientPersistence persistence, Ack ackType) {
		this.mqttService = mqttService;
		this.serverURI = serverURI;
		this.clientId = clientId;
		this.persistence = persistence;
		messageAck = ackType;
		StringBuffer buff = new StringBuffer(this.getClass().getCanonicalName());
		buff.append(" ");
		buff.append(clientId);
		buff.append(" ");
		buff.append("on host ");
		buff.append(serverURI);
		wakeLockTag = buff.toString();
	}

	/**
	 * Determines if this client is currently connected to the server.
	 * 
	 * @return <code>true</code> if connected, <code>false</code> otherwise.
	 */
	@Override
	public boolean isConnected() {
		if (myClient != null)
			return myClient.isConnected();
		return false;
	}

	/**
	 * Returns the client ID used by this client.
	 * <p>
	 * All clients connected to the same server or server farm must have a
	 * unique ID.
	 * </p>
	 * 
	 * @return the client ID used by this client.
	 */
	@Override
	public String getClientId() {
		return clientId;
	}

	/**
	 * Returns the URI address of the server used by this client.
	 * <p>
	 * The format of the returned String is the same as that used on the
	 * constructor.
	 * </p>
	 * 
	 * @return the server's address, as a URI String.
	 */
	@Override
	public String getServerURI() {
		return serverURI;
	}

	/**
	 * Close the client. Releases all resource associated with the client. After
	 * the client has been closed it cannot be reused. For instance attempts to
	 * connect will fail.
	 * 
	 * @throws MqttException
	 *             if the client is not disconnected.
	 */
	@Override
	public void close() {
		Log.d(TAG, "close()");
		try {
			if (myClient != null) {
				myClient.close();
			}
		} catch (MqttException e) {
			Log.e(TAG, "ANSY MQTT close exception");
		}

	}

	@Override
	public IMqttToken connect() throws MqttException {
		return connect(null, null);
	}

	@Override
	public IMqttToken connect(MqttConnectOptions options) throws MqttException {
		return connect(options, null, null);
	}

	@Override
	public IMqttToken connect(Object userContext, IMqttActionListener callback)
			throws MqttException {
		return connect(new MqttConnectOptions(), userContext, callback);
	}

	/**
	 * Connects to an MQTT server using the specified options.
	 * <p>
	 * The server to connect to is specified on the constructor. It is
	 * recommended to call {@link #setCallback(MqttCallback)} prior to
	 * connecting in order that messages destined for the client can be accepted
	 * as soon as the client is connected.
	 * </p>
	 * <p>
	 * The method returns control before the connect completes. Completion can
	 * be tracked by:
	 * <ul>
	 * <li>Waiting on the returned token {@link IMqttToken#waitForCompletion()}
	 * or</li>
	 * <li>Passing in a callback {@link IMqttActionListener}</li>
	 * </ul>
	 * </p>
	 * 
	 * @param options
	 *            a set of connection parameters that override the defaults.
	 * @param userContext
	 *            optional object for used to pass context to the callback. Use
	 *            null if not required.
	 * @param callback
	 *            optional listener that will be notified when the connect
	 *            completes. Use null if not required.
	 * @return token used to track and wait for the connect to complete. The
	 *         token will be passed to any callback that has been set.
	 * @throws MqttException
	 *             for any connected problems, including communication errors
	 */

	@Override
	public IMqttToken connect(MqttConnectOptions options, Object userContext,
			IMqttActionListener callback) throws MqttException {

		IMqttToken token = new SkyMqttTokenAndroid(this, userContext, callback);

		connectOptions = options;
		connectToken = token;
		final String activityToken = storeToken(connectToken);
		reconnectActivityToken = activityToken;
		try {
			Log.e("lxs", "begin to new connect listener");
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken) {

				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					doAfterConnectSuccess(activityToken);
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken,
						Throwable exception) {
					doAfterConnectFail(activityToken, exception);

				}
			};

			if (myClient != null) {
				if (isConnecting) {
					Log.d(TAG,
							"myClient != null and the client is connecting. Connect return directly.");
					return token;
				} else if (!disconnected) {
					Log.d(TAG,
							"myClient != null and the client is connected and notify!");
					doAfterConnectSuccess(activityToken);
				} else {
					Log.d(TAG,
							"myClient != null and the client is not connected");
					setConnectingState(true);
					myClient.connect(connectOptions, null, listener);
				}
			}

			// if myClient is null, then create a new connection
			else {
				Log.e("lxs", "begin to new mqtt async client");
				myClient = new MqttAsyncClient(serverURI, clientId, persistence);
				myClient.setCallback(this);

				Log.e(TAG, "Do Real connect! " + connectOptions.getServerURIs());
				setConnectingState(true);
				myClient.connect(connectOptions, null, listener);
				Log.e(TAG, "connect over " + listener);
			}
		} catch (Exception e) {
			doAfterConnectFail(activityToken, e);
		}

		return token;
	}

	/**
	 * Reconnect<br>
	 * Only appropriate if cleanSession is false and we were connected. Declare
	 * as synchronized to avoid multiple calls to this method to send connect
	 * multiple times
	 */
	synchronized void reconnect() {
		if (isConnecting) {
			Log.d(TAG, "The client is connecting. Reconnect return directly.");
			return;
		}

		if (disconnected) {
			// use the activityToke the same with action connect
			Log.d(TAG, "Do Real Reconnect!");

			try {

				IMqttActionListener listener = new MqttConnectionListener(
						reconnectActivityToken);

				myClient.connect(connectOptions, null, listener);
				setConnectingState(true);
			} catch (MqttException e) {
				Log.e(TAG,
						"Cannot reconnect to remote server." + e.getMessage());
				setConnectingState(false);
				// handleException(resultBundle, e);
			}
		}
	}

	/**
	 * Acquires a partial wake lock for this client
	 */
	private void acquireWakeLock() {
		if (wakelock == null) {
			PowerManager pm = (PowerManager) mqttService
					.getSystemService(Context.POWER_SERVICE);
			wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					wakeLockTag);
		}
		wakelock.acquire();

	}

	private void doAfterConnectSuccess(final String activityToken) {
		// since the device's cpu can go to sleep, acquire a wakelock and drop
		// it later.
		acquireWakeLock();

		IMqttToken token = connectToken;
		removeMqttToken(activityToken);
		Log.e(SkyMqttService.TAG, "connect success " + activityToken);
		if (token != null) {
			((SkyMqttTokenAndroid) token).notifyComplete();

		} else {
			Log.e(SkyMqttService.TAG, "simpleAction : token is null");
		}

		setConnectingState(false);
		disconnected = false;
		releaseWakeLock();
	}

	private void doAfterConnectFail(final String activityToken,
			Throwable exception) {
		acquireWakeLock();
		disconnected = true;
		setConnectingState(false);
		IMqttToken token = connectToken;
		removeMqttToken(activityToken);
		if (token != null) {
			((SkyMqttTokenAndroid) token).notifyFailure(exception);
		} else {
			Log.e(TAG, "simpleAction : token is null");
		}
		releaseWakeLock();
	}

	private void handleException(final String activityToken, Throwable exception) {
		IMqttToken token = connectToken;
		removeMqttToken(activityToken);
		if (token != null) {
			((SkyMqttTokenAndroid) token).notifyFailure(exception);
		} else {
			Log.e(TAG, "simpleAction : token is null");
		}
	}

	/**
	 * General-purpose IMqttActionListener for the Client context
	 * <p>
	 * Simply handles the basic success/failure cases for operations which don't
	 * return results
	 * 
	 */
	private class MqttConnectionListener implements IMqttActionListener {

		private final String activityToken;

		private MqttConnectionListener(String activityToken) {
			this.activityToken = activityToken;
		}

		@Override
		public void onSuccess(IMqttToken asyncActionToken) {
			Log.d(TAG, "mqtt action on success ssss" + activityToken);

			IMqttToken token =	removeMqttToken(activityToken);
			if (token != null) {
				((SkyMqttTokenAndroid) token).notifyComplete();

			} else {
				Log.e(SkyMqttService.TAG, "simpleAction : token is null");
			}
		}

		@Override
		public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
			Log.d(TAG, "mqtt action on failure");
			IMqttToken token = connectToken;
			removeMqttToken(activityToken);
			if (token != null) {
				((SkyMqttTokenAndroid) token).notifyFailure(exception);
			} else {
				Log.e(TAG, "simpleAction : token is null");
			}
		}
	}

	void offline() {

		if (!disconnected) {
			Exception e = new Exception("Android offline");
			connectionLost(e);
		}
	}

	@Override
	public IMqttToken disconnect() throws MqttException {
		IMqttToken token = new SkyMqttTokenAndroid(this, null,
				(IMqttActionListener) null);
		final String activityToken = storeToken(token);

		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken) {

				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d(TAG, "mqtt action on success");

					IMqttToken token = connectToken;
					removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyComplete();

					} else {
						Log.e(SkyMqttService.TAG,
								"simpleAction : token is null");
					}
					if (callback != null) {
						callback.connectionLost(null);
					}
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken,
						Throwable exception) {
					Log.d(TAG, "mqtt action on failure");
					IMqttToken token = removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyFailure(exception);
					} else {
						Log.e(TAG, "simpleAction : token is null");
					}
				}
			};
			try {
				myClient.disconnect(null, listener);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {
			Log.e(TAG, "NOT_CONNECTED for action disconnect");
		}

		if (connectOptions.isCleanSession()) {
			// assume we'll clear the stored messages at this point
			Log.e(TAG, "lxs todo clear arrived messages");
			// service.messageStore.clearArrivedMessages(clientHandle);
		}
		releaseWakeLock();
		return token;
	}

	@Override
	public IMqttToken disconnect(long quiesceTimeout) throws MqttException {
		IMqttToken token = new SkyMqttTokenAndroid(this, null,
				(IMqttActionListener) null);
		final String activityToken = storeToken(token);

		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken) {

				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d(TAG, "mqtt action on success");

					IMqttToken token = connectToken;
					removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyComplete();

					} else {
						Log.e(SkyMqttService.TAG,
								"simpleAction : token is null");
					}
					if (callback != null) {
						callback.connectionLost(null);
					}
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken,
						Throwable exception) {
					Log.d(TAG, "mqtt action on failure");
					IMqttToken token = connectToken;
					removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyFailure(exception);
					} else {
						Log.e(TAG, "simpleAction : token is null");
					}
				}
			};
			try {
				myClient.disconnect(null, listener);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {
			Log.e(TAG, "NOT_CONNECTED for action disconnect");
		}

		if (connectOptions.isCleanSession()) {
			// assume we'll clear the stored messages at this point
			Log.e(TAG, "lxs todo clear arrived messages");
			// service.messageStore.clearArrivedMessages(clientHandle);
		}
		releaseWakeLock();

		return token;
	}

	/**
	 * Disconnects from the server.
	 * <p>
	 * An attempt is made to quiesce the client allowing outstanding work to
	 * complete before disconnecting. It will wait for a maximum of 30 seconds
	 * for work to quiesce before disconnecting. This method must not be called
	 * from inside {@link MqttCallback} methods.
	 * </p>
	 * 
	 * @param userContext
	 *            optional object used to pass context to the callback. Use null
	 *            if not required.
	 * @param callback
	 *            optional listener that will be notified when the disconnect
	 *            completes. Use null if not required.
	 * @return token used to track and wait for the disconnect to complete. The
	 *         token will be passed to any callback that has been set.
	 * @throws MqttException
	 *             for problems encountered while disconnecting
	 * @see #disconnect(long, Object, IMqttActionListener)
	 */
	@Override
	public IMqttToken disconnect(Object userContext,
			IMqttActionListener callback) throws MqttException {
		Log.d(TAG, "disconnect()");
		disconnected = true;
		IMqttToken token = new SkyMqttTokenAndroid(this, userContext, callback);
		final String activityToken = storeToken(token);

		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken) {

				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d(TAG, "mqtt action on success");

					IMqttToken token = connectToken;
					removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyComplete();

					} else {
						Log.e(SkyMqttService.TAG,
								"simpleAction : token is null");
					}
					if (getCallback() != null) {
						getCallback().connectionLost(null);
					}
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken,
						Throwable exception) {
					Log.d(TAG, "mqtt action on failure");
					IMqttToken token = connectToken;
					removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyFailure(exception);
					} else {
						Log.e(TAG, "simpleAction : token is null");
					}
				}
			};
			try {
				myClient.disconnect(null, listener);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {
			Log.e(TAG, "NOT_CONNECTED for action disconnect");
		}

		if (connectOptions.isCleanSession()) {
			// assume we'll clear the stored messages at this point
			Log.e(TAG, "lxs todo clear arrived messages");
			// service.messageStore.clearArrivedMessages(clientHandle);
		}
		releaseWakeLock();

		return token;
	}

	/**
	 * Disconnects from the server.
	 * <p>
	 * The client will wait for {@link MqttCallback} methods to complete. It
	 * will then wait for up to the quiesce timeout to allow for work which has
	 * already been initiated to complete. For instance when a QoS 2 message has
	 * started flowing to the server but the QoS 2 flow has not completed.It
	 * prevents new messages being accepted and does not send any messages that
	 * have been accepted but not yet started delivery across the network to the
	 * server. When work has completed or after the quiesce timeout, the client
	 * will disconnect from the server. If the cleanSession flag was set to
	 * false and next time it is also set to false in the connection, the
	 * messages made in QoS 1 or 2 which were not previously delivered will be
	 * delivered this time.
	 * </p>
	 * <p>
	 * This method must not be called from inside {@link MqttCallback} methods.
	 * </p>
	 * <p>
	 * The method returns control before the disconnect completes. Completion
	 * can be tracked by:
	 * <ul>
	 * <li>Waiting on the returned token {@link IMqttToken#waitForCompletion()}
	 * or</li>
	 * <li>Passing in a callback {@link IMqttActionListener}</li>
	 * </ul>
	 * </p>
	 * 
	 * @param quiesceTimeout
	 *            the amount of time in milliseconds to allow for existing work
	 *            to finish before disconnecting. A value of zero or less means
	 *            the client will not quiesce.
	 * @param userContext
	 *            optional object used to pass context to the callback. Use null
	 *            if not required.
	 * @param callback
	 *            optional listener that will be notified when the disconnect
	 *            completes. Use null if not required.
	 * @return token used to track and wait for the disconnect to complete. The
	 *         token will be passed to any callback that has been set.
	 * @throws MqttException
	 *             for problems encountered while disconnecting
	 */
	@Override
	public IMqttToken disconnect(long quiesceTimeout, Object userContext,
			IMqttActionListener callback) throws MqttException {
		IMqttToken token = new SkyMqttTokenAndroid(this, userContext, callback);
		String activityToken = storeToken(token);

		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken);
			try {
				myClient.disconnect(null, listener);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {
			Log.e(TAG, "NOT_CONNECTED for action disconnect");
		}

		if (connectOptions.isCleanSession()) {
			// assume we'll clear the stored messages at this point
			Log.e(TAG, "lxs todo clear arrived messages");
			// service.messageStore.clearArrivedMessages(clientHandle);
		}
		releaseWakeLock();
		return token;
	}

	/**
	 * Publishes a message to a topic on the server.
	 * <p>
	 * A convenience method, which will create a new {@link MqttMessage} object
	 * with a byte array payload and the specified QoS, and then publish it.
	 * </p>
	 * 
	 * @param topic
	 *            to deliver the message to, for example "finance/stock/ibm".
	 * @param payload
	 *            the byte array to use as the payload
	 * @param qos
	 *            the Quality of Service to deliver the message at. Valid values
	 *            are 0, 1 or 2.
	 * @param retained
	 *            whether or not this message should be retained by the server.
	 * @return token used to track and wait for the publish to complete. The
	 *         token will be passed to any callback that has been set.
	 * @throws MqttPersistenceException
	 *             when a problem occurs storing the message
	 * @throws IllegalArgumentException
	 *             if value of QoS is not 0, 1 or 2.
	 * @throws MqttException
	 *             for other errors encountered while publishing the message.
	 *             For instance, too many messages are being processed.
	 * @see #publish(String, MqttMessage, Object, IMqttActionListener)
	 */
	@Override
	public IMqttDeliveryToken publish(String topic, byte[] payload, int qos,
			boolean retained) throws MqttException, MqttPersistenceException {
		return publish(topic, payload, qos, retained, null, null);
	}

	@Override
	public IMqttDeliveryToken publish(String topic, MqttMessage message)
			throws MqttException, MqttPersistenceException {
		return publish(topic, message, null, null);
	}

	@Override
	public IMqttDeliveryToken publish(String topic, byte[] payload, int qos,
			boolean retained, Object userContext, IMqttActionListener callback)
			throws MqttException, MqttPersistenceException {

		MqttMessage message = new MqttMessage(payload);
		message.setQos(qos);
		message.setRetained(retained);
		SkyMqttTokenAndroid token = new SkyMqttTokenAndroid(this, userContext,
				callback, message);

		final String activityToken = storeToken(token);

		IMqttDeliveryToken sendToken = null;

		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken) {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d(TAG, "mqtt action on success");

					IMqttToken token = connectToken;
					// removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyComplete();

					} else {
						Log.e(SkyMqttService.TAG,
								"simpleAction : token is null");
					}
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken,
						Throwable exception) {
					Log.d(TAG, "mqtt action on failure");
					IMqttToken token = connectToken;
					// remove on delivery
					// removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyFailure(exception);
					} else {
						Log.e(TAG, "simpleAction : token is null");
					}
				}
			};
			try {

				sendToken = myClient.publish(topic, payload, qos, retained,
						null, listener);
				storeSendDetails(topic, message, sendToken, null, activityToken);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {

			Log.e(TAG, "NOT_CONNECTED");

		}

		token.setDelegate(sendToken);

		return sendToken;
	}

	private void storeSendDetails(final String topic, final MqttMessage msg,
			final IMqttDeliveryToken messageToken,
			final String invocationContext, final String activityToken) {
		savedTopics.put(messageToken, topic);
		savedSentMessages.put(messageToken, msg);
		savedActivityTokens.put(messageToken, activityToken);
		savedInvocationContexts.put(messageToken, invocationContext);
	}

	/**
	 * Publishes a message to a topic on the server.
	 * <p>
	 * Once this method has returned cleanly, the message has been accepted for
	 * publication by the client and will be delivered on a background thread.
	 * In the event the connection fails or the client stops, Messages will be
	 * delivered to the requested quality of service once the connection is
	 * re-established to the server on condition that:
	 * <ul>
	 * <li>The connection is re-established with the same clientID
	 * <li>The original connection was made with (@link
	 * MqttConnectOptions#setCleanSession(boolean)} set to false
	 * <li>The connection is re-established with (@link
	 * MqttConnectOptions#setCleanSession(boolean)} set to false
	 * <li>Depending when the failure occurs QoS 0 messages may not be
	 * delivered.
	 * </ul>
	 * </p>
	 * 
	 * <p>
	 * When building an application, the design of the topic tree should take
	 * into account the following principles of topic name syntax and semantics:
	 * </p>
	 * 
	 * <ul>
	 * <li>A topic must be at least one character long.</li>
	 * <li>Topic names are case sensitive. For example, <em>ACCOUNTS</em> and
	 * <em>Accounts</em> are two different topics.</li>
	 * <li>Topic names can include the space character. For example,
	 * <em>Accounts
	 * 	payable</em> is a valid topic.</li>
	 * <li>A leading "/" creates a distinct topic. For example,
	 * <em>/finance</em> is different from <em>finance</em>. <em>/finance</em>
	 * matches "+/+" and "/+", but not "+".</li>
	 * <li>Do not include the null character (Unicode <samp
	 * class="codeph">\x0000</samp>) in any topic.</li>
	 * </ul>
	 * 
	 * <p>
	 * The following principles apply to the construction and content of a topic
	 * tree:
	 * </p>
	 * 
	 * <ul>
	 * <li>The length is limited to 64k but within that there are no limits to
	 * the number of levels in a topic tree.</li>
	 * <li>There can be any number of root nodes; that is, there can be any
	 * number of topic trees.</li>
	 * </ul>
	 * </p>
	 * <p>
	 * The method returns control before the publish completes. Completion can
	 * be tracked by:
	 * <ul>
	 * <li>Setting an {@link IMqttAsyncClient#setCallback(MqttCallback)} where
	 * the {@link MqttCallback#deliveryComplete(IMqttDeliveryToken)} method will
	 * be called.</li>pu
	 * <li>Waiting on the returned token {@link MqttToken#waitForCompletion()}
	 * or</li>
	 * <li>Passing in a callback {@link IMqttActionListener} to this method</li>
	 * </ul>
	 * </p>
	 * 
	 * @param topic
	 *            to deliver the message to, for example "finance/stock/ibm".
	 * @param message
	 *            to deliver to the server
	 * @param userContext
	 *            optional object used to pass context to the callback. Use null
	 *            if not required.
	 * @param callback
	 *            optional listener that will be notified when message delivery
	 *            has completed to the requested quality of service
	 * @return token used to track and wait for the publish to complete. The
	 *         token will be passed to callback methods if set.
	 * @throws MqttPersistenceException
	 *             when a problem occurs storing the message
	 * @throws IllegalArgumentException
	 *             if value of QoS is not 0, 1 or 2.
	 * @throws MqttException
	 *             for other errors encountered while publishing the message.
	 *             For instance, client not connected.
	 * @see MqttMessage
	 */
	@Override
	public IMqttDeliveryToken publish(String topic, MqttMessage message,
			Object userContext, IMqttActionListener callback)
			throws MqttException, MqttPersistenceException {

		SkyMqttTokenAndroid token = new SkyMqttTokenAndroid(this, userContext,
				callback, message);

		final String activityToken = storeToken(token);

		IMqttDeliveryToken sendToken = null;

		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken) {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d(TAG, "mqtt action on success");

					IMqttToken token = connectToken;
					// removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyComplete();

					} else {
						Log.e(SkyMqttService.TAG,
								"simpleAction : token is null");
					}
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken,
						Throwable exception) {
					Log.d(TAG, "mqtt action on failure");
					IMqttToken token = connectToken;
					// remove on delivery
					// removeMqttToken(activityToken);
					if (token != null) {
						((SkyMqttTokenAndroid) token).notifyFailure(exception);
					} else {
						Log.e(TAG, "simpleAction : token is null");
					}
				}
			};
			try {

				sendToken = myClient.publish(topic, message, null, listener);
				storeSendDetails(topic, message, sendToken, null, activityToken);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {

			Log.e(TAG, "NOT_CONNECTED");

		}

		token.setDelegate(sendToken);

		return sendToken;
	}

	@Override
	public IMqttToken subscribe(String topic, int qos) throws MqttException,
			MqttSecurityException {
		return subscribe(topic, qos, null, null);
	}

	@Override
	public IMqttToken subscribe(String[] topic, int[] qos)
			throws MqttException, MqttSecurityException {
		return subscribe(topic, qos, null, null);
	}

	@Override
	public IMqttToken subscribe(String topic, int qos, Object userContext,
			IMqttActionListener callback) throws MqttException {
		IMqttToken token = new SkyMqttTokenAndroid(this, userContext, callback,
				new String[] { topic });
		final String activityToken = storeToken(token);

		Log.e("lxs",
				"myClient " + myClient + " subscribe is connectted: "
						+ myClient.isConnected() + "callback "
						+ token.getActionCallback());
		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken);
			try {
				Log.e("lxs", "subscribe topic: " + topic + " qos: " + qos);
				myClient.subscribe(topic, qos, null, listener);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {
			Log.e("subscribe", "not connect");
		}

		return token;
	}

	@Override
	public IMqttToken subscribe(String[] topic, int[] qos, Object userContext,
			IMqttActionListener callback) throws MqttException {
		IMqttToken token = new SkyMqttTokenAndroid(this, userContext, callback,
				topic);
		String activityToken = storeToken(token);

		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken);
			try {
				myClient.subscribe(topic, qos, null, listener);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {
			Log.e("subscribe", "NOT_CONNECTED");
		}

		return token;
	}

	@Override
	public IMqttToken unsubscribe(String topic) throws MqttException {
		return unsubscribe(topic, null, null);
	}

	@Override
	public IMqttToken unsubscribe(String[] topic) throws MqttException {
		return unsubscribe(topic, null, null);
	}

	@Override
	public IMqttToken unsubscribe(String topic, Object userContext,
			IMqttActionListener callback) throws MqttException {
		IMqttToken token = new SkyMqttTokenAndroid(this, userContext, callback);
		String activityToken = storeToken(token);

		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken);
			try {
				myClient.unsubscribe(topic, null, listener);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {
			Log.e("unsubscribe", "NOT_CONNECTED");
		}
		return token;
	}

	@Override
	public IMqttToken unsubscribe(String[] topic, Object userContext,
			IMqttActionListener callback) throws MqttException {
		IMqttToken token = new SkyMqttTokenAndroid(this, userContext, callback);
		String activityToken = storeToken(token);
		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new MqttConnectionListener(
					activityToken);
			try {
				myClient.unsubscribe(topic, null, listener);
			} catch (Exception e) {
				handleException(activityToken, e);
			}
		} else {
			Log.e("unsubscribe", "NOT_CONNECTED");
		}
		return token;
	}

	/**
	 * Returns the delivery tokens for any outstanding publish operations.
	 * <p>
	 * If a client has been restarted and there are messages that were in the
	 * process of being delivered when the client stopped, this method returns a
	 * token for each in-flight message to enable the delivery to be tracked.
	 * Alternately the {@link MqttCallback#deliveryComplete(IMqttDeliveryToken)}
	 * callback can be used to track the delivery of outstanding messages.
	 * </p>
	 * <p>
	 * If a client connects with cleanSession true then there will be no
	 * delivery tokens as the cleanSession option deletes all earlier state. For
	 * state to be remembered the client must connect with cleanSession set to
	 * false
	 * </P>
	 * 
	 * @return zero or more delivery tokens
	 */
	@Override
	public IMqttDeliveryToken[] getPendingDeliveryTokens() {
		return myClient.getPendingDeliveryTokens();
	}

	@Override
	public void setCallback(MqttCallback callback) {
		this.callback = callback;

	}

	public MqttCallback getCallback() {
		return this.callback;
	}

	/**
	 * 
	 * @param isConnecting
	 */
	synchronized void setConnectingState(boolean isConnecting) {
		this.isConnecting = isConnecting;
	}

	/**
	 * @param token
	 *            identifying an operation
	 * @return an identifier for the token which can be passed to the Android
	 *         Service
	 */
	public synchronized String storeToken(IMqttToken token) {
		tokenMap.put(tokenNumber, token);
		return Integer.toString(tokenNumber++);
	}

	/**
	 * Get a token identified by a string, and remove it from our map
	 * 
	 * @param data
	 * @return the token
	 */
	private synchronized IMqttToken removeMqttToken(String activityToken) {
		// private synchronized IMqttToken removeMqttToken(Bundle data) {
		// String activityToken =
		// data.getString(SkyMqttServiceConstants.CALLBACK_ACTIVITY_TOKEN);
		if (activityToken != null) {
			int tokenNumber = Integer.parseInt(activityToken);
			IMqttToken token = tokenMap.get(tokenNumber);
			tokenMap.delete(tokenNumber);
			return token;
		}
		return null;
	}

	/**
	 * Get the SSLSocketFactory using SSL key store and password
	 * <p>
	 * A convenience method, which will help user to create a SSLSocketFactory
	 * object
	 * </p>
	 * 
	 * @param keyStore
	 *            the SSL key store which is generated by some SSL key tool,
	 *            such as keytool in Java JDK
	 * @param password
	 *            the password of the key store which is set when the key store
	 *            is generated
	 * @return SSLSocketFactory used to connect to the server with SSL
	 *         authentication
	 * @throws MqttSecurityException
	 *             if there was any error when getting the SSLSocketFactory
	 */
	public SSLSocketFactory getSSLSocketFactory(InputStream keyStore,
			String password) throws MqttSecurityException {
		try {
			SSLContext ctx = null;
			SSLSocketFactory sslSockFactory = null;
			KeyStore ts;
			ts = KeyStore.getInstance("BKS");
			ts.load(keyStore, password.toCharArray());
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
			tmf.init(ts);
			TrustManager[] tm = tmf.getTrustManagers();
			ctx = SSLContext.getInstance("SSL");
			ctx.init(null, tm, null);

			sslSockFactory = ctx.getSocketFactory();
			return sslSockFactory;

		} catch (KeyStoreException e) {
			throw new MqttSecurityException(e);
		} catch (CertificateException e) {
			throw new MqttSecurityException(e);
		} catch (FileNotFoundException e) {
			throw new MqttSecurityException(e);
		} catch (IOException e) {
			throw new MqttSecurityException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new MqttSecurityException(e);
		} catch (KeyManagementException e) {
			throw new MqttSecurityException(e);
		}
	}

	@Override
	public void disconnectForcibly() throws MqttException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void disconnectForcibly(long disconnectTimeout) throws MqttException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void disconnectForcibly(long quiesceTimeout, long disconnectTimeout)
			throws MqttException {
		throw new UnsupportedOperationException();
	}

	private void releaseWakeLock() {
		if (wakelock != null && wakelock.isHeld()) {
			wakelock.release();
		}
	}

	@Override
	public void connectionLost(Throwable why) {
		Log.d(TAG, "connectionLost(" + why.getMessage() + ")");
		disconnected = true;
		try {
			myClient.disconnect(null, new IMqttActionListener() {

				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					// No action
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken,
						Throwable exception) {
					// No action
				}
			});
		} catch (Exception e) {
			// ignore it - we've done our best
		}

		if (callback != null) {
			callback.connectionLost(why);
		}
		// client has lost connection no need for wake lock
		releaseWakeLock();
	}

	/**
	 * Callback to indicate a message has been delivered (the exact meaning of
	 * "has been delivered" is dependent on the QOS value)
	 * 
	 * @param messageToken
	 *            the messge token provided when the message was originally sent
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken messageToken) {

		Log.d(TAG, "deliveryComplete(" + messageToken + ")");

		MqttMessage message = savedSentMessages.remove(messageToken);
		if (message != null) { // If I don't know about the message, it's
			// irrelevant
			// String topic = savedTopics.remove(messageToken);
			String activityToken = savedActivityTokens.remove(messageToken);
			// String invocationContext = savedInvocationContexts
			// .remove(messageToken);
			IMqttToken token = removeMqttToken(activityToken);
			if (token != null) {
				if (callback != null) {
					callback.deliveryComplete((IMqttDeliveryToken) token);
				}
			}
		}

		// this notification will have kept the connection alive but send the
		// previously sechudled ping anyway
	}

	/**
	 * Callback when a message is received
	 * 
	 * @param topic
	 *            the topic on which the message was received
	 * @param message
	 *            the message itself
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message)
			throws Exception {

		Log.d(TAG, "messageArrived(" + topic + ",{" + message.toString() + "})");

		// String messageId = java.util.UUID.randomUUID().toString();

		if (callback != null) {
			try {
				if (messageAck == Ack.AUTO_ACK) {
					callback.messageArrived(topic, message);
					// mqttService.acknowledgeMessageArrival(clientHandle,
					// messageId);
				} else {
					// message.messageId = messageId;
					callback.messageArrived(topic, message);
				}

				// let the service discard the saved message details
			} catch (Exception e) {
				// Swallow the exception
			}
		}
	}
}
