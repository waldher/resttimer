/*
 * This file is part of LGBR's Rest Timer.
 * 
 * LGBR's Rest Timer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * LGBR's Rest Timer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with LGBR's Rest Timer.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.laserbunny.resttimer;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class RestTimer extends Activity {
    SharedPreferences settings;
	
	private static final String SECONDS_PREFERENCE = "restSeconds";
	private static final int DEFAULT_SECONDS = 30;
	
	private TextView restLengthSecondsEditText;
    private TextView tapToRestTextView;
    private TextView countdownTextView;
    private ViewGroup countdownLayout;

	private RestCountdown restCountdown;
	
	private PowerManager.WakeLock wakeLock;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        settings = PreferenceManager.getDefaultSharedPreferences(this);

        {
	        int seconds = settings.getInt(SECONDS_PREFERENCE, DEFAULT_SECONDS);
	        restLengthSecondsEditText = ((TextView)findViewById(R.id.restLengthSecondsEditText));
	        restLengthSecondsEditText.setText(String.valueOf(seconds));
		}		
        
        restCountdown = new RestCountdown();
        
        countdownLayout = (ViewGroup)findViewById(R.id.countdownLayout);
        countdownTextView = (TextView)findViewById(R.id.countdownTextView);
        tapToRestTextView = (TextView)findViewById(R.id.tapToRestTextView);
        
		updateTapToRestText();
        
        countdownLayout.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View v){
        		String startTimerControl = settings.getString("startTimerControl", "both");
        		if(startTimerControl.equals("both") || startTimerControl.equals("tap")){
	        		if(restCountdown.secondsLeft <= 0)
	        			restCountdown.startRest();
	        		else
	        			restCountdown.stopRest();
        		}
        	}
        });
        
        restLengthSecondsEditText.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				try {
					int newSeconds = Integer.parseInt(restLengthSecondsEditText.getText().toString());
					SharedPreferences.Editor editor = settings.edit();
					editor.putInt(SECONDS_PREFERENCE, newSeconds);
					editor.commit();
				} catch(NumberFormatException e){
				}
				return false;
			}
		});
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	if(settings.getBoolean("preventSleep", true)){
    		preventSleep();
    	} else {
    		if(wakeLock != null && wakeLock.isHeld()){
    			wakeLock.release();
    		}
    	}
    	
    	updateTapToRestText();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();

		if(wakeLock != null && wakeLock.isHeld()){
			wakeLock.release();
		}
    }
    
    private void preventSleep(){
		if(wakeLock == null){
        	PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
			wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "RestTimer");
		}
		
		if(!wakeLock.isHeld()){
			wakeLock.acquire();
		}
    }
    
    public void updateTapToRestText(){
    	String startTimerControl = settings.getString("startTimerControl", "both");
        
        if(startTimerControl.equals("both")){
        	tapToRestTextView.setText(getString(R.string.bothStartRest));
        } else if(startTimerControl.equals("tap")){
        	tapToRestTextView.setText(getString(R.string.tapStartRest));
        } else if(startTimerControl.equals("camera")){
        	tapToRestTextView.setText(getString(R.string.cameraStartRest));
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.options_menu, menu);
    	
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()){
    	case R.id.settings:
    		Intent intent = new Intent(this, RestTimerSettings.class);
    		startActivity(intent);
    		break;
    	}
    	
    	return false;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	if(keyCode == KeyEvent.KEYCODE_CAMERA){
    		String startTimerControl = settings.getString("startTimerControl", "both");
    		if(startTimerControl.equals("both") || startTimerControl.equals("camera")){
	    		if(restCountdown.secondsLeft <= 0){
	    			restCountdown.startRest();
	    		}
    		}
    		return true;
    	}
    	return false;
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
    }
    
    private class RestCountdown implements Handler.Callback {
    	private int secondsLeft;
    	private boolean vibrate = false;
        private Handler countdownHandler;
        private Vibrator vibrator;
        
        private Uri alertUri;
        
        private int iteration = 0; // Used to make sure we don't listen to old messages
    	
    	private RestCountdown(){
    		secondsLeft = 0;
    		countdownHandler = new Handler(this);
    		vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);

    		alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    	}
    	
		private void updateText() {
			if(secondsLeft > 0){
				vibrate = true;
	    		countdownLayout.setBackgroundColor(Color.RED);
				tapToRestTextView.setText(getString(R.string.restLeft));
				countdownTextView.setText(String.valueOf(secondsLeft));
				secondsLeft--;
				
				Message message = new Message();
				message.arg1 = iteration;
				countdownHandler.sendMessageDelayed(message, 1000);
			} else {
				if(vibrate) {
					
					if(settings.getBoolean("vibrate", true)){
						long[] pattern = {0,800,400,800};
						vibrator.vibrate(pattern,-1);
					}
					
					if(settings.getBoolean("playSound", true)){
						try {
							final AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
							if (audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) != 0) {
					    		MediaPlayer mediaPlayer = new MediaPlayer();
			    				mediaPlayer.setDataSource(getApplicationContext(), alertUri);
								mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
								mediaPlayer.setLooping(false);
								mediaPlayer.prepare();
								mediaPlayer.start();
							}
						} catch(IOException e){
						} catch(IllegalStateException e){
						}
					}

				}
				countdownTextView.setText("");
				updateTapToRestText();
				countdownLayout.setBackgroundColor(Color.GREEN);
			}
		}
		
		private void startRest(){
			iteration++;
			secondsLeft = Integer.parseInt(restLengthSecondsEditText.getText().toString());
			
			if(settings.getBoolean("vibrate", true)){
				vibrator.vibrate(200);
			}
			
			updateText();
		}
		
		private void stopRest(){
			iteration++;
			secondsLeft = 0;
			vibrate = false;
			updateText();
		}
		
		@Override
		public boolean handleMessage(Message msg) {
			if(msg.arg1 == iteration) {
				updateText();
			}
			return true;
		}
    }
}