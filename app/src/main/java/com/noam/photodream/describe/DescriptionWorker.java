package com.noam.photodream.describe;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Labels new photos in the background, only while the phone charges (it takes battery).
 * Started after every sync, when the photo folders change and when Settings opens; if photos are
 * left over it schedules itself again.
 */
public class DescriptionWorker extends Worker {

    private static final String UNIQUE_NAME = "describe-photos";
    private static final int PER_RUN = 150;

    public DescriptionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Ask for a labeling run (does nothing if one is already waiting). */
    public static void schedule(Context context, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DescriptionWorker.class)
                .setConstraints(new Constraints.Builder().setRequiresCharging(true).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        DescriptionJob.Outcome out = DescriptionJob.run(getApplicationContext(), false, PER_RUN, null, null);
        if (out.remaining > 0 && !isStopped()) schedule(getApplicationContext(), ExistingWorkPolicy.APPEND_OR_REPLACE);
        return Result.success();
    }
}
