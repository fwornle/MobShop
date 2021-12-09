package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.udacity.project4.locationreminders.data.dto.ReminderDTO

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
    private lateinit var reminderDto: ReminderDTO


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

        // test database item
        reminderDto = ReminderDTO(
                "test title 1",
                "test description 1",
                "test location 1",
                1.0,
                1.0,
                UUID.randomUUID().toString(),
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
        dao.saveReminder(reminderDto)

        // read it back
        val readBackReminder = dao.getReminderById(reminderDto.id)

        // check for equality
        assertThat(readBackReminder, notNullValue())
        assertThat(readBackReminder?.id, `is`(reminderDto.id))
        assertThat(readBackReminder?.title, `is`(reminderDto.title))
        assertThat(readBackReminder?.description, `is`(reminderDto.description))
        assertThat(readBackReminder?.location, `is`(reminderDto.location))
        assertThat(readBackReminder?.latitude, `is`(reminderDto.latitude))
        assertThat(readBackReminder?.longitude, `is`(reminderDto.longitude))

    }


}

