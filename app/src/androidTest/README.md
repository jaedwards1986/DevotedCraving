# JNI device smoke test

The JVM adapter contract test runs with `:app:testDebugUnitTest` and requires no native
library or GGUF file.

The device smoke test is intentionally separate. It loads the actual
`libdavoted_llama.so`, loads a real GGUF model, and sends a raw prompt directly to
`JniLlamaCppRuntime`. It does not use `PromptContextAssembler` or
`LlamaCppAdapter`.

## Prepare the model asset

1. Obtain a small, valid GGUF model compatible with the pinned llama.cpp build.
2. Place it locally at
   `app/src/androidTest/assets/gguf/smoke.gguf`. The GGUF is a test input and must
   not be committed to the repository.
3. Connect the Pixel 9 Pro XL with USB debugging enabled and verify it appears in
   `adb devices`.
4. Run:

   ```bash
   ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.ggufAsset=gguf/smoke.gguf
   ```

The instrumentation test copies the asset into the target application's private
files directory, loads it through JNI, performs greedy generation, checks for
nonempty output, and releases the model in a `finally` block.

A passing JVM test proves adapter-to-runtime contract behavior with a deterministic
fake. A passing device test proves the packaged native library can load the supplied
GGUF and return generated text on that device. Neither result alone establishes
production response quality or sustained Pixel performance.
