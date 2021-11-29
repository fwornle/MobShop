package com.udacity.project4.locationreminders.data

import com.google.android.gms.tasks.SuccessContinuation
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import java.lang.Exception

// use FakeDataSource that acts as a test double to the LocalDataSource
// inject the reminders stored in this source via the constructor of the class
class FakeDataSource(var reminders: MutableList<ReminderDTO>? = mutableListOf()) : ReminderDataSource {

    override suspend fun getReminders(): Result<List<ReminderDTO>> {
        // return the entire list of reminders from fake local data source... if any
        reminders?.let {
            return Result.Success(ArrayList(it))
        }
        return Result.Error(
            "No reminders found in (fake) local storage."
        )
    }

    override suspend fun saveReminder(reminder: ReminderDTO) {
        // store provided reminder in fake local data source (list)
        reminders?.add(reminder)
    }

    override suspend fun getReminder(id: String): Result<ReminderDTO> {
        // fetch reminder associated with provided id
        reminders?.firstOrNull {it.id == id} ?.let {
            // found it
            return Result.Success(it)
        }

        // reminder with ID not found
        return Result.Error(
            "Reminder with ID $id not found in (fake) local storage."
        )
    }

    override suspend fun deleteAllReminders() {
        // empty list to fake deleting all records from local data source (DB)
        reminders?.clear()
    }

}