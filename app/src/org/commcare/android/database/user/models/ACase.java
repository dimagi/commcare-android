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

/**
 * 
 */
package org.commcare.android.database.user.models;

import org.commcare.android.database.EncryptedModel;
import org.commcare.cases.model.Case;

/**
 * NOTE: All new fields should be added to the case class using the "data" class,
 * as it demonstrated by the "userid" field. This prevents problems with datatype
 * representation across versions.
 * 
 * @author Clayton Sims
 * @date Mar 19, 2009 
 *
 */
public class ACase extends Case implements EncryptedModel {
    public static final String STORAGE_KEY = "AndroidCase";
    
    
    public ACase() {
        super();
    }
    
    public ACase(String a, String b) {
        super(a,b);
    }
    public boolean isBlobEncrypted() {
        return true;
    }

    public boolean isEncrypted(String data) {
        if (data.equals("casetype")) {
            return true;
        } else if (data.equals("externalid")) {
            return true;
        } return false;
    }
}
