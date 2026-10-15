package com.sameer.ondeviceai.llmchat

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** What streams out of one reply: text chunks, with tool calls in the order they happened. */
sealed interface ReplyEvent {
    data class Text(val text: String) : ReplyEvent
    data class ToolCalled(val call: ToolCallEvent) : ReplyEvent
}

/** A loaded model plus the current conversation with it. */
class LlmSession private constructor(
    private val engine: Engine,
    private val tools: AppTools,
    val backendName: String,
) : AutoCloseable {

    private var conversation: Conversation = newConversation()

    private fun newConversation(): Conversation = engine.createConversation(
        ConversationConfig(
            systemInstruction = Contents.of(SYSTEM_PROMPT),
            tools = listOf(tool(tools)),
            // No samplerConfig: each .litertlm file carries its model's recommended sampling settings.
        ),
    )

    /**
     * Sends [text] and streams the reply. LiteRT-LM runs any tool calls itself and feeds the
     * results back to the model. Text chunks and tool calls both arrive on LiteRT-LM's worker
     * thread, so sending both into one channel keeps them in order.
     */
    fun send(text: String): Flow<ReplyEvent> = callbackFlow {
        val finished = AtomicBoolean(false)
        tools.listener = { trySend(ReplyEvent.ToolCalled(it)) }
        conversation.sendMessageAsync(
            text,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val chunk = message.toString()
                    if (chunk.isNotEmpty()) trySend(ReplyEvent.Text(chunk))
                }

                override fun onDone() {
                    finished.set(true)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    finished.set(true)
                    // Stop button: cancelProcess() ends the reply with a CancellationException.
                    if (throwable is CancellationException) close() else close(throwable)
                }
            },
            // Gemma 4 and Qwen3.5 can reason silently before answering. Off for faster replies.
            extraContext = mapOf("enable_thinking" to false),
        )
        awaitClose {
            tools.listener = null
            if (!finished.get()) runCatching { conversation.cancelProcess() }
        }
    }.buffer(Channel.UNLIMITED)

    /** Stops the reply that is streaming, if any. */
    fun stop() {
        runCatching { conversation.cancelProcess() }
    }

    /** Forgets the chat history and starts a fresh conversation with the same loaded model. */
    fun reset() {
        conversation.close()
        conversation = newConversation()
    }

    override fun close() {
        runCatching { conversation.close() }
        engine.close()
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a helpful assistant running entirely on the user's phone, inside the " +
                "On-Device AI Playground app. You can call tools to read the phone's status, " +
                "turn the flashlight on or off, change the app's theme, save or list notes, and search " +
                "the web. Search the web for current events or facts you aren't sure of, and say which " +
                "source you used. Call a tool when the request needs one; otherwise answer directly. " +
                "Keep answers short."

        /**
         * Loads the model. Tries the GPU first and falls back to the CPU, since GPU support varies
         * across mid-range phones. Loading takes several seconds, so it runs off the main thread.
         */
        suspend fun open(context: Context, modelFile: File): LlmSession = withContext(Dispatchers.IO) {
            val tools = AppTools(context.applicationContext)
            var lastError: Throwable? = null
            for ((name, backend) in listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU())) {
                val engine = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        // Shorter context than the model's maximum, to fit mid-range RAM.
                        maxNumTokens = 4096,
                        // Lets LiteRT-LM cache compiled artifacts so the next load is faster.
                        cacheDir = context.cacheDir.absolutePath,
                    ),
                )
                try {
                    engine.initialize()
                    return@withContext LlmSession(engine, tools, name)
                } catch (e: Exception) {
                    runCatching { engine.close() }
                    lastError = e
                }
            }
            throw IllegalStateException("Couldn't load the model: ${lastError?.message}", lastError)
        }
    }
}
