/*
 * Copyright (C) 2009 JavaRosa
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

package org.commcare.android.database.user.models;


import org.commcare.android.storage.framework.Table;
import org.javarosa.core.model.User;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;

/**
 * Old Android user model now deprecated by combined User model in javarosa
 */

@Table(AUser.STORAGE_KEY)
public class AUser implements Persistable, IMetaData
{
    public static final String STORAGE_KEY = "USER";
    public static final String TYPE_STANDARD = "standard";
    public static final String TYPE_DEMO = "demo";
    public static final String KEY_USER_TYPE = "user_type";
    
    public static final String META_UID = "uid";
    public static final String META_USERNAME = "username";
    public static final String META_ID = "userid";
    public static final String META_WRAPPED_KEY = "wrappedkey";
    public static final String META_SYNC_TOKEN = "synctoken";

    private int recordId = -1; //record id on device
    private String username;
    private String password;
    private String uniqueId;  //globally-unique id
    
    private byte[] wrappedKey;
    
    private boolean rememberMe = false;
    private String syncToken = null;
    
    /** String -> String **/
    private Hashtable<String,String> properties = new Hashtable<String,String>(); 

    public AUser() {
        setUserType(TYPE_STANDARD);
    }

    public AUser(String name, String passw, String uniqueID) {
        this(name, passw, uniqueID, TYPE_STANDARD);
    }
    
    public AUser(String name, String passw, String uniqueID, String userType) {
        username = name.toLowerCase();
        password = passw;
        uniqueId = uniqueID;
        setUserType(userType);
        rememberMe = false;
    }

    ///fetch the value for the default user and password from the RMS
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        this.username = ExtUtil.readString(in);
        this.password = ExtUtil.readString(in);
        this.recordId = ExtUtil.readInt(in);
        this.uniqueId = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        this.rememberMe = ExtUtil.readBool(in);
        this.wrappedKey = ExtUtil.nullIfEmpty(ExtUtil.readBytes(in));
        this.syncToken = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        this.properties = (Hashtable)ExtUtil.read(in, new ExtWrapMap(String.class, String.class), pf);
    }

    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, username);
        ExtUtil.writeString(out, password);
        ExtUtil.writeNumeric(out, recordId);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(uniqueId));
        ExtUtil.writeBool(out, rememberMe);
        ExtUtil.writeBytes(out, ExtUtil.emptyIfNull(wrappedKey));
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(syncToken));
        ExtUtil.write(out, new ExtWrapMap(properties));
    }
    
    public String getUsername()
    {
        return username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setID(int recordId)
    {

        this.recordId = recordId;
    }

    public int getID() {
        return recordId;
    }

    public String getUserType() {
        if(properties.containsKey(KEY_USER_TYPE)) {
            return properties.get(KEY_USER_TYPE);
        } else {
            return null;
        }
    }
    
    public void setUserType(String userType) {
        properties.put(KEY_USER_TYPE,userType);
    }

    public void setUsername(String username) {
        this.username = username.toLowerCase();
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
    
    public void setUuid(String uuid) {
        this.uniqueId = uuid;
    }
    
    public String getUniqueId() {
        return uniqueId;
    }
    
    public void setWrappedKey(byte[] key) {
        this.wrappedKey = key;
    }
    
    public byte[] getWrappedKey() {
        return wrappedKey;
    }
    
    public Hashtable<String, String> getProperties() {
        return this.properties;
    }
    
    public void setProperty(String key, String val) {
        this.properties.put(key, val);
    } 
    
    public String getProperty(String key) {
        return (String)this.properties.get(key);
    }

    public Hashtable getMetaData() {
        Hashtable ret = new Hashtable();
        for(String name : getMetaDataFields()) {
            ret.put(name, getMetaData(name));
        }
        return ret;
    }
    
    public void setSyncToken(String syncToken) {
        this.syncToken = syncToken;
    }
    
    public String getSyncToken() {
        return syncToken;
    }

    public Object getMetaData(String fieldName) {
        if(META_UID.equals(fieldName)) {
            return uniqueId;
        } else if(META_USERNAME.equals(fieldName)) {
            return username;
        } else if(META_ID.equals(fieldName)) {
            return Integer.valueOf(recordId);
        } else if (META_WRAPPED_KEY.equals(fieldName)) {
            return wrappedKey;
        } else if (META_SYNC_TOKEN.equals(fieldName)) {
            return ExtUtil.emptyIfNull(syncToken);
        }
        throw new IllegalArgumentException("No metadata field " + fieldName  + " for User Models");
    }

    public String[] getMetaDataFields() {
        return new String[] {META_UID, META_USERNAME, META_ID, META_WRAPPED_KEY, META_SYNC_TOKEN};
    }
    
    //Don't ever save!
    private String cachedPwd;
    public void setCachedPwd(String password) {
        this.cachedPwd = password;
    }
    public String getCachedPwd() {
        return this.cachedPwd;
    }

    public User toNewUser() {
        User user = new User(username, password, uniqueId, getUserType());
        user.setRecordId(recordId);
        user.setWrappedKey(getWrappedKey());
        user.properties = getProperties();
        user.setLastSyncToken(syncToken);
        user.setRememberMe(this.rememberMe == true); // for phillip
        return user;
    }
}