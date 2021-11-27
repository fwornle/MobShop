package com.udacity.project4.locationreminders.savereminder

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SaveReminderViewModelTest: KoinTest {

    @Before
    fun setup() {

        // stop the original app koin, which is launched when the application starts (in "MyApp")
        stopKoin()

        /**
         * use Koin Library as a service locator
         */
        val myModule = module {

            // declare a ViewModel - to be injected into Fragment with dedicated injector using
            // "by viewModel()"
            //
            // class RemindersListViewModel(
            //    app: Application,
            //    private val dataSource: ReminderDataSource
            // ) : BaseViewModel(app) { ... }
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

            // declare a (singleton) repository service with interface "ReminderDataSource"
            // note: the repo needs the DAO of the room database (RemindersDao)
            //       ... which is why it is declared (below) as singleton object and injected
            //           using "get()" in the lambda of the declaration
            //
            // class RemindersLocalRepository(
            //    private val remindersDao: RemindersDao,
            //    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
            //) : ReminderDataSource { ... }
            single<ReminderDataSource> { RemindersLocalRepository(get()) }

            // declare the local DB singleton object - used as data source for the repository
            // note: LocalDB.createRemindersDao returns a DAO with interface RemindersDao
            //       ... the DAO is needed by the repo (where it is injected, see "get()", above)
            //
            // object LocalDB {
            //
            //    /**
            //     * static method that creates a reminder class and returns the DAO of the reminder
            //     */
            //    fun createRemindersDao(context: Context): RemindersDao {
            //        return Room.databaseBuilder(
            //            context.applicationContext,
            //            RemindersDatabase::class.java, "locationReminders.db"
            //        ).build().reminderDao()
            //    }
            single { LocalDB.createRemindersDao(ApplicationProvider.getApplicationContext()) }
        }

        // instantiate viewModels, repos and DBs and inject them as services into consuming classes
        // ... using KOIN framework (as "service locator"): https://insert-koin.io/
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(listOf(myModule))
        }

    }  // setup()

    @Test
    fun validateEnteredData_ensuresTitleAndDescriptionNotEmpty() {

        // given a viewModel and some reminder data item with empty title and empty description
        val _viewModel: SaveReminderViewModel by inject()
        val reminderData = ReminderDataItem(
            "",
            "",
            "",
            1.0,
            2.0,
        )

        // when validating the data item
        val result = _viewModel.validateAndSaveReminder(reminderData)

        // then false should be returned
        Assert.assertEquals(false, result)
    }

    @After
    fun stopKoinAfterTest() = stopKoin()

}