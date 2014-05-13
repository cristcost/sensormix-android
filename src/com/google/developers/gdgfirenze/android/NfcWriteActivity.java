package com.google.developers.gdgfirenze.android;

import java.nio.charset.Charset;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class NfcWriteActivity extends Activity {

	private PendingIntent pendingIntent;
	private IntentFilter[] intentFiltersArrays;
	private String[][] techListsArrays;
	private NfcAdapter mAdapter;
	
    private static final String TAG = NfcWriteActivity.class.getSimpleName();


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nfc_write);

		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndef.addDataType("*/*"); /*
									 * Handles all MIME based dispatches. You
									 * should specify only the ones that you
									 * need.
									 */
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
		}
		intentFiltersArrays = new IntentFilter[] { ndef };
		techListsArrays = new String[][] { new String[] { NfcF.class.getName() } };

		mAdapter = NfcAdapter.getDefaultAdapter(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mAdapter != null) {
			mAdapter.disableForegroundDispatch(this);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mAdapter != null) {
			mAdapter.enableForegroundDispatch(this, pendingIntent,
					intentFiltersArrays, techListsArrays);
		}
	}

	@Override
	public void onNewIntent(Intent intent) {
		try {
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			// do something with tagFromIntent
			Ndef ndefTag = Ndef.get(tag);
			if(ndefTag.isWritable()) {
				String mimeType = Intent.normalizeMimeType("application/com.google.developers.gdgfirenze.android");
				NdefRecord mimeRecord = NdefRecord.createMime(mimeType,
					    "Beam me up, Android".getBytes(Charset.forName("US-ASCII")));
				NdefMessage msg = new NdefMessage(mimeRecord);
				ndefTag.connect();
				ndefTag.writeNdefMessage(msg);
				ndefTag.close();
				Toast toast = Toast
						.makeText(this, "Tags properly prepared", Toast.LENGTH_SHORT);
				toast.show();
				finish();
			}
		} catch (Exception e) {
			Toast toast = Toast
					.makeText(this, "Error on tag preparation", Toast.LENGTH_SHORT);
			toast.show();
            Log.e(TAG, "Error Writing tag...", e);
		} 


	}

}
