package com.noam.photodream.describe;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks which describers may run now. The rules (tested with fakes):
 * only available ones; the background job (not in the foreground) skips engines that need the
 * foreground (Gemini Nano). Labels always come first so every photo gets at least those.
 */
public final class DescriberSelector {

    private DescriberSelector() { }

    public static List<PhotoDescriber> select(Context context, List<PhotoDescriber> all, boolean appInForeground) {
        List<PhotoDescriber> out = new ArrayList<>();
        for (PhotoDescriber d : all) {
            if (d.needsForeground() && !appInForeground) continue;
            if (!d.isAvailable(context)) continue;
            out.add(d);
        }
        out.sort((a, b) -> Boolean.compare(a.needsForeground(), b.needsForeground()));   // cheap engines first
        return out;
    }

    /** Settings text: "labels" / "labels+sentences" status key. */
    public static boolean sentencesPossible(Context context, List<PhotoDescriber> all) {
        for (PhotoDescriber d : all) if (d.needsForeground() && d.isAvailable(context)) return true;
        return false;
    }
}
