package com.noam.photodream.describe;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** ML Kit image labeling (on-device base model, bundled): works on every phone, in the background. */
public class LabelDescriber implements PhotoDescriber {

    private ImageLabeler labeler;

    @Override public String engine() { return "labels"; }

    @Override public boolean isAvailable(Context context) { return true; }

    @Override public boolean needsForeground() { return false; }

    @Override
    public Result describe(Context context, Bitmap bitmap) throws Exception {
        if (labeler == null) labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        List<ImageLabel> found = Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS);
        List<String> texts = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        for (ImageLabel l : found) {
            texts.add(l.getText());
            confidences.add(l.getConfidence());
        }
        return new Result(LabelFilter.pick(texts, confidences), "");
    }

    public void close() {
        if (labeler != null) labeler.close();
    }
}
