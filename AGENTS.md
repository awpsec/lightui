# AGENTS.md

## Cursor Cloud specific instructions

`lightui` is a **native Android (Java) chat client** with **no Gradle and no local backend/server**. It is a thin client that talks to OpenRouter or any OpenAI-compatible endpoint (all keys/endpoints are entered at runtime by the user; none are bundled). See `README.md` for feature/usage details and `build.sh` for the exact build steps.

### Environment (already provisioned by the VM snapshot)

The following are baked into the VM and do **not** need reinstalling (the startup update script is intentionally a no-op — there are no package-manager dependencies):

- JDK 17 at `/usr/lib/jvm/java-17-openjdk-amd64` (default `JAVA_HOME`) and JDK 21 (system default `java`).
- Android SDK at `$HOME/android-sdk`: platform `android-34`, build-tools `34.0.0`, `platform-tools`, `emulator`, and system images `system-images;android-34;default;x86_64` (AOSP) and `system-images;android-34;google_apis;x86_64`.
- Three AVDs (all AOSP `android-34`, x86_64, no Google Play services needed since the app uses plain `HttpURLConnection`):
  - `lightui_lp3` — **prefer this for realistic testing.** Custom screen matching the Light Phone III target device: **1080×1240 @ 420 dpi** (near-square 3.92" panel). There is no official LP3 system image/skin; this is a stock AOSP image with the screen geometry overridden in `~/.android/avd/lightui_lp3.avd/config.ini` (`hw.lcd.width/height/density`). It renders the app at the real device proportions, which differ a lot from a normal tall phone.
  - `lightui_aosp` — generic `pixel_5` geometry (1080×2340), useful as a plain tall-phone reference.
  - `lightui_avd` — google_apis image; **avoid under software emulation** — it is much heavier and its `system_server` repeatedly hits the watchdog and restarts (services flap, launcher gets stuck on `FallbackHome`), making UI automation unreliable.
- `~/.bashrc` exports `JAVA_HOME` (JDK 17), `ANDROID_HOME`/`ANDROID_SDK_ROOT`, and adds `cmdline-tools/latest/bin`, `platform-tools`, and `emulator` to `PATH`. New login shells pick these up automatically; if you run a non-login shell, `source ~/.bashrc` first.

### Building the APK

- Build with `./build.sh` (output: `build/lightui-release.apk`, signed with `~/.android/debug.keystore`).
- **Must build with JDK 17, not JDK 21.** build-tools `34.0.0`'s `d8` crashes (`NullPointerException` on an anonymous class) when dexing bytecode emitted by JDK 21. `JAVA_HOME` in `~/.bashrc` already points at JDK 17, so a normal `./build.sh` works. If you ever see that `d8` NPE, confirm `java -version`/`$JAVA_HOME` is 17.

### Running / testing on the emulator (important gotchas)

- **Nested KVM does not work here.** The VM's host kernel throws `kernel BUG at arch/x86/kvm/x86.c` when the Android emulator tries to create a KVM vCPU, so a normal (KVM-accelerated) emulator boot hangs. Start the emulator with **software emulation** instead:
  `emulator -avd lightui_lp3 -gpu swiftshader_indirect -no-snapshot -no-audio -no-window -accel off -memory 3072`
  (swap in `lightui_aosp` for a generic tall-phone screen.)
  (`-no-window` is fine — drive it via `adb`; add a window only if a desktop/computer-use session needs to see it.)
- **It is slow.** Under TCG software emulation a cold boot to `sys.boot_completed=1` takes ~5-15 min, and after that `package`/`activity` system services may need another minute. Poll `adb shell getprop sys.boot_completed` and `adb shell service check package`; be patient rather than assuming failure.
- **Do not hammer it, and wait for idle.** The guest is slow; app cold-start causes a load spike that can trigger a transient system ANR. After boot, wait for `/proc/loadavg` to drop (the AOSP image settles to <1 when idle) before interacting. Rapid `am force-stop`/relaunch loops and bursts of `input` events can wedge `system_server`; space out interactions and wait for each screen to settle. If the `input`/`package` service briefly reports "not found", the system is mid-restart — wait and retry.
- **Do not disable `com.google.android.googlequicksearchbox`** on the google_apis image — the Pixel launcher depends on it and the home screen falls back to `FallbackHome`.
- `adb root` works (google_apis image). App data lives at `/data/data/com.lightos.minimalchat/` (config in `shared_prefs/minimal-chat.xml`, chats in `files/`); the app is **not** `run-as`-debuggable, so use `adb root` to read/seed it.
- `adb exec-out screencap -p` can return an all-black frame during transitions or while the soft keyboard animates; take the screenshot again after the UI settles. `adb shell screenrecord` captures the framebuffer more reliably.
- Suppress crash/ANR popups that steal focus with `adb shell settings put global hide_error_dialogs 1`.

### End-to-end chat test without any external API key

The app's core flow is: pick a model → send a message → render the streamed reply. To exercise it fully offline:

1. Run a local OpenAI-compatible mock on the host that serves `GET /v1/models` and a streaming (SSE) `POST /v1/chat/completions` (chunks of `data: {"choices":[{"delta":{"content":"..."}}]}` then `data: [DONE]`).
2. The emulator reaches the host at `10.0.2.2`, and the app enables cleartext traffic, so configure the endpoint as `http://10.0.2.2:<port>/v1`.
3. Either add the endpoint + refresh models in `settings` (swipe left from the chat pane), or seed `shared_prefs/minimal-chat.xml` via `adb root` with keys: `customEndpoints`, `modelCatalog`, `myModels`, `model`(+`modelSelected`), and JSON maps `modelSources` (`{"<id>":"custom"}`) and `modelEndpoints` (`{"<id>":"<endpoint>"}`).
4. Send a message: tap the "ask" field (bottom row), wait for the keyboard to fully open, then `adb shell input text "your%smessage"` (use `%s` for spaces) and tap the send arrow. Verified against the local mock, the reply streams in with a tok/s stat.

UI-automation gotchas:
- Enter text with `adb shell input text` (the on-screen IME must be enabled — the default `LatinIME` is). The `ACTION_SEND`/`EXTRA_TEXT` share intent does **not** reliably pre-fill the box (the app rebuilds the input view after `setText`), so don't rely on it.
- The open soft keyboard raises the send arrow; tap it in its raised position, or press BACK to hide the keyboard first and tap the arrow on the bottom input row.
- `adb exec-out screencap -p` can return an all-black frame while the keyboard animates or during transitions; retake after it settles, or use `adb shell screenrecord` (captures the framebuffer reliably).
