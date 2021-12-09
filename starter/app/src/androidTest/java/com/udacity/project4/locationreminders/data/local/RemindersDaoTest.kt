package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import kotlinx.coroutines.Dispatchers

import org.junit.Before
import org.junit.runner.RunWith

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.*


@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
// unit test the DAO
@SmallTest
class RemindersDaoTest {

    // fake DB (room, in-memory)
    private lateinit var fakeDB: RemindersDatabase
    private lateinit var dao: RemindersDao

    // test data for (fake) DB
    private lateinit var reminderDtoList: MutableList<ReminderDTO>


    // testing "architecture components" --> execute everything synchronously
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {

        // create fake datasource
        fakeDB = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemindersDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        // fetch DAO
        dao = fakeDB.reminderDao()

        // generate some test database items (location reminders)
        reminderDtoList = mutableListOf<ReminderDTO>()
        reminderDtoList.add(
            ReminderDTO(
                "test title 1",
                "test description 1",
                "test location 1",
                1.0,
                1.0,
                UUID.randomUUID().toString(),
            )
        )
        reminderDtoList.add(
            ReminderDTO(
                "test title 2",
                "test description 2",
                "test location 2",
                2.0,
                2.0,
                UUID.randomUUID().toString(),
            )
        )
        reminderDtoList.add(
            ReminderDTO(
                "test title 3",
                "test description 3",
                "test location 3",
                3.0,
                3.0,
                UUID.randomUUID().toString(),
            )
        )

    }

    @After
    fun tearDown() {
        fakeDB.close()
    }

    /**
     * check insertion of reminder data into the DB
     */
    @Test
    fun saveReminder_storesDataInDB() = runBlockingTest {

        // store one reminder in (fake) DB
        dao.saveReminder(reminderDtoList[0])

        // read it back
        val readBackReminder = dao.getReminderById(reminderDtoList[0].id)

        // check for equality
        assertThat(readBackReminder, notNullValue())

    }


}

