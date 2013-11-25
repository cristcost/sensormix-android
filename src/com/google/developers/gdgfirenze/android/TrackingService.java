package com.google.developers.gdgfirenze.android;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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

	private static final String DEVICE_TYPE = "urn:rixf:net.sensormix/device_types/android";

	public class TrackingServiceBinder extends Binder {
		TrackingService getService() {
			return TrackingService.this;
		}
	}

	private final ScheduledExecutorService executorService;

	private static final DateFormat dateFormat = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss.SSS");

	private static Logger logger = Logger.getLogger(TrackingService.class
			.getName());

	private final IBinder binder = new TrackingServiceBinder();

	private Handler handler;

	private String deviceId;

	private long syncFrequency;

	public TrackingService() {
		handler = new Handler();
		executorService = Executors.newSingleThreadScheduledExecutor();
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
			JSONObject obj = createJsonUpdatePacket();
			postData(obj);
		} catch (RuntimeException e) {
			logger.info("Service runtime exception! " + e.getMessage());
		} catch (JSONException e) {
			logger.info("Service JSONException exception! " + e.getMessage());
		}
	}

	private JSONObject createJsonUpdatePacket() throws JSONException {
		
		JSONObject root = new JSONObject();
		
		JSONObject obj = new JSONObject();
		root.put("sample", obj);
		
		obj.put("device_id", deviceId);
		obj.put("time", getCurrentTimeAsString());
		obj.put("battery_level", readBatteryLevel());

		Location location = getGpsPosition();
		if (location != null) {
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
		}

		List<ScanResult> scanResults = getWifiFootprint();
		if (scanResults != null) {
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
		}
		return root;
	}
	
	private JSONObject createJsonDefinitionPacket() throws JSONException {
		JSONObject root = new JSONObject();	
		JSONObject obj = new JSONObject();	
		root.put("sensor", obj);
		String name = android.os.Build.MODEL + " " + android.os.Build.ID;
		String description = "Android: " + android.os.Build.VERSION.RELEASE;
		description += " - Model: " + android.os.Build.MODEL;
		obj.put("id", deviceId);
		obj.put("type", DEVICE_TYPE);
		obj.put("name", name);
		obj.put("description", description);
		return root;
	}
	

	public void startProcessing() {
		setupServiceParametersFromPreferences();
		sendDefinition();
		executorService.scheduleAtFixedRate(this, 0, syncFrequency,
				TimeUnit.SECONDS);
	}

	private void setupServiceParametersFromPreferences() {
		SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(this);

		String syncFrequencyString = p.getString("sync_frequency", "5");
		try {
			syncFrequency = Long.parseLong(syncFrequencyString);
		} catch (NumberFormatException e) {
			syncFrequency = 5;
		}
		deviceId = p.getString("device_id",
				Secure.getString(getContentResolver(), Secure.ANDROID_ID));
	}
	
	private void sendDefinition() {
		try {
			JSONObject obj = createJsonDefinitionPacket();
			postData(obj);
		} catch (RuntimeException e) {
			logger.info("Service runtime exception! " + e.getMessage());
		} catch (JSONException e) {
			logger.info("Service JSONException exception! " + e.getMessage());
		}		
	}

	public void stopProcessing() {
		executorService.shutdown();
		try {
			executorService.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			postToastToGui("Service terminated after 30 seconds!");
		}
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

	private void postData(JSONObject jsonSamplePacket) {
		Intent intent = new Intent(this, DataSenderService.class);
		intent.putExtra(DataSenderService.INTENT_EXTRA, jsonSamplePacket.toString());
		startService(intent);
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
