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
import java.io.UnsupportedEncodingException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.commcare.android.database.user.models.User;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.AndroidLogSerializer;
import org.commcare.android.javarosa.DeviceReportWriter;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

import android.content.SharedPreferences;
import android.os.AsyncTask;


/**
 * Background task for uploading completed forms.
 * 
 * @author csims@dimagi.com
 * 
 **/

public class ExceptionReportTask extends AsyncTask<Throwable, String, String>  
{
    @Override
    protected String doInBackground(Throwable... values) {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	
    	//TODO: This is ridiculous. Just do the normal log submission process
    	DeviceReportWriter report;
    	try{
    		report = new DeviceReportWriter(baos);
    	} catch(IOException e){
    		report = null;
    	}
		

		String fallbacktext = null;
		for(Throwable ex : values) {
			String exceptionText = getStackTrace(ex);
			if(fallbacktext == null) {
				fallbacktext = exceptionText;
			}
			if(report != null) {
				report.addReportElement(new AndroidLogSerializer(new AndroidLogEntry("forceclose", exceptionText, new Date())));
			}
		}
		
		byte[] data;
		try {
			if(report == null) { throw new IOException();}
			report.write();
			data = baos.toByteArray();
		} catch (IOException e) {
			//_weak_
			e.printStackTrace();
			String fsDate = new Date().toString();
			data = ("<?xml version='1.0' ?><n0:device_report xmlns:n0=\"http://code.javarosa.org/devicereport\"><device_id>FAILSAFE</device_id><report_date>" + fsDate +"</report_date><log_subreport><log_entry date=\"" + fsDate + "\"><entry_type>forceclose</entry_type><entry_message>" + fallbacktext + "</entry_message></log_entry></log_subreport></device_report>").getBytes();
		}
		
        String URI = CommCareApplication._().getString(R.string.PostURL);
        try {
        	SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        	URI = settings.getString("PostURL", CommCareApplication._().getString(R.string.PostURL));
        } catch(Exception e) {
        	//D-oh. Really?
        }
        
        
        //TODO: Send this with the standard logging subsystem
        String payload = new String(data);
        System.out.println("Outgoing payload: " + payload);
        
        MultipartEntity entity = new MultipartEntity();
        try {
        	//Apparently if you don't have a filename in the multipart wrapper, some receivers
        	//don't properly receive this post.
        	StringBody body = new StringBody(payload, "text/xml", MIME.DEFAULT_CHARSET) {
				@Override
				public String getFilename() {
					return "exceptionreport.xml";
				}
        	};
			entity.addPart("xml_submission_file", body);
		} catch (IllegalCharsetNameException e1) {
			e1.printStackTrace();
		} catch (UnsupportedCharsetException e1) {
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
        
        HttpRequestGenerator generator;
        try {
        	User user = CommCareApplication._().getSession().getLoggedInUser();
            if(user.getUserType().equals(User.TYPE_DEMO)) {
            	generator = new HttpRequestGenerator();
            } else {
            	generator = new HttpRequestGenerator(user);
            }
        } catch(Exception e){
        	generator = new HttpRequestGenerator();
        }
        
        
        try {
			HttpResponse response = generator.postData(URI, entity);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			response.getEntity().writeTo(bos);
			System.out.println("Response: " + new String(bos.toByteArray()));
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
    
    public static String getStackTrace(Throwable e) {
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
		e.printStackTrace(new PrintStream(bos));
		return new String(bos.toByteArray());
    }
    
}
