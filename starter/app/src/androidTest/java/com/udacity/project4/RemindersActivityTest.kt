package com.udacity.project4

import android.app.Application
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView

import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.*
import com.udacity.project4.authentication.AuthenticationActivity
import com.udacity.project4.locationreminders.RemindersActivity
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.util.DataBindingIdlingResource
import com.udacity.project4.util.monitorActivity
import com.udacity.project4.utils.EspressoIdlingResource
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.AutoCloseKoinTest
import org.koin.test.get
import java.util.*

@RunWith(AndroidJUnit4::class)
@LargeTest
// END TO END test to black box test the app
class RemindersActivityTest: AutoCloseKoinTest() {

    private lateinit var repository: ReminderDataSource
    private lateinit var _viewModel: SaveReminderViewModel
    private lateinit var appContext: Application

    // an idling resource that waits for Data Binding to have no pending bindings
    private val dataBindingIdlingResource = DataBindingIdlingResource()

    // test data
    private lateinit var testReminder: ReminderDTO

    // UI Automator - used to click on system elements during test
    // ... see: https://alexzh.com/ui-testing-of-android-runtime-permissions/ for some background
    private val device: UiDevice

    init {
        // set-up UI Automator
        val instrumentation = getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
    }

    // introduce a short delay in test execution, after having clicked on user account (using
    // UI Automator) --> need some time to pass, otherwise the test is flaky
    // https://stackoverflow.com/questions/52818524/delay-test-in-espresso-android-without-freezing-main-thread
    private fun waitFor(delay: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription(): String = "wait for $delay milliseconds"
            override fun perform(uiController: UiController, v: View?) {
                uiController.loopMainThreadForAtLeast(delay)
            }
        }
    }


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

        // initialize test reminder (used to populate reminders list)
        testReminder = ReminderDTO(
            "e2e.title",
            "e2e.description",
            "e2e.location",
            46.0,
            24.0,
            UUID.randomUUID().toString()
        )

    }

    /**
     * Idling resources tell Espresso that the app is idle or busy. This is needed when operations
     * are not scheduled in the main Looper (for example when executed on a different thread).
     */
    @Before
    fun registerIdlingResource() {
        IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource)
        IdlingRegistry.getInstance().register(dataBindingIdlingResource)
    }

    /**
     * Unregister your Idling Resource so it can be garbage collected and does not leak any memory.
     */
    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource)
        IdlingRegistry.getInstance().unregister(dataBindingIdlingResource)
    }


    // E2E testing... RemindersListFragment
    @Suppress("UNCHECKED_CAST")
    @Test
    fun remindersActivityTest_fragmentReminders() = runBlocking {

        // add a reminder to the repository
        repository.saveReminder(testReminder)

        // startup with the Reminders screen
        //
        // ... done manually here (as opposed to @get:Rule
        //     so we get a chance to initialize the repo first (see above)
        //
        // ... need to launch the *activity* rather than the *fragment* to allow for navigation
        //     to take place (as the activity holds the fragment container)
        val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)

        // monitor activityScenario for "idling" (used to flow control the espresso tests)
        dataBindingIdlingResource.monitorActivity(activityScenario)

        // verify that the remindersList fragment is in view
        onView(withId(R.id.reminderssRecyclerView)).check((matches(isDisplayed())))

        // verify that the location reminder from the DB is displayed correctly
        onView(withId(R.id.title)).check(matches(withText(testReminder.title)))
        onView(withId(R.id.description)).check(matches(withText(testReminder.description)))
        onView(withId(R.id.location)).check(matches(withText(testReminder.location)))

        // click on the "add reminder" and travel to SaveReminder fragment
        onView(withId(R.id.addReminderFAB)).perform(click())

        // verify that we have navigated to the saveReminder fragment
        onView(withId(R.id.clSaveReminderFragment)).check((matches(isDisplayed())))

        // go back to the remindersList fragment
        Espresso.pressBack()

        // verify that we have navigated back to the remindersList fragment
        onView(withId(R.id.reminderssRecyclerView)).check((matches(isDisplayed())))

        // make sure the activityScenario is closed before resetting the db
        activityScenario.close()

    }

    // all permissions granted...
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_NETWORK_STATE
    )

    // E2E testing... SaveRemindersFragment
    @Suppress("UNCHECKED_CAST")
    @Test
    fun remindersActivityTest_fragmentSaveReminder() = runBlocking {

        // add a reminder to the repository
        repository.saveReminder(testReminder)

        // fetch SaveReminderViewModel to set some test data
        _viewModel = inject<SaveReminderViewModel>().value
        _viewModel.reminderSelectedLocationStr.postValue("Espresso test location")

        // startup with the RemindersActivity screen (fragment container)
        //
        // ... done manually here (as opposed to @get:Rule
        //     so we get a chance to initialize the repo first (see above)
        //
        // ... need to launch the *activity* rather than the *fragment* to allow for navigation
        //     to take place (as the activity holds the fragment container)
        val activityScenario = ActivityScenario.launch(RemindersActivity::class.java)

        // monitor activityScenario for "idling" (used to flow control the espresso tests)
        dataBindingIdlingResource.monitorActivity(activityScenario)

        // verify that the ReminderListFragment is in view
        onView(withId(R.id.reminderssRecyclerView)).check((matches(isDisplayed())))

        // click on the "save reminder" and navigate to SaveReminder fragment
        onView(withId(R.id.addReminderFAB)).perform(click())

        // verify that the SaveRemindersFragment is in view
        onView(withId(R.id.clSaveReminderFragment)).check((matches(isDisplayed())))

        // fill out the form...
        onView(withId(R.id.reminderTitle)).perform(clearText(), typeText("Have an espresso..."))
        onView(withId(R.id.reminderTitle)).check(matches(withText("Have an espresso...")))
        onView(withId(R.id.reminderDescription)).perform(clearText(), typeText("While we are testing..."))
        onView(withId(R.id.reminderDescription)).check(matches(withText("While we are testing...")))

        // check location information
        onView(withId(R.id.selectedLocation)).check(matches(withText("Espresso test location")))


        // click on Reminder Location - should navigate to select location screen
        onView(withId(R.id.selectLocation)).perform(click())
        onView(withId(R.id.clSelectLocationFragment)).check((matches(isDisplayed())))

//        // allow foreground access to location data (map)
//        val permissionSettings = device.wait(Until.findObject(
//            By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button")
//            //  .text("While using the app")
//        ), 2000)
//
//        // click primary creds label
//        permissionSettings.click()
//
//        // wait a little... to allow user to give permission
//        onView(isRoot()).perform(waitFor(3000))

        // go back
        Espresso.pressBack()
        onView(withId(R.id.clSaveReminderFragment)).check((matches(isDisplayed())))

        // click on the "save reminder" and travel to SaveReminder fragment
        onView(withId(R.id.saveReminder)).perform(click())

        // verify that we have navigated to the saveReminder fragment
        onView(withId(R.id.clSaveReminderFragment)).check((matches(isDisplayed())))

        // go back to the remindersList fragment
        Espresso.pressBack()

        // verify that we have navigated back to the remindersList fragment
        onView(withId(R.id.reminderssRecyclerView)).check((matches(isDisplayed())))

        // make sure the activityScenario is closed before resetting the db
        activityScenario.close()

    }


    // E2E testing... AuthenticationActivity
    @Test
    fun remindersActivityTest_loginActivity() = runBlocking {

        // add a reminder to the repository
        repository.saveReminder(testReminder)

        // open new activity container w/h class "AuthenticationActivity"
        val authenticationActivityScenario = ActivityScenario.launch(AuthenticationActivity::class.java)

        // monitor authenticationActivityScenario for "idling" (used to flow control the espresso tests)
        dataBindingIdlingResource.monitorActivity(authenticationActivityScenario)

        // verify that the login screen is in view
        onView(withId(R.id.main_layout)).check((matches(isDisplayed())))

        // check a few things on the display
        onView(withId(R.id.titleText)).check(matches(withText(R.string.welcome_to_the_location_reminder_app)))
        onView(withId(R.id.status)).check(matches(withText(R.string.signed_out)))
        val loginBtn = onView(withId(R.id.auth_button)).check(matches(withText(R.string.login)))

        // click on the login button
        loginBtn.perform(click())

        // click on the user provided email account
        val emailAccount = device.wait(Until.findObject(
            By.res("com.google.android.gms:id/credential_primary_label")
            //  .text("Frank Douvre")
        ), 2000)

        // click primary creds label
        emailAccount.click()

        // wait a little... to remove flakiness
        onView(isRoot()).perform(waitFor(2000))

        // verify that we have navigated to the remindersList fragment
        onView(withId(R.id.reminderssRecyclerView)).check((matches(isDisplayed())))

        // make sure the activityScenario is closed before resetting the db
        authenticationActivityScenario.close()

    }

}
