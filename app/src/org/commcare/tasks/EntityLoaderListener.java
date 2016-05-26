package org.commcare.tasks;

import org.commcare.modern.models.Entity;
import org.commcare.modern.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;

public interface EntityLoaderListener {
    void attachLoader(EntityLoaderTask task);

    void deliverLoadResult(List<Entity<TreeReference>> entities,
                           List<TreeReference> references,
                           NodeEntityFactory factory);

    void deliverLoadError(Exception e);
}
