package com.google.developers.gdgfirenze.android;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONObject;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

public class MainActivity extends Activity implements
		ActionBar.OnNavigationListener {

	private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";
	private static final DateFormat dateFormat = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss.SSS");
	private static final String TAG = MainActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		getFragmentManager().beginTransaction()
				.replace(R.id.container, new ServiceControlSectionFragment())
				.commit();

		// Set up the action bar to show a dropdown list.
		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		// Set up the dropdown list navigation in the action bar.
		actionBar.setListNavigationCallbacks(
				// Specify a SpinnerAdapter to populate the dropdown list.
				new ArrayAdapter<String>(actionBar.getThemedContext(),
						android.R.layout.simple_list_item_1,
						android.R.id.text1, new String[] {
								getString(R.string.title_home),
								getString(R.string.title_service_control) }),
				this);
	}

	public void onResume() {
		super.onResume();
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
			handleNfcTagDiscovert();
		}
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		// Restore the previously serialized current dropdown position.
		if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
			getActionBar().setSelectedNavigationItem(
					savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.action_settings:
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		// Serialize the current dropdown position.
		outState.putInt(STATE_SELECTED_NAVIGATION_ITEM, getActionBar()
				.getSelectedNavigationIndex());
	}

	@Override
	public boolean onNavigationItemSelected(int position, long id) {
		// When the given dropdown item is selected, show its contents in the
		// container view.

		switch (position) {
		case 0:
			getFragmentManager().beginTransaction()
					.replace(R.id.container, new HomeSectionFragment())
					.commit();
			break;
		case 1:
			getFragmentManager()
					.beginTransaction()
					.replace(R.id.container,
							new ServiceControlSectionFragment()).commit();
			break;
		}

		return true;
	}

	public void startServiceButtonClick(View v) {
		getApplicationContext().startService(
				new Intent(getApplicationContext(), TrackingService.class));
	}

	public void stopServiceButtonClick(View v) {
		getApplicationContext().stopService(
				new Intent(getApplicationContext(), TrackingService.class));
	}

	public static class HomeSectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		public HomeSectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {

			View rootView = inflater.inflate(R.layout.fragment_home, container,
					false);

			return rootView;
		}
	}

	public static class ServiceControlSectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		public ServiceControlSectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {

			View rootView = inflater.inflate(R.layout.fragment_service_control,
					container, false);

			return rootView;
		}
	}
	
	private void handleNfcTagDiscovert() {
		try {
			Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
			SharedPreferences p = PreferenceManager
					.getDefaultSharedPreferences(this);
			StringBuilder id = new StringBuilder();
			byte[] data = tag.getId();
			for (int i = 0; i < data.length; i++) {
				id.append(String.format("%02x", data[i]));
				if (i < data.length - 1) {
					id.append(":");
				}
			}
			JSONObject jsonSamplePacket = new JSONObject();
			JSONObject obj = new JSONObject();
			jsonSamplePacket.put("sample", obj);
			obj.put("device_id", p.getString("device_id",
					Secure.getString(getContentResolver(), Secure.ANDROID_ID)));
			obj.put("time", dateFormat.format(new Date()));
			obj.put("nfc", id);
			Intent intent = new Intent(this, DataSenderService.class);
			intent.putExtra(DataSenderService.INTENT_EXTRA,
					jsonSamplePacket.toString());
			startService(intent);
			Toast toast = Toast.makeText(this, "NFC Tag " + id,
					Toast.LENGTH_SHORT);
			toast.show();
		} catch (Exception e) {
			Log.e(TAG, "Error Writing tag...", e);
		}
	}

}
