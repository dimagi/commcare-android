package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Data class for holding flags related to a Connect job delivery
 */
@Table(ConnectJobDeliveryFlagRecord.STORAGE_KEY)
public class ConnectJobDeliveryFlagRecord extends Persisted implements Serializable {
    public static final String STORAGE_KEY = "connect_delivery_flags";

    public static final String META_DELIVERY_ID = "delivery_id";
    public static final String META_CODE = "code";
    public static final String META_DESCRIPTION = "description";

    @Persisting(1)
    @MetaField(META_DELIVERY_ID)
    private int deliveryId;

    @Persisting(2)
    @MetaField(META_CODE)
    private String code;
    @Persisting(3)
    @MetaField(META_DESCRIPTION)
    private String description;

    public static List<ConnectJobDeliveryFlagRecord> fromJson(JSONObject json, int deliveryId) {
        List<ConnectJobDeliveryFlagRecord> flags = new ArrayList<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            try {
                String key = keys.next();
                ConnectJobDeliveryFlagRecord flag = new ConnectJobDeliveryFlagRecord();
                flag.deliveryId = deliveryId;
                flag.code = key;
                flag.description = json.getString(key);
                flags.add(flag);
            } catch (JSONException e) {
                Logger.exception("Error parsing delivery flag", e);
            }
        }
        return flags;
    }

    public int getDeliveryId() {
        return deliveryId;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
