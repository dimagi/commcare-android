package org.commcare.android.tasks;

import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;

public interface EntityLoaderListener {
    void attach(EntityLoaderTask task);
    void deliverResult(List<Entity<TreeReference>> entities,
                       List<TreeReference> references,
                       NodeEntityFactory factory,
                       boolean locationChanged);
    void deliverError(Exception e);
}
