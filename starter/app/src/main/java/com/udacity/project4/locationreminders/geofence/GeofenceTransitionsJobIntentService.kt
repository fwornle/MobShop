package com.udacity.project4.locationreminders.geofence

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.sendNotification
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class GeofenceTransitionsJobIntentService : JobIntentService(), CoroutineScope {

    private var coroutineJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    companion object {
        private const val JOB_ID = 573

        // start the JobIntentService to handle the geofencing transition events
        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(
                context,
                GeofenceTransitionsJobIntentService::class.java, JOB_ID,
                intent
            )
        }
    }

    // this will be triggered, as soon as the user enters the geoFence perimeter
    override fun onHandleWork(intent: Intent) {

        // Get the geoFences that were triggered
        // (note: a single event can trigger multiple geoFences)
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        val triggeringGeofences = geofencingEvent.triggeringGeofences

        // send notification with the triggering geoFences
        // note: polymorphism
        //       --> call-up parameter is a list of geoFences
        //       --> local implementation of sendNotificatino is used (see below)
        sendNotification(triggeringGeofences)

    }

    // fetch ID associated with triggering geoFence (coincides with location reminder ID in DB)
    private fun sendNotification(triggeringGeofences: List<Geofence>) {

        // fetch the ID of the first geoFence in the list of possibly simultaneously
        // triggered geoFences
        val requestId = when {
            triggeringGeofences.isNotEmpty() ->
                triggeringGeofences[0].requestId
            else -> {
                Timber.e("Weird - received a geoFence event without triggerings --> not sending notification to user.")
                return
            }
        }

        // get the local repository instance
        val remindersLocalRepository: RemindersLocalRepository by inject()

        // ... interaction to the repository has to be through a coroutine scope
        CoroutineScope(coroutineContext).launch(SupervisorJob()) {

            // get the reminder with the request id
            val result = remindersLocalRepository.getReminder(requestId)

            // location found in DB?
            if (result is Result.Success<ReminderDTO>) {

                // yes --> fetch associated location reminder data
                //         ... and send it down the notification channel
                val reminderDTO = result.data

                // send a notification to the user with the reminder details
                // note: polymorphism
                //       --> call-up parameter is a ReminderDataItem
                //       --> implementation of sendNotificatino from NotificationUtils.kt is used
                sendNotification(
                    this@GeofenceTransitionsJobIntentService, ReminderDataItem(
                        reminderDTO.title,
                        reminderDTO.description,
                        reminderDTO.location,
                        reminderDTO.latitude,
                        reminderDTO.longitude,
                        reminderDTO.id
                    )
                )

            }  // if (location found in DB)

        }  // Coroutine scope

    }  // sendNotification

}  // class GeofenceTransitionsJobIntentService