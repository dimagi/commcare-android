package org.commcare.android.tasks;

import java.util.List;

import org.commcare.android.models.Entity;
import org.javarosa.core.model.instance.TreeReference;

public interface EntityLoaderListener {
	public void attach(EntityLoaderTask task);
	public void deliverResult(List<Entity<TreeReference>> entities, List<TreeReference> references);
	public void deliverError(Exception e);
}
