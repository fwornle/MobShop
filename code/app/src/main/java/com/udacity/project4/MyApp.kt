package com.udacity.project4

import android.app.Application
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // initialize Timber (logging) lib
        Timber.plant(Timber.DebugTree())

        /**
         * use Koin Library as a service locator
         */
        val myModule = module {

            // declare a ViewModel - to be injected into Fragment with dedicated injector using
            // "by viewModel()"
            //
            // class RemindersListViewModel(
            //    code: Application,
            //    private val dataSource: ReminderDataSource
            // ) : BaseViewModel(code) { ... }
            viewModel {
                RemindersListViewModel(
                    get(),  // code (context)
                    get() as ReminderDataSource  // repo as data source
                )
            }

            // declare a ViewModel - to be injected into Fragment with standard injector using
            // "by inject()"
            // --> this view model is declared singleton to be used across multiple fragments
            //
            // class SaveReminderViewModel(
            //    val code: Application,
            //    val dataSource: ReminderDataSource
            // ) : BaseViewModel(code) { ... }
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
            single { LocalDB.createRemindersDao(this@MyApp) }
        }

        // instantiate viewModels, repos and DBs and inject them as services into consuming classes
        // ... using KOIN framework (as "service locator"): https://insert-koin.io/
        startKoin {
            androidContext(this@MyApp)
            modules(listOf(myModule))
        }
    }
}