package com.udacity.project4.locationreminders.reminderslist

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.testutils.MainCoroutineRule
import com.udacity.project4.locationreminders.testutils.getOrAwaitValue

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.test.AutoCloseKoinTest
import org.robolectric.Shadows.shadowOf
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


    // use our own dispatcher for coroutine testing (swaps out Dispatcher.Main to a version which
    // can be used for testing, where asynchronous tasks should run synchronously)
    //
    // ... see: udacity Android Kotlin course, lesson 5.4: MainCoroutineRule and Injecting Dispatchers
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

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
    fun `loadReminders fetches all reminders from repository and triggers observer of remindersList`() =
        mainCoroutineRule.runBlockingTest {

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
    fun `loadReminders displays an error message (snackBar) when fetching from the local data source fails`()  =
        mainCoroutineRule.runBlockingTest {

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
        assertThat(_viewModel.showSnackBar.getOrAwaitValue(),
            equalTo("Could not fetch reminders from (fake) local storage."))
        assertThat(_viewModel.showNoData.getOrAwaitValue(),
            equalTo(true))

    }

    // LiveData: force error by setting shouldReturnError to true (fake data source)
    @Test
    fun `shouldReturnError - setting shouldReturnError to true should cause error when reading from DB`() {

        mainCoroutineRule.runBlockingTest {

            // GIVEN...
            // ... a broken (fake) data source (to simulate a read error)
            reminderRepo = FakeDataSource(null)

            // ... and 'simulating an error when reading the reminders from the DB'
            (reminderRepo as FakeDataSource).setReturnError(true)

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
            assertThat(
                _viewModel.showSnackBar.getOrAwaitValue(),
                equalTo("Test exception")
            )
            assertThat(
                _viewModel.showNoData.getOrAwaitValue(),
                equalTo(true)
            )

        }

    }

    // test loading spinner
    @Test
    fun `check_loading - loadingSpinner appears and disappears`() {

            // GIVEN...
            // ... some data in the (fake) data source
            reminderRepo = FakeDataSource(reminderDtoList)

            // ... and a fresh viewModel with this data source injected (via constructor)
            _viewModel = RemindersListViewModel(
                ApplicationProvider.getApplicationContext(),
                reminderRepo,
            )

            // WHEN calling function loadReminders
            mainCoroutineRule.pauseDispatcher()
            _viewModel.loadReminders()

            // check that loading spinner has been started
            assertThat(
                _viewModel.showLoading.getOrAwaitValue(),
                equalTo(true)
            )
            mainCoroutineRule.resumeDispatcher()

            // drain the (roboelectric) main looper
            //
            // http://robolectric.org/blog/2019/06/04/paused-looper/
            // if you see test failures like Main looper has queued unexecuted runnables, you may
            // need to insert shadowOf(getMainLooper()).idle() calls to your test to drain the main
            // Looper. Its recommended to step through your test code with a watch set on
            // Looper.getMainLooper().getQueue() to see the status of the looper queue, to determine
            // the appropriate point to add a shadowOf(getMainLooper()).idle() call.
            shadowOf(Looper.getMainLooper()).idle()

            // check that loading spinner has been stopped
            assertThat(
                _viewModel.showLoading.getOrAwaitValue(),
                equalTo(false)
            )

        }

}