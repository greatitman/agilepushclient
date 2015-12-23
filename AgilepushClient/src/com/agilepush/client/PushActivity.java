package com.agilepush.client;

import com.skypush.demo.R;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PushActivity extends Activity {
	private String mDeviceID;
	private String mServerIP;
	private String mServerPort;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mDeviceID = Secure.getString(this.getContentResolver(),
				Secure.ANDROID_ID);
		((TextView) findViewById(R.id.target_text)).setText(mDeviceID);

		final Button startButton = ((Button) findViewById(R.id.start_button));
		final Button stopButton = ((Button) findViewById(R.id.stop_button));
		startButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Editor editor = getSharedPreferences(SkyMqttService.TAG,
						MODE_PRIVATE).edit();

				int keepalive;
				int timeout;
				String server = ((AutoCompleteTextView) findViewById(R.id.serverURI))
						.getText().toString();
				String port = ((EditText) findViewById(R.id.port)).getText()
						.toString();

				if (server.equals(SkyMqttConstants.empty)
						|| port.equals(SkyMqttConstants.empty)) {
					String notificationText = getString(R.string.missingOptions);

					Toast toast = Toast.makeText(getApplicationContext(),
							notificationText, Toast.LENGTH_LONG);
					toast.show();
					Log.e("lxs", "ip or port is empty");
					return;
				}

				String username = ((EditText) findViewById(R.id.uname))
						.getText().toString();
				String password = ((EditText) findViewById(R.id.password))
						.getText().toString();
				String sslkey = null;
				boolean ssl = ((CheckBox) findViewById(R.id.sslCheckBox))
						.isChecked();
				if (ssl) {
					sslkey = ((EditText) findViewById(R.id.sslKeyLocaltion))
							.getText().toString();
				}
				try {
					timeout = Integer
							.parseInt(((EditText) findViewById(R.id.timeout))
									.getText().toString());
				} catch (NumberFormatException nfe) {
					timeout = 60;
				}
				try {
					keepalive = Integer
							.parseInt(((EditText) findViewById(R.id.keepalive))
									.getText().toString());
				} catch (NumberFormatException nfe) {
					keepalive = 200;
				}

				boolean cleanSession = ((CheckBox) findViewById(R.id.cleanSessionCheckBox))
						.isChecked();

				mServerIP = server;
				mServerPort = port;
				Log.e("lxs", "server info " + mServerIP + ":" + mServerPort);
				editor.putString(SkyMqttService.PREF_DEVICE_ID, mDeviceID);
				Log.e("lxs", "device id " + mDeviceID);
				editor.putString(SkyMqttService.PREF_SERVER_IP, mServerIP);
				editor.putString(SkyMqttService.PREF_SERVER_PORT, mServerPort);
				editor.putString(SkyMqttService.PREF_USER_NAME, username);
				editor.putString(SkyMqttService.PREF_PASSWD, password);

				editor.putInt(SkyMqttService.PREF_TIMEOUT, timeout);
				editor.putInt(SkyMqttService.PREF_KEEPALIVE, timeout);
				editor.putBoolean(SkyMqttService.PREF_CLEAN_SESSION, cleanSession);
				

				editor.commit();
				SkyMqttService.actionStart(getApplicationContext());
				startButton.setEnabled(false);
				stopButton.setEnabled(true);
			}
		});
		stopButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				SkyMqttService.actionStop(getApplicationContext());
				startButton.setEnabled(true);
				stopButton.setEnabled(false);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		SharedPreferences p = getSharedPreferences(SkyMqttService.TAG,
				MODE_PRIVATE);
		boolean started = p.getBoolean(SkyMqttService.PREF_STARTED, false);

		((Button) findViewById(R.id.start_button)).setEnabled(!started);
		((Button) findViewById(R.id.stop_button)).setEnabled(started);
	}

}