# XDM → Kotlin Multiplatform / Kotlin Native Assessment

## 1. Kotlin Multiplatform (KMP) — Android Target

### Goal
Port the desktop download manager to Android as a native app. Different UI (Compose for Android or traditional Android Views), same core engine.

### What Works Today (JVM-Only)
| Component | Implementation | KMP Status |
|-----------|---------------|------------|
| Networking | Custom `XDMHttpClient` (raw socket), `JavaHttpClient` (java.net wrapper) | **JVM-only** — `java.net.Socket`, `java.net.HttpURLConnection`, `javax.net.ssl.SSLSocket` |
| File I/O | `java.io.RandomAccessFile`, `java.io.File`, `java.io.FileInputStream` | **JVM-only** |
| Crypto | `javax.crypto.Cipher` (HLS AES-128) | **JVM-only** |
| Compose UI | `compose.desktop.currentOs` + `material3` | **Works with Compose Multiplatform** (same API on Android) |
| JSON | `json-simple` (Java library) | **JVM-only** — use `kotlinx.serialization` |
| FTP | `commons-net` | **JVM-only** |
| Threading | Raw `java.lang.Thread`, `synchronized` | **JVM-only** — replace with `kotlinx.coroutines` |
| JNA (Windows tray) | `com.sun.jna` | **JVM-only** (not needed on Android) |
| Process launcher | `java.lang.ProcessBuilder` (yt-dlp, ffmpeg) | **JVM-only** (not available on Android without NDK) |

### What Must Change for Android

#### High Effort

| Change | Estimated Effort | Notes |
|--------|-----------------|-------|
| Replace `java.net.*` networking with **Ktor Client** | **4–6 weeks** | The entire HTTP engine (`XDMHttpClient`, `JavaHttpClient`, `HttpChannel`, `ChunkedInputStream`, `KeepAliveConnectionCache`, `ProxyResolver`, `SocketFactory`) must be rewritten. |
| Replace `java.io.File` with **okio Path** | **2–3 weeks** | File I/O is used pervasively in `SegmentImpl`, `SegmentDownloader`, all state persistence, metadata, ffmpeg management, thumbnail cache. |
| Replace `java.lang.Thread` with **kotlinx.coroutines** | **2–3 weeks** | `AbstractChannel` runs one thread per chunk. `QueueScheduler` is a raw `while(true)` thread. `ClipboardMonitor` polls. All need coroutine equivalents. |
| Remove `commons-net` FTP, use Ktor FTP or custom impl | **1 week** | FTP is a minor protocol; if Android support is desired, Ktor or a custom socket-based implementation is needed. |
| Replace `json-simple` with `kotlinx.serialization` | **1 week** | Only 2 files (`tags.json`, `yt-combined.json`) use json-simple. Persistence is otherwise custom text format which also needs replacement. |

#### Medium Effort

| Change | Estimated Effort | Notes |
|--------|-----------------|-------|
| Replace global `java.net.Authenticator` | **2–3 days** | Used for proxy/auth. Ktor has built-in auth. |
| Replace `javax.crypto.Cipher` | **2–3 days** | HLS AES-128 decryption. Can use `javax.crypto` via Android SDK (available on Android) or Ktor crypto. |
| Replace `BigInteger` for hex IV parsing | **1 day** | Trivial replacement with Kotlin stdlib. |

#### Low Effort

| Change | Estimated Effort | Notes |
|--------|-----------------|-------|
| yt-dlp / ffmpeg integration | **1 week** | Cannot run CLI binaries on Android without NDK. Would need to bundle or use Android `ProcessBuilder`. Possible but unconventional. |
| System tray → Android notifications | **3–5 days** | `compose.desktop.Tray` → `AndroidNotificationManager` |
| Clipboard monitoring → Android | **1–2 days** | `ClipboardManager` API is different but analogous. |

### Architectural Concerns

1. **yoUISeparation** — The UI (`MainWindowUI.kt`, dialogs) is tightly coupled to `XDMApp` singletons. For Android, you'd want a ViewModel pattern. The core engine would need an abstraction layer (`DownloadEngine` interface) with platform-specific implementations.

2. **State persistence** — Custom text format (`downloads.txt`, `state.txt`, `metadata/<uuid>`) works but isn't portable. A cross-platform solution like SQLDelight or DataStore would be better.

3. **FFmpeg/yt-dlp** — These are native binaries. On Android, you'd need to bundle them as JNI libraries or use NDK. This is a significant effort and may not be practical for all architectures (arm64, x86_64, armeabi-v7a).

### Estimated Timeline (One Developer)

| Phase | Duration |
|-------|----------|
| Core engine refactor (networking, I/O, coroutines) | 6–8 weeks |
| Android-specific UI (Compose for Android) | 4–6 weeks |
| Testing and polish | 2–4 weeks |
| **Total** | **12–18 weeks** |

### Verdict

**Doable, but a major rewrite of the networking and I/O layers.** The Compose UI is the easiest part (same API). The hardest parts are:
1. Replacing the custom socket-based HTTP client with Ktor
2. Replacing all file I/O with okio
3. Coroutine-ifying the thread-based download engine

If the networking layer is extracted into a clean interface first, the rewrite can be done incrementally.

---

## 2. Kotlin Native on Desktop

### Goal
Eliminate JVM dependency entirely — compile the desktop app to a native binary via Kotlin/Native (using Compose Multiplatform desktop backend instead of `compose.desktop.currentOs`).

### Current State
The project already uses **Compose Desktop**, which actually compiles to native binaries via the JVM (it runs on the JVM). True Kotlin/Native desktop would use the Compose Multiplatform native backend, which is still experimental.

### What Blocks Native Compilation

| Dependency | Kotlin/Native Status | Workaround |
|-----------|---------------------|------------|
| `java.net.Socket` | **Not available** | Replace with Ktor client (KMP-compatible) |
| `java.io.*` | **Not available** | Replace with okio |
| `javax.crypto.*` | **Not available** | Use platform crypto (Apple Security Framework, Windows BCrypt, Linux OpenSSL) via expect/actual |
| `java.net.Authenticator` | **Not available** | Custom implementation |
| `java.util.Base64` | **Not available** | Use Kotlin stdlib or `okio` Base64 |
| `java.net.URL` | **Not available** | Replace with `kotlinx.serialization` URLs or Ktor URL parsing |
| `java.lang.ProcessBuilder` | **Available via Kotlin/Native** (`kotlin.system`) | Works |
| `javax.xml.parsers.*` (F4M manifest parsing) | **Not available** | Replace with `kotlinx.serialization.xml` or a simple XML parser |
| `java.awt.TrayIcon` | **Not available** | Compose Desktop has built-in `Tray` — already used |
| `org.w3c.dom.*` (F4M parsing) | **Not available** | Same — needs XML parser replacement |
| `com.sun.jna` (Windows tray) | **Not available** | Use `platform.posix` or `cinterop` |
| `commons-net` (FTP) | **Not available** | Replace with Ktor or custom socket implementation |
| `org.tukaani:xz` (XZ decompression) | **Not available** | Replace with `kotlinx.compress` or bundle native library |
| `json-simple` | **Not available** | Replace with `kotlinx.serialization` |

### Severity of Migration

The project is **heavily dependent on JVM-specific APIs**. Every layer of the application uses `java.*`:

- **Network**: Entirely `java.net.Socket` / `java.net.HttpURLConnection`
- **File I/O**: Every download, every state save, every config read
- **Crypto**: `javax.crypto` for HLS
- **XML**: `javax.xml.parsers` for F4M / HDS manifests
- **Compression**: `org.tukaani:xz` for XZ
- **JSON**: `json-simple`
- **Windows interop**: JNA

This means **virtually every file** in the project would need changes.

### What Already Works

| Component | Status |
|-----------|--------|
| Compose Desktop UI | ✅ Works natively with Compose Multiplatform |
| Compose Material 3 | ✅ Works |
| Coroutines usage | ✅ (if adopted) |
| Logging (custom `Logger`) | ✅ Trivial to port |
| String utilities | ✅ Pure Kotlin |
| Format utilities | ✅ Pure Kotlin |
| yt-dlp/ffmpeg CLI launcher | ✅ `ProcessBuilder` is available |
| Config parsing | ✅ (pure Kotlin text parsing) |

### Estimated Timeline (One Developer)

| Phase | Duration |
|-------|----------|
| Replace networking with Ktor | 4–6 weeks |
| Replace file I/O with okio | 2–3 weeks |
| Replace crypto with expect/actual | 1–2 weeks |
| Replace XML parser | 1 week |
| Replace json-simple, xz, JNA | 1–2 weeks |
| Replace thread usage with coroutines | 2–3 weeks |
| Testing and polish | 2–4 weeks |
| **Total** | **14–22 weeks** |

### Verdict

**Technically doable, but the effort-to-value ratio is poor.** The project already compiles to a native-looking binary (via `jlink` / `jpackage` in the Gradle build, producing `.msi`, `.deb`, `.dmg` installers). Users get a self-contained native app with the JVM bundled.

Key reasons **not** to pursue Kotlin/Native on desktop today:
1. Compose Desktop **already produces distributable native packages**. The user experience is the same.
2. Kotlin/Native's Compose Multiplatform backend is experimental and has platform-specific bugs.
3. The JVM gives you mature profiling, debugging, and garbage collection.
4. Access to the entire Java ecosystem (libraries, tools) is lost.
5. The effort (14–22 weeks) is better spent on Android support or new features.

### When To Reconsider

- When Compose Multiplatform native backend reaches stable (likely 2026+)
- If you need to distribute a single ~15MB binary with zero JVM dependency
- If you're already doing KMP for Android and the desktop native target becomes a "free" additional target

---

## 3. Recommendation

| Target | Recommended? | Timeline | Confidence |
|--------|-------------|----------|------------|
| **Android (KMP)** | **Yes, medium priority** | 12–18 weeks | Moderate — networking rewrite is the biggest risk |
| **Kotlin Native Desktop** | **No, low priority** | 14–22 weeks | Low — JVM already delivers native packages |

### If You Choose Android:
1. Start by extracting a clean `DownloadEngine` interface
2. Replace networking with Ktor Client (this is the hardest step)
3. Ship an MVP with HTTP downloads only, no yt-dlp/ffmpeg
4. Add streaming protocols (HLS, HDS, DASH) incrementally
5. Use Compose Multiplatform to share UI code where possible

### If You Choose Kotlin Native Desktop:
1. Wait for Compose Multiplatform native backend to stabilize
2. Use expect/actual to abstract JVM-specific APIs
3. Port one download protocol fully before tackling others
