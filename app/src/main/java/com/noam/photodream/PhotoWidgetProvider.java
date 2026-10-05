package com.noam.photodream;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home-screen widget: one random photo from the phone folders and the already-synced cloud caches
 * (never the network), in its source's frame color. Android refreshes it about every 30 minutes
 * ({@code updatePeriodMillis}, also after a reboot or an app update); tapping it opens the preview.
 */
public class PhotoWidgetProvider extends AppWidgetProvider {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Random RANDOM = new Random();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        refresh(context, manager, appWidgetIds, goAsync());
    }

    /** The user resized the widget: render again at the new size. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        refresh(context, manager, new int[]{appWidgetId}, goAsync());
    }

    private static void refresh(Context context, AppWidgetManager manager, int[] ids, BroadcastReceiver.PendingResult result) {
        final Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                for (int id : ids) manager.updateAppWidget(id, build(app, manager, id));
            } finally {
                if (result != null) result.finish();
            }
        });
    }

    private static RemoteViews build(Context ctx, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_photo);
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, PreviewActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        Bitmap picture = null;
        try {
            picture = renderRandomPhoto(ctx, manager.getAppWidgetOptions(widgetId));
        } catch (RuntimeException | OutOfMemoryError e) {
            // fall through to the "no photo" message: a widget must never crash the launcher
        }
        if (picture != null) {
            views.setImageViewBitmap(R.id.widget_image, picture);
            views.setViewVisibility(R.id.widget_image, View.VISIBLE);
            views.setViewVisibility(R.id.widget_empty, View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_image, View.GONE);
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
        }
        return views;
    }

    /** A random visible photo, center-cropped to the widget's size inside a frame in its source's color. */
    private static Bitmap renderRandomPhoto(Context ctx, Bundle options) {
        List<Photo> photos = PhotoMarksStore.get(ctx).marks().visible(PhotoRepository.loadAll(ctx));
        if (photos.isEmpty()) return null;

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        // the launcher reports dp; use the larger of portrait/landscape so rotating doesn't blur it
        int wDp = Math.max(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110));
        int hDp = Math.max(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110));
        int[] size = WidgetMath.fit(WidgetMath.dpToPx(wDp, dm.density), WidgetMath.dpToPx(hDp, dm.density),
                WidgetMath.maxBitmapBytes(dm.widthPixels, dm.heightPixels));

        // a few tries: the first photo may be unreadable
        for (int attempt = 0; attempt < 5; attempt++) {
            Photo photo = photos.get(RANDOM.nextInt(photos.size()));
            Bitmap source = BitmapLoader.load(ctx, photo.uri, Math.max(size[0], size[1]));
            if (source == null) continue;
            int frame = FrameColors.argb(new Prefs(ctx).getFrameColor(photo.sourceId));
            return framed(source, size[0], size[1], frame);
        }
        return null;
    }

    private static Bitmap framed(Bitmap source, int w, int h, int frameColor) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(frameColor);
        int border = Math.max(3, Math.round(Math.min(w, h) * 0.04f));
        RectF inner = new RectF(border, border, w - border, h - border);

        // center-crop the photo into the inner rectangle
        float scale = Math.max(inner.width() / source.getWidth(), inner.height() / source.getHeight());
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postTranslate(inner.left + (inner.width() - source.getWidth() * scale) / 2f,
                inner.top + (inner.height() - source.getHeight() * scale) / 2f);
        c.save();
        c.clipRect(inner);
        c.drawBitmap(source, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        c.restore();
        source.recycle();
        return out;
    }
}
