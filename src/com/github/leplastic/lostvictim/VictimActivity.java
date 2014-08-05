package com.github.leplastic.lostvictim;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class VictimActivity extends Activity {
	
	// controls
	private ImageView imgStatus;
	private TextView txtStatusDesc;
	private EditText editMessage;
	private Button btnSendMessage;
	private ListView lstMessagesSent;
	
	// service
	//private BroadcastReceiver receiver;
	private Intent svcIntent;
	
	// others
	private static ArrayList<TextMessageListItem> data;
	private ArrayAdapter<TextMessageListItem> adapter;
	private String state;
	private Dictionary<String, String> dictStates;
	private VictimActivity instance;
	
	// haptic feedback
	private Vibrator vibrator;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); 
		setContentView(R.layout.victim_layout);
		
		instance = this;
		
		// create controls
		imgStatus = (ImageView) findViewById(R.id.imgStatus);
		txtStatusDesc = (TextView) findViewById(R.id.txtStatusDesc);
		editMessage = (EditText) findViewById(R.id.editMessage);
		btnSendMessage = (Button) findViewById(R.id.btnSendMessage);
		lstMessagesSent = (ListView) findViewById(R.id.lstMessagesSent);
		
		// register friendly state names
		dictStates = new Hashtable<String, String>();
		dictStates.put("Scanning", "Looking for other nearby nodes...");
		dictStates.put("Station", "Connected, sending messages");
		dictStates.put("Beaconing", "Accepting connections...");
		dictStates.put("Providing", "Clients connected");
		
		// listener for state updates
		ContentResolver cr = getContentResolver();
		String URL = "content://net.diogomarques.wifioppish.MessagesProvider/status";
		Uri uri = Uri.parse(URL);
		cr.registerContentObserver(uri, true, new ContentObserver(null) {
			
			@Override
			public void onChange(boolean selfChange) {
				this.onChange(selfChange, null);
			}		

			@Override
			public void onChange(boolean selfChange, Uri uri) {
				checkLostStatus(uri);
			}		
		});
		
		// listener for message sent updates
		Uri sentUri = Uri.parse("content://net.diogomarques.wifioppish.MessagesProvider/sent");
		cr.registerContentObserver(sentUri, true, new ContentObserver(null) {
			
			@Override
			public void onChange(boolean selfChange) {
				this.onChange(selfChange, null);
			}		

			@Override
			public void onChange(boolean selfChange, Uri uri) {
				uri = (uri == null) ? 
						Uri.parse("content://net.diogomarques.wifioppish.MessagesProvider/sent") :
						uri;
				Cursor c = getContentResolver().query(uri, null, "", null, "");
				
				int vibrateCount = 0;
				
				if(c.moveToFirst()) {
					boolean dataChanged = false;
					do {
						String message =  c.getString(c.getColumnIndex("message"));
						if( ! message.equals("") && 
								c.getString(c.getColumnIndex("status")).equals("sentNet") ) {
							
							for(int i = 0; i < data.size(); i++) {
								TextMessageListItem tmli = data.get(i);
								if(tmli.getMessage().equals(message) && !tmli.sentNetwork) {
									tmli.setSentNetwork(true);
									dataChanged = true;
									vibrateCount++;
								}
							}
						}
					} while(c.moveToNext());
					
					if(dataChanged) {
						runOnUiThread(new Runnable() {
							public void run() {
								adapter.notifyDataSetChanged();
								
								// provide feedback
								if(vibrator == null)
									vibrator = (Vibrator) instance.getSystemService(Context.VIBRATOR_SERVICE);
								
								vibrator.vibrate(200);
							}
						});
					}
				}
			}	
			
		});
		
		// start the service, if not instanciated already
		svcIntent = new Intent("net.diogomarques.wifioppish.service.LOSTService.START_SERVICE");  
		startService(svcIntent);
		
		// control actions - btnSendMessage
		btnSendMessage.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// pass message to service
				ContentValues cv = new ContentValues();
				String msg = editMessage.getText().toString();
				cv.put("customMessage", msg);
				Uri dest = Uri.parse("content://net.diogomarques.wifioppish.MessagesProvider/customsend");
				Uri uriRec = getContentResolver().insert(dest, cv);
				
				// add to list
				data.add(data.size(), new TextMessageListItem(msg, System.currentTimeMillis()));
				adapter.notifyDataSetChanged();
				editMessage.setText("");
				
				// hide keyboard
				InputMethodManager inputManager = (InputMethodManager)
						getSystemService(Context.INPUT_METHOD_SERVICE); 
				inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
						InputMethodManager.HIDE_NOT_ALWAYS);
			}
		});
		btnSendMessage.setEnabled(false);
		
		// control action - editMessage
		editMessage.addTextChangedListener(new TextWatcher(){
	        public void afterTextChanged(Editable s) {
	            btnSendMessage.setEnabled( (s.length() >= 3) );
	        }
	        public void beforeTextChanged(CharSequence s, int start, int count, int after){}
	        public void onTextChanged(CharSequence s, int start, int before, int count){}
	    });
		
		// control lstMessagesSent
		if(data == null)
			data = new ArrayList<VictimActivity.TextMessageListItem>();
		
		adapter = new TextMessageArrayAdapter(getApplicationContext(),
		        android.R.layout.simple_list_item_1, data);
		lstMessagesSent.setAdapter(adapter);
		
		// update current status
		checkLostStatus(Uri.parse("content://net.diogomarques.wifioppish.MessagesProvider/status"));
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	/**
	 * Provides visual feedback of Environment changes by changing the 
	 * descriptive text and the top image of the activity.
	 * @param newState New state from the most recent transition
	 */
	private void changeStatus(String newState) {
		state = newState;
		
		runOnUiThread(new Runnable() {
		     @Override
		     public void run() {
		    	 String niceDesc = dictStates.get(state);
		    	 txtStatusDesc.setText(niceDesc == null ? state : niceDesc);

		    	 if(state.equals("Beaconing")) {
		    		 imgStatus.setImageResource(R.drawable.beaconing);
		    	 } else if(state.equals("Providing")) {
		    		 imgStatus.setImageResource(R.drawable.providing);
		    	 } else if(state.equals("Scanning")) {
		    		 imgStatus.setImageResource(R.drawable.scanning);
		    	 } else if(state.equals("Station")) {
		    		 imgStatus.setImageResource(R.drawable.station);
		    	 } else if(state.equals("InternetCheck")) {
		    		 imgStatus.setImageResource(R.drawable.check_internet);
		    	 } else if(state.equals("InternetConn")) {
		    		 imgStatus.setImageResource(R.drawable.internet_available);
		    	 } else {
		    		 imgStatus.setImageResource(R.drawable.no_connection);
		    	 }
		    }
		});
	}
	
	/**
	 * Checks for changes on LOST status and runs actions accordingly
	 * @param uri The exact update URI of LOST Status or null if unknown
	 */
	private void checkLostStatus(Uri uri) {
		Cursor c = getContentResolver().query(
				uri == null ? Uri.parse("content://net.diogomarques.wifioppish.MessagesProvider/status") : uri, null, "", null, "");
		
		if(c.moveToFirst()) {
			do {
				if(c.getString(c.getColumnIndex("statuskey")).equals("state")) {
					changeStatus(c.getString(c.getColumnIndex("statusvalue")));
				}
			} while(c.moveToNext());
		}
	}
	
	/**
	 * Adapter to show data on message list
	 * @author André
	 *
	 */
	private class TextMessageArrayAdapter extends ArrayAdapter<TextMessageListItem> {
		final private ArrayList<TextMessageListItem> data;
		private Context context;
		
	    public TextMessageArrayAdapter(Context context, int resource,
				List<TextMessageListItem> objects) {
			super(context, resource, objects);
			this.context = context;
			this.data = (ArrayList<VictimActivity.TextMessageListItem>) objects;
		}
	    
	    @Override
	    public View getView(int position, View convertView, ViewGroup parent) {
	      LayoutInflater inflater = (LayoutInflater) context
	          .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	      View rowView = inflater.inflate(R.layout.listview_victim_messages, parent, false);
	      TextView textView = (TextView) rowView.findViewById(R.id.lblMessageContents);
	      TextMessageListItem curItem = data.get(position);
	      textView.setText(curItem.getMessage());
	      
	      if(curItem.isSentWebservice())
	    	  textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.sent_cloud, 0, 0, 0);
	      else if(curItem.isSentNetwork())
	    	  textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.green_check, 0, 0, 0);
	      else
	    	  textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.waiting, 0, 0, 0);
	    	  
	      return rowView;
	    }
	}
	
	/**
	 * List item container for a text message with sending status and the 
	 * message envelope
	 * @author André Silva <asilva@lasige.di.fc.ul.pt>
	 */
	private class TextMessageListItem implements Comparable<TextMessageListItem> {
		private String message;
		private long timestamp;
		private boolean sentNetwork;
		private boolean sentWebservice;
		
		/**
		 * Creates a new instance
		 * @param envelope Message envelope to be sent over network
		 */
		public TextMessageListItem(String msg, long time) {
			super();
			this.message = msg;
			this.timestamp = time;
			this.sentNetwork = false;
		}
		
		/**
		 * Gets the text message contained in the envelope
		 * @return Text message
		 */
		public String getMessage() {
			return message;
		}
		
		/**
		 * Gets the timestamp of the envelope
		 * @return timestamp of message creation
		 */
		public long getTimestamp() {
			return timestamp;
		}

		/**
		 * Return whenever the message is sent or not
		 * @return True if already sent; False otherwise
		 */
		public boolean isSentNetwork() {
			return sentNetwork;
		}

		/**
		 * Sets the sending status of message
		 * @param sent True if already sent; False otherwise
		 */
		public void setSentNetwork(boolean sent) {
			this.sentNetwork = sent;
		}
		
		/**
		 * Checks if the message on the envelope is equal to the message to compare
		 * @param otherMsg Text message to be compared
		 * @param otherTime Timestamp to be compared
		 * @return True if the message is equal (same attributes); false otherwise
		 */
		public boolean messageEquals(String otherMsg, long otherTime) {
			return message.equals(otherMsg) && otherTime == timestamp;
		}

		@Override
		public String toString() {
			return getMessage();
		}

		@Override
		public int compareTo(TextMessageListItem another) {
			// descending order
			long result = another.getTimestamp() - this.getTimestamp();
			
			if(result == 0)
				return 0;
			
			return result < 0 ? -1 : 1;
		}

		public boolean isSentWebservice() {
			return sentWebservice;
		}

		public void setSentWebservice(boolean sentWebservice) {
			this.sentWebservice = sentWebservice;
		}
	}
}
