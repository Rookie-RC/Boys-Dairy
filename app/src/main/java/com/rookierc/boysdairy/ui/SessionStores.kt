package com.rookierc.boysdairy.ui

import android.app.Application
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Holds the [ViewModelStore] of the current "unlock session".
 *
 * Everything inside the unlocked UI (navigation, every screen ViewModel, an unsaved Add form, an
 * AI result on screen) is created from this store and thrown away by [end] the moment the app
 * locks, so no stale state is visible after the next unlock.
 *
 * The holder itself is an Activity-scoped ViewModel on purpose: it survives configuration changes
 * (rotation, language switch), so only a real lock - not a rotation - resets the screens.
 */
class SessionStoreHolder : ViewModel() {

    var store: ViewModelStore = ViewModelStore()
        private set

    /** Destroys all ViewModels of the finished session and prepares a fresh store. */
    fun end() {
        store.clear()
        store = ViewModelStore()
    }

    override fun onCleared() {
        store.clear()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionStoreHolder() }
        }
    }
}

/**
 * A [ViewModelStoreOwner] backed by a session store. It also provides the creation extras that the
 * app's ViewModel factories need (`APPLICATION_KEY` for `appContainer()`), because a plain
 * [ViewModelStoreOwner] would hand them `CreationExtras.Empty`.
 */
internal class SessionViewModelStoreOwner(
    override val viewModelStore: ViewModelStore,
    private val application: Application,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        }
}
