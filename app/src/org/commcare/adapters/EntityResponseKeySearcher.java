package org.commcare.adapters;

import android.app.Activity;

import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class EntityResponseKeySearcher extends EntitySearcherBase {
    private final Hashtable<String, String> topIdResults;
    private final List<Entity<TreeReference>> matchList = new ArrayList<>();

    public EntityResponseKeySearcher(EntityListAdapter adapter,
                                     NodeEntityFactory nodeFactory,
                                     List<Entity<TreeReference>> fullEntityList,
                                     Activity context, Hashtable<String, String> topIdResults) {
        super(context, nodeFactory, adapter, fullEntityList);

        this.topIdResults = topIdResults;
    }

    @Override
    protected void search() {
        // TODO PLM: make work in async mode

        if (isCancelled()) {
            return;
        }

        for (Entity<TreeReference> entity : fullEntityList) {
            String entityExtraData = topIdResults.get(entity.extraKey);
            if (entityExtraData != null) {
                matchList.add(entity);
            }
            if (matchList.size() >= topIdResults.size()) {
                break;
            }
        }
    }

    @Override
    protected List<Entity<TreeReference>> getMatchList() {
        return matchList;
    }
}
