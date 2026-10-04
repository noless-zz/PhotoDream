package com.noam.photodream.onedrive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Decides which OneDrive photos should be on the phone after a sync.
 * Pure Java (no Android) so it can be unit-tested.
 *
 * Rules:
 *  - never more than {@code target} photos
 *  - photos deleted from OneDrive are removed from the phone
 *  - photos already downloaded are kept (saves data), except that about
 *    {@code rotateFraction} of them are swapped for new random ones each
 *    sync, so the screensaver keeps showing something new
 */
public final class SyncPlanner {

    public static final class Plan {
        public final Set<String> keep = new LinkedHashSet<>();
        public final List<String> download = new ArrayList<>();
        public final Set<String> delete = new LinkedHashSet<>();
    }

    private SyncPlanner() { }

    public static Plan plan(List<String> remote, Set<String> cached, int target,
                            double rotateFraction, Random random) {
        Plan p = new Plan();
        Set<String> remoteSet = new HashSet<>(remote);

        // 1. cached photos that still exist in OneDrive, in random order
        List<String> stillThere = new ArrayList<>();
        for (String id : cached) {
            if (remoteSet.contains(id)) stillThere.add(id);
            else p.delete.add(id);                      // gone from OneDrive
        }
        Collections.shuffle(stillThere, random);

        // 2. how many to keep: leave room for fresh ones if OneDrive has more to offer
        int notCached = remoteSet.size() - stillThere.size();
        int rotate = notCached > 0 ? (int) Math.round(Math.min(target, stillThere.size()) * rotateFraction) : 0;
        int keepCount = Math.min(stillThere.size(), Math.max(0, target - Math.max(rotate, 0)));
        if (keepCount + notCached < target) keepCount = Math.min(stillThere.size(), target - notCached);
        for (int i = 0; i < stillThere.size(); i++) {
            if (i < keepCount) p.keep.add(stillThere.get(i));
            else p.delete.add(stillThere.get(i));
        }

        // 3. fill up with random photos we don't have yet
        List<String> candidates = new ArrayList<>();
        for (String id : remoteSet) if (!cached.contains(id)) candidates.add(id);
        Collections.sort(candidates);                     // stable before shuffling (testable)
        Collections.shuffle(candidates, random);
        int room = target - p.keep.size();
        for (int i = 0; i < candidates.size() && i < room; i++) p.download.add(candidates.get(i));
        return p;
    }
}
