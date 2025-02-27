package org.commcare.tasks;

import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;

public interface EntityLoaderListener {
    void attachLoader(EntityLoaderTask task);

    /**
             * Delivers the results of an entity loading operation by providing the loaded entities,
             * their associated tree references, and a factory for creating node entities.
             * The focus target index designates which entity should receive focus after loading.
             *
             * @param entities the list of entities loaded during the operation
             * @param references the list of tree references corresponding to the loaded entities
             * @param factory the factory used for creating node entities from the loaded data
             * @param focusTargetIndex the index of the entity that should be in focus post-loading
             */
            void deliverLoadResult(List<Entity<TreeReference>> entities, List<TreeReference> references,
            NodeEntityFactory factory, int focusTargetIndex);

    /**
 * Notifies the listener of an error that occurred during the entity loading process.
 *
 * @param e the exception that describes the error encountered while loading entities.
 */
void deliverLoadError(Exception e);

    /**
 * Reports progress updates during the loading process.
 *
 * <p>This method accepts one or more integer values that represent progress metrics,
 * such as percentage completion or other indicators of the loading progress.
 *
 * @param values one or more progress update values
 */
void deliverProgress(Integer... values);
}
