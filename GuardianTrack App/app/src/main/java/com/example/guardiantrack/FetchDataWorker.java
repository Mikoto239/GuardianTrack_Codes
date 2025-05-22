package com.example.guardiantrack;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class FetchDataWorker extends Worker {

    private Context context;

    public FetchDataWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        // Call the method in MyBackgroundService to fetch the notification data
        fetchnotificationData();

        // Return success after the work is done
        return Result.success();
    }

    private void fetchnotificationData() {
        // Assuming MyBackgroundService is the service containing the method
        if (context instanceof MyBackgroundService) {
            MyBackgroundService service = (MyBackgroundService) context;
            service.fetchnotificationData();
        }
    }
}
