package org.commcare.interfaces;

import org.commcare.CommCareApplication;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.cases.entity.SortableEntityAdapter;
import org.commcare.models.AsyncNodeEntityFactory;
import org.commcare.suite.model.Detail;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;

/**
 * Created by willpride on 5/31/17.
 */
public abstract class AndroidSortableEntityAdapter extends SortableEntityAdapter {

    public AndroidSortableEntityAdapter(List<Entity<TreeReference>> entityList, Detail detail, NodeEntityFactory factory) {
        super(entityList, detail, factory instanceof AsyncNodeEntityFactory);
    }

    @Override
    public void notifyBadFilter(String[] args) {
        CommCareApplication.notificationManager().reportNotificationMessage(
                NotificationMessageFactory.message(
                        NotificationMessageFactory.StockMessages.Bad_Case_Filter, args));
    }
}
