package com.sameer.ondeviceai.llmchat

import android.content.Context
import java.io.File

/** A .litertlm model the chat can run. */
data class ModelSpec(
    val displayName: String,
    val fileName: String,
    /** Null for a file you copied in yourself; those can't be downloaded from the app. */
    val downloadUrl: String?,
    val approxSizeGb: Double,
    val recommendedRamGb: Int?,
) {
    fun file(context: Context): File = File(modelsDir(context), fileName)

    companion object {
        /** Gemma 4 E2B supports tool calling; Google's AI Edge Gallery uses it for its agent chat. */
        val Gemma4E2B = ModelSpec(
            displayName = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            approxSizeGb = 2.6,
            recommendedRamGb = 8,
        )

        val Qwen34B = ModelSpec(
            displayName = "Qwen3 4B",
            fileName = "qwen3_4b_mixed_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm?download=true",
            approxSizeGb = 2.6,
            recommendedRamGb = 8,
        )

        private val catalog = listOf(Gemma4E2B, Qwen34B)

        /**
         * Where models live on the phone. This is the app's external files folder, so you can copy
         * any .litertlm model in yourself and it shows up in the chat's model picker:
         * adb push <file> /sdcard/Android/data/com.sameer.ondeviceai/files/models/
         */
        fun modelsDir(context: Context): File =
            (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).also { it.mkdirs() }

        /** The built-in models plus every other .litertlm file found in [modelsDir]. */
        fun available(context: Context): List<ModelSpec> {
            val known = catalog.map { it.fileName }.toSet()
            val copiedIn = modelsDir(context)
                .listFiles { file -> file.isFile && file.name.endsWith(".litertlm") && file.name !in known }
                .orEmpty()
                .sortedBy { it.name.lowercase() }
                .map { file ->
                    ModelSpec(
                        displayName = file.nameWithoutExtension,
                        fileName = file.name,
                        downloadUrl = null,
                        approxSizeGb = file.length() / 1e9,
                        recommendedRamGb = null,
                    )
                }
            return catalog + copiedIn
        }
    }
}
