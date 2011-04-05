/**
 * 
 */
package org.commcare.android.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.models.Case;
import org.commcare.android.models.Entity;
import org.commcare.android.models.EntityFactory;
import org.commcare.android.models.User;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Suite;

import android.content.Context;
import android.os.AsyncTask;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

/**
 * @author ctsims
 *
 */
public class CallInPhoneListener extends PhoneStateListener {
	
	private Context context;
	private AndroidCommCarePlatform platform;
	
	private Hashtable<String, String> cachedNumbers;
	private User user;
	
	public CallInPhoneListener(Context context, AndroidCommCarePlatform platform, User user) {
		this.context = context;
		this.platform = platform;
		cachedNumbers = new Hashtable<String, String>();
		this.user = user;
	}

	
	@Override
	public void onCallStateChanged(int state, String incomingNumber) {
		super.onCallStateChanged(state, incomingNumber);
		if(state == TelephonyManager.CALL_STATE_RINGING) {
			synchronized(cachedNumbers) {
				for(String number : cachedNumbers.keySet()) {
					if(PhoneNumberUtils.compare(context, number, incomingNumber)) {
						Toast t = Toast.makeText(context, "Incoming Call From: " + cachedNumbers.get(number), Toast.LENGTH_LONG);
						t.show();
						break;
					}
				}
			}
		}
	}


	public void startCache() {
		
		AsyncTask<Void, Void, Void> loader = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				synchronized(cachedNumbers) {
					Set<String> usedDetails = new HashSet<String>();
					
					for(Suite s : platform.getInstalledSuites() ){
						for(Entry e : s.getEntries().values()) {
							ArrayList<Detail> details = new ArrayList<Detail>();
							if(e.getShortDetailId() != null && !e.getShortDetailId().equals("") && !usedDetails.contains(e.getShortDetailId())) {
								details.add(s.getDetail(e.getShortDetailId()));
								usedDetails.add(e.getShortDetailId());
							}
							if(e.getLongDetailId() != null && !e.getLongDetailId().equals("") && !usedDetails.contains(e.getLongDetailId())) {
								details.add(s.getDetail(e.getLongDetailId()));
								usedDetails.add(e.getLongDetailId());
							}
							
							for(Detail d : details) {
								Set<Integer> phoneIds = new HashSet<Integer>();
								String[] forms = d.getTemplateForms();
								for(int i = 0 ; i < forms.length ; ++i) {
									if("phone".equals(forms[i])) {
										phoneIds.add(i);
									}
								}
								if(phoneIds.size() == 0 ) { continue;}
								//We have a winner!
								
								if(e.getReferences().containsKey("referral")) {
									
								} else if(e.getReferences().containsKey("case")) {
									SqlIndexedStorageUtility<Case> storage = CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class);
									EntityFactory factory = new EntityFactory(d, user);
									for(SqlStorageIterator<Case> i = storage.iterate() ; i.hasMore() ;){
										Case c = i.nextRecord();
										Entity<Case> entity = factory.getEntity(c);
										if(entity != null) {
											for(Integer id : phoneIds) {
												String number = entity.getFields()[id];
												if(number != null && !number.equals("")) {
													cachedNumbers.put(number, c.getName());
												}
											}
										}
									}
								}
							}
						}
					}
					System.out.println("Caching Complete");
					return null;
				}
			}
		};
		loader.execute();
	}
}
