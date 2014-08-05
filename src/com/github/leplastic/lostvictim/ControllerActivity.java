package com.github.leplastic.lostvictim;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class ControllerActivity extends Activity {
	
	private ImageView imgLostStatus;
	private TextView txtLostStatus;
	private TextView txtEnvStatus;
	private Button btnStartStop;
	private Intent svcIntent;
	private String envState;
	private String lostStatus;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); 
		setContentView(R.layout.controller_layout);
		
		svcIntent = new Intent("net.diogomarques.wifioppish.service.LOSTService.START_SERVICE");  
		
		imgLostStatus = (ImageView) findViewById(R.id.imgLostStatus);
		txtLostStatus = (TextView) findViewById(R.id.txtLostStatus);
		txtEnvStatus = (TextView) findViewById(R.id.txtEnvStatus);
		btnStartStop = (Button) findViewById(R.id.btnStartStop);
		
		// listener for state and service updates
		String URL = "content://net.diogomarques.wifioppish.MessagesProvider/status";
		Uri uri = Uri.parse(URL);
		Uri uriRead = Uri.parse(URL);
		ContentResolver cr = getContentResolver();
		cr.registerContentObserver(uri, true, new ContentObserver(null) {
			
			@Override
			public void onChange(boolean selfChange) {
				this.onChange(selfChange, null);
			}	

			@Override
			public void onChange(boolean selfChange, Uri uri) {
				updateServiceStats(uri);
			}		
		});
		
		// update current status
		updateServiceStats(null);
		
		// button service on/off
		btnStartStop.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				boolean disabled = lostStatus == null || lostStatus.equals("Disabled");
				if(disabled) {
					startService(svcIntent);
				} else {
					stopService(svcIntent);
				}
			}
			
		});
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		//unregisterReceiver(receiver);
		envState = txtEnvStatus.getText().toString();
		//stopService(svcIntent);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if(envState != null)
			txtEnvStatus.setText(envState);
	}
	
	/**
	 * Updates interface to the current LOST service status
	 * @param uri Update uri to update a specific field or null to get all values
	 */
	private void updateServiceStats(Uri uri) {
		Cursor c = getContentResolver().query(
				uri == null ? Uri.parse("content://net.diogomarques.wifioppish.MessagesProvider/status") : uri, null, "", null, "");
		
		if(c.moveToFirst()) {
			do {
				if(c.getString(c.getColumnIndex("statuskey")).equals("state")) {
					envState = c.getString(c.getColumnIndex("statusvalue"));
					
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							txtEnvStatus.setText(envState);
						}
					});
				} else if(c.getString(c.getColumnIndex("statuskey")).equals("service")) {
					lostStatus = c.getString(c.getColumnIndex("statusvalue"));
					
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							txtLostStatus.setText(lostStatus);
							boolean isDisabled = lostStatus.equals("Disabled");
							Drawable icon = getResources().
									getDrawable(isDisabled ? R.drawable.lightoff : R.drawable.lighton);
							imgLostStatus.setImageDrawable(icon);
							String text = getResources().
									getString(isDisabled ? R.string.btnStartStop_do_enable : R.string.btnStartStop_do_disable);
							btnStartStop.setText(text);
							
							// update possibly bugged environment state
							txtEnvStatus.setText(R.string.env_status_default);
						}
					});
				}
			} while(c.moveToNext());
		}
		
		if(lostStatus == null || lostStatus.equals("Disabled")) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// update possibly bugged environment state
					txtEnvStatus.setText(R.string.env_status_default);
				}
			});
		}
	}
	
	/**
	 * Checks if a service is running
	 * @param serviceClass Service name
	 * @return True if the service is running; false otherwise
	 */
	private boolean isServiceRunning(String serviceClass) {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
}
