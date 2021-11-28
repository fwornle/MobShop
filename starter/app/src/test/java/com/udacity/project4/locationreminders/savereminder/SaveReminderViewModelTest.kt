package com.udacity.project4.locationreminders.savereminder

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
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

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SaveReminderViewModelTest: AutoCloseKoinTest() {

    // declare globally used variables
    private lateinit var reminderData: ReminderDataItem
    private lateinit var privateTestFun: Method

    private var testInitialized = false
    private val _viewModel: SaveReminderViewModel by inject()

    // run before every test
    @Before
    fun setupClass() {

        // run once, ab beginning of test suite  ----------------------------------------

        // ... done this way to avoid hassle with @BeforeClass & @JvmStatic when using DI
        //     see: https://stackoverflow.com/questions/32952884/junit-beforeclass-non-static-work-around-for-spring-boot-application
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

                // declare a (singleton) repository service with interface "ReminderDataSource"
                // note: the repo needs the DAO of the room database (RemindersDao)
                //       ... which is why it is declared (below) as singleton object and injected
                //           using "get()" in the lambda of the declaration
                single<ReminderDataSource> { RemindersLocalRepository(get()) }

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

            // now initialized
            testInitialized = true

        }  // if(testInitialized)


        // run before each test ----------------------------------------

        // pre-initialize reminderData (valid)
        reminderData = ReminderDataItem(
            "test title",
            "test description",
            "test location",
            1.0,
            2.0,
        )

    }  // setupClass()


    @Test
    fun `validateEnteredData returns false if title is empty`() {

        // given access to the viewModel and the PRIVATE method to be tested via reflection
        //     see: https://medium.com/mindorks/how-to-unit-test-private-methods-in-java-and-kotlin-d3cae49dccd
        privateTestFun = _viewModel.javaClass.getDeclaredMethod("validateEnteredData", reminderData.javaClass)
        privateTestFun.setAccessible(true)

        // when validating the data item
        reminderData.title = null

        // then false should be returned
        Assert.assertEquals(false, privateTestFun(_viewModel, reminderData))

    }

//    @Test
//    fun `validateEnteredData returns false if location is empty`() {
//
//        // given a viewModel
//        val _viewModel: SaveReminderViewModel by inject()
//
//        // when validating the data item
//        val reminderData = ReminderDataItem(
//            "hello",
//            "irrelevant",
//            "",
//            1.0,
//            2.0,
//        )
//
//        // then false should be returned
//        Assert.assertEquals(false, _viewModel.validateEnteredData(reminderData))
//    }
//
//    @Test
//    fun `validateEnteredData returns true if neither title nor location are empty`() {
//
//        // given a viewModel
//        val _viewModel: SaveReminderViewModel by inject()
//
//        // when validating the data item
//        val reminderData = ReminderDataItem(
//            "hello",
//            "",
//            "mystery",
//            1.0,
//            2.0,
//        )
//
//        // then true should be returned
//        Assert.assertEquals(true, _viewModel.validateEnteredData(reminderData))
//    }

}