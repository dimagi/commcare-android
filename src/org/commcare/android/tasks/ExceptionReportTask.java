/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.util.DeviceReport;
import org.odk.collect.android.logic.GlobalConstants;

import android.os.AsyncTask;


/**
 * Background task for uploading completed forms.
 * 
 * @author csims@dimagi.com
 * 
 **/

public class ExceptionReportTask extends AsyncTask<Throwable, String, String>  
{
	String URI = "https://pact.dimagi.com/receiver/submit/pact";

    @Override
    protected String doInBackground(Throwable... values) {		
		DeviceReport report = new DeviceReport(CommCareApplication._());
		

		String fallbacktext = null;
		for(Throwable ex : values) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ex.printStackTrace(new PrintStream(bos));
			String exceptionText = new String(bos.toByteArray());
			if(fallbacktext == null) {
				fallbacktext = exceptionText;
			}

			report.addLogMessage("forceclose", exceptionText, new Date());
		}
		
		byte[] data;
		try {
			data = report.serializeReport();
		} catch (IOException e) {
			//_weak_
			e.printStackTrace();
			String fsDate = new Date().toString();
			data = ("<?xml version='1.0' ?><n0:device_report xmlns:n0=\"http://code.javarosa.org/devicereport\"><device_id>FAILSAFE</device_id><report_date>" + fsDate +"</report_date><log_subreport><log_entry date=\"" + fsDate + "\"><entry_type>forceclose</entry_type><entry_message>" + fallbacktext + "</entry_message></log_entry></log_subreport></device_report>").getBytes();
		}
		
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, GlobalConstants.CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, GlobalConstants.CONNECTION_TIMEOUT);
        HttpClientParams.setRedirecting(params, false);

        // setup client
        DefaultHttpClient httpclient = new DefaultHttpClient(params);
        HttpPost httppost = new HttpPost(URI);
        
        httppost.setEntity(new ByteArrayEntity(data));
        
        try {
			httpclient.execute(httppost);
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//We seem to have to return something...
		return null;
    }
}
