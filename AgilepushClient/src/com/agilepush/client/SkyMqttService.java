package com.agilepush.client;

import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

/* 
 * PushService that does all of the work.
 * Most of the logic is borrowed from KeepAliveService.
 * http://code.google.com/p/android-random/source/browse/trunk/TestKeepAlive/src/org/devtcg/demo/keepalive/KeepAliveService.java?r=219
 */
@TargetApi(Build.VERSION_CODES.DONUT)
public class SkyMqttService extends Service {
	// this is the log tag
	public static final String TAG = "DemoPushService";
	// the IP address, where your MQTT broker is running.

	// MQTT client ID, which is given the broker. In this example, I also use
	// this for the topic header.
	// You can use this to run push notifications for multiple apps with one
	// MQTT broker.
	public static String MQTT_CLIENT_ID = "skypush";

	// These are the actions for the service (name are descriptive enough)
	private static final String ACTION_START = MQTT_CLIENT_ID + ".START";
	private static final String ACTION_STOP = MQTT_CLIENT_ID + ".STOP";
	// private static final String ACTION_KEEPALIVE = MQTT_CLIENT_ID
	// + ".KEEP_ALIVE";
	private static final String ACTION_RECONNECT = MQTT_CLIENT_ID
			+ ".RECONNECT";

	// Connectivity manager to determining, when the phone loses connection
	private ConnectivityManager mConnMan;

	// Whether or not the service has been started.
	private boolean mStarted = false;

	// Retry intervals, when the connection is lost.
	private static final long INITIAL_RETRY_INTERVAL = 1000 * 10;
	private static final long MAXIMUM_RETRY_INTERVAL = 1000 * 60 * 30;

	// Preferences instance
	private SharedPreferences mPrefs;
	// We store in the preferences, whether or not the service has been started
	public static final String PREF_STARTED = "isStarted";
	// We also store the deviceID (target)
	public static final String PREF_DEVICE_ID = "deviceID";

	public static final String	PREF_SERVER_IP = "serverIP";

	public static final String	PREF_SERVER_PORT = "serverPort";
	
	public static final String	PREF_USER_NAME = "userName";
	
	public static final String	PREF_PASSWD = "password";
	
	public static final String	PREF_TIMEOUT = "timeOut";
	
	public static final String	PREF_KEEPALIVE = "keepAlive";
	
	public static final String	PREF_CLEAN_SESSION = "cleanSession";
	// We store the last retry interval
	public static final String PREF_RETRY = "retryInterval";

	private boolean cleanSession = false;

	private long mStartTime;
	// private MqttConnectOptions options;

	private String serverURI = null;
	private String mClientID;

	// mapping from client handle strings to actual client connections.

	private String clientHandle;
	private String mTopic = "/World";
	private SkyMqttAndroidClient mClient = null;
	MqttConnectOptions conOpt;
	
	private static Context mCtx;
	// An intent receiver to deal with changes in network connectivity
	private NetworkConnectionIntentReceiver networkConnectionMonitor;


	private BackgroundDataPreferenceReceiver backgroundDataPreferenceMonitor;
	private volatile boolean backgroundDataEnabled = true;

	// Static method to start the service
	public static void actionStart(Context ctx) {
		Intent i = new Intent(ctx, SkyMqttService.class);
		i.setAction(ACTION_START);
		ctx.startService(i);
		mCtx = ctx;
	}
	

	// Static method to stop the service
	public static void actionStop(Context ctx) {
		Intent i = new Intent(ctx, SkyMqttService.class);
		i.setAction(ACTION_STOP);
		ctx.startService(i);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		Log.d(TAG, "Creating service");
		mStartTime = System.currentTimeMillis();

		// Get instances of preferences, connectivity manager and notification
		// manager
		mPrefs = getSharedPreferences(TAG, MODE_PRIVATE);
		mConnMan = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

		/*
		 * If our process was reaped by the system for any reason we need to
		 * restore our state with merely a call to onCreate. We record the last
		 * "started" value and restore it here if necessary.
		 */
		handleCrashedService();
	}

	// This method does any necessary clean-up need in case the server has been
	// destroyed by the system
	// and then restarted
	private void handleCrashedService() {
		if (wasStarted() == true) {
			Log.d(TAG, "Handling crashed service...");

			// Do a clean start
			start();
		}
	}

	void reconnect() {
		if (this.isOnline() && !cleanSession) {
			mClient.reconnect();
		}
	}

	/**
	 * Notify clients we're offline
	 */
	public void notifyClientsOffline() {
		if (!cleanSession) {
			mClient.offline();
		}
	}

	/**
	 * @return whether the android service can be regarded as online
	 */
	public boolean isOnline() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		if (cm.getActiveNetworkInfo() != null
				&& cm.getActiveNetworkInfo().isAvailable()
				&& cm.getActiveNetworkInfo().isConnected()
				&& backgroundDataEnabled) {
			return true;
		}

		return false;
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "Service destroyed (started=" + mStarted + ")");

		// Stop the services, if it has been started
		if (mStarted == true) {
			stop();
		}
		unregisterBroadcastReceivers();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		Log.d(TAG, "Service started with intent=" + intent);
		registerBroadcastReceivers();
		// Do an appropriate action based on the intent.
		if (intent.getAction().equals(ACTION_STOP) == true) {
			stop();
			stopSelf();
		} else if (intent.getAction().equals(ACTION_START) == true) {
			start();
		} else if (intent.getAction().equals(ACTION_RECONNECT) == true) {
			if (isNetworkAvailable()) {
				reconnectIfNecessary();
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	// Reads whether or not the service has been started from the preferences
	private boolean wasStarted() {
		return mPrefs.getBoolean(PREF_STARTED, false);
	}

	// Sets whether or not the services has been started in the preferences.
	private void setStarted(boolean started) {
		mPrefs.edit().putBoolean(PREF_STARTED, started).commit();
		mStarted = started;
	}

	private synchronized void start() {
		Log.d(TAG, "Starting service...");

		// Do nothing, if the service is already running.
		if (mStarted == true) {
			Log.w(TAG, "Attempt to start connection that is already active");
			return;
		}
		String serverIP = mPrefs.getString(PREF_SERVER_IP, null);
		String serverPort = mPrefs.getString(PREF_SERVER_PORT, null);
		String deviceID = mPrefs.getString(PREF_DEVICE_ID,null);
		// Create a new connection only if the device id is not NULL
		Log.e("lxs","server ip " +serverIP);
		if (serverIP == null || serverPort==null || deviceID==null) {
			Log.e("lxs","Device ID not found.");
			return;
		} else
		{
			//String serverURI = "tcp://172.22.198.201:1883";
			serverURI = "tcp://" + serverIP + ":" + serverPort;
			mClientID = deviceID;
			clientHandle = serverURI + mClientID;
		}
		String username = mPrefs.getString(PREF_USER_NAME, "admin");
		String password = mPrefs.getString(PREF_PASSWD, "admin123");
		int timeout = mPrefs.getInt(PREF_TIMEOUT, 60);
		int keepalive = mPrefs.getInt(PREF_KEEPALIVE, 120);
		cleanSession = mPrefs.getBoolean(PREF_CLEAN_SESSION, false);
		
		conOpt = new MqttConnectOptions();
		conOpt.setCleanSession(cleanSession);
		conOpt.setConnectionTimeout(timeout);
		conOpt.setKeepAliveInterval(keepalive);
		conOpt.setUserName(username);
		conOpt.setPassword(password.toCharArray());
		// : TODO for SSL
		// boolean ssl = false;
		// String ssl_key = null;
		// conOpt.setWill(topic, message.getBytes(), qos.intValue(),
		// retained.booleanValue());
		
		

		// Establish an MQTT connection
		connect();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		subscribe();
		// publish();
	}

	private synchronized void stop() {
		// Do nothing, if the service is not running.
		if (mStarted == false) {
			Log.w(TAG, "Attempt to stop connection not active.");
			return;
		}
		// Save stopped state in the preferences
		setStarted(false);
		// stopping the service.
		cancelReconnect();

		// Destroy the MQTT connection if there is one
		disconnect();
	}

	private void disconnect() {
		String[] actionArgs = new String[1];
		actionArgs[0] = mTopic;
		final SkyActionListener callback = new SkyActionListener(mCtx,
				SkyActionListener.Action.DISCONNECT, clientHandle, actionArgs);

		try {
			mClient.disconnect(null, callback);
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	//
	private synchronized void connect() {
		Log.d(TAG, "Connecting...client: "+mClientID+" server: "+serverURI);

		try {
			String[] actionArgs = new String[1];
			actionArgs[0] = mClientID;

			final SkyActionListener concallback = new SkyActionListener(mCtx,
					SkyActionListener.Action.CONNECT, clientHandle, actionArgs);

			mClient = new SkyMqttAndroidClient(this, serverURI, mClientID);
			mClient.setCallback(new SkyMqttCallbackHandler(mCtx, clientHandle,
					mClientID, serverURI));

			IMqttToken connectToken = mClient
					.connect(conOpt, null, concallback);

		} catch (MqttException e) {
			// Schedule a reconnect, if we failed to connect
			Log.d(TAG,
					"MqttException: "
							+ (e.getMessage() != null ? e.getMessage() : "NULL"));
			if (isNetworkAvailable()) {
				scheduleReconnect(mStartTime);
			}
		}
		setStarted(true);
	}

	//
	private synchronized void subscribe() {
		Log.d(TAG, "Subscribe...");

		try {
			// TODO: should move the the connected success
			int qos = 0;
			String[] topics = new String[1];
			topics[0] = mTopic;

			final SkyActionListener subcallback = new SkyActionListener(mCtx,
					SkyActionListener.Action.SUBSCRIBE, clientHandle, topics);
			mClient.subscribe(mTopic, qos, null, subcallback);

		} catch (MqttException e) {
			// Schedule a reconnect, if we failed to connect
			Log.d(TAG,
					"MqttException: "
							+ (e.getMessage() != null ? e.getMessage() : "NULL"));
			if (isNetworkAvailable()) {
				scheduleReconnect(mStartTime);
			}
		}

	}

	@SuppressWarnings("unused")
	private void publish() {
		String topic = "test";
		String message = "hello world";
		int qos = 1;
		boolean retained = false;

		String[] args = new String[2];
		args[0] = message;
		args[1] = topic + ";qos:" + qos + ";retained:" + retained;

		final SkyActionListener pubcallback = new SkyActionListener(mCtx,
				SkyActionListener.Action.PUBLISH, clientHandle, args);

		try {
			mClient.publish(topic, message.getBytes(), qos, retained, null,
					pubcallback);
		} catch (MqttPersistenceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// We schedule a reconnect based on the starttime of the service
	public void scheduleReconnect(long startTime) {
		// the last keep-alive interval
		long interval = mPrefs.getLong(PREF_RETRY, INITIAL_RETRY_INTERVAL);

		// Calculate the elapsed time since the start
		long now = System.currentTimeMillis();
		long elapsed = now - startTime;

		// Set an appropriate interval based on the elapsed time since start
		if (elapsed < interval) {
			interval = Math.min(interval * 4, MAXIMUM_RETRY_INTERVAL);
		} else {
			interval = INITIAL_RETRY_INTERVAL;
		}

		Log.d(TAG, "Rescheduling connection in " + interval + "ms.");

		// Save the new internval
		mPrefs.edit().putLong(PREF_RETRY, interval).commit();

		// Schedule a reconnect using the alarm manager.
		Intent i = new Intent();
		i.setClass(this, SkyMqttService.class);
		i.setAction(ACTION_RECONNECT);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmMgr.set(AlarmManager.RTC_WAKEUP, now + interval, pi);
	}

	// Remove the scheduled reconnect
	public void cancelReconnect() {
		Intent i = new Intent();
		i.setClass(this, SkyMqttService.class);
		i.setAction(ACTION_RECONNECT);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmMgr.cancel(pi);
	}

	private synchronized void reconnectIfNecessary() {
		if (mStarted == true && mClient == null) {
			Log.d(TAG, "Reconnecting...");
			connect();
		}
	}

	// Check if we are online
	private boolean isNetworkAvailable() {
		NetworkInfo info = mConnMan.getActiveNetworkInfo();
		if (info == null) {
			return false;
		}
		return info.isConnected();
	}

	@SuppressWarnings("deprecation")
	private void registerBroadcastReceivers() {
		if (networkConnectionMonitor == null) {
			networkConnectionMonitor = new NetworkConnectionIntentReceiver();
			registerReceiver(networkConnectionMonitor, new IntentFilter(
					ConnectivityManager.CONNECTIVITY_ACTION));
		}

		if (Build.VERSION.SDK_INT < 14 /** Build.VERSION_CODES.ICE_CREAM_SANDWICH **/
		) {
			// Support the old system for background data preferences
			ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
			backgroundDataEnabled = cm.getBackgroundDataSetting();
			if (backgroundDataPreferenceMonitor == null) {
				backgroundDataPreferenceMonitor = new BackgroundDataPreferenceReceiver();
				registerReceiver(
						backgroundDataPreferenceMonitor,
						new IntentFilter(
								ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED));
			}
		}
	}

	@SuppressLint("NewApi")
	private void unregisterBroadcastReceivers() {
		if (networkConnectionMonitor != null) {
			unregisterReceiver(networkConnectionMonitor);
			networkConnectionMonitor = null;
		}

		if (Build.VERSION.SDK_INT < 14 /** Build.VERSION_CODES.ICE_CREAM_SANDWICH **/
		) {
			if (backgroundDataPreferenceMonitor != null) {
				unregisterReceiver(backgroundDataPreferenceMonitor);
			}
		}
	}

	/*
	 * Called in response to a change in network connection - after losing a
	 * connection to the server, this allows us to wait until we have a usable
	 * data connection again
	 */
	private class NetworkConnectionIntentReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "Internal network status receive.");
			// we protect against the phone switching off
			// by requesting a wake lock - we request the minimum possible wake
			// lock - just enough to keep the CPU running until we've finished
			PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
			WakeLock wl = pm
					.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
			wl.acquire();
			Log.d(TAG, "Reconnect for Network recovery.");
			if (isOnline()) {
				Log.d(TAG, "Online,reconnect.");
				// we have an internet connection - have another try at
				// connecting
				reconnect();
				Log.e(TAG, "Online,reconnect done.");
			} else {
				disconnect();
				cancelReconnect();
				notifyClientsOffline();
				mClient = null;
			}

			wl.release();
		}
	}

	/**
	 * Detect changes of the Allow Background Data setting - only used below
	 * ICE_CREAM_SANDWICH
	 */
	private class BackgroundDataPreferenceReceiver extends BroadcastReceiver {

		@SuppressWarnings("deprecation")
		@Override
		public void onReceive(Context context, Intent intent) {
			ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
			Log.d(TAG, "Reconnect since BroadcastReceiver.");
			if (cm.getBackgroundDataSetting()) {
				if (!backgroundDataEnabled) {
					backgroundDataEnabled = true;
					// we have the Internet connection - have another try at
					// connecting
					reconnect();
				}
			} else {
				backgroundDataEnabled = false;
				notifyClientsOffline();
			}
		}
	}

}