package com.udacity.project4.locationreminders.savereminder

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
class SaveReminderViewModelTest: AutoCloseKoinTest() {

    // declare globally used variables
    private lateinit var reminderData: ReminderDataItem
    private lateinit var privateTestFun: Method

    // viewModel from Koin service locator
    private lateinit var _viewModel: SaveReminderViewModel

    // reminder repository and fake data
    private lateinit var reminderRepo: ReminderDataSource
    private lateinit var reminderDtoList: MutableList<ReminderDTO>
    private lateinit var reminderNew: ReminderDTO

    // avoid re-curring (re)-initializing of the Koin, if possible
    //
    // ... done this way to avoid hassle with @BeforeClass (needs to be static --> @JvmStatic +
    //     encapsulation in companion object... but cannot be static, because we are using Koin
    //     service locator to resolve dependencies)
    //     see: https://stackoverflow.com/questions/32952884/junit-beforeclass-non-static-work-around-for-spring-boot-application
    private var testInitialized = false

    // test liveData
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    // run before each individual test
    @Before
    fun setupClass() {

        // run ONCE, ab BEGINNING of test suite  ----------------------------------------
        if (testInitialized) {

            // stop the original app koin, which is launched when the application starts (in "MyApp")
            stopKoin()

            /**
             * use Koin Library as a service locator
             */
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
                single {
                    SaveReminderViewModel(
                        get(),
                        get() as ReminderDataSource
                    )
                }

                // ReminderDataSource
                //
                // declare a (singleton) repository service with interface "ReminderDataSource"
                // note: the repo needs the DAO of the room database (RemindersDao)
                //       ... which is why it is declared (below) as singleton object and injected
                //           using "get()" in the lambda of the declaration
                single<ReminderDataSource> { RemindersLocalRepository(get()) }

                // RemindersDao
                //
                // declare the local DB singleton object - used as data source for the repository
                // note: LocalDB.createRemindersDao returns a DAO with interface RemindersDao
                //       ... the DAO is needed by the repo (where it is injected, see "get()", above)
                single { LocalDB.createRemindersDao(ApplicationProvider.getApplicationContext()) }
            }

            // instantiate viewModels, repos and DBs and inject them as services into consuming classes
            // ... using KOIN framework (as "service locator"): https://insert-koin.io/
            startKoin {
                androidContext(ApplicationProvider.getApplicationContext())
                modules(listOf(myModule))
            }

            // Koin now initialized --> service provision via Koin now possible
            // ... set flag to avoid unnecessary re-initializations
            testInitialized = true

        }  // if(testInitialized)


        // run BEFORE EACH individual test ----------------------------------------

        // "inject" viewModel dependency (to obtain a freshly initialized VM)
        _viewModel = inject<SaveReminderViewModel>().value

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

        // define a 'fresh fake repository' for each test
        reminderRepo = FakeDataSource(reminderDtoList)

        // re-initialize reminderData with a valid data record
        reminderData = ReminderDataItem(
            "test title",
            "test description",
            "test location",
            1.0,
            2.0,
        )

    }  // setupClass()

    /* ******************************************************************
     * test suite for: validateEnteredData (private method)
     * ******************************************************************/
    @Test
    fun `validateEnteredData returns false if title is missing`() {

        // given...
        // ... access to the viewModel (injected from Koin module) and
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // when validating the data item with the following 'impairment'
        reminderData.title = null

        // then false should be returned
        Assert.assertEquals(false, privateTestFun(_viewModel, reminderData))

    }

    @Test
    fun `validateEnteredData returns false if location is missing`() {

        // given...
        // ... access to the viewModel (injected from Koin module) and
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // when validating the data item with the following 'impairment'
        reminderData.location = ""

        // then false should be returned
        Assert.assertEquals(false, privateTestFun(_viewModel, reminderData))

    }

    @Test
    fun `validateEnteredData returns false if both title and location are missing`() {

        // given...
        // ... access to the viewModel (injected from Koin module) and
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // when validating the data item with the following 'impairment'
        reminderData.title = null
        reminderData.location = ""

        // then false should be returned
        Assert.assertEquals(false, privateTestFun(_viewModel, reminderData))

    }

    @Test
    fun `validateEnteredData returns true if neither title nor location are missing`() {

        // given...
        // ... access to the viewModel (injected from Koin module) and
        // ... access to the PRIVATE method to be tested via REFLECTION (see:
        //     https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd)
        privateTestFun = _viewModel.javaClass
            .getDeclaredMethod("validateEnteredData", reminderData.javaClass)
            .apply { isAccessible = true }

        // when validating the data item with the following 'impairment'
        // -- none --

        // then true should be returned
        Assert.assertEquals(true, privateTestFun(_viewModel, reminderData))

    }

    // LiveData: snackBarInt
    @Test
    fun `validateEnteredData triggers single event showSnackBarInt when title is missing`() {

        // GIVEN...
        // ... access to the viewModel (injected from Koin module) and
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
        // ... access to the viewModel (injected from Koin module) and
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


    // test repository ------------------------------------------------------------

    // getReminders
    @ExperimentalCoroutinesApi
    @Test
    fun `getReminders requests all reminders from local data source`() = runBlockingTest {

        // WHEN reminders are requested from the repository / location reminder repository
        val reminders = reminderRepo.getReminders() as Result.Success

        // THEN reminders are loaded from the local data source
        assertThat(reminders.data, IsEqual(reminderDtoList))

    }

    // getReminder --> Result.Success
    @ExperimentalCoroutinesApi
    @Test
    fun `getReminder requests existing reminder from repository`() = runBlockingTest {

        // WHEN an existent reminder is requested from the location reminder repository
        val reminder = reminderRepo.getReminder(reminderDtoList.first().id) as Result.Success

        // THEN this reminder is loaded from the repository / location reminder repository
        assertThat(reminder.data, IsEqual(reminderDtoList.first()))

    }

    // getReminder --> Result.Error
    @ExperimentalCoroutinesApi
    @Test
    fun `getReminder requests non-existing reminder from repository`() = runBlockingTest {

        // WHEN a non-existent reminder is requested from the location reminder repository
        val fakeId = "this is a fake ID"
        val noReminder = reminderRepo.getReminder(fakeId) as Result.Error

        // THEN the return value is an error message
        assertThat(noReminder.message, IsEqual("Reminder with ID $fakeId not found in (fake) local storage."))

    }

    // saveReminder
    @ExperimentalCoroutinesApi
    @Test
    fun `saveReminder writes new reminder to repository`() = runBlockingTest {

        // WHEN a new reminder is added to the location reminder repository
        reminderRepo.saveReminder(reminderNew)
        val reminderReadBack = reminderRepo.getReminder(reminderNew.id) as Result.Success

        // THEN this reminder is stored in the repository
        assertThat(reminderReadBack.data, IsEqual(reminderNew))

    }

    // deleteAllReminders
    @ExperimentalCoroutinesApi
    @Test
    fun `deleteAllReminders deletes all reminders from repository`() = runBlockingTest {

        // WHEN all reminders are deleted from the location reminder repository
        reminderRepo.deleteAllReminders()

        // THEN the repository is empty
        val reminderReadBack = reminderRepo.getReminders() as Result.Success
        assertThat(reminderReadBack.data, Is(empty()))

    }

}