# Default model artifact lock

Status: pinned candidate; awaiting a successful clean CI download, checksum verification, APK packaging, and device smoke test.

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

## Acceptance gates still required

1. Clean CI download succeeds from the pinned revision.
2. Downloaded file SHA-256 equals the value above.
3. GGUF is packaged into the APK at the expected asset path.
4. APK build succeeds and the artifact is published.
5. Pixel 9 Pro XL offline load/inference smoke test passes.

Until all five gates pass, this is a provenance-verified artifact lock, not a runtime-validated model.