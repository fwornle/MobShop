package com.udacity.project4.locationreminders.reminderslist

import android.os.Bundle
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
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

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
//UI Testing
@MediumTest
class ReminderListFragmentTest {

    //    TODO: test the displayed data on the UI.

    @Test
    fun reminderListFragment_DisplayedInUi() = runBlockingTest{

//        // GIVEN - Add active (incomplete) task to the DB
//        val activeTask = Task("Active Task", "AndroidX Rocks", false)
//        repository.saveTask(activeTask)
//
//        // WHEN - ReminderListFragment launched to display location reminders
//        launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)
//
//        // THEN - Task details are displayed on the screen
//        // make sure that the title/description are both shown and correct
//        onView(withId(R.id.reminderssRecyclerView)).check(matches(isDisplayed()))
//        onView(withId(R.id.task_detail_title_text)).check(matches(withText("Active Task")))
//        onView(withId(R.id.task_detail_description_text)).check(matches(isDisplayed()))
//        onView(withId(R.id.task_detail_description_text)).check(matches(withText("AndroidX Rocks")))
//        // and make sure the "active" checkbox is shown unchecked
//        onView(withId(R.id.task_detail_complete_checkbox)).check(matches(isDisplayed()))
//        onView(withId(R.id.task_detail_complete_checkbox)).check(matches(not(isChecked())))
    }

    //    TODO: add testing for the error messages.


    // navigation test: RemindersList --> SaveReminder
    @Test
    fun clickAddReminderButton_navigateToSaveReminderFragment() {

        // GIVEN - on the reminder list screen
        val scenario = launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)
        val navController = mock(NavController::class.java)
        scenario.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

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

        // GIVEN we are on the SaveReminder screen
        val saveReminderScenario = launchFragmentInContainer<SaveReminderFragment>(Bundle(), R.style.AppTheme)
        val navController = mock(NavController::class.java)
        saveReminderScenario.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

        // WHEN clicking on the "SAVE" button (FAB)
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
        val navController = mock(NavController::class.java)
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