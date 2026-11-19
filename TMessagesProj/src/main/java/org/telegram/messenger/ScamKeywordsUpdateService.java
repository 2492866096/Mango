package org.telegram.messenger;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Public word-list refresh only; Android may defer execution during battery saving. */
public final class ScamKeywordsUpdateService extends JobService {
    private static final int JOB_ID = 0x5343414D;
    private JobParameters runningJob;
    private final Runnable refreshListener = new Runnable() {
        @Override public void run() {
            if (runningJob != null && !ScamProtectionManager.isRefreshing() && !ScamUsersManager.isAnyRefreshing()) {
                JobParameters completed = runningJob;
                runningJob = null;
                ScamProtectionManager.removeListener(this);
                ScamUsersManager.removeListener(this);
                jobFinished(completed, ScamProtectionManager.hasRefreshFailed() || ScamUsersManager.hasRefreshFailed());
            }
        }
    };

    public static void schedule() {
        try {
            Context context = ApplicationLoader.applicationContext;
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;
            ComponentName component = new ComponentName(context, ScamKeywordsUpdateService.class);
            for (JobInfo job : scheduler.getAllPendingJobs()) {
                if (job.getId() == JOB_ID && component.equals(job.getService())
                        && job.isPeriodic() && job.isPersisted()
                        && job.getIntervalMillis() == ScamProtectionManager.REFRESH_INTERVAL_MS
                        && job.getNetworkType() == JobInfo.NETWORK_TYPE_ANY) return;
            }
            JobInfo job = new JobInfo.Builder(JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(ScamProtectionManager.REFRESH_INTERVAL_MS)
                    .setBackoffCriteria(5 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .setPersisted(true)
                    .build();
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                FileLog.e(new IllegalStateException("Unable to schedule scam keyword refresh"));
            }
        } catch (Exception error) {
            // The in-process timer remains available if the system rejects the job.
            FileLog.e(error);
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        ApplicationLoader.postInitApplication();
        runningJob = params;
        ScamProtectionManager.addListener(refreshListener);
        ScamUsersManager.addListener(refreshListener);
        ScamProtectionManager.refreshForEnabledAccounts();
        if (!ScamProtectionManager.isRefreshing() && !ScamUsersManager.isAnyRefreshing()) {
            ScamProtectionManager.removeListener(refreshListener);
            ScamUsersManager.removeListener(refreshListener);
            runningJob = null;
            return false;
        }
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        ScamProtectionManager.removeListener(refreshListener);
        ScamUsersManager.removeListener(refreshListener);
        runningJob = null;
        return true;
    }

    @Override public void onDestroy() {
        ScamProtectionManager.removeListener(refreshListener);
        ScamUsersManager.removeListener(refreshListener);
        runningJob = null;
        super.onDestroy();
    }
}
