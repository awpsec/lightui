# AGENTS.md

## Cursor Cloud specific instructions

`lightui` is a **native Android (Java) chat client** with **no Gradle and no local backend/server**. It is a thin client that talks to OpenRouter or any OpenAI-compatible endpoint (all keys/endpoints are entered at runtime by the user; none are bundled). See `README.md` for feature/usage details and `build.sh` for the exact build steps.

### Environment (already provisioned by the VM snapshot)

The following are baked into the VM and do **not** need reinstalling (the startup update script is intentionally a no-op — there are no package-manager dependencies):

- JDK 17 at `/usr/lib/jvm/java-17-openjdk-amd64` (default `JAVA_HOME`) and JDK 21 (system default `java`).
- Android SDK at `$HOME/android-sdk`: platform `android-34`, build-tools `34.0.0`, `platform-tools`, `emulator`, and system image `system-images;android-34;google_apis;x86_64`.
- An AVD named `lightui_avd`.
- `~/.bashrc` exports `JAVA_HOME` (JDK 17), `ANDROID_HOME`/`ANDROID_SDK_ROOT`, and adds `cmdline-tools/latest/bin`, `platform-tools`, and `emulator` to `PATH`. New login shells pick these up automatically; if you run a non-login shell, `source ~/.bashrc` first.

### Building the APK

- Build with `./build.sh` (output: `build/lightui-release.apk`, signed with `~/.android/debug.keystore`).
- **Must build with JDK 17, not JDK 21.** build-tools `34.0.0`'s `d8` crashes (`NullPointerException` on an anonymous class) when dexing bytecode emitted by JDK 21. `JAVA_HOME` in `~/.bashrc` already points at JDK 17, so a normal `./build.sh` works. If you ever see that `d8` NPE, confirm `java -version`/`$JAVA_HOME` is 17.

### Running / testing on the emulator (important gotchas)

- **Nested KVM does not work here.** The VM's host kernel throws `kernel BUG at arch/x86/kvm/x86.c` when the Android emulator tries to create a KVM vCPU, so a normal (KVM-accelerated) emulator boot hangs. Start the emulator with **software emulation** instead:
  `emulator -avd lightui_avd -gpu swiftshader_indirect -no-snapshot -no-audio -no-window -accel off -memory 3072`
  (`-no-window` is fine — drive it via `adb`; add a window only if a desktop/computer-use session needs to see it.)
- **It is slow.** Under TCG software emulation a cold boot to `sys.boot_completed=1` takes ~10-15 min, and after that `package`/`activity` system services may need another 1-2 min. Poll `adb shell getprop sys.boot_completed` and `adb shell service check package`; be patient rather than assuming failure.
- **Do not hammer it.** The guest runs at very high load; rapid `am force-stop`/relaunch loops and bursts of `input` events can wedge `system_server` into repeated ANRs. Space out interactions, wait for each screen to settle, and prefer a single deterministic action over retries.
- `adb root` works (google_apis image). App data lives at `/data/data/com.lightos.minimalchat/` (config in `shared_prefs/minimal-chat.xml`, chats in `files/`); the app is **not** `run-as`-debuggable, so use `adb root` to read/seed it.
- `adb exec-out screencap -p` can return an all-black frame during transitions or while the soft keyboard animates; take the screenshot again after the UI settles. `adb shell screenrecord` captures the framebuffer more reliably.
- Suppress crash/ANR popups that steal focus with `adb shell settings put global hide_error_dialogs 1`.

### End-to-end chat test without any external API key

The app's core flow is: pick a model → send a message → render the streamed reply. To exercise it fully offline:

1. Run a local OpenAI-compatible mock on the host that serves `GET /v1/models` and a streaming (SSE) `POST /v1/chat/completions` (chunks of `data: {"choices":[{"delta":{"content":"..."}}]}` then `data: [DONE]`).
2. The emulator reaches the host at `10.0.2.2`, and the app enables cleartext traffic, so configure the endpoint as `http://10.0.2.2:<port>/v1`.
3. Either add the endpoint + refresh models in `settings` (swipe left from the chat pane), or seed `shared_prefs/minimal-chat.xml` via `adb root` with keys: `customEndpoints`, `modelCatalog`, `myModels`, `model`(+`modelSelected`), and JSON maps `modelSources` (`{"<id>":"custom"}`) and `modelEndpoints` (`{"<id>":"<endpoint>"}`).
4. Pre-fill the message box without fighting the on-screen keyboard by launching with a share intent:
   `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "your message" -n com.lightos.minimalchat/.MainActivity`
   then tap the send arrow (bottom-right).
