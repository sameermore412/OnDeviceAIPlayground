package com.sameer.ondeviceai.llmchat

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : ModelState
    data object Loading : ModelState
    data class Ready(val backend: String) : ModelState
    data class Failed(val message: String) : ModelState
}

sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem
    data class Model(override val id: Long, val text: String) : ChatItem
    data class Tool(override val id: Long, val call: ToolCallEvent) : ChatItem
    data class Error(override val id: Long, val message: String) : ChatItem
}

/** Speed of the last reply. Each streamed chunk is roughly one token. */
data class ReplyStats(val timeToFirstTokenMs: Long, val tokensPerSecond: Double)

/**
 * Owns the model's lifecycle (download, load, close) and the chat. Scoped to the activity, so the
 * model stays loaded and a download keeps going when you go back to the home screen.
 */
class LlmChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _models = MutableStateFlow(ModelSpec.available(application))
    val models: StateFlow<List<ModelSpec>> = _models.asStateFlow()

    private val _selected = MutableStateFlow(_models.value.first())
    val selected: StateFlow<ModelSpec> = _selected.asStateFlow()

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastStats = MutableStateFlow<ReplyStats?>(null)
    val lastStats: StateFlow<ReplyStats?> = _lastStats.asStateFlow()

    private var session: LlmSession? = null
    private var downloadJob: Job? = null
    private var nextId = 0L

    init {
        showSelected()
    }

    /** Re-reads the models folder, e.g. after you copy a new .litertlm file in. */
    fun refreshModels() {
        _models.value = ModelSpec.available(getApplication())
    }

    /** Switches to [model]: frees the current one (only one fits in memory) and loads the new one. */
    fun select(model: ModelSpec) {
        // A load can't be interrupted part way, so wait for it before switching.
        if (model == _selected.value || _isGenerating.value || _modelState.value == ModelState.Loading) return
        downloadJob?.cancel()
        session?.close()
        session = null
        _items.value = emptyList()
        _lastStats.value = null
        _selected.value = model
        showSelected()
    }

    private fun showSelected() {
        if (_selected.value.file(getApplication()).exists()) load()
        else _modelState.value = ModelState.NotDownloaded
    }

    fun download() {
        val model = _selected.value
        val url = model.downloadUrl ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _modelState.value = ModelState.Downloading(0, null)
            try {
                ModelDownloader.download(url, model.file(getApplication())) { downloaded, total ->
                    _modelState.value = ModelState.Downloading(downloaded, total)
                }
                load()
            } catch (e: CancellationException) {
                _modelState.value = ModelState.NotDownloaded
                throw e
            } catch (e: Exception) {
                _modelState.value = ModelState.Failed(e.message ?: "Download failed")
            }
        }
    }

    /** Stops the download; the partial file is kept, so the next download resumes. */
    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun retry() {
        if (_selected.value.file(getApplication()).exists()) load() else download()
    }

    private fun load() {
        val file = _selected.value.file(getApplication())
        viewModelScope.launch {
            _modelState.value = ModelState.Loading
            try {
                val opened = LlmSession.open(getApplication(), file)
                session = opened
                _modelState.value = ModelState.Ready(opened.backendName)
            } catch (e: Exception) {
                _modelState.value = ModelState.Failed(e.message ?: "Couldn't load the model")
            }
        }
    }

    fun send(text: String) {
        val current = session ?: return
        val prompt = text.trim()
        if (prompt.isEmpty() || _isGenerating.value) return

        append(ChatItem.User(newId(), prompt))
        _isGenerating.value = true
        viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            var firstToken = 0L
            var chunks = 0
            var replyId: Long? = null // The model bubble that text chunks currently go into.
            try {
                current.send(prompt).collect { event ->
                    when (event) {
                        is ReplyEvent.Text -> {
                            if (chunks++ == 0) firstToken = SystemClock.elapsedRealtime()
                            val id = replyId ?: newId().also {
                                replyId = it
                                append(ChatItem.Model(it, ""))
                            }
                            _items.update { list ->
                                list.map { if (it is ChatItem.Model && it.id == id) it.copy(text = it.text + event.text) else it }
                            }
                        }
                        is ReplyEvent.ToolCalled -> {
                            append(ChatItem.Tool(newId(), event.call))
                            replyId = null // Text after a tool call starts a new bubble.
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                append(ChatItem.Error(newId(), e.message ?: "Something went wrong"))
            } finally {
                _isGenerating.value = false
                if (chunks > 1) {
                    val seconds = (SystemClock.elapsedRealtime() - firstToken) / 1000.0
                    _lastStats.value = ReplyStats(
                        timeToFirstTokenMs = firstToken - start,
                        tokensPerSecond = if (seconds > 0) (chunks - 1) / seconds else 0.0,
                    )
                }
            }
        }
    }

    fun stop() {
        session?.stop()
    }

    fun newChat() {
        if (_isGenerating.value) return
        session?.reset()
        _items.value = emptyList()
        _lastStats.value = null
    }

    private fun append(item: ChatItem) = _items.update { it + item }

    private fun newId() = nextId++

    override fun onCleared() {
        session?.close()
        session = null
    }
}
