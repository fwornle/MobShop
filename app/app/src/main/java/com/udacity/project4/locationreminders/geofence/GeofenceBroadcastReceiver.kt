package com.udacity.project4.locationreminders.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.udacity.project4.locationreminders.savereminder.SaveReminderFragment.Companion.ACTION_GEOFENCE_EVENT
import timber.log.Timber

/**
 * Triggered by the Geofence.  Since we can have many Geofences at once, we pull the request
 * ID from the first Geofence, and locate it within the cached data in our Room DB
 *
 * Or users can add the reminders and then close the app, So our app has to run in the background
 * and handle the geofencing in the background.
 * To do that you can use https://developer.android.com/reference/android/support/v4/app/JobIntentService to do that.
 *
 */

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    // adapted from: https://developer.android.com/training/location/geofencing
    override fun onReceive(context: Context, intent: Intent) {

        // are we triggered by a tripped geoFence wire?
        if (intent.action == ACTION_GEOFENCE_EVENT) {

            // yup --> grab the geoFencing event object from provided intent
            val geofencingEvent = GeofencingEvent.fromIntent(intent)

            // only process valid events...
            if (geofencingEvent.hasError()) {
                val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
                Timber.e(errorMessage)
                return
            }

            // ... only looking for ENTER geoFence transitions:
            when (geofencingEvent.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER,
                // Geofence.GEOFENCE_TRANSITION_EXIT,
                -> {

                    // start JobIntentService to handle the geofencing transition events
                    //
                    // ... schedules background work to retrieve from local DB the location reminder
                    //     data associated with the newly triggered geoFence
                    GeofenceTransitionsJobIntentService.enqueueWork(context, intent)

                }
                else -> {
                    // log the unhandled transition
                    Timber.i("Encountered unhandled geoFence transition (e.g. EXIT, DWELL, ...).")
                }

            }  // when

        }  // if (ACTION_GEOFENCE_EVENT)

    }  // onReceive

}  // GeofenceBroadcastReceiver