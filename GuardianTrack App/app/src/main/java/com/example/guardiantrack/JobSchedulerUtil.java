package com.example.guardiantrack;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

public class JobSchedulerUtil {
    public static void scheduleJob(Context context, int jobId) {
        ComponentName serviceComponent = new ComponentName(context, MyJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(jobId, serviceComponent);
        builder.setPersisted(true); // Ensure job persists across reboots
        builder.setPeriodic(60000); // Set periodic interval to 50 minutes
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING);


        // Optional: Add additional constraints if needed
        builder.setRequiresCharging(false); // Set to true if job requires charging
        builder.setRequiresDeviceIdle(false); // Set to true if job requires device to be idle

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.schedule(builder.build());
        }
    }

    public static void checkJobStatus(Context context, int jobId) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            for (JobInfo jobInfo : jobScheduler.getAllPendingJobs()) {
                if (jobInfo.getId() == jobId) {
                    // Job is scheduled
                    Log.d("JobSchedulerUtil", "Job is scheduled with ID: " + jobId);
                    return;
                }
            }
            // Job is not scheduled
            Log.d("JobSchedulerUtil", "Job is not scheduled with ID: " + jobId);
        }
    }
}
