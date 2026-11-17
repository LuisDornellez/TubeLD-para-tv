package com.ld.tubeld

import android.app.job.JobParameters
import android.app.job.JobService

/** Revisión periódica en segundo plano: si hay versión nueva, muestra una notificación. */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                val info = Updater.fetchNewer(applicationContext)
                if (info != null) Updater.notifyNew(applicationContext, info)
            } catch (_: Throwable) {
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
