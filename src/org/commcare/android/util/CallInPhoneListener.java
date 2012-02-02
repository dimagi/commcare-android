/**
 * 
 */
package org.commcare.android.util;

import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

import org.commcare.android.R;
import org.commcare.android.models.User;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author ctsims
 *
 */
public class CallInPhoneListener extends PhoneStateListener {
	
	private Context context;
	private AndroidCommCarePlatform platform;
	
	private Hashtable<String, String[]> cachedNumbers;
	private User user;
	
	private Toast currentToast;
	
	private Timer toastTimer;
	
	private boolean running = false;
	
	public CallInPhoneListener(Context context, AndroidCommCarePlatform platform, User user) {
		this.context = context;
		this.platform = platform;
		cachedNumbers = new Hashtable<String, String[]>();
		this.user = user;
		toastTimer = new Timer("toastTimer");
	}

	
	@Override
	public void onCallStateChanged(int state, String incomingNumber) {
		super.onCallStateChanged(state, incomingNumber);
		if(state == TelephonyManager.CALL_STATE_RINGING) {
			String caller = getCaller(incomingNumber);
			if(caller != null) {
				LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				View layout = inflater.inflate(R.layout.call_toast, null);
				TextView textView = (TextView)layout.findViewById(R.id.incoming_call_text);
				textView.setText("Incoming Call From: " + caller);
				
				currentToast = new Toast(context);
				currentToast.setDuration(Toast.LENGTH_LONG);
				
				currentToast.setView(layout);
				currentToast.setGravity(Gravity.BOTTOM, 0,0);
				running = true;
				startToastLoop();
			}
		} else {
			running = false;
		}
	}
	
	public void startToastLoop() {
		
		synchronized(toastTimer) {
			toastTimer.schedule(
					new TimerTask() {
						int runtimes = 0;
						public void run() {
							if(runtimes > 100 || running == false) {
								this.cancel();
							} else {
								runtimes++;
								currentToast.show();
							}
						}
					}, 0, 200);
		}
	}


	public void startCache() {
		
		AsyncTask<Void, Void, Void> loader = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				try {
//					synchronized(cachedNumbers) {
//						Set<String> usedDetails = new HashSet<String>();
//						
//						for(Suite s : platform.getInstalledSuites() ){
//							for(Entry e : s.getEntries().values()) {
//								ArrayList<Detail> details = new ArrayList<Detail>();
//								if(e.getShortDetailId() != null && !e.getShortDetailId().equals("") && !usedDetails.contains(e.getShortDetailId())) {
//									details.add(s.getDetail(e.getShortDetailId()));
//									usedDetails.add(e.getShortDetailId());
//								}
//								if(e.getLongDetailId() != null && !e.getLongDetailId().equals("") && !usedDetails.contains(e.getLongDetailId())) {
//									details.add(s.getDetail(e.getLongDetailId()));
//									usedDetails.add(e.getLongDetailId());
//								}
//								
//								for(Detail d : details) {
//									Set<Integer> phoneIds = new HashSet<Integer>();
//									String[] forms = d.getTemplateForms();
//									for(int i = 0 ; i < forms.length ; ++i) {
//										if("phone".equals(forms[i])) {
//											phoneIds.add(i);
//										}
//									}
//									if(phoneIds.size() == 0 ) { continue;}
//									//We have a winner!
//									
//									if(e.getReferences().containsKey("referral")) {
//										
//									} else if(e.getReferences().containsKey("case")) {
//										SqlIndexedStorageUtility<ACase> storage = CommCareApplication._().getStorage(ACase.STORAGE_KEY, ACase.class);
//										EntityFactory factory = new EntityFactory(d, user);
//										for(SqlStorageIterator<ACase> i = storage.iterate() ; i.hasMore() ;){
//											ACase c = i.nextRecord();
//											Entity<ACase> entity = factory.getEntity(c);
//											if(entity != null) {
//												for(Integer id : phoneIds) {
//													String number = entity.getFields()[id];
//													if(number != null && !number.equals("")) {
//														cachedNumbers.put(number, new String[] {c.getName(), c.getCaseId(), e.getCommandId()});
//													}
//												}
//											}
//										}
//									}
//								}
//							}
//						}
//						System.out.println("Caching Complete");
						return null;
//					}
				} catch(SessionUnavailableException sue) {
					//We got logged out in the middle of 
					return null;
				}
			}
		};
		loader.execute();
	}
	
	public String getCaller(String incomingNumber) {
		synchronized(cachedNumbers) {
			for(String number : cachedNumbers.keySet()) {
				if(PhoneNumberUtils.compare(context, number, incomingNumber)) {
					return cachedNumbers.get(number)[0];
				}
			}
		}
		return null;
	}
	
	public Intent getDetailIntent(Context context, String incomingNumber) {
//		synchronized(cachedNumbers) {
//			for(String number : cachedNumbers.keySet()) {
//				if(PhoneNumberUtils.compare(context, number, incomingNumber)) {
//					String[] details = cachedNumbers.get(number);
//					
//					Intent i = new Intent(context, ReferenceDetailActivity.class);
//					i.putExtra(CommCareSession.STATE_COMMAND_ID, details[2]);
//					i.putExtra(CommCareSession.STATE_CASE_ID, details[1]);
//					i.putExtra(ReferenceDetailActivity.IS_DEAD_END, true);
//					return i;
//				}
//			}
//		}
		return null;
	}
}
