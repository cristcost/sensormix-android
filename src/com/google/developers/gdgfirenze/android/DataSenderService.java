package com.google.developers.gdgfirenze.android;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class DataSenderService extends IntentService {

	public static final String INTENT_ACTION = "com.google.developers.gdgfirenze.android.intent.SEND_DATA";
	public static final String INTENT_EXTRA = "DataToSend";
	
	private static Logger logger = Logger.getLogger(TrackingService.class
			.getName());

	private final BlockingQueue<String> queue = new LinkedBlockingQueue<String>(
			64);

	public DataSenderService() {
		super("Data Senser Service");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent.hasExtra(INTENT_EXTRA)) {

			SharedPreferences p = PreferenceManager
					.getDefaultSharedPreferences(getApplicationContext());

			String serverUrl = p.getString("server_url", "");
			String data = intent.getStringExtra(INTENT_EXTRA);
			postData(serverUrl, data, getApplicationContext());
		}
	}

	private void postData(String url, String data, Context context) {

		HttpParams myParams = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(myParams, 10000);
		HttpConnectionParams.setSoTimeout(myParams, 10000);
		HttpClient httpclient = new DefaultHttpClient(myParams);

		try {
			queue.offer(data, 10, TimeUnit.SECONDS);
		} catch (InterruptedException e1) {
			Toast toast = Toast.makeText(context,
					"Unable to queue sample in sensormix! Sample is lost...",
					Toast.LENGTH_SHORT);
			toast.show();
		}

		try {
			String bodyForHttpPostRequest;
			while ((bodyForHttpPostRequest = queue.peek()) != null) {

				HttpPost httppost = new HttpPost(url.toString());
				httppost.setHeader("Content-type", "application/json");

				StringEntity se = new StringEntity(bodyForHttpPostRequest);
				se.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE,
						"application/json"));
				httppost.setEntity(se);

				HttpResponse response = httpclient.execute(httppost);
				String temp = EntityUtils.toString(response.getEntity());
				logger.info("JSON post response: " + temp);

				// sample sent, let's remove from the queue
				queue.poll();
			}

		} catch (ClientProtocolException e) {
			logger.info("Service ClientProtocolException! " + e.getMessage());
		} catch (IOException e) {
			logger.info("Service IOException! " + e.getMessage());
		}
	}

}
