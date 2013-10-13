package com.google.developers.gdgfirenze.android;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.widget.Toast;

public class TrackingService extends Service implements Runnable {

	public class TrackingServiceBinder extends Binder {
		TrackingService getService() {
			return TrackingService.this;
		}
	}

	private static final DateFormat dateFormat = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss.SSS");

	private static Logger logger = Logger.getLogger(TrackingService.class
			.getName());

	private final IBinder binder = new TrackingServiceBinder();

	private Handler handler;

	private boolean isActive;

	private Thread t;

	private Object deviceId;

	private String serverUrl;

	private long syncFrequency;

	public TrackingService() {
		handler = new Handler();
	}

	@Override
	public IBinder onBind(Intent intent) {
		logger.info("Bound the service");
		return binder;
	}

	@Override
	public void onCreate() {
		logger.info("Created the service");

		startProcessing();
	}

	@Override
	public void onDestroy() {
		logger.info("Destroy the service");

		stopProcessing();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		logger.info("Started the service");

		return Service.START_STICKY;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		logger.info("Unbinding the service");

		return true;
	}

	@Override
	public void run() {
		try {
			while (isActive) {

				JSONObject obj = new JSONObject();

				obj.put("device_id", deviceId);
				obj.put("time", getCurrentTimeAsString());
				obj.put("battery_level", readBatteryLevel());

				Location location = getGpsPosition();
				JSONObject positionObj = new JSONObject();

				positionObj.put("lat", location.getLatitude());
				positionObj.put("lng", location.getLongitude());
				positionObj.put("alt", location.getAltitude());
				positionObj.put("time",
						getTimeAsString(new Date(location.getTime())));
				positionObj.put("accuracy", location.getAccuracy());
				positionObj.put("bearing", location.getBearing());
				positionObj.put("speed", location.getSpeed());

				obj.put("position", positionObj);

				List<ScanResult> scanResults = getWifiFootprint();
				JSONArray scanresultsObj = new JSONArray();
				for (ScanResult s : scanResults) {
					JSONObject scanObj = new JSONObject();

					scanObj.put("frequency", s.frequency);
					scanObj.put("level", s.level);
					scanObj.put("bssid", s.BSSID);
					scanObj.put("capabilities", s.capabilities);
					scanObj.put("ssid", s.SSID);

					scanresultsObj.put(scanObj);
				}
				obj.put("wifi_scans", scanresultsObj);
				postData(serverUrl, obj);
				Thread.sleep(syncFrequency * 1000);
			}
		} catch (InterruptedException e) {
			logger.info("Service cycle interrupted! " + e.getMessage());
		} catch (RuntimeException e) {
			logger.info("Service runtime exception! " + e.getMessage());
		} catch (JSONException e) {
			logger.info("Service JSONException exception! " + e.getMessage());
		}
	}

	public void startProcessing() {
		if (t == null) {
			isActive = true;
			SharedPreferences p = PreferenceManager
					.getDefaultSharedPreferences(this);

			serverUrl = p.getString("server_url", "");
			String syncFrequencyString = p.getString("sync_frequency", "5");
			try {
				syncFrequency = Long.parseLong(syncFrequencyString);
			} catch (NumberFormatException e) {
				syncFrequency = 5;
			}
			deviceId = p.getString("device_id",
					Secure.getString(getContentResolver(), Secure.ANDROID_ID));
			t = new Thread(this);
			t.start();
		}
	}

	public void stopProcessing() {
		isActive = false;

		try {
			t.join();
		} catch (InterruptedException e) {
			logger.info("Service cycle interrupted! " + e.getMessage());
		} catch (Exception e) {
			logger.info("Service runtime exception! " + e.getMessage());
		}
		t = null;
	}

	public void stopService() {
		stopProcessing();
		stopSelf();
	}

	private String getCurrentTimeAsString() {
		Date date = new Date();
		return getTimeAsString(date);
	}

	private Location getGpsPosition() {
		String serviceString = Context.LOCATION_SERVICE;
		LocationManager locationManager;
		locationManager = (LocationManager) getSystemService(serviceString);

		String provider = LocationManager.GPS_PROVIDER;
		Location location = locationManager.getLastKnownLocation(provider);

		return location;
	}

	private String getTimeAsString(Date date) {
		return dateFormat.format(date);
	}

	private List<ScanResult> getWifiFootprint() {
		String serviceString = Context.WIFI_SERVICE;
		WifiManager wifiManager;
		wifiManager = (WifiManager) getSystemService(serviceString);

		return wifiManager.getScanResults();
	}

	private void postData(String url, JSONObject obj) {

		HttpParams myParams = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(myParams, 10000);
		HttpConnectionParams.setSoTimeout(myParams, 10000);
		HttpClient httpclient = new DefaultHttpClient(myParams);
		String json = obj.toString();

		try {

			HttpPost httppost = new HttpPost(url.toString());
			httppost.setHeader("Content-type", "application/json");

			StringEntity se = new StringEntity(json);
			se.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE,
					"application/json"));
			httppost.setEntity(se);

			HttpResponse response = httpclient.execute(httppost);
			String temp = EntityUtils.toString(response.getEntity());
			logger.info("JSON post response: " + temp);

		} catch (ClientProtocolException e) {
			logger.info("Service ClientProtocolException! " + e.getMessage());
		} catch (IOException e) {
			logger.info("Service IOException! " + e.getMessage());
		}
	}

	private void postToastToGui(final String msg) {
		final Runnable tostOnGuiThread = new Runnable() {

			@Override
			public void run() {
				Toast toast = Toast.makeText(TrackingService.this, msg,
						Toast.LENGTH_SHORT);
				toast.show();
			}
		};

		handler.post(tostOnGuiThread);
	}

	private float readBatteryLevel() {
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = this.registerReceiver(null, ifilter);

		int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

		return level / (float) scale;
	}
}
