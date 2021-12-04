package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
//Medium Test to test the repository
@MediumTest
class RemindersLocalRepositoryTest {

    // generate some test database items (location reminders)
    private var reminderDto1 = ReminderDTO(
        "test title 1",
        "test description 1",
        "test location 1",
        1.0,
        1.0,
        UUID.randomUUID().toString(),
    )
    private var reminderDto2 = ReminderDTO(
        "test title 2",
        "test description 2",
        "test location 2",
        2.0,
        2.0,
        UUID.randomUUID().toString(),
    )
    private var reminderDto3 = ReminderDTO(
        "test title 3",
        "test description 3",
        "test location 3",
        3.0,
        3.0,
        UUID.randomUUID().toString(),
    )

    // define fake "local DB"
    private var localReminders: List<ReminderDTO> = listOf(
        reminderDto1,
        reminderDto2,
        reminderDto3,
    )

    // declare fake data source (initialized anew prior to running each new test - @Before)
    private lateinit var reminderLocalDataSource: RemindersDao

    // declare repository (= class under test)
    private lateinit var reminderRepository: RemindersLocalRepository

//    @Before
//    fun createRepository() {
//        reminderLocalDataSource = // todo: room inMemory DB (localReminders.toMutableList())
//        reminderRepository = RemindersLocalRepository(
//            reminderLocalDataSource,
//            Dispatchers.Unconfined,
//        )
//    }


}