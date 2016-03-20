package org.commcare.adapters;

import android.app.Activity;

import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Filter entities by those whose guid (case id) is present in the provided set
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class EntityGuidFilterer extends EntityFiltererBase {
    private final Set<String> guidSet;
    private final List<Entity<TreeReference>> matchList = new ArrayList<>();

    public EntityGuidFilterer(EntityListAdapter adapter,
                              NodeEntityFactory nodeFactory,
                              List<Entity<TreeReference>> fullEntityList,
                              Activity context, Set<String> guidSet) {
        super(context, nodeFactory, adapter, fullEntityList);

        this.guidSet = guidSet;
    }

    @Override
    protected void filter() {
        // TODO PLM: make work in async mode

        if (isCancelled() || guidSet.isEmpty()) {
            return;
        }

        for (Entity<TreeReference> entity : fullEntityList) {
            if (guidSet.contains(entity.extraKey)) {
                matchList.add(entity);

                if (matchList.size() >= guidSet.size()) {
                    break;
                }
            }
        }
    }

    @Override
    protected List<Entity<TreeReference>> getMatchList() {
        return matchList;
    }
}
