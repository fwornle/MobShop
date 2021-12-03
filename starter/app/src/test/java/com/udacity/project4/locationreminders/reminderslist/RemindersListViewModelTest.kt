package com.udacity.project4.locationreminders.reminderslist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.R
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.locationreminders.testutils.getOrAwaitValue

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.hamcrest.core.Is
import org.hamcrest.core.IsEqual
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.AutoCloseKoinTest
import org.koin.test.inject
import java.lang.reflect.Method
import java.util.*

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RemindersListViewModelTest: AutoCloseKoinTest() {

    // viewModel
    private lateinit var _viewModel: RemindersListViewModel

    // reminder repository and fake data
    private lateinit var reminderRepo: ReminderDataSource
    private lateinit var reminderDtoList: MutableList<ReminderDTO>
    private lateinit var dataList: ArrayList<ReminderDataItem>


    // test liveData
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    // run before each individual test
    @Before
    fun setupTest() {

        // run BEFORE EACH individual test ----------------------------------------

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

        // turn list of ReminderDTO items (fake local data source) to array of ReminderDataItem-s
        dataList = ArrayList<ReminderDataItem>()
        dataList.addAll(reminderDtoList.map { reminder ->
            //map the reminder data from the DTOs to the format for the UI
            ReminderDataItem(
                reminder.title,
                reminder.description,
                reminder.location,
                reminder.latitude,
                reminder.longitude,
                reminder.id
            )
        })

    }  // setupTest()


    /* ******************************************************************
     * test suite for: loadReminders
     * ******************************************************************/

    @Test
    fun `loadReminders fetches all reminders from repository and triggers observer of remindersList`()  = runBlockingTest {

        // GIVEN...
        // ... some data in the (fake) data source
        reminderRepo = FakeDataSource(reminderDtoList)

        // ... and a fresh viewModel with this data source injected (via constructor)
        _viewModel = RemindersListViewModel(
            ApplicationProvider.getApplicationContext(),
            reminderRepo,
        )

        // WHEN calling function loadReminders
        _viewModel.loadReminders()

        // THEN the reminder is verified and stored in the repository
        assertThat(_viewModel.remindersList.getOrAwaitValue(), equalTo(dataList))


    }

    @Test
    fun `loadReminders displays an error message (snackBar) when fetching from the local data source fails`()  = runBlockingTest {

        // GIVEN...
        // ... a broken (fake) data source (to simulate a read error)
        reminderRepo = FakeDataSource(null)

        // ... and a fresh viewModel with this data source injected (via constructor)
        _viewModel = RemindersListViewModel(
            ApplicationProvider.getApplicationContext(),
            reminderRepo,
        )

        // WHEN calling function loadReminders
        _viewModel.loadReminders()

        // THEN...
        // ... the observer of liveData showSnackBar is triggered (and value set to error message)
        // ... the observer of liveData showNoData is triggered (and value set to true)
        assertThat(_viewModel.showSnackBar.getOrAwaitValue(), equalTo("Could not fetch reminders from (fake) local storage."))
        assertThat(_viewModel.showNoData.getOrAwaitValue(), equalTo(true))

    }

}