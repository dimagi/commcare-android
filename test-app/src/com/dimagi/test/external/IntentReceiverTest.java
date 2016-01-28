package com.dimagi.test.external;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class IntentReceiverTest extends Activity {

    static final Boolean[] listening = new Boolean[]{Boolean.FALSE};

    static ArrayList<String> broadcasts;

    static BroadcastReceiver receiver;

    private Button b;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.intent_receiver);
        b = (Button)this.findViewById(R.id.btn_intent_listen);
        b.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                synchronized (listening) {
                    if (listening[0]) {
                        stopListening();
                    } else {
                        startListening();
                    }
                }
            }
        });
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        updateState();
    }

    private void updateState() {
        TextView tv = (TextView)this.findViewById(R.id.txt_receiver_updates);

        String joined = "";

        synchronized (listening) {

            if (broadcasts != null) {
                for (String s : broadcasts) {
                    joined += s + "\n";
                }
            }
        }

        tv.setText(joined);
        if (listening[0]) {
            b.setText("Stop Listening for Broadcasts");
        } else {
            b.setText("Start Listening for Broadcast Intents");
        }
    }

    private void startListening() {
        synchronized (listening) {
            stopListening();
            this.listening[0] = true;
            broadcasts = new ArrayList<String>();

            receiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    synchronized (listening) {
                        String broadcast = "From CommCare (" + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "): " + intent.getAction();
                        broadcasts.add(broadcast);
                    }
                    IntentReceiverTest.this.updateState();
                }

            };

            broadcasts = new ArrayList<String>();
            IntentFilter filter = new IntentFilter();
            filter.addAction("org.commcare.dalvik.api.action.data.update");
            filter.addAction("org.commcare.dalvik.api.action.session.login");
            filter.addAction("org.commcare.dalvik.api.action.session.logout");
            this.registerReceiver(receiver, filter);
            IntentReceiverTest.this.updateState();

        }

    }

    private void stopListening() {
        synchronized (listening) {
            if (this.listening[0]) {
                this.listening[0] = false;
                this.unregisterReceiver(receiver);
                broadcasts.clear();
            }
        }

    }

    /* (non-Javadoc)
     * @see android.app.Activity#onRetainNonConfigurationInstance()
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        return this;
    }
}
