package org.commcare.pn.workermanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.gson.Gson
import org.commcare.connect.ConnectConstants.CCC_DEST_DELIVERY_PROGRESS
import org.commcare.connect.ConnectConstants.CCC_DEST_LEARN_PROGRESS
import org.commcare.connect.ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE
import org.commcare.connect.ConnectConstants.CCC_DEST_PAYMENTS
import org.commcare.connect.ConnectConstants.CCC_MESSAGE
import org.commcare.connect.ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.pn.workers.PNApiSyncWorker
import org.commcare.pn.workers.PNApiSyncWorker.Companion.ACTION
import org.commcare.pn.workers.PNApiSyncWorker.Companion.PN_DATA
import java.util.concurrent.TimeUnit

class PNApiSyncWorkerManager(val context: Context) {


    companion object{
        val PN_SYNC_BACKOFF_DELAY_IN_MILLIS: Long = 3 * 60 * 1000L  // min 3 minutes
    }

    lateinit var pns : ArrayList<Map<String,String>>
    val requiredWorkerThread = HashMap<String, WorkRequest>()

    constructor(context: Context, pns : ArrayList<Map<String,String>>):this(context){
        this.pns = pns
        startPNApiSync()
    }

//    constructor(context: Context,fcmMessageData: List<FCMMessageData>):this(context){
//
//    }


    fun startPNApiSync(){

        for(pn in pns){

            when(pn.get("action")){


                null -> {
                    continue
                }


                CCC_MESSAGE ->{
                    if(!requiredWorkerThread.containsKey(CCC_MESSAGE)){
                        requiredWorkerThread.put(CCC_MESSAGE,getWorkRequest(pn,
                            PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_PERSONALID_MESSAGING
                        ))
                    }
                }

                CCC_DEST_PAYMENTS->{
                    createOpportunitiesSyncWorkRequest(pn)
                    if(pn.containsKey(OPPORTUNITY_ID)){
                        val opportunityId = pn.get(OPPORTUNITY_ID)
                        if(!requiredWorkerThread.containsKey(PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_DELIVERY_PROGRESS.toString() +"-${opportunityId}")){
                            requiredWorkerThread.put(PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_DELIVERY_PROGRESS.toString()+"-${opportunityId}",getWorkRequest(pn,
                                PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_DELIVERY_PROGRESS))
                        }
                    }
                }

                CCC_PAYMENT_INFO_CONFIRMATION->{
                    createOpportunitiesSyncWorkRequest(pn)
                }


                CCC_DEST_OPPORTUNITY_SUMMARY_PAGE -> {
                    createOpportunitiesSyncWorkRequest(pn)
                }


                CCC_DEST_LEARN_PROGRESS -> {
                    createOpportunitiesSyncWorkRequest(pn)
                    if(pn.containsKey(OPPORTUNITY_ID)){
                        val opportunityId = pn.get(OPPORTUNITY_ID)
                        if(!requiredWorkerThread.containsKey(PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_LEARNING_PROGRESS.toString() +"-${opportunityId}")){
                            requiredWorkerThread.put(PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_LEARNING_PROGRESS.toString()+"-${opportunityId}",getWorkRequest(pn,
                                PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_LEARNING_PROGRESS))
                        }
                    }
                }

                CCC_DEST_DELIVERY_PROGRESS -> {
                    createOpportunitiesSyncWorkRequest(pn)
                    if(pn.containsKey(OPPORTUNITY_ID)){
                        val opportunityId = pn.get(OPPORTUNITY_ID)
                        if(!requiredWorkerThread.containsKey(PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_DELIVERY_PROGRESS.toString() +"-${opportunityId}")){
                            requiredWorkerThread.put(PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_DELIVERY_PROGRESS.toString()+"-${opportunityId}",getWorkRequest(pn,
                                PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_DELIVERY_PROGRESS))
                        }
                    }
                }

            }
        }

        if(requiredWorkerThread.size>0){
            for(workRequest in requiredWorkerThread){
                WorkManager.getInstance(context).enqueue(workRequest.value)
            }
        }

    }


    private fun createOpportunitiesSyncWorkRequest(pn:Map<String,String>){
        if(!requiredWorkerThread.containsKey(PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_OPPORTUNITY.toString() )){
            requiredWorkerThread.put(PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_OPPORTUNITY.toString() ,getWorkRequest(pn,
                PNApiSyncWorker.Companion.SYNC_ACTION.SYNC_OPPORTUNITY))
        }
    }




    fun getWorkRequest(pn: Map<String,String>,action: PNApiSyncWorker.Companion.SYNC_ACTION): WorkRequest {

        val pnJsonString = Gson().toJson(pn)
        val syncActionString = Gson().toJson(action)
        val inputData = Data.Builder()
            .putString(PN_DATA, pnJsonString)
            .putString(ACTION,syncActionString)
            .build()

         return OneTimeWorkRequestBuilder<PNApiSyncWorker>()
             .setInputData(inputData)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                PN_SYNC_BACKOFF_DELAY_IN_MILLIS,
                TimeUnit.MILLISECONDS
            ).build()

    }


}