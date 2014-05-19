/**
 * 
 */
package org.commcare.dalvik.provider;

import java.util.Hashtable;
import java.util.NoSuchElementException;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.application.CommCareApplication;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * The case data content provider defines the interface for external applications
 * to gain read only access to the current user's sandbox. External applications require
 * explicit permissions to access the content provider, and only data in the currently
 * logged in user's sandbox is provided.
 * 
 * The CaseDataAPI class is responsible for defining the data models and structures that
 * can be queried with the content provider.
 * 
 * Note that this content provider requires a permission to be granted for read access.
 * 
 * No write access is currently supported.
 * 
 * @author ctsims
 *
 */
public class CaseDataContentProvider extends ContentProvider {
	
	//TODO: Caching - Use a cache table here or use an LRU or other system provided cache?
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#getType(android.net.Uri)
	 */
	@Override
	public String getType(Uri uri) {
        int match = CaseDataAPI.UriMatch(uri);
        
        switch(match) {
        case CaseDataAPI.MetadataColumns.MATCH_CASES:
			return CaseDataAPI.MetadataColumns.CONTENT_TYPE;
        case CaseDataAPI.MetadataColumns.MATCH_CASE:
			return CaseDataAPI.MetadataColumns.CONTENT_TYPE_ITEM;
        case CaseDataAPI.DataColumns.MATCH_DATA:
			return CaseDataAPI.DataColumns.CONTENT_TYPE;
        case CaseDataAPI.IndexColumns.MATCH_INDEX:
			return CaseDataAPI.IndexColumns.CONTENT_TYPE;
        case CaseDataAPI.AttachmentColumns.MATCH_ATTACHMENTS:
			return CaseDataAPI.AttachmentColumns.CONTENT_TYPE;
        }
		
		return null;
	}


	/* (non-Javadoc)
	 * @see android.content.ContentProvider#onCreate()
	 */
	@Override
	public boolean onCreate() {		
		return true;
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		//first, determine whether we're logged in and whether we have a valid data set to even be iterating over.
		try {
			CommCareApplication._().getUserDbHandle();
		} catch(SessionUnavailableException sue) {
			//This implies that the user isn't logged in. In the future we should probably broadcast an intent
			//that notifies the other service to trigger a Login event.
			return null;
		}
		
		
		//Standard dispatcher following Android best practices
        int match = CaseDataAPI.UriMatch(uri);
        
        switch(match) {
        case CaseDataAPI.MetadataColumns.MATCH_CASES:
        case CaseDataAPI.MetadataColumns.MATCH_CASE:
			return queryCaseList(uri, projection, selection, selectionArgs, sortOrder);
        case CaseDataAPI.DataColumns.MATCH_DATA:
			return queryCaseData(uri.getLastPathSegment(), projection, selection, selectionArgs, sortOrder);
        case CaseDataAPI.IndexColumns.MATCH_INDEX:
        case CaseDataAPI.AttachmentColumns.MATCH_ATTACHMENTS:
        	//Unimplemented
        	return null;
        }
		throw new IllegalArgumentException("URI: " + uri.toString() +" is not a valid content path for CommCare Case Data");
		
	}
	



	//this is the complex case. Querying the full case database for metadata.
	private Cursor queryCaseList(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

		//Not cached yet. Long term this should be a priority.
		SqlStorage<ACase> storage = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class);
		
		//What we'd really like to do here is raw DB queries, but sadly it looks like we don't actually index 
		//enough of the right fields for that, so we're going to deserialize the models. We'll consider 
		//revising this API
		
		MatrixCursor retCursor = new MatrixCursor(new String[] {CaseDataAPI.MetadataColumns._ID,
																CaseDataAPI.MetadataColumns.CASE_ID, 
																CaseDataAPI.MetadataColumns.CASE_NAME,
																CaseDataAPI.MetadataColumns.CASE_TYPE,
																CaseDataAPI.MetadataColumns.DATE_OPENED,
																CaseDataAPI.MetadataColumns.LAST_MODIFIED,
																CaseDataAPI.MetadataColumns.OWNER_ID,
																CaseDataAPI.MetadataColumns.STATUS});
		
		//We either need to iterate over all cases, or just get back the one.

		//No Case
		if(CaseDataAPI.UriMatch(uri) == CaseDataAPI.MetadataColumns.MATCH_CASES)  {
			for(Case c: storage) {
				retCursor.addRow(new Object[] {c.getID(), c.getCaseId(), c.getName(), c.getTypeId(), c.getDateOpened(), c.getLastModified(), c.getUserId(), c.isClosed() ? "closed" : "open"});
			}
		} else {
			//Case defined.
			try {
				Case c = storage.getRecordForValue(ACase.INDEX_CASE_ID, uri.getLastPathSegment());
				retCursor.addRow(new Object[] {c.getID(), c.getCaseId(), c.getName(), c.getTypeId(), c.getDateOpened(), c.getLastModified(), c.getUserId(), c.isClosed() ? "closed" : "open"});
			} catch(NoSuchElementException nsee) {
				//No cases with a matching index.
				return retCursor;
			}
		}
		return retCursor;
	}
	
	/**
	 * Query the casedb for the key/value pairs for a specific case.
	 * 
	 * @return
	 */
	private Cursor queryCaseData(String caseId, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		//Demo only, we'll pull this out when we're doing this for real and centralize it/manage its lifecycle more carefully
		SqlStorage<ACase> storage = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class);
		
		//Default projection.
		MatrixCursor retCursor = new MatrixCursor(new String[] {CaseDataAPI.DataColumns._ID,
																CaseDataAPI.DataColumns.CASE_ID, 
																CaseDataAPI.DataColumns.DATUM_ID, 
																CaseDataAPI.DataColumns.VALUE});

		Case c;
		try {
			c = storage.getRecordForValue(ACase.INDEX_CASE_ID, caseId);
		} catch(NoSuchElementException nsee) {
			//No cases with a matching index.
			return retCursor;
		}
		int i = 0;
		Hashtable<String, String> properties = c.getProperties();
		for(String key : properties.keySet()) {
			retCursor.addRow(new Object[] {i, caseId, key, c.getPropertyString(key)});
			++i;
		}
		
		return retCursor;
	}


	/** All of the below are invalid due to the read-only nature of the content provider. It's not 100% clear from spec how to express
	 * the read-only-ness. **/

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection,String[] selectionArgs) {
		// Case content provider is read only.
		//TODO: Throw an exception here? Read up on the spec 
		return 0;
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
	 */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// Case content provider is read only.
		return 0;
	}
	

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
	 */
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		return null;
	}

}
