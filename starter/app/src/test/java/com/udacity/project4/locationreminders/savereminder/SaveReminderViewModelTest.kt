package com.udacity.project4.locationreminders.savereminder

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.R
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.locationreminders.testutils.MainCoroutineRule
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
import org.koin.test.AutoCloseKoinTest
import java.lang.reflect.Method
import java.util.*

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SaveReminderViewModelTest: AutoCloseKoinTest() {

    // declare globally used variables
    private lateinit var reminderData: ReminderDataItem
    private lateinit var privateTestFun: Method

    // viewModel
    private lateinit var _viewModel: SaveReminderViewModel

    // reminder repository and fake data
    private lateinit var reminderRepo: ReminderDataSource
    private lateinit var reminderDtoList: MutableList<ReminderDTO>
    private lateinit var reminderNew: ReminderDTO

    
    // test liveData
    //    @get:Rule
    //    var instantExecutorRule = InstantTaskExecutorRule()

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

        // re-initialize reminderData with a valid data record
        reminderData = ReminderDataItem(
            "test title",
            "test description",
            "test location",
            1.0,
            2.0,
            UUID.randomUUID().toString(),
        )

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

        // new entry to be added to the repo
        reminderNew = ReminderDTO(
            "test title 4",
            "test description 4",
            "test location 4",
            4.0,
            4.0,
            UUID.randomUUID().toString(),
        )

        // get a fresh fake data source (repository)
        reminderRepo = FakeDataSource(reminderDtoList)

        // get a fresh viewModel
        _viewModel = SaveReminderViewModel(
            ApplicationProvider.getApplicationContext(),
            reminderRepo,
        )

    }  // setupTest()


    /* ******************************************************************
     * test suite for: validateEnteredData (private method)
     * ******************************************************************/
    @Test
    fun `validateEnteredData returns false if title is missing`() {

        // GIVEN...
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // WHEN validating the data item with the following 'impairment'
        reminderData.title = null

        // THEN false should be returned
        Assert.assertEquals(false, privateTestFun(_viewModel, reminderData))

    }

    @Test
    fun `validateEnteredData returns false if location is missing`() {

        // GIVEN...
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // WHEN validating the data item with the following 'impairment'
        reminderData.location = ""

        // THEN false should be returned
        Assert.assertEquals(false, privateTestFun(_viewModel, reminderData))

    }

    @Test
    fun `validateEnteredData returns false if both title and location are missing`() {

        // GIVEN...
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // WHEN validating the data item with the following 'impairment'
        reminderData.title = null
        reminderData.location = ""

        // THEN false should be returned
        Assert.assertEquals(false, privateTestFun(_viewModel, reminderData))

    }

    @Test
    fun `validateEnteredData returns true if neither title nor location are missing`() {

        // GIVEN...
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // WHEN validating the data item with the following 'impairment'
        // -- none --

        // THEN true should be returned
        Assert.assertEquals(true, privateTestFun(_viewModel, reminderData))

    }

    /* ******************************************************
     * combined test - validate and save reminder
     * ******************************************************/
    @Test
    fun `validateAndSaveReminder stores valid reminder in repository`()  =
        mainCoroutineRule.runBlockingTest {

        // GIVEN...
        // ... some VALID reminder

        // WHEN calling function validateAndSaveReminder
        _viewModel.validateAndSaveReminder(reminderData)
        val reminderReadBack = reminderRepo.getReminder(reminderData.id) as Result.Success

        // THEN the reminder is verified and stored in the repository
        assertThat(reminderReadBack.data.title, IsEqual(reminderData.title))
        assertThat(reminderReadBack.data.description, IsEqual(reminderData.description))
        assertThat(reminderReadBack.data.location, IsEqual(reminderData.location))
        assertThat(reminderReadBack.data.latitude, IsEqual(reminderData.latitude))
        assertThat(reminderReadBack.data.longitude, IsEqual(reminderData.longitude))
        assertThat(reminderReadBack.data.id, IsEqual(reminderData.id))

    }

    @Test
    fun `validateAndSaveReminder refused to store invalid reminder in repository`()  =
        mainCoroutineRule.runBlockingTest {

        // GIVEN...
        // ... some INVALID reminder
        reminderData.title = null

        // WHEN calling function validateAndSaveReminder
        _viewModel.validateAndSaveReminder(reminderData)
        val reminderReadBack = reminderRepo.getReminder(reminderData.id) as Result.Error

        // THEN the reminder is verified and stored in the repository
        assertThat(reminderReadBack.message, IsEqual("Reminder with ID ${reminderData.id} not found in (fake) local storage."))

    }

    /* ******************************************************
     * liveData testing
     * ******************************************************/

    // LiveData: snackBarInt
    @Test
    fun `validateEnteredData triggers single event showSnackBarInt when title is missing`() {

        // GIVEN...
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // WHEN...
        // ... validating the data item with the following 'impairment' and after triggering the
        //     method to be tested (private --> indirection via reflected method for access)
        reminderData.title = ""
        privateTestFun(_viewModel, reminderData)

        // THEN the snackbar event (showSnackBarInt) should be triggered
        //
        // --> use LiveData extension function 'getOrAwaitValue' to fetch LiveData value of
        //     SingleEvent 'showSnackBarInt' or return with an error after 2 seconds (timeout)
        // --> using assertThat from hamcrest library directly (as org.junit.* 'indirection' has
        //     been deprecated
        assertThat(_viewModel.showSnackBarInt.getOrAwaitValue(), equalTo(R.string.err_enter_title))

    }

    // LiveData: snackBarInt
    @Test
    fun `validateEnteredData triggers single event showSnackBarInt when location is missing`() {

        // GIVEN...
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // WHEN...
        // ... validating the data item with the following 'impairment' and after triggering the
        //     method to be tested (private --> indirection via reflected method for access)
        reminderData.location = ""
        privateTestFun(_viewModel, reminderData)

        // THEN the snackbar event (showSnackBarInt) should be triggered
        //
        // --> use LiveData extension function 'getOrAwaitValue' to fetch LiveData value of
        //     SingleEvent 'showSnackBarInt' or return with an error after 2 seconds (timeout)
        // --> using assertThat from hamcrest library directly (as org.junit.* 'indirection' has
        //     been deprecated
        assertThat(_viewModel.showSnackBarInt.getOrAwaitValue(), equalTo(R.string.err_select_location))

    }

    // LiveData: reminderTitle
    @Test
    fun `setting value in MutableLiveData reminderTitle triggers LiveData observer`() {

        // WHEN...
        // ... setting the value of the LiveData element to be tested
        _viewModel.reminderTitle.value = "test"

        // THEN the associated LiveData observer should be triggered
        assertThat(_viewModel.reminderTitle.getOrAwaitValue(), equalTo("test"))

    }

    // LiveData: reminderDescription
    @Test
    fun `setting value in MutableLiveData reminderDescription triggers LiveData observer`() {

        // WHEN...
        // ... setting the value of the LiveData element to be tested
        _viewModel.reminderDescription.value = "test"

        // THEN the associated LiveData observer should be triggered
        assertThat(_viewModel.reminderDescription.getOrAwaitValue(), equalTo("test"))

    }

    // LiveData: reminderSelectedLocationStr
    @Test
    fun `setting value in MutableLiveData reminderSelectedLocationStr triggers LiveData observer`() {

        // WHEN...
        // ... setting the value of the LiveData element to be tested
        _viewModel.reminderSelectedLocationStr.value = "test"

        // THEN the associated LiveData observer should be triggered
        assertThat(_viewModel.reminderSelectedLocationStr.getOrAwaitValue(), equalTo("test"))

    }

    // LiveData: latitude
    @Test
    fun `setting value in MutableLiveData latitude triggers LiveData observer`() {

        // WHEN...
        // ... setting the value of the LiveData element to be tested
        _viewModel.latitude.value = 1.0

        // THEN the associated LiveData observer should be triggered
        assertThat(_viewModel.latitude.getOrAwaitValue(), equalTo(1.0))

    }

    // LiveData: longitude
    @Test
    fun `setting value in MutableLiveData longitude triggers LiveData observer`() {

        // WHEN...
        // ... setting the value of the LiveData element to be tested
        _viewModel.longitude.value = 1.0

        // THEN the associated LiveData observer should be triggered
        assertThat(_viewModel.longitude.getOrAwaitValue(), equalTo(1.0))

    }

    // LiveData: onClear
    @Test
    fun `calling onClear triggers all LiveData observers and sets values to null`() {

        // WHEN...
        // ... all LiveData element are set to non-null values and
        _viewModel.reminderTitle.value = "test"
        _viewModel.reminderDescription.value = "test"
        _viewModel.reminderSelectedLocationStr.value = "test"
        _viewModel.latitude.value = 1.0
        _viewModel.longitude.value = 1.0

        // ... onClear is called
        _viewModel.onClear()

        // THEN all associated LiveData observers should be triggered and the values should be null
        assertThat(_viewModel.reminderTitle.getOrAwaitValue(), equalTo(null))
        assertThat(_viewModel.reminderDescription.getOrAwaitValue(), equalTo(null))
        assertThat(_viewModel.reminderSelectedLocationStr.getOrAwaitValue(), equalTo(null))
        assertThat(_viewModel.latitude.getOrAwaitValue(), equalTo(null))
        assertThat(_viewModel.longitude.getOrAwaitValue(), equalTo(null))

    }


    // test repository ------------------------------------------------------------

    // getReminders
    @Test
    fun `getReminders requests all reminders from local data source`() =
        mainCoroutineRule.runBlockingTest {

        // WHEN reminders are requested from the repository / location reminder repository
        val reminders = reminderRepo.getReminders() as Result.Success

        // THEN reminders are loaded from the local data source
        assertThat(reminders.data, IsEqual(reminderDtoList))

    }

    // getReminder --> Result.Success
    @Test
    fun `getReminder requests existing reminder from repository`() =
        mainCoroutineRule.runBlockingTest {

        // WHEN an existent reminder is requested from the location reminder repository
        val reminder = reminderRepo.getReminder(reminderDtoList.first().id) as Result.Success

        // THEN this reminder is loaded from the repository / location reminder repository
        assertThat(reminder.data, IsEqual(reminderDtoList.first()))

    }

    // getReminder --> Result.Error
    @Test
    fun `getReminder requests non-existing reminder from repository`() =
        mainCoroutineRule.runBlockingTest {

        // WHEN a non-existent reminder is requested from the location reminder repository
        val fakeId = "this is a fake ID"
        val noReminder = reminderRepo.getReminder(fakeId) as Result.Error

        // THEN the return value is an error message
        assertThat(noReminder.message, IsEqual("Reminder with ID $fakeId not found in (fake) local storage."))

    }

    // saveReminder
    @Test
    fun `saveReminder writes new reminder to repository`() =
        mainCoroutineRule.runBlockingTest {

        // WHEN a new reminder is added to the location reminder repository
        reminderRepo.saveReminder(reminderNew)
        val reminderReadBack = reminderRepo.getReminder(reminderNew.id) as Result.Success

        // THEN this reminder is stored in the repository
        assertThat(reminderReadBack.data, IsEqual(reminderNew))

    }

    // deleteAllReminders
    @Test
    fun `deleteAllReminders deletes all reminders from repository`() =
        mainCoroutineRule.runBlockingTest {

        // WHEN all reminders are deleted from the location reminder repository
        reminderRepo.deleteAllReminders()

        // THEN the repository is empty
        val reminderReadBack = reminderRepo.getReminders() as Result.Success
        assertThat(reminderReadBack.data, Is(empty()))

    }

}