package com.rookierc.boysdairy

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.rookierc.boysdairy.ai.AiAnalysisService
import com.rookierc.boysdairy.ai.AiProvider
import com.rookierc.boysdairy.ai.ApiKeyStore
import com.rookierc.boysdairy.ai.ClaudeTextGenerator
import com.rookierc.boysdairy.ai.DeepSeekTextGenerator
import com.rookierc.boysdairy.data.AppDatabase
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.data.backup.BackupManager
import com.rookierc.boysdairy.data.settingsDataStore
import com.rookierc.boysdairy.security.DataStorePinLockoutStore
import com.rookierc.boysdairy.security.LockManager
import com.rookierc.boysdairy.security.PinAttemptLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; one instance for the whole process. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val entryRepository: EntryRepository by lazy { EntryRepository(database.entryDao()) }

    val settingsRepository: SettingsRepository = SettingsRepository(appContext.settingsDataStore)

    val lockManager: LockManager = LockManager(
        settings = settingsRepository,
        scope = applicationScope,
        // Wall-clock time + DataStore: the lockout must survive process death and reboots.
        limiter = PinAttemptLimiter(
            store = DataStorePinLockoutStore(appContext.settingsDataStore),
            now = System::currentTimeMillis,
        ),
    )

    val backupManager: BackupManager by lazy {
        BackupManager(appContext.contentResolver, entryRepository)
    }

    val apiKeyStore: ApiKeyStore by lazy { ApiKeyStore(appContext) }

    /** Talks to the network only when analyze() is called on explicit user action. */
    val aiAnalysisService: AiAnalysisService by lazy {
        AiAnalysisService(
            entryRepository = entryRepository,
            settingsRepository = settingsRepository,
            apiKeyStore = apiKeyStore,
            generators = mapOf(
                AiProvider.DEEPSEEK to DeepSeekTextGenerator(),
                AiProvider.CLAUDE to ClaudeTextGenerator(),
            ),
        )
    }
}

class BoysDairyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Lock whenever the whole app leaves the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    container.lockManager.lock()
                }
            },
        )
    }
}

/** Access to the DI container from inside a `viewModelFactory { initializer { ... } }` block. */
fun CreationExtras.appContainer(): AppContainer =
    (this[APPLICATION_KEY] as BoysDairyApp).container
