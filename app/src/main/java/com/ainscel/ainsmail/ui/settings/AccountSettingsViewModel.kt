package com.ainscel.ainsmail.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.AccountDirectory
import com.ainscel.ainsmail.security.AccountError
import com.ainscel.ainsmail.security.AccountWrite
import com.ainscel.ainsmail.security.readImageBytes
import com.ainscel.ainsmail.security.toAccountError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class AccountSettingsUiState(
    val loaded: Boolean = false,
    val unreadable: Boolean = false,
    val failure: AccountError? = null,
)

class AccountSettingsViewModel(
    private val appContext: Context,
    private val book: AccountBook,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    var state by mutableStateOf(AccountSettingsUiState())
        private set

    init {
        showDirectory(book.directory.value)
        viewModelScope.launch {
            book.directory.collect(::showDirectory)
        }
    }

    private fun showDirectory(directory: AccountDirectory) {
        state = when (directory) {
            AccountDirectory.Loading -> state
            AccountDirectory.Unreadable ->
                if (state.loaded && state.unreadable) state
                else state.copy(loaded = true, unreadable = true)
            is AccountDirectory.Ready ->
                if (state.loaded && !state.unreadable) state
                else state.copy(loaded = true, unreadable = false)
        }
    }

    fun rename(id: String, displayName: String) {
        viewModelScope.launch {
            val written = withContext(io) { book.rename(id, displayName) }
            publish(written)
        }
    }

    fun replaceAvatar(id: String, uri: Uri) {
        viewModelScope.launch {
            val written = withContext(io) {
                val bytes = appContext.readImageBytes(uri) ?: return@withContext AccountWrite.BadImage
                book.replaceAvatar(id, bytes)
            }
            publish(written)
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            val written = withContext(io) { book.remove(id) }
            publish(written)
        }
    }

    private fun publish(written: AccountWrite) {
        state = state.copy(failure = written.toAccountError())
    }

    companion object {
        fun factory(context: Context, book: AccountBook): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AccountSettingsViewModel(appContext, book, Dispatchers.IO) as T
                }
            }
        }
    }
}
