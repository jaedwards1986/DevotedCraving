# Default model artifact lock

Status: provenance verified and clean CI download/SHA-256 verification passed. APK packaging now distributes the model separately because Android's APK ZIP32 packager cannot accept a single 4.69 GB entry. Standalone model artifact publication and Pixel device validation remain pending.

## Canonical artifact

- Repository: bartowski/Lexi-Llama-3-8B-Uncensored-GGUF
- File: Lexi-Llama-3-8B-Uncensored-Q4_K_S.gguf
- Quantization: Q4_K_S
- Architecture: Llama 3 8B
- Immutable Hugging Face revision: 5ef9a5e9e4883c33d7e3db2860b8cdf6829db54e
- SHA-256: 5039334f503f8110e81bbfd04add9c84f7284c0d31f12e2f8c857cd34626eaee
- Hugging Face Xet-backed hash: 6ab71ba464124a148fcf1070831c740342162d61203d99b5f66b66e2e4f79c8a
- Published remote size: 4.69 GB

Source file metadata: https://huggingface.co/bartowski/Lexi-Llama-3-8B-Uncensored-GGUF/blob/5ef9a5e9e4883c33d7e3db2860b8cdf6829db54e/Lexi-Llama-3-8B-Uncensored-Q4_K_S.gguf

## Why the source changed

The previous TensorBlock repository advertised a Q4_K_S file but the workflow's exact hf download request returned File not found. The build now uses bartowski's corresponding Lexi Llama 3 8B Q4_K_S artifact. This is a repository/file-source substitution, not a change to the intended model family or quantization.

The Hugging Face file page identifies bartowski's quantization commit as verified and publishes the SHA-256 and remote file size. The workflow pins both the immutable repository revision and SHA-256 so a moving branch cannot silently change the model bytes.

## Packaging decision

The model is downloaded to the CI runner's temporary staging directory, independently size-checked and SHA-256-verified, then published as a separate `forgeai-default-model` workflow artifact. The installable APK is built without embedding the GGUF. This avoids the Android ZIP32 per-entry limit (4,294,967,295 bytes), which the 4.69 GB model exceeds.

The app still needs a user-facing model import/download and storage lifecycle that places the GGUF at an app-accessible file path before `GGUFManager.load(File)` can be invoked. Separate artifact publication does not itself prove that end-user model installation flow.

## Acceptance gates

1. Clean CI download succeeds from the pinned revision. **Passed in run 45655559189.**
2. Downloaded file SHA-256 equals the value above. **Passed in run 45655559189.**
3. Installable APK packages successfully without the standalone GGUF. **Pending rerun.**
4. APK archive and standalone model artifact publish successfully. **Pending rerun.**
5. Pixel 9 Pro XL offline model load/inference smoke test passes. **Pending device test.**
6. App-level model import/storage flow is implemented and tested. **Not yet implemented.**

Until the remaining gates pass, this is a provenance-verified model artifact with a validated CI download/checksum, not a runtime-validated app integration.
