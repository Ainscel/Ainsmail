package com.ainscel.ainsmail.ui.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ainscel.ainsmail.security.SecretRead
import com.ainscel.ainsmail.security.SecretStore
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class SecretsUiState(
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val apiKey: String = "",
    val model: String = SecretStore.DEFAULT_MODEL,
    val clientId: String = "",
    val clientSecret: String = "",
    val revealKey: Boolean = false,
    val revealClientSecret: Boolean = false,
    val savedTick: Long = 0,
    val errorTick: Long = 0,
)

class SecretsViewModel(
    private val store: SecretStore,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    var state by mutableStateOf(SecretsUiState())
        private set

    init {
        viewModelScope.launch {
            val read = withContext(io) { store.read() }
            state = when (read) {
                is SecretRead.Ok -> state.copy(
                    loaded = true,
                    apiKey = read.secrets.deepSeekApiKey,
                    model = read.secrets.deepSeekModel,
                    clientId = read.secrets.googleClientId,
                    clientSecret = read.secrets.googleClientSecret,
                )
                SecretRead.Missing -> state.copy(loaded = true)
                is SecretRead.Unreadable -> state.copy(loaded = true, loadFailed = true)
            }
        }
    }

    fun onEdit(edit: SecretsEdit) {
        when (edit) {
            is SecretsEdit.ApiKey -> state = state.copy(apiKey = edit.value)
            is SecretsEdit.Model -> state = state.copy(model = edit.value)
            is SecretsEdit.ClientId -> state = state.copy(clientId = edit.value)
            is SecretsEdit.ClientSecret -> state = state.copy(clientSecret = edit.value)
            SecretsEdit.ToggleKey -> state = state.copy(revealKey = !state.revealKey)
            SecretsEdit.ToggleClientSecret -> state = state.copy(revealClientSecret = !state.revealClientSecret)
            SecretsEdit.Save -> save()
        }
    }

    private fun save() {
        val current = state
        viewModelScope.launch {
            val read = withContext(io) {
                try {
                    store.write(
                        SecretStore.Secrets(
                            deepSeekApiKey = current.apiKey,
                            deepSeekModel = current.model,
                            googleClientId = current.clientId,
                            googleClientSecret = current.clientSecret,
                        ),
                    )
                    store.read()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    SecretRead.Unreadable(error)
                }
            }
            state = when (read) {
                is SecretRead.Ok -> state.copy(
                    loaded = true,
                    loadFailed = false,
                    apiKey = read.secrets.deepSeekApiKey,
                    model = read.secrets.deepSeekModel,
                    clientId = read.secrets.googleClientId,
                    clientSecret = read.secrets.googleClientSecret,
                    savedTick = state.savedTick + 1,
                )
                SecretRead.Missing -> state.copy(loadFailed = true, errorTick = state.errorTick + 1)
                is SecretRead.Unreadable -> state.copy(loadFailed = true, errorTick = state.errorTick + 1)
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SecretsViewModel(SecretStore(appContext), Dispatchers.IO) as T
                }
            }
        }
    }
}

sealed interface SecretsEdit {
    data class ApiKey(val value: String) : SecretsEdit
    data class Model(val value: String) : SecretsEdit
    data class ClientId(val value: String) : SecretsEdit
    data class ClientSecret(val value: String) : SecretsEdit
    data object ToggleKey : SecretsEdit
    data object ToggleClientSecret : SecretsEdit
    data object Save : SecretsEdit
}
