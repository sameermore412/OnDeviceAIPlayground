package com.sameer.ondeviceai.experiments

/** One on-device AI task in the playground. Add an entry here to get a card on the home screen. */
enum class Experiment(
    val title: String,
    val description: String,
    val engine: String,
) {
    LlmChat(
        title = "Local chat",
        description = "Chat with Gemma 4 E2B running entirely on the phone. It can call tools in the app: flashlight, theme, notes and device status.",
        engine = "LiteRT-LM",
    ),
    TextRecognition(
        title = "Text recognition",
        description = "Point the camera at printed text and read it out live.",
        engine = "ML Kit",
    ),
    ImageLabeling(
        title = "Image labeling",
        description = "Name the objects in a photo or the camera view.",
        engine = "ML Kit",
    ),
    SmartReply(
        title = "Smart reply",
        description = "Suggest short replies to a chat message.",
        engine = "ML Kit",
    ),
}
