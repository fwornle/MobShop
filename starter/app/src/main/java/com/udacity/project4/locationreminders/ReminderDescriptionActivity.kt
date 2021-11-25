package com.udacity.project4.locationreminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.Navigation
import com.udacity.project4.databinding.ActivityReminderDescriptionBinding
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import java.util.*


/**
 * Activity that displays the reminder details after the user clicks on the notification
 */
class ReminderDescriptionActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ReminderDataItem = "EXTRA_ReminderDataItem"

        // receive the reminder object after the user clicks on the notification
        fun newIntent(context: Context, reminderDataItem: ReminderDataItem): Intent {
            val intent = Intent(context, ReminderDescriptionActivity::class.java)
            intent.putExtra(EXTRA_ReminderDataItem, reminderDataItem)
            return intent
        }
    }

    // data binding
    private lateinit var binding: ActivityReminderDescriptionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // inflate layout
        binding = DataBindingUtil.setContentView(
            this,
            com.udacity.project4.R.layout.activity_reminder_description
        )

        // fetch data from intent provided by triggering notification
        var reminderDataItem = ReminderDataItem(
            "<not set>",
            "<not set>",
            "<not set>",
            -1.0,
            -1.0,
            "invalid"
        )

        // attempt to read extra data from notification
        val extras = intent.extras
        extras?.let {
            if (it.containsKey(EXTRA_ReminderDataItem)) {
                // extract the extra-data in the notification
                reminderDataItem = it.getParcelable<ReminderDataItem>(EXTRA_ReminderDataItem)!!
            }
        }

        // set layout variable 'reminderDataItem'
        binding.reminderDataItem = reminderDataItem

        // set onClick handler for DISMISS button
        // ... navigate back to the main app
        binding.btDismiss.setOnClickListener {
            val intent = Intent(this, RemindersActivity::class.java)
            startActivity(intent)
        }

    }
}
