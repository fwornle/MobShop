package com.udacity.project4.locationreminders.reminderslist

import android.os.Bundle
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.R
import com.udacity.project4.locationreminders.savereminder.SaveReminderFragment
import com.udacity.project4.locationreminders.savereminder.SaveReminderFragmentDirections
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.local.FakeDataSource
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.util.ToastMatcher.Companion.onToast
import org.junit.Before
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.stopKoin
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.test.AutoCloseKoinTest
import org.koin.test.inject
import java.util.*


@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
// UI Testing
@MediumTest
class ReminderListFragmentTest: AutoCloseKoinTest() {

    // test data for (fake) DB
    private lateinit var reminderDtoList: MutableList<ReminderDTO>

    // fake data source (repo)
    private lateinit var reminderRepo: ReminderDataSource

    // need to launch a fragment scenario to test it...
    // ... and configure it with the mock(ito)ed NavController
    private lateinit var reminderListFragementScenario: FragmentScenario<ReminderListFragment>
    private lateinit var navController: NavController

    // (alternatively... but then the repo is not swapped-out to the fake data source)
    //
    // recreate RemindersActivity environment (before each rule is run)
    //   @get: Rule
    //    val activityScenarioRule: ActivityScenarioRule<RemindersActivity> =
    //        ActivityScenarioRule(RemindersActivity::class.java)

    @Before
    fun setUp() {

        // generate some test database items (location reminders)
        reminderDtoList = mutableListOf<ReminderDTO>()

        // generate some test data
        for (idx in 0..19) {
            reminderDtoList.add(
                ReminderDTO(
                    "test title $idx",
                    "test description $idx",
                    "test location $idx",
                    idx.toDouble(),
                    idx.toDouble(),
                    UUID.randomUUID().toString(),
                )
            )
        }

        // get a fresh fake data source (repository)
        reminderRepo = FakeDataSource(reminderDtoList)


        /**
         * use Koin Library as a service locator
         */

        // stop the original app koin, which is launched when the application starts (in "MyApp")
        stopKoin()

        // define Koin service locator module with fake data source as repo (linked into the VM)
        val myModule = module {

            // declare a ViewModel - to be injected into Fragment with dedicated injector using
            // "by viewModel()"
            viewModel {
                RemindersListViewModel(
                    get(),  // app (context)
                    get() as ReminderDataSource  // repo as data source
                )
            }

            // declare a ViewModel - to be injected into Fragment with standard injector using
            // "by inject()"
            // --> this view model is declared singleton to be used across multiple fragments
            //
            // class SaveReminderViewModel(
            //    val app: Application,
            //    val dataSource: ReminderDataSource
            // ) : BaseViewModel(app) { ... }
            single {
                SaveReminderViewModel(
                    get(),
                    get() as ReminderDataSource
                )
            }

            // ReminderDataSource
            //
            // declare a (singleton) repository service with interface "ReminderDataSource"
            single<ReminderDataSource> { reminderRepo }

        }  // myModule

        // instantiate viewModels, repos and DBs and inject them as services into consuming classes
        // ... using KOIN framework (as "service locator"): https://insert-koin.io/
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(listOf(myModule))
        }

        // launch reminder list fragment
        reminderListFragementScenario = launchFragmentInContainer(Bundle(), R.style.AppTheme)

        // attach the navController (for navigation tests)
        navController = mock(NavController::class.java)
        reminderListFragementScenario.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

    }  // setUp()


    // check that RecyclerView is visible
    @Test
    fun isListFragmentVisible_onLaunch() {

        // check if recyclerView is visible (on launch)
        onView(withId(R.id.reminderssRecyclerView)).check(matches(isDisplayed()))

    }

    // check for specific item from DB
    @Test
    fun itemWithText_doesExist() {

        // index of item in the list to be tested (off screen)
        val testItemIdx = 17
        val testReminder = reminderDtoList[testItemIdx]

        // attempt to scroll to selected list item
        onView(withId(R.id.reminderssRecyclerView)) // scrollTo will fail the test if no item matches.
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText(testReminder.title))
                )
            )
    }

    // check for specific item from DB at corresponding position in RV
    @Test
    fun specificItemWithText_doesExistAtCorrectPosition() {

        // index of item in the list to be tested
        val testItemIdx = 1
        val testReminder = reminderDtoList[testItemIdx]

        // select a specific item in the list and check title
        onView(withId(R.id.reminderssRecyclerView))
            .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(testItemIdx, scrollTo()))
            .check(matches(hasDescendant(withText(testReminder.title))))

    }

    // check for a non-existent reminder item
    @Test(expected = PerformException::class)
    fun itemWithText_doesNotExist() {

        // attempt to scroll to an item that contains the special text.
        onView(withId(R.id.reminderssRecyclerView)) // scrollTo will fail the test if no item matches.
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("not in the list"))
                )
            )
    }


    // LiveData: showErrorMessage
    //
    // Dec 2021:  test still fails with API 30+, as there is a currently unresolved problem with
    //            the espresso library, see: https://knowledge.udacity.com/questions/608423
    @Test
    fun setError_ErrorMessageIsDisplayed() = runBlockingTest {

        // GIVEN...
        // ... access to the viewModel (from Koin service locator module)
        val _viewModel = inject<RemindersListViewModel>().value

        // WHEN...
        // ... setting liveData value to error message
        //     --> use postValue, as this is on a background thread (within the test environment)
        val testToastText = "some error occurred"
        _viewModel.showErrorMessage.postValue(testToastText)

        // THEN the Toast should be displayed
        // ... onToast method (from ToastMatcher - author: see ToastMatcher.kt)
        onToast(testToastText).check(matches(isDisplayed()))

    }


    // navigation test: RemindersList --> SaveReminder
    @Test
    fun clickAddReminderButton_navigateToSaveReminderFragment() {

        // GIVEN - on the ReminderList fragment
        // ... done centrally in @Before

        // WHEN - click on the "+" button (FAB)
        onView(withId(R.id.addReminderFAB)).perform(click())

        // THEN - verify that we navigate to the save reminder screen
        verify(navController).navigate(
            ReminderListFragmentDirections.toSaveReminder()
        )
    }

    // navigation test: saveReminderFragment --> reminderListFragment
    @Test
    fun clickSaveButton_navigateToReminderListFragment() {

        // GIVEN - on the SaveReminder fragment
        val saveReminderFragmentScenario = launchFragmentInContainer<SaveReminderFragment>(Bundle(), R.style.AppTheme)
        // ... (with the NavController hooked up to this container)
        saveReminderFragmentScenario.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

        // WHEN clicking the "SAVE" button (FAB)
        onView(withId(R.id.saveReminder)).perform(click())

        // THEN - verify that we navigate back to the Reminder List screen (via popBackStack)
        //
        // note: this is done by setting the observable liveData '_viewModel.navigationCommand' to
        //       'NavigationCommand.Back' (in private method SaveReminderViewModel.saveReminder)
        //       ... which triggers 'findNavController().popBackStack()' (see: the implementation
        //       of the 'navigationCommand' liveData observer in BaseFragment.kt)
        verify(navController).popBackStack()

    }

    // navigation test: saveReminderFragment --> selectLocationFragment
    @Test
    fun clickLocation_navigateToSelectLocationFragment() {

        // GIVEN we are on the SaveReminder screen
        val saveReminderScenario = launchFragmentInContainer<SaveReminderFragment>(Bundle(), R.style.AppTheme)
        // ... (with the NavController hooked up to this container)
        saveReminderScenario.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

        // WHEN clicking on the location button (FAB)
        onView(withId(R.id.selectLocation)).perform(click())

        // THEN - verify that we navigate to the select location screen
        verify(navController).navigate(
            SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment()
        )

    }

}