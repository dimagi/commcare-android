package org.commcare.android.tasks;

import java.util.List;

import org.commcare.android.models.Entity;
<<<<<<< HEAD
=======
import org.commcare.android.models.NodeEntityFactory;
>>>>>>> master
import org.javarosa.core.model.instance.TreeReference;

public interface EntityLoaderListener {
    public void attach(EntityLoaderTask task);
    public void deliverResult(List<Entity<TreeReference>> entities, List<TreeReference> references, NodeEntityFactory factory);
    public void deliverError(Exception e);
}
