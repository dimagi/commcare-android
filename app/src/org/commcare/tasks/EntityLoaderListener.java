package org.commcare.tasks;

import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;

public interface EntityLoaderListener {
    void attachLoader(EntityLoaderTask task);

    void deliverLoadResult(List<Entity<TreeReference>> entities, List<TreeReference> references,
            NodeEntityFactory factory, int focusTargetIndex);

    void deliverLoadError(Exception e);

    void deliverProgress(Integer... values);
}
