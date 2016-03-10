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
    private final Activity context;
    private final EntityListAdapter adapter;
    private final Identification[] topIdResults;

    public EntityResponseKeySearcher(EntityListAdapter adapter,
                                     NodeEntityFactory nodeFactory,
                                     List<Entity<TreeReference>> full,
                                     Activity context, Identification[] topIdResults) {
        super(nodeFactory);

        this.adapter = adapter;
        this.full = full;
        this.context = context;
        this.topIdResults = topIdResults;
    }

    @Override
    protected void search() {
        final List<Entity<TreeReference>> matchList = new ArrayList<>();

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

        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.setCurrent(matchList);
                adapter.setCurrentSearchTerms(null);
                adapter.update();
            }
        });
    }
}
