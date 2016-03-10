package org.commcare.adapters;

import android.app.Activity;

import com.simprints.libsimprints.Identification;

import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class EntityResponseKeySearcher extends EntitySearcherBase {
    private final List<Entity<TreeReference>> full;
    private final Identification[] topIdResults;
    private final List<Entity<TreeReference>> matchList = new ArrayList<>();

    public EntityResponseKeySearcher(EntityListAdapter adapter,
                                     NodeEntityFactory nodeFactory,
                                     List<Entity<TreeReference>> full,
                                     Activity context, Identification[] topIdResults) {
        super(context, nodeFactory, adapter);

        this.full = full;
        this.topIdResults = topIdResults;
    }

    @Override
    protected void search() {

        if (isCancelled()) {
            return;
        }

        for (Entity<TreeReference> entity : full) {
            for (Identification idReading : topIdResults) {
                if (idReading.getGuid().equals(entity.extraKey)) {
                    matchList.add(entity);
                }
                if (matchList.size() >= topIdResults.length) {
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
