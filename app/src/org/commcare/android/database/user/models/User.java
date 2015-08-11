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
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;


@Table(User.STORAGE_KEY)
public class User extends org.commcare.suite.model.User
{
    public static final String TYPE_DEMO = "demo";

    public static final String META_WRAPPED_KEY = "wrappedkey";
    public static final String META_SYNC_TOKEN = "synctoken";
    
    private byte[] wrappedKey;
    
    /** String -> String **/
    private Hashtable<String,String> properties = new Hashtable<String,String>(); 

    public User () {
        setUserType(STANDARD);
    }

    public User(String name, String passw, String uniqueID) {
        this(name, passw, uniqueID, STANDARD);
    }
    
    public User(String name, String passw, String uniqueID, String userType) {
        username = name.toLowerCase();
        password = passw;
        uniqueId = uniqueID;
        setUserType(userType);
        rememberMe = false;
    }

    ///fetch the value for the default user and password from the RMS
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);
        this.wrappedKey = ExtUtil.nullIfEmpty(ExtUtil.readBytes(in));
    }

    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeBytes(out, ExtUtil.emptyIfNull(wrappedKey));
    }
    
    public void setWrappedKey(byte[] key) {
        this.wrappedKey = key;
    }
    
    public byte[] getWrappedKey() {
        return wrappedKey;
    }

    public Object getMetaData(String fieldName) {
        try{
            return super.getMetaData(fieldName);
        }catch(IllegalArgumentException e){
            if (META_WRAPPED_KEY.equals(fieldName)) {
                return wrappedKey;
            } else if (META_SYNC_TOKEN.equals(fieldName)) {
                return ExtUtil.emptyIfNull(syncToken);
            }
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
    
}
