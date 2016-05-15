package org.commcare.adapters;

import android.app.Activity;

import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Filter (and order) entities by those whose 'extra key', most likely case id,
 * is present in the provided ordered key set
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class EntityKeyFilterer extends EntityFiltererBase {
    private final LinkedHashSet<String> orderedKeySet;

    /**
     * @param orderedKeySet Keys that are used to filter and order the Entity
     *                      list based on the entity's 'extra key' field
     */
    public EntityKeyFilterer(EntityListAdapter adapter,
                             NodeEntityFactory nodeFactory,
                             List<Entity<TreeReference>> fullEntityList,
                             Activity context, LinkedHashSet<String> orderedKeySet) {
        super(context, nodeFactory, adapter, fullEntityList);

        this.orderedKeySet = orderedKeySet;
    }

    @Override
    protected void filter() {
        if (isCancelled() || orderedKeySet.isEmpty()) {
            return;
        }

        // Add entities whose extra keys are in the key set, preserving key set
        // ordering. Don't assume one-to-one correspondence between entities
        // and keys: depending on the appliciation we might want to attach the
        // same data to multiple entities
        HashMap<String, List<Entity<TreeReference>>> keyToEntitiesMap =
                buildKeyToEntitiesMap(fullEntityList);
        for (String key : orderedKeySet) {
            if (keyToEntitiesMap.containsKey(key)) {
                matchList.addAll(keyToEntitiesMap.get(key));
            }
        }
    }

    /**
     * Group entities by their 'extra key' field
     *
     * @return A map from 'extra key' values to a list of entities that have
     * that 'extra key'
     */
    private static HashMap<String, List<Entity<TreeReference>>> buildKeyToEntitiesMap(List<Entity<TreeReference>> entityList) {
        // NOTE PLM: potentially expensive in presence of large entity set;
        // could build at entity load time or forgoe ordering or constrain the
        // key to entity mapping to be one-to-one
        HashMap<String, List<Entity<TreeReference>>> keyToEntitiesMap = new HashMap<>();
        for (Entity<TreeReference> entity : entityList) {
            if (keyToEntitiesMap.containsKey(entity.extraKey)) {
                keyToEntitiesMap.get(entity.extraKey).add(entity);
            } else {
                ArrayList<Entity<TreeReference>> list = new ArrayList<>();
                list.add(entity);
                keyToEntitiesMap.put(entity.extraKey, list);
            }
        }
        return keyToEntitiesMap;
    }
}
