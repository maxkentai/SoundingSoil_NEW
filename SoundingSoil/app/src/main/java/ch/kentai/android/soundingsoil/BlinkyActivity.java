/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package ch.kentai.android.soundingsoil;

import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.nfc.FormatException;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
//import android.support.v4.content.ContextCompat;


import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.graphics.Color;

import com.newventuresoftware.waveform.WaveformView;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import ch.kentai.android.soundingsoil.adapter.DiscoveredBluetoothDevice;
import ch.kentai.android.soundingsoil.databinding.ActivityBlinkyBinding;
import ch.kentai.android.soundingsoil.scanner.SimpleBluetoothDevice;
import ch.kentai.android.soundingsoil.viewmodels.BlinkyViewModel;
import ch.kentai.android.soundingsoil.scanner.ScannerFragment;
import ch.kentai.android.soundingsoil.utils.RepeatListener;

import static ch.kentai.android.soundingsoil.viewmodels.BlinkyViewModel.getCurrentTimezoneOffset;

@SuppressWarnings("ConstantConditions")
public class BlinkyActivity extends AppCompatActivity implements ScannerFragment.OnDeviceSelectedListener {

	private WaveformView mRealtimeWaveformView;
	private RecordingThread mRecordingThread;


	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";

	private BlinkyViewModel mViewModel;


	@BindView(R.id.data_sent_received) TextView mDataReceived;

	@BindView(R.id.duration_text) EditText mDurationField;
	@BindView(R.id.period_text) EditText mPeriodField;
	@BindView(R.id.occurence_text) EditText mOccurenceField;

	@BindView(R.id.mon_button) ImageButton mMonButton;
	@BindView(R.id.mon_state) TextView mMonState;

	@BindView(R.id.connex_state) TextView mConnexState;

	@BindView(R.id.rec_button) ImageButton mRecButton;
	@BindView(R.id.rec_state) TextView mRecStateView;

	@BindView(R.id.vol_up_button) ImageButton mVolUpButton;
	@BindView(R.id.vol_down_button) ImageButton mVolDownButton;

	@BindView(R.id.status_req_button) Button mStatusReqButton;
	@BindView(R.id.clear_mon_button) Button mClearButton;

	@BindView(R.id.conn_button) Button mConnButton;

    @BindView(R.id.connectedIV) ImageView mConnectedView;

    @BindView(R.id.mon_button_part) LinearLayout mon_part;
    @BindView(R.id.vol_control_part) LinearLayout vol_part;

	@BindView(R.id.rec_time) TextView mRecTimeView;
	@BindView(R.id.rec_number) TextView mRecNumberView;

    private ObjectAnimator anim;
	private static final String TAG = "BlinkyActivity";
	//private boolean mRecordingSamplerReady = false;

	private ProgressBar pg;

	private static final int REQUEST_CODE = 0;
	static final String[] PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO};
	private  int recTime = 0;
	private int recState = 0;

	//private Runnable recTimer;


	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//setContentView(R.layout.activity_blinky);

		final Intent intent = getIntent();
		final DiscoveredBluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
		final String deviceName = device.getName();
		final String deviceAddress = device.getAddress();

		// Configure the view model
		mViewModel = ViewModelProviders.of(this).get(BlinkyViewModel.class);
		mViewModel.connect(device);

		ActivityBlinkyBinding binding =
				DataBindingUtil.setContentView(this, R.layout.activity_blinky);
		binding.setLifecycleOwner(this);
		binding.setViewmodel(mViewModel);

		ButterKnife.bind(this);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setTitle(deviceName);
		getSupportActionBar().setSubtitle(deviceAddress);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		// Set up views
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final TextView connectionState = findViewById(R.id.connection_state);
		final View content = findViewById(R.id.device_container);
		final View notSupported = findViewById(R.id.not_supported);


		final Animation animation = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
		animation.setDuration(500); // duration - half a second
		animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
		animation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
		animation.setRepeatMode(Animation.REVERSE); // Reverse animation at the end so the button will fade back in

		pg = findViewById(R.id.volumeBar);


		LinearLayout commMonitor = findViewById(R.id.comm_log);
		commMonitor.setVisibility(View.GONE);
		LinearLayout recSettings = findViewById(R.id.rec_settings);
		recSettings.setVisibility(View.GONE);



		mRealtimeWaveformView = (WaveformView) findViewById(R.id.waveformView);
		mRecordingThread = new RecordingThread(new AudioDataReceivedListener() {
			@Override
			public void onAudioDataReceived(short[] data) {
				mRealtimeWaveformView.setSamples(data);
			}
		});


		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				//mRecordingSamplerReady = true;
			}
		}, 1000);


		Thread recTimer = new Thread() {
			@Override
			public void run() {

				while(!isInterrupted()) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mRecTimeView.setText(" " + Integer.toString(recTime) + "s"); //this is the textview
					}
				});

				if (recState == 2 || recState == 1) {
					// state recording or waiting
					if (recTime > 0) {
						recTime -= 1;
					}
				} else {
					// state stopped or preparing
					recTime = 0;
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
			}
			}
		};
		recTimer.start();


		try {
			SQLiteDatabase mDataBase = this.openOrCreateDatabase("Presets", MODE_PRIVATE, null);
			mDataBase.execSQL("CREATE TABLE IF NOT EXISTS presets (duration VARCHAR, period VARCHAR, occurences VARCHAR)");
			mDataBase.execSQL("INSERT INTO presets (duration, period, ocurrences) VALUES ('10', '20', '3')");
		} catch (Exception e) {
			e.printStackTrace();
		}




		mDurationField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				try {
					mViewModel.setDuration(s.toString());
				} catch (NumberFormatException nfe) {
					Log.d(TAG, "period error");
				}
			}
		});

		mPeriodField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				try {
					mViewModel.setPeriod(s.toString());
				} catch (NumberFormatException nfe) {
					Log.d(TAG, "period error");
				}
			}
		});

		mOccurenceField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				try {
					mViewModel.setOccurence(s.toString());
				} catch (NumberFormatException nfe) {
					Log.d(TAG, "period error");
				}
			}
		});

		mConnectedView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(commMonitor.getVisibility() == View.GONE) {
					commMonitor.setVisibility(View.VISIBLE);
					recSettings.setVisibility(View.VISIBLE);
				} else {
					commMonitor.setVisibility(View.GONE);
					recSettings.setVisibility(View.GONE);
				}
			}
		});

		mMonButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mViewModel.toggleMon();
			}
		});


		mRecButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

			    // check permission
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                    Location location = null;

                    if (isNetworkEnabled) {
                        location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    } else if (isGPSEnabled) {
                        location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }

                    if (location != null) {
                        double longitude = location.getLongitude();
                        double latitude = location.getLatitude();

                        mViewModel.setLatitude(String.format("%.4f", latitude));
                        mViewModel.setLongitude(String.format("%.4f", longitude));
                    }
                }
				mViewModel.toggleRec();
			}
		});

		mStatusReqButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mViewModel.requestDeviceStatus();
			}
		});



		mClearButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mViewModel.clearDataSentReceived();
			}
		});


		mVolUpButton.setOnTouchListener(new RepeatListener(400, 100, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mViewModel.sendStringToBlinkyManager("vol +");
			}
		}
		));


		mVolDownButton.setOnTouchListener(new RepeatListener(400, 100, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mViewModel.sendStringToBlinkyManager("vol -");
			}
		}
		));


		mConnButton.setOnClickListener((v -> {
			// check BT status. if connected -> disconnect
			String btState = mViewModel.getBTStateChanged().getValue();
			if(btState != null) {
				if(btState.equalsIgnoreCase("disconnected")) {
					mViewModel.sendStringToBlinkyManager("inq");
					showDeviceScanningDialog();
				} else {
					mViewModel.sendStringToBlinkyManager("disc");
				}
			}
		}));



		// observe -----------------------
		mViewModel.getBTStateChanged().observe(this, btState -> {
			Log.d(TAG, "Audio Monitor state: " + mViewModel.getMonState().getValue());

			if(btState.equalsIgnoreCase("disconnected")) {
				// turn off monitor if on
				if (mViewModel.getMonState().getValue()) {
					mViewModel.toggleMon();
				}


				final Handler handler = new Handler();
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						vol_part.setAlpha(.5f);
						mVolDownButton.setEnabled(false);
						mVolUpButton.setEnabled(false);
						mConnButton.setText("CONNECT");
					}
				}, 500);

			} else { // connected
                mConnButton.setText("DISCONNECT");
				if (mViewModel.getMonState().getValue()) {	// monitor on
					vol_part.setAlpha(1.0f);
					mVolDownButton.setEnabled(true);
					mVolUpButton.setEnabled(true);
				}
            }
			Log.d(TAG, "Audio BT state: " + btState);
		});

		mViewModel.isDeviceReady().observe(this, deviceReady -> {
			progressContainer.setVisibility(View.GONE);
			content.setVisibility(View.VISIBLE);
			mViewModel.requestDeviceStatus();
			Log.d(TAG, "device ready: " + deviceReady);
		});

		mViewModel.getConnectionState().observe(this, text -> {
			if (text != null) {
				progressContainer.setVisibility(View.VISIBLE);
				notSupported.setVisibility(View.GONE);
				connectionState.setText(text);
				Log.d(TAG, "connection state: " + text);
			}
		});

		mViewModel.isConnected().observe(this, this::onConnectionStateChanged);

		mViewModel.isSupported().observe(this, supported -> {
			if (!supported) {
				progressContainer.setVisibility(View.GONE);
				notSupported.setVisibility(View.VISIBLE);
			}
		});


		mViewModel.getMonState().observe(this, isOn -> {
			mMonState.setText(isOn ? R.string.mon_state_on : R.string.mon_state_off);
			//if (mRecordingSamplerReady) {
				if (isOn) 	{
					mMonButton.setColorFilter(Color.GREEN);
					//mRecordingThread.startRecording();
					if (mViewModel.getBTStateChanged().getValue().equalsIgnoreCase("disconnected")) {
					} else {
						mRecordingThread.startRecording();
						vol_part.setAlpha(1.0f);
						mVolDownButton.setEnabled(true);
						mVolUpButton.setEnabled(true);

					}
				}
				else {
					mMonButton.setColorFilter(Color.argb(255, 10, 180, 10));
					mRecordingThread.stopRecording();
					vol_part.setAlpha(.5f);
					mVolDownButton.setEnabled(false);
					mVolUpButton.setEnabled(false);

				}
				//Log.i("recorder", "ready");
			//}
		});


		mViewModel.getRecState().observe(this, state -> {
			recState = state;
			if (state == 0) {
				mRecStateView.setText(R.string.rec_state_off);
				this.manageBlinkEffect(false);
				mRecButton.setColorFilter(Color.argb(255, 166, 51, 51));
				//mRecStateView.setBackgroundColor(Color.WHITE);
			}
			else if (state == 1) {
				mRecStateView.setText(R.string.rec_state_wait);
				this.manageBlinkEffect(true);
				// request time of next record to set wait countdown timer


			}
			else if(state == 2) {
				mRecStateView.setText(R.string.rec_state_on);
				this.manageBlinkEffect(false);
				//mRecStateView.setBackgroundColor(Color.RED);
				mRecButton.setColorFilter(Color.argb(255, 250, 69, 32));
			}
			else if(state == 3) {
				mRecStateView.setText(R.string.rec_state_preparing);
				this.manageBlinkEffect(true);
				//mRecStateView.setBackgroundColor(Color.RED);
			}
		});


		mViewModel.getRecNumber().observe(this, recNumber -> {
			Log.d(TAG, "Rec Number: " + recNumber);

			mRecNumberView.setText(recNumber + " of " + mViewModel.getOccurence().getValue());
		});


		mViewModel.getNextRecTime().observe(this, nextRectime -> {
			Log.d(TAG, "Next Rec Time: " + nextRectime);
			int nRecTime = Integer.parseInt(nextRectime);
			long unixTime = System.currentTimeMillis() / 1000;
			unixTime += getCurrentTimezoneOffset();		// add time zone offset
			recTime = nRecTime - (int)unixTime;
			Log.d(TAG, "Next Rec in: " + recTime + "s");
		});


		mViewModel.getFilepath().observe(this, path -> {
			if (path.equalsIgnoreCase("--")) {

			} else {
				// compare timestamp e.g. /190507/175838.wav from filepath with current time and duration setting to set rec time countdown timer
				int start = path.indexOf("/") + 1;
				int end = path.indexOf(".wav");
				if (start  < path.length() && end < path.length()) {
					String inputTime = path.substring(start, end);
					Log.d(TAG, "Rec Start time: " + inputTime);

					SimpleDateFormat inputFormat = new SimpleDateFormat("yyMMdd/HHmmss");
					Date date = null;

					try {
						date = inputFormat.parse(inputTime);
					} catch (ParseException e) {
					}

					// get local time
					long unixTime = System.currentTimeMillis();
					long diff = unixTime - date.getTime();
					Log.d(TAG, "Rec time difference: " + unixTime + " " + date.getTime() + " "  + diff);

					int dur;
					try {
						dur = Integer.parseInt(mViewModel.getDuration().getValue());
					}
					catch (NumberFormatException e)
					{
						dur = 0;
					}

					if (dur != 0) {
						// not endless recording duration
						// reset the count down
						recTime = dur - (int)(diff / 1000);
						Log.d(TAG, "Rec time: " + recTime);
					} else {

					}
				}
			}
			Log.d(TAG, "Filepath: " + path);
		});


		mViewModel.getVolume().observe(this, string -> {
			try {
				int vol = (int)Float.parseFloat(string);
				pg.setProgress(vol);
				Log.d(TAG, "BT Vol: " + vol);
			} catch (NumberFormatException e) {

			}
		});


		mViewModel.getDataReceived().observe(this, string
				-> {
		});

		mViewModel.getDataSent().observe(this, string
				-> {
		});

		mViewModel.getDataSentReceived().observe(this, string
				-> {
			mDataReceived.setText("");
			for (String str : string) {
				mDataReceived.append(str + "\n");
			}
		});

		anim = ObjectAnimator.ofInt(mRecButton, "colorFilter", Color.argb(255, 166, 51, 51), Color.argb(255, 250, 69, 32));

//		anim = ObjectAnimator.ofInt(mRecStateView, "backgroundColor", Color.WHITE, Color.RED,
//				Color.WHITE);

	}


	@Override
	public void onDeviceSelected(final SimpleBluetoothDevice device) {
		mViewModel.sendStringToBlinkyManager("conn " + device.name);
	}

	@Override
	public void onDialogCanceled() {
		// do nothing
	}




	@OnClick(R.id.action_clear_cache)
	public void onTryAgainClicked() {
		mViewModel.reconnect();
	}

	private void onConnectionStateChanged(final boolean connected) {
		Log.d(TAG, "connex state changed: " + connected);
		if (connected) {
			mViewModel.requestDeviceStatus();

			mConnectedView.setImageResource(R.drawable.icons8_connected_48);
			mConnectedView.setColorFilter(Color.GREEN);
		}
		else {
			mConnectedView.setImageResource(R.drawable.icons8_disconnected_48);
			mConnectedView.setColorFilter(Color.RED);
		}
	}

	private void manageBlinkEffect(boolean isOn) {
		if (isOn) {
			anim.setDuration(1000);
			anim.setEvaluator(new ArgbEvaluator());
			anim.setRepeatMode(ValueAnimator.REVERSE);
			anim.setRepeatCount(ValueAnimator.INFINITE);
			anim.start();
		} else {
			anim.end();
		}
	}
	private void showDeviceScanningDialog() {
		final ScannerFragment dialog = ScannerFragment.getInstance(); // Device that is advertising directly does not have the GENERAL_DISCOVERABLE nor LIMITED_DISCOVERABLE flag set.
		dialog.show(getSupportFragmentManager(), "scan_fragment");
	}




//	private void startPermissionsActivity() {
//		PermissionsActivity.startActivityForResult(this, REQUEST_CODE, PERMISSIONS);
//	}
//
//	@Override
//	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//		super.onActivityResult(requestCode, resultCode, data);
//		if (requestCode == REQUEST_CODE && resultCode == PermissionsActivity.PERMISSIONS_DENIED) {
//			finish();
//		}
//	}


	@Override
	protected void onStop() {
		super.onStop();

		mRecordingThread.stopRecording();
	}


	@Override
	protected void onPause() {
		//mRecordingSampler.stopRecording();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		//mRecordingSampler.release();
		mViewModel.disconnect();
		super.onDestroy();
	}


}
