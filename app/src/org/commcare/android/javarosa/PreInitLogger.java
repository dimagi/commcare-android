/**
 * 
 */
package org.commcare.android.javarosa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import org.javarosa.core.api.ILogger;
import org.javarosa.core.log.IFullLogSerializer;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.services.Logger;

/**
 * This class keeps track of logs before the app has fully initialized its storage engine
 * 
 * @author ctsims
 *
 */
public class PreInitLogger implements ILogger {
	private ArrayList<AndroidLogEntry> logs = new ArrayList<AndroidLogEntry>();
	
	public PreInitLogger() {
		
	}
	/* (non-Javadoc)
	 * @see org.javarosa.core.api.ILogger#log(java.lang.String, java.lang.String, java.util.Date)
	 */
	@Override
	public void log(String type, String message, Date logDate) {
		logs.add(new AndroidLogEntry(type, message, logDate));

	}
	
	public void dumpToNewLogger() {
		for(AndroidLogEntry log : logs) {
			if(Logger._() != null) {
				Logger._().log(log.getType(), log.getMessage(), log.getTime());
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.api.ILogger#clearLogs()
	 */
	@Override
	public void clearLogs() {

	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.api.ILogger#serializeLogs(org.javarosa.core.log.IFullLogSerializer)
	 */
	@Override
	public <T> T serializeLogs(IFullLogSerializer<T> serializer) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.api.ILogger#serializeLogs(org.javarosa.core.log.StreamLogSerializer)
	 */
	@Override
	public void serializeLogs(StreamLogSerializer serializer) throws IOException {

	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.api.ILogger#serializeLogs(org.javarosa.core.log.StreamLogSerializer, int)
	 */
	@Override
	public void serializeLogs(StreamLogSerializer serializer, int limit) throws IOException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.api.ILogger#panic()
	 */
	@Override
	public void panic() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.api.ILogger#logSize()
	 */
	@Override
	public int logSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.api.ILogger#halt()
	 */
	@Override
	public void halt() {
		// TODO Auto-generated method stub

	}

}
