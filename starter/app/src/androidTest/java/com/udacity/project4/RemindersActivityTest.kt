package com.udacity.project4

import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.udacity.project4.locationreminders.RemindersActivity
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.AutoCloseKoinTest
import org.koin.test.get
import java.util.*

@RunWith(AndroidJUnit4::class)
@LargeTest
// END TO END test to black box test the app
class RemindersActivityTest :
    AutoCloseKoinTest() {// Extended Koin Test - embed autoclose @after method to close Koin after every test

    private lateinit var repository: ReminderDataSource
    private lateinit var appContext: Application

    /**
     * As we use Koin as a Service Locator Library to develop our code, we'll also use Koin to test our code.
     * at this step we will initialize Koin related code to be able to use it in out testing.
     */
    @Before
    fun init() {

        // stop the original app koin
        stopKoin()

        appContext = getApplicationContext()

        val myModule = module {
            viewModel {
                RemindersListViewModel(
                    appContext,
                    get() as ReminderDataSource
                )
            }
            single {
                SaveReminderViewModel(
                    appContext,
                    get() as ReminderDataSource
                )
            }
            single { RemindersLocalRepository(get()) }

            // ... expose ReminderDataSource, so that the fetching ot the repository works with
            //     a simple 'get()' (see below)
            single<ReminderDataSource> {
                get<RemindersLocalRepository>()
            }

            single { LocalDB.createRemindersDao(appContext) }
        }

        // declare a new koin module
        startKoin {
            modules(listOf(myModule))
        }

        // get our real repository (type: ReminderDataSource - exposed in Koin module, see above)
        repository = get()

        //clear the data to start fresh
        runBlocking {
            repository.deleteAllReminders()
        }
    }


    // E2E testing...
    @Test
    fun login() = runBlocking {

        // set initial state of the repository
        repository.saveReminder(
            ReminderDTO(
                "e2e.title",
                "e2e.description",
                "e2e.location",
                46.0,
                24.0,
                UUID.randomUUID().toString()
            )
        )

        // startup with the Reminders(List) screen
        // ... done manually here (as opposed to @get:Rule
        //     so we get a chance to initialize the repo first (see above)
        val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)

        // Click on the task on the list and verify that all the data is correct.
        onView(withText("e2e.title")).perform(click())
        onView(withId(R.id.title)).check(matches(withText("e2e.title")))
        onView(withId(R.id.description)).check(matches(withText("e2e.description")))
        onView(withId(R.id.location)).check(matches(withText("e2e.location")))

//        // Click on the edit button, edit, and save.
//        onView(withId(R.id.edit_task_fab)).perform(click())
//        onView(withId(R.id.add_task_title_edit_text)).perform(replaceText("NEW TITLE"))
//        onView(withId(R.id.add_task_description_edit_text)).perform(replaceText("NEW DESCRIPTION"))
//        onView(withId(R.id.save_task_fab)).perform(click())
//
//        // Verify task is displayed on screen in the task list.
//        onView(withText("NEW TITLE")).check(matches(isDisplayed()))
//        // Verify previous task is not displayed.
//        onView(withText("TITLE1")).check(doesNotExist())

        // Make sure the activity is closed before resetting the db.
        activityScenario.close()
    }

}
