/**
 * 
 */
package org.commcare.android.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.commcare.android.application.CommCareApplication;
import org.javarosa.core.model.utils.DateUtils;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.telephony.TelephonyManager;

/**
 * @author ctsims
 *
 */
public class DeviceReport {
	
	private static final String XMLNS = "http://code.javarosa.org/devicereport";
	
	Document document;
	Element reportNode;
	
	Context mContext;
	
	public DeviceReport(CommCareApplication application) {
		this.mContext = application;

        String did = application.getPhoneId();
		
		document = new Document();
		reportNode = document.createElement(XMLNS, "device_report");
		document.addChild(Element.ELEMENT, reportNode);
		
		Element deviceId = reportNode.createElement(XMLNS,"device_id");
		deviceId.addChild(Element.TEXT, did);
		reportNode.addChild(Element.ELEMENT,deviceId);
		
		Element reportDate = reportNode.createElement(XMLNS,"report_date");
		reportDate.addChild(Element.TEXT, DateUtils.formatDate(new Date(), DateUtils.FORMAT_ISO8601));
		reportNode.addChild(Element.ELEMENT,reportDate);
		
		Element appVersion = reportNode.createElement(XMLNS,"app_version");
		appVersion.addChild(Element.TEXT, application.getCurrentVersionString());
		reportNode.addChild(Element.ELEMENT,appVersion);
	}
	
	public void addLogMessage(String type, String message, Date dateTime) {
		Element lroot = logroot();
		Element entry = lroot.createElement(XMLNS,"log");
		entry.setAttribute(null, "date", DateUtils.formatDateTime(dateTime, DateUtils.FORMAT_ISO8601));
		
		Element nType = entry.createElement(XMLNS,"type");
		nType.addChild(Element.TEXT, type);
		entry.addChild(Element.ELEMENT,nType);
		
		Element nMessage = entry.createElement(XMLNS,"msg");
		nMessage.addChild(Element.TEXT, message);
		entry.addChild(Element.ELEMENT,nMessage);
		
		lroot.addChild(Element.ELEMENT,entry);
	}
	
	private Element log;
	
	private Element logroot() {
		if(log ==null ) {
			log = reportNode.createElement(XMLNS,"log_subreport");
			reportNode.addChild(Element.ELEMENT,log);
		}
		return log;
	}
	
	//TODO: These should use the file system, not memory, but for now we're only using it
	//for minor stuff like exception log sending
	
	public byte[] serializeReport() throws IOException{
		XmlSerializer ser = new KXmlSerializer();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		 
		ser.setOutput(bos, null);
		document.write(ser);
		
		return bos.toByteArray();
	}
	
	public InputStream getSerializedReportStream() throws IOException{
		XmlSerializer ser = new KXmlSerializer();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		 
		ser.setOutput(bos, null);
		document.write(ser);
		
		//Note: If this gets too big, we can just write a wrapper to stream bytes one at a time
		//to the array. It'll probably be the XML DOM itself which blows up the memory, though...
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		return bis;
	}
}
