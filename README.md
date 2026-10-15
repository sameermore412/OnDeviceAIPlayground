# On-Device AI Playground

An Android app (Kotlin + Jetpack Compose) for trying on-device AI models on mid-range phones.
Each experiment is its own screen, listed on the home screen alongside the phone's RAM and CPU.

Open the folder in Android Studio and run the `app` configuration. Min Android 12 (API 31).

To add an experiment, add an entry to `experiments/Experiment.kt` and a branch in `ExperimentScreen.kt`.

## Local chat (LiteRT-LM)

Runs Gemma 4 E2B (about 2.6 GB, best with 8 GB of RAM) through LiteRT-LM, on the GPU with a CPU fallback.
The app downloads the model on first use. Any other `.litertlm` model (for example a converted
Qwen3.5 4B) can be copied into the models folder and picked from the menu at the top of the chat:

    adb push <model>.litertlm /sdcard/Android/data/com.sameer.ondeviceai/files/models/

To convert a Hugging Face Qwen3.5 checkpoint, see LiteRT-LM's `models/qwen3_5/README.md` (`litert_torch.generative.export_hf`).

The model can call these in-app tools, defined in `llmchat/AppTools.kt`:
`getDeviceStatus`, `getCurrentDateTime`, `setFlashlight`, `setAppTheme`, `addNote`, `listNotes`, `webSearch`.

`webSearch` uses Brave Search if you add `brave.api.key=<key>` to `local.properties`
(free tier at https://brave.com/search/api/); without a key it searches Wikipedia.
To add a tool, add a method with `@Tool` and `@ToolParam` annotations there.
