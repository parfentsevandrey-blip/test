# Android platform capabilities & performance engineering for cinematic, always-animated Compose visuals (minSdk 33 / targetSdk 37; current to Sept 2026)

_Labeling conventions used below: "(secondary)" = third-party / non-Google source; "(search snippet)" = content seen only in a search-result summary, not in a fetched page, so treat it as lower confidence. API levels: 33 = Android 13, 34 = 14, 35 = 15, 36 = 16, 37 = 17; "37.2" = Android 17 QPR2 minor SDK._

## Q1. HDR in app UI: Ultra HDR, extended-range brightness / HDR headroom, rendering HDR highlights (sun glare, lightning)

### Takeaway
HDR is a per-window opt-in. `window.colorMode = COLOR_MODE_HDR` turns it on and only takes effect from Android 14 (API 34). `Display.getHdrSdrRatio()` (API 34) reports how much headroom exists right now, and `Window.setDesiredHdrHeadroom()` (API 35) lets the app cap it. Google's guidance is to turn HDR on only while HDR content is visible and to keep headroom modest (about 1.5–2x) when the screen is mostly SDR UI. Headroom changes over time (it shrinks in bright surroundings and is stuck at 1.0 on some OEM devices), so sun glare and lightning need a graceful SDR bloom fallback.

### Cited Findings
**Turning HDR on for a window (API 34+)**
- The Ultra HDR image format is supported from Android 14 (API 34), and `Bitmap.hasGainmap()` is available from Android 14. To show an Ultra HDR image at full dynamic range, set `window.colorMode = ActivityInfo.COLOR_MODE_HDR`. The docs say: "These APIs are available from Android 8; however, images are not displayed in Ultra HDR unless the device is running Android 14 or higher." — [Display Ultra HDR images](https://developer.android.com/media/grow/ultra-hdr/display)
- Google recommends switching the color mode at runtime when HDR content appears, and not setting it statically in the manifest. It also says to turn HDR mode off when no HDR content is showing, to save device resources and battery. — [Display Ultra HDR images](https://developer.android.com/media/grow/ultra-hdr/display)
- Android captures screenshots in SDR and tone-maps any HDR content in them. — [Display Ultra HDR images](https://developer.android.com/media/grow/ultra-hdr/display)

**Headroom APIs (API 34 / 35)**
- `Display.getHdrSdrRatio()` returns "the current hdr/sdr ratio expressed as the ratio of targetHdrPeakBrightnessInNits / targetSdrWhitePointInNits". If `isHdrSdrRatioAvailable()` is false, it always returns 1.0f. Available since API 34. — [Display.HdrSdrRatio, Microsoft Learn mirror of the Android API docs (secondary)](https://learn.microsoft.com/en-us/dotnet/api/android.views.display.hdrsdrratio?view=net-android-34.0)
- Android 15: "Android 15 chooses HDR headroom that is appropriate for the underlying device capabilities and bit-depth of the panel. For pages that have lots of SDR content, such as a messaging app displaying a single HDR thumbnail, this behavior can end up adversely influencing the perceived brightness of the SDR content. Android 15 lets you control the HDR headroom with `setDesiredHdrHeadroom` to strike a balance between SDR and HDR content." — [Android 15 features](https://developer.android.com/about/versions/15/features)
- Google's Sept 2025 guidance sets `window.colorMode = COLOR_MODE_HDR` together with `window.desiredHdrHeadroom`. The sample values are 0f for SDR only, 1.5f for mixed/mostly SDR, 3f for mixed/mostly HDR and 5f for HDR only. It recommends "around 2x for a mixed scene and 5-8x for a fully-HDR scene", with full-screen HDR tied to content metadata such as Ultra HDR `max_content_boost`. — [Android Developers Blog: HDR and User Interfaces (Sept 2025)](https://android-developers.googleblog.com/2025/09/hdr-and-user-interfaces.html)
- An Activity only respects the desired headroom when `COLOR_MODE_HDR` is set. A `SurfaceView` showing HDR content does **not** need `COLOR_MODE_HDR` to have its headroom constrained. — [HDR and User Interfaces](https://android-developers.googleblog.com/2025/09/hdr-and-user-interfaces.html)
- **Simultaneous contrast:** when HDR peaks are on screen, nearby SDR content looks dimmer, "grey" or "washed out" even though its luminance has not changed. Google's advice is to "Resist the temptation to 'brighten' SDR content instead" and to limit headroom rather than raise SDR luminance. — [HDR and User Interfaces](https://android-developers.googleblog.com/2025/09/hdr-and-user-interfaces.html)
- HDR headroom is dynamic and disappears in bright ambient conditions, so tone-mapping to SDR has to happen on demand. The post also gives these figures:
  - High-end phone displays typically have gamma 2.2, P3 gamut and roughly 2000 nits peak.
  - ITU-R BT.2408 puts graphics reference white at 203 nits.
  - Android's UI toolkit delivers HDR through the `setExtendedRangeBrightness`/"extended range brightness" mechanism, with rendering "tailored to the specific display and current conditions".
  — [Android Developers Blog: What is HDR? (Aug 2025)](https://android-developers.googleblog.com/2025/08/what-is-hdr.html)

**How the system composes SDR and HDR (device side)**
- Android 13 added SDR dimming. When HDR content is on screen, display brightness goes up and SDR layers are dimmed in proportion, so they look as bright as before. Requirements:
  - An AIDL HWC with hardware-accelerated dimming, plus at least one HDR type reported by `Display.getHdrCapabilities`.
  - An `sdrHdrRatioMap` in the display config. Without it, SDR dimming stays off.
  - `minimumHdrPercentOfScreen` is now tunable; it was previously fixed at 50%.
  — [AOSP: Mixed SDR and HDR composition](https://source.android.com/docs/core/display/mixed-sdr-hdr)
- AOSP lists these trade-offs: shorter battery life (especially when the HWC hands dimming to the GPU), possible screen-health effects from long periods of high brightness, and "black crush" (lost detail in dark areas) caused by SDR dimming. — [AOSP: Mixed SDR and HDR composition](https://source.android.com/docs/core/display/mixed-sdr-hdr)

**Drawing values above SDR white**
- AGSL's `toLinearSrgb()`/`fromLinearSrgb()` convert between the working color space and `LINEAR_EXTENDED_SRGB`. That space has "sRGB color primaries with linear transfer function, supporting extended range values below 0.0 and above 1.0". Color uniforms must be declared `layout(color)` to be color-managed. — [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl)
- Android 16 adds:
  - `ImageFormat.HEIC_ULTRAHDR`.
  - ISO 21496-1 gainmap parameters: you can set the colorspace the gainmap math is applied in, and use an HDR base image with an SDR gainmap.
  - Ultra HDR in AVIF is announced as "stay tuned".
  — [Android 16 features](https://developer.android.com/about/versions/16/features)

**Device pitfalls**
- One developer reports that on some ColorOS devices `getHdrCapabilities()` reports HDR but `getHdrSdrRatio()` is always 1.0, and "there is no public API to detect or bypass" this. — [fliks-app PR #1247 (single developer report, secondary)](https://github.com/fliks-app/fliks/pull/1247)

### Inferences
- **Suggested architecture for sun glare and lightning:**
  1. **Check capability (API 34+).** Read `display.isHdrSdrRatioAvailable` and `display.hdrSdrRatio`. Treat a ratio of about 1.0 as "SDR only" even when `getHdrCapabilities()` claims HDR (see the ColorOS report).
  2. **Enable HDR per scene.** Set `COLOR_MODE_HDR` only while an HDR-worthy scene (bright sun, storm) is visible and the activity is resumed. Switch back to `COLOR_MODE_DEFAULT` when battery saver or thermal pressure kicks in (see Q4).
  3. **Cap headroom on API 35+.** Keep `desiredHdrHeadroom` around 1.5–2.0, because a weather screen is mostly SDR UI (text, glass cards) over the sky. Reserve 3–5x+ for a UI-less "immersive" mode.
  4. **Adapt the shader.** Pass the live headroom to the AGSL sky as a uniform (`uHdrHeadroom`). Emit linear values above 1.0 only up to that ratio, and roll highlights into an SDR bloom when the ratio is 1.0.
- **Two ways to draw highlights, different confidence levels:**
  - **Documented path:** Ultra HDR (gainmap) bitmaps. Glare sprites or lightning-bolt textures would be pre-authored as Ultra HDR and drawn with Canvas/Image in a `COLOR_MODE_HDR` window.
  - **Not confirmed:** procedural AGSL output above 1.0 in `LINEAR_EXTENDED_SRGB`. It is plausible because AGSL works in an extended-range space, but no fetched doc says HWUI keeps RuntimeShader output above 1.0 through to the display. Prototype it on a Pixel (API 34+) before designing around it.
- **The whole window changes when HDR turns on.** Compose has no per-composable HDR. Expect simultaneous-contrast "greying" of glass panels and text during HDR moments. Short HDR bursts (a lightning flash) may be less noticeable than a constant HDR sun, but toggling or ramping headroom often may cause visible brightness "pumping". Test this.
- **Treat HDR as the top quality tier.** AOSP says it can cost battery and screen health, so gate it behind the Q4 signals (battery saver, thermal status, visibility).
- **Another route:** an HDR video loop or GL/Vulkan sky in a `SurfaceView` can carry HDR on its own layer. Per the blog, its headroom can be constrained without switching the window to `COLOR_MODE_HDR`.

### Gaps
- No fetched primary source shows Canvas, RuntimeShader or Compose drawing with values above 1.0 appearing brighter than SDR white in a `COLOR_MODE_HDR` window. Only gainmap bitmaps are explicitly documented. It is also unknown whether HWUI switches to an FP16 buffer in HDR mode, and what that costs in memory and bandwidth.
- Exact API levels were not confirmed in fetched pages for `SurfaceControl.Transaction.setExtendedRangeBrightness` (believed API 34), `Display.registerHdrSdrRatioChangedListener` (believed API 34) and the SurfaceControl-level desired-headroom API (believed API 35).
- No published figure was found for the power cost of HDR mode, and no list of which devices report an HDR/SDR ratio above 1.0. Field telemetry will be needed.
- The sources do not say whether the system smooths or animates changes to desired headroom.

---

## Q2. AGSL RuntimeShader & RenderEffect (performance, compilation, limits, Vulkan/Graphite backend) vs alternative renderers (GL/Vulkan SurfaceView, Filament, Rive, Lottie, Media3 video loops)

### Takeaway
At minSdk 33, AGSL (`RuntimeShader`, `RenderEffect.createRuntimeShaderEffect`) is available on every target device and is the best-integrated way to draw a full-screen procedural sky inside Compose. The shader compiles when the object is constructed (so keep instances stable), is limited to the GLSL ES 1.0 feature set, and runs on HWUI's RenderThread; most modern devices execute it through Skia's Vulkan backend. For glass, Compose now blurs a composable's own content, and Android 17 QPR2 (SDK 37.2) adds `RenderNode.setBackdropRenderEffect` for blurring what's behind. Haze is still the practical cross-version backdrop blur, and in its author's benchmark it beat the native API on CPU frame time. Move to a SurfaceView renderer (GL/Vulkan or Filament) for multi-pass simulation, 3D, a separate frame rate or resolution, or thread isolation. Use Rive or Lottie for authored vector elements, and Media3 video loops on a SurfaceView for photoreal footage.

### Cited Findings
**AGSL availability and language limits**
- "Since Android 13, you've been able to use AGSL to create custom RuntimeShaders that extend Shader." Android 16 (API 36) adds `RuntimeColorFilter` and `RuntimeXfermode` for AGSL color filters and custom blending. — [Android 16 features](https://developer.android.com/about/versions/16/features)
- Android 15 (API 35) adds:
  - `Canvas.clipShader()`/`clipOutShader()`, which use a shader as an alpha-mask clip.
  - `Matrix44` for 3D canvas transforms.
  — [Android 15 features](https://developer.android.com/about/versions/15/features)
- AGSL language rules:
  - The feature set is fixed at **GLSL ES 1.0**, for maximum device reach.
  - `main` takes local coordinates with the **origin at the top left**.
  - There is **no preprocessor**. Convert `#define` to `const`; the compiler does constant folding and branch elimination.
  - `half`/`short` are medium precision.
  - Color uniforms need `layout(color)`.
  — [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl)
- How Skia runs it: "With SkSL, you are programming a stage of the Skia pipeline". Your code becomes one function inside a larger generated fragment shader. Child shaders are sampled with `.eval()`, not `sample()`. Output must be **premultiplied** alpha, and coordinates are local rather than normalized. — [Skia: SkSL & Runtime Effects](https://skia.org/docs/user/sksl/)
- `RuntimeShader` compiles the AGSL source when it is constructed. In Compose it is typically cached with `remember`; without a key it lives for the whole composition. — [HotSwan blog (secondary)](https://hotswan.dev/blog/compose-agsl-shader-tuning)
- To apply AGSL to already-rendered content: create a RenderEffect from a RuntimeShader, naming the uniform that receives the RenderNode's contents, then set it on a GraphicsLayerScope through `Modifier.graphicsLayer`. — [Android Developers (Medium): AGSL: Made in the Shade(r) (search snippet)](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a)

**RenderEffect, blur and "glass"**
- Compose added a **progressive blur** API in **Compose 1.13.0-alpha03**. It "requires Android 13 (API 33) or newer". It blurs the composable's **own** content, not what's behind it: "A frosted toolbar needs to blur the list, image, or other content _behind_ it". — [Chris Banes: Compose has progressive blur. Do you still need Haze?](https://chrisbanes.me/posts/compose-progressive-blur/)
- Native backdrop blur in Android 17 QPR2:
  - **Android 17 QPR 2 (SDK 37.2)** adds `RenderNode.setBackdropRenderEffect`, which "does apply an effect to content drawn behind a node". It only works on that version or later.
  - In Banes's benchmark, the native backdrop API "had higher CPU frame times than Haze's existing source-based path" when the content behind was changing.
  — [Chris Banes](https://chrisbanes.me/posts/compose-progressive-blur/)
- **Haze 2.0:**
  - Adds a **Glass** effect (refraction + blur + tint + lighting).
  - Adds experimental support for the Android 17 QPR2 native backdrop RenderEffect, as a lower-GPU-memory alternative to source-based capture. — [Haze 2.0 summary on daily.dev (secondary)](https://daily.dev/posts/haze-2-0-pksvqps2b)
  - Haze's README: "Built-in Blur and Glass use `HazePerformanceMode.Default`, which selects the fixed `Balanced` profile. Begin with that setting and measure a release-like build on representative devices before selecting `Quality`, `Performance`, or `Fixed(...)`." — [Haze GitHub](https://github.com/chrisbanes/haze)

**Renderer backend: Skia Vulkan (SkiaVk), ANGLE, Graphite**
- The Android 14 CDD "STRONGLY RECOMMENDED" device implementations "use SkiaVk with HWUI". A code change suggested this becomes a "MUST" in the Android 15 CDD. Pixel reportedly has done this since Android 10, and the Galaxy S24 Ultra sets `ro.hwui.use_vulkan=true`. — [Mishaal Rahman (journalist) on X](https://x.com/MishaalRahman/status/1752063453227798926) (CDD text not verified directly)
- HWUI's backend can be switched for testing with `adb shell setprop debug.hwui.renderer skiavk`, which resets on reboot. — [SkiaVK project (secondary)](https://github.com/dyokism/SkiaVK)
- **ANGLE:**
  - Android 15 ships ANGLE as an optional OpenGL ES-on-Vulkan layer. You can test it with Developer Options → "Experimental: Enable ANGLE".
  - Google plans to ship ANGLE as the GL system driver on more new devices, "with the future expectation that OpenGL/ES will be only available through ANGLE". It also says it will "continue support for OpenGL ES on all devices".
  — [Android 15 features](https://developer.android.com/about/versions/15/features)
- **Vulkan is now the official API.** In March 2025 Google made Vulkan "the official graphics API for Android". "Starting with our next Android release, more devices will use Vulkan to process all graphics commands." OpenGL games will run through ANGLE. "Over 45% of sessions from new games on Unity use Vulkan." — [Android Developers Blog, Mar 2025](https://android-developers.googleblog.com/2025/03/building-excellent-games-with-better-graphics-and-performance.html)
- **Resizability (targetSdk 37):** the Android 17 features page lists the behavior change "Restrictions on orientation and resizability are ignored" for apps targeting 37. Full-screen shaders must therefore handle arbitrary aspect ratios and live resizes. — [Android 17 features](https://developer.android.com/about/versions/17/features)

**Alternatives**
- **Filament** (v1.77.1):
  - Android backends: OpenGL ES 3.0+ and Vulkan 1.0 (WebGPU is also listed).
  - Renders into SurfaceView/TextureView through the `UiHelper` API; `gltfio-android` and `filament-utils-android` are available.
  - Features: clustered forward renderer, Cook-Torrance PBR, HDR bloom, depth-of-field bokeh, SSAO, screen-space reflections, several tone mappers, and color grading (exposure, night adaptation, white balance, channel mixer).
  — [Filament GitHub](https://github.com/google/filament)
- **Rive:**
  - The **Compose API** (beta, described as production-ready and recommended for new projects) needs a "Rive worker which owns a thread for Rive operations, including file and asset decoding, advancing, and drawing". — [Rive Android runtime docs](https://rive.app/docs/runtimes/android/android)
  - Android Compose defaults to the **Rive Renderer**. The legacy runtime's Canvas/Skia option lost Skia in v10.0.0. Vector Feathering needs the Rive Renderer. — [Rive: Choose a renderer](https://rive.app/docs/runtimes/choose-a-renderer)
- **Lottie:**
  - `RenderMode` is `AUTOMATIC`, `HARDWARE` or `SOFTWARE`.
  - Animations with many or large masks and mattes ran "significantly worse with hardware acceleration" before Android P, because of `RenderNode#textureUpload`.
  - `AUTOMATIC` uses a heuristic that falls back to software above 4 masks/mattes on affected API levels.
  — [lottie-android PR #1072](https://github.com/airbnb/lottie-android/pull/1072/files); [Lottie CHANGELOG](https://github.com/airbnb/lottie-android/blob/master/CHANGELOG.md)
- **Media3 video loops:**
  - "`SurfaceView` is more power efficient, with `TextureView` increasing total power draw during video playback by as much as 30% on some devices."
  - "For video playback, the display and decoding of the video stream account for most of the power consumed during playback."
  — [Media3: Battery consumption](https://developer.android.com/media/media3/exoplayer/battery-consumption)
  - `media3-ui-compose` provides a `PlayerSurface` composable (since Media3 1.6.0), and the SurfaceView surface type is recommended. — [Media3 Surface types](https://developer.android.com/media/media3/ui/surface); [ProAndroidDev (secondary, search snippet)](https://proandroiddev.com/from-androidview-to-playersurface-modernizing-exoplayer-with-media3s-compose-ui-74e40ce81f94)

### Inferences
- **Cost model for a full-screen sky.** Fragment work is roughly pixels × per-pixel ALU cost × frames per second. At the same shader cost, 1080×2400 (2.6 MP) works out to:

  | Resolution | Frame rate | Pixel shades per second |
  |---|---|---|
  | Full | 120 Hz | about 311 M |
  | Full | 60 Hz | about 156 M |
  | Half | 30 Hz | about 19 M |

  Half resolution at 30 Hz is about 16x less work than full resolution at 120 Hz. Sky gradients, clouds and fog are low-frequency, so resolution and frame rate are the two strongest quality knobs.
- **AGSL rules of thumb** (from the compile-at-construction behavior and GLSL ES 1.0 limits):
  - Create each `RuntimeShader` once (in `remember`/`drawWithCache`) and change only uniforms per frame.
  - Keep `uniform float iTime` in `float` (high precision) and wrap it on the CPU, for example modulo a loop period. `half` is fp16 on many mobile GPUs, with an 11-bit significand. At t ≈ 8 s a `half` time value already moves in about 7.8 ms steps, and at t ≈ 64 s in about 62 ms steps, which is visibly steppy at 60–120 Hz.
  - Express quality (fbm octaves, rain layers) as a `const` loop bound with a uniform-controlled early `break`. ES 1.0 loops need constant bounds; `break` support in SkSL should be verified.
  - Warm up shaders by constructing them, and ideally drawing once, during a static splash or placeholder frame.
- **Glass forces a placement choice.** Backdrop blur in Haze or `setBackdropRenderEffect` can only capture content that lives in the HWUI RenderNode tree.
  - If the sky is AGSL inside Compose, glass panels can blur and refract the animated sky.
  - If the sky is a SurfaceView (custom GL/Vulkan, Filament, video), HWUI cannot sample it. Glass would then have to be rendered inside that renderer, or would blur only static UI.
  - This is the key architectural trade-off.
- **When to choose which** (synthesis):

  | Option | Best for | Main costs / limits |
  |---|---|---|
  | AGSL in Compose (`ShaderBrush`, `RenderEffect`) | Procedural sky, clouds (fbm), rain/snow as stateless procedural layers, heat haze, lightning glow; glass refraction over the sky | GLSL ES 1.0; single pass per draw; no persistent simulation buffers without offscreen layers; shares RenderThread/GPU with the UI; frame rate tied to the window |
  | Custom GLES/Vulkan `SurfaceView` | Stateful particles, multi-pass or fluid clouds, compute, fixed low-res buffer upscaled by the compositor (e.g. `SurfaceHolder.setFixedSize`, unverified here), its own render thread + ADPF hint session, its own `Surface.setFrameRate` | Engineering cost; HWUI can't blur it; GL moving to ANGLE, so prefer Vulkan or an engine |
  | Filament | 3D dioramas / PBR terrain, physically based lighting, HDR bloom and tone mapping | APK size (not found), SurfaceView/TextureView integration, learning curve |
  | Rive | Interactive vector illustrations with state machines (weather icons, characters) | Not for full-screen procedural; adds its own worker thread and renderer |
  | Lottie | Designer-made icons and short loops | CPU-heavy on complex comps (masks/mattes); avoid always-on full-screen |
  | Media3 video loop | Photoreal timelapse skies | Decode + display power; content can't react to live data (wind, sun angle); SurfaceView required for efficiency; asset size |
- **Testing backends.** Since most devices run HWUI on Vulkan (and GL is moving to ANGLE), test AGSL performance and first-use hitching on both the `skiavk` and GL HWUI backends (`debug.hwui.renderer`) and on Mali, Adreno and PowerVR GPUs.

### Gaps
- **Graphite:** I found no reliable source saying Skia Graphite has shipped, or is planned, as HWUI's renderer in Android 15, 16 or 17.
- **Shader limits and caching:** no official AGSL limits were found (maximum uniforms, loop or instruction limits, `break` support). There is also no public documentation of how HWUI/Skia caches compiled runtime-effect GPU pipelines across launches, or of an API to pre-warm them.
- **Missing library data:** Filament AAR size, Rive's Android GPU backend (GLES vs Vulkan), and current Lottie-on-Compose performance numbers.
- **Android 17 QPR2 timing:** the ship date of QPR2 (SDK 37.2) wasn't confirmed; as of Sept 2026 it is probably still in beta. It is also unconfirmed which API should gate minor-SDK features (likely `Build.VERSION.SDK_INT_FULL`).

---

## Q3. Frame pacing & refresh rate: 120 Hz, adaptive refresh rate (ARR), requested frame-rate APIs, Choreographer, avoiding recomposition-driven animation

### Takeaway
Frame budgets are 16 ms at 60 Hz, 11 ms at 90 Hz and 8 ms at 120 Hz. On devices with adaptive refresh rate (Android 15 QPR1+ with HAL support), views vote for a frame rate with `View.setRequestedFrameRate` (numbers or NORMAL/HIGH categories) and Compose votes with `Modifier.preferredFrameRate` (Compose 1.9); the highest vote wins, touch boosts to HIGH, and ARR is on by default. On older devices, or for SurfaceView content, `Surface.setFrameRate` (API 30) is the hint. Animations should read state only in the layout or draw phase so a moving sky never triggers recomposition.

### Cited Findings
- **Frame budgets:** 16 ms at 60 fps, 11 ms at 90 fps, 8 ms at 120 fps. — [Slow rendering](https://developer.android.com/topic/performance/vitals/render)
- **Frame Rate API (API 30):**
  - Methods: `Surface.setFrameRate(frameRate, compatibility[, changeFrameRateStrategy])`, `SurfaceControl.Transaction.setFrameRate`, and the NDK equivalents. Use `FRAME_RATE_COMPATIBILITY_DEFAULT` for games and animations.
  - No guarantees: "there is no guarantee that your app will get the frame rate you request". The system may pick a multiple of the request (60 → 120). Battery saver is one of the limiting factors.
  - "A game that intends to not run higher than 60Hz (to reduce power usage...) can call `Surface.setFrameRate(60, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)`."
  - Don't call it "every frame or multiple times per second"; a switch can drop a frame.
  - For even pacing, use presentation timestamps (`EGL_ANDROID_presentation_time`, `VK_GOOGLE_display_timing`, `ASurfaceTransaction_setDesiredPresentTime`) or the Android Frame Pacing library.
  - `preferredRefreshRate` is the window-level alternative.
  — [Frame rate guide](https://developer.android.com/media/optimize/performance/frame-rate)
- **ARR availability:**
  - ARR "introduced in Android 15, enables the display refresh rate on supported hardware to adapt to the content frame rate using discrete VSync steps", reducing power while avoiding janky mode switches.
  - Android 16 adds `Display.hasArrSupport()` and `Display.getSuggestedFrameRate(int)`, and restores `Display.getSupportedRefreshRates()`. RecyclerView 1.4 supports ARR.
  — [Android 16 features](https://developer.android.com/about/versions/16/features)
- **ARR details:**
  - Supported on Android 15 QPR1+ on devices that implement the required HAL APIs. — [Optimize frame rate with ARR](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate)
  - **Categories:** `REQUESTED_FRAME_RATE_CATEGORY_DEFAULT`, `NO_PREFERENCE`, `NORMAL` ("normally 60 Hz or close to it") and `HIGH` (higher smoothness, higher power). Views can also request 30, 60 or 120 explicitly.
  - **Voting:** rates that are multiples of each other → the highest wins. Non-multiples → any vote above 60 counts as "High", otherwise "Normal". A 60 Hz vote plus a High vote gives 120 Hz.
  - **Touch boost:** `ACTION_DOWN` raises the rate to High "for some time after touch release". It can be disabled with `Window.setFrameRateBoostOnTouchEnabled(false)`, which is not recommended.
  - **Automatic boosts:** animations that move or resize content automatically get a higher rate. Explicit rates on `SurfaceView`/`TextureView` are respected. `View.setFrameContentVelocity()` drives velocity-based rates and must be updated every frame.
  - **On by default:** ARR can be disabled with `Window.setFrameRatePowerSavingsBalanced(false)` or `android:windowIsFrameRatePowerSavingsBalanced`, which is also not recommended.
  - Rates set on a ViewGroup don't propagate to its children.
  - For "small animations such as progress bars and audio visualizers, this high refresh rate is unnecessary, and results in high power consumption."
  — [Optimize frame rate with ARR](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate)
- **Compose support:** "Compose 1.9 adds ARR support" through `Modifier.preferredFrameRate(frameRate: Float)` and `Modifier.preferredFrameRate(frameRateCategory: FrameRateCategory)` (e.g. `FrameRateCategory.High`). Votes from all composables are consolidated per frame. — [Optimize frame rate with ARR](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate); [preferredFrameRate API reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/preferredFrameRate.modifier)
  - `Modifier.requestedFrameRate` was renamed to `Modifier.preferredFrameRate`, `FrameRateCategory.NoPreference` was removed, and when applied several times the highest value wins. — [Compose UI release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui) (search snippet)
- `View.setRequestedFrameRate` / the `RequestedFrameRate` property appears in the API 35 surface. — [Microsoft Learn mirror (secondary)](https://learn.microsoft.com/en-us/dotnet/api/android.views.view.requestedframerate?view=net-android-35.0)
- **Avoiding recomposition-driven animation:**
  - Defer state reads as long as possible.
  - Use lambda modifiers (`Modifier.offset { }`, `graphicsLayer { }`) so updates skip composition. With `offset { }`, "Compose skips composition and goes straight to layout phase".
  - For colors animated every frame, replace `Modifier.background(color)` with `drawBehind { drawRect(color) }`. "Compose skips composition and layout, proceeding directly to the draw phase."
  - Use `derivedStateOf` to limit recompositions, and avoid backwards writes.
  — [Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- **Choreographer timing:** Perfetto's FrameTimeline expected timeline follows "the Choreographer callback schedule". The actual timeline runs from `Choreographer#doFrame` until SurfaceFlinger or the GPU finishes. — [Perfetto FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline)
- **Android 17 (targetSdk 37):**
  - Apps get "a new lock-free implementation of android.os.MessageQueue that improves performance and reduces missed frames".
  - ART adds generational garbage collection to its Concurrent Mark-Compact collector, making young-generation collections cheaper.
  — [Android 17 features](https://developer.android.com/about/versions/17/features) (search snippet; the fetched page links to a separate "MessageQueue behavior change" guide)

### Inferences
- **Frame loop pattern:**
  - Drive sky time from a single `LaunchedEffect` loop using `withFrameNanos` (or `withInfiniteAnimationFrameNanos`) and store it in a `mutableFloatStateOf`.
  - Read that time **only** inside `drawWithCache { onDrawBehind { shader.setFloatUniform("iTime", t()); drawRect(brush) } }` or a `graphicsLayer {}` lambda. Each frame then only invalidates draw.
  - Never read animated values in composition. For example, avoid `Modifier.background(animatedColor)` and composable parameters that take animated values.
  - Make every animation time-based (seconds, not frame counts) so speed stays the same at 30, 60 or 120 Hz.
- **Suggested frame-rate policy with ARR** (API 35 QPR1+ with `hasArrSupport()`; API 36 to query):

  | Situation | Rate | How |
  |---|---|---|
  | Ambient drift (clouds, slow fog) | ~30 Hz | `Modifier.preferredFrameRate(30f)` on the sky |
  | Rain/snow particles, active wind | 60 Hz (NORMAL) | Frame-rate vote |
  | Parallax drag, lightning strike, transitions | HIGH | Short-lived vote |

  The highest vote on screen wins, so a stray shimmer or `CircularProgressIndicator` can pull the whole window to 120 Hz. Touch boost will temporarily override low votes, which is by design.
- **Without ARR** (API 33–34, or devices with no HAL support):
  - Frame-rate votes may be ignored, and an animating window typically renders at the panel's current rate (often 120 Hz).
  - Options include `Surface.setFrameRate` for SurfaceView renderers, `WindowManager.LayoutParams.preferredRefreshRate`, or throttling invalidation to every Nth vsync. Throttling saves GPU/CPU time even if the panel stays at 120 Hz, but must be even (every 2nd or 4th vsync) to avoid judder.
  - Which of these each OEM honors must be measured.
- **Stable votes:** avoid frequent frame-rate changes. Pick a rate per scene or state with hysteresis, since each change can drop a frame.

### Gaps
- `Modifier.preferredFrameRate` behavior on API 33–34 (probably a no-op) is not verified. The meaning of `Display.getSuggestedFrameRate(int)`'s argument (probably a category constant) is not verified.
- No published figures for ARR power savings. No list of devices with ARR HAL support.
- A `REQUESTED_FRAME_RATE_CATEGORY_LOW` constant appears in one search snippet but not in the fetched ARR page. Verify it in the API reference.
- The Android 17 MessageQueue behavior-change guide (compatibility risks for apps that reflect into MessageQueue internals) was not fetched.

---

## Q4. Adaptive quality: ADPF thermal headroom & listeners, Performance Hint API, Game Mode API, battery saver / low-power detection, pausing when not visible, frame-budget heuristics

### Takeaway
A non-game app can still use most of ADPF. The useful pieces are thermal status listeners and headroom (API 30; poll no more than once every 10 s), thermal headroom thresholds (API 35), Performance Hint sessions for your own render thread (power-efficiency mode and CPU+GPU work durations in API 35), and CPU/GPU headroom (API 36). The Game Mode API only works for apps declared as games. Combine these with battery saver, "remove animations", visibility and measured frame times in a quality governor with hysteresis that steps down to a sustainable level.

### Cited Findings
**Thermal API**
- API levels: `PowerManager.getThermalHeadroom(int forecastSeconds)` is API 30; the NDK equivalent is API 31; `getThermalHeadroomThresholds()` was added in Android 15. — [ADPF Thermal API](https://developer.android.com/games/optimize/adpf/thermal)
- Thermal statuses:
  - `NONE`
  - `LIGHT` and `MODERATE`: "no significant impact on performance"
  - `SEVERE`, `CRITICAL`, `EMERGENCY` and `SHUTDOWN`: "Significant throttling that impacts performance"
  — [ADPF Thermal API](https://developer.android.com/games/optimize/adpf/thermal)
- **Headroom scale:** 0.0 = no throttling; 1.0 = `THERMAL_STATUS_SEVERE`. Heuristics:
  - **> 1.0:** status could already be SEVERE or higher; reduce workload immediately.
  - **> 0.95:** could be MODERATE or higher; reduce immediately.
  - **> 0.85:** could be LIGHT; "keep watchout and reduce if possible".
  - Example: if `getThermalHeadroom(30)` returns 0.8, headroom is expected to reach 0.8 in 30 seconds.
  — [ADPF Thermal API](https://developer.android.com/games/optimize/adpf/thermal)
- **Call limits:** "You shouldn't call it more than once every 10 seconds". Calling too often returns `NaN`. Avoid calling from multiple threads. If the first value is `NaN`, the API isn't available on the device. — [ADPF Thermal API](https://developer.android.com/games/optimize/adpf/thermal)
- **Listener:** `PowerManager.addThermalStatusListener(OnThermalStatusChangedListener)` and `getCurrentThermalStatus()`. — [ADPF Thermal API](https://developer.android.com/games/optimize/adpf/thermal)
- **Mitigations:** reduce frame rate, fidelity and resolution; lower GPU quality settings. "Once the device overheats, the workload must drop below the sustainable performance level in order to dissipate heat... make sure to find a sustainable quality level." — [ADPF Thermal API](https://developer.android.com/games/optimize/adpf/thermal)

**Performance Hint API and CPU/GPU headroom**
- Android 15 adds `PerformanceHintManager.Session.setPreferPowerEfficiency(boolean)` ("great for long-running background workloads") and `reportActualWorkDuration(android.os.WorkDuration)`. `WorkDuration` reports both GPU and CPU durations "allowing the system to adjust CPU and GPU frequencies together". — [Android 15 features](https://developer.android.com/about/versions/15/features)
- The API surface:
  - `PerformanceHintManager.createHintSession(int[] tids, long initialTargetWorkDurationNanos)`
  - `getPreferredUpdateRateNanos()`
  - Session methods `reportActualWorkDuration`, `updateTargetWorkDuration`, `setThreads`, `setPreferPowerEfficiency`, `sendHint` and `close`
  — [PerformanceHintManager reference](https://developer.android.com/reference/android/os/PerformanceHintManager)
- Android 16 adds `SystemHealthManager.getCpuHeadroom(CpuHeadroomParams)` and `getGpuHeadroom(GpuHeadroomParams)`. The params let you choose the time window and whether you get average or minimum resource availability. Google recommends using them together with the ADPF thermal APIs. — [Android 16 features](https://developer.android.com/about/versions/16/features)
- ADPF "focus is on games, but you can also use the features for other performance-intensive apps". Its components include:
  - Power Efficiency Mode (Android 15).
  - Fixed Performance Mode, for benchmarking without dynamic clocking.
  — [ADPF overview](https://developer.android.com/games/optimize/adpf)

**Game Mode API (games only)**
- The Game Mode API requires `android:appCategory="game"` (or `isGame`). "If an application is not a game, the getGameMode() method always returns GAME_MODE_UNSUPPORTED". API 31+. — [Game Mode API](https://developer.android.com/games/optimize/adpf/gamemode/gamemode-api)

**Battery saver, reduced motion, sensors, vitals**
- **Battery saver:** `PowerManager.isPowerSaveMode()` returns true in power save mode, when "applications should reduce their functionality in order to conserve battery as much as possible". `ACTION_POWER_SAVE_MODE_CHANGED` is only delivered to receivers registered at runtime. — [PowerManager reference (Microsoft Learn mirror, secondary)](https://learn.microsoft.com/en-us/dotnet/api/android.os.powermanager.ispowersavemode?view=net-android-35.0); [PowerManager (archived Android docs mirror)](https://emanual.github.io/Android-docs/reference/android/os/PowerManager.html)
- Battery saver is one of the factors that can stop an app getting its requested refresh rate. — [Frame rate guide](https://developer.android.com/media/optimize/performance/frame-rate)
- **Reduced motion:** cross-platform frameworks check `ValueAnimator.areAnimatorsEnabled()` (the user's "remove animations"/animator duration scale). They fall back to the power-save check on API levels without it, because power saving disables animations. — [Xamarin.Forms AndroidTicker (secondary)](https://github.com/xamarin/Xamarin.Forms/blob/master/Xamarin.Forms.Platform.Android/AndroidTicker.cs)
- **Sensors:** "If a sensor listener is registered and its activity is paused, the sensor will continue to acquire data and use battery resources unless you unregister the sensor." — [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- **Android vitals battery thresholds:** core vitals include **Excessive battery usage** (bad-behavior threshold 1% overall) and **Excessive partial wake locks** (5%), alongside user-perceived crash rate (1.09%) and ANR rate (0.47%). — [Android vitals](https://developer.android.com/topic/performance/vitals)

### Inferences
- **Quality governor (synthesis).** Inputs:
  - Thermal status via the listener (push).
  - `getThermalHeadroom(10)`, polled at most every 10 s from a single thread.
  - API 36+: `SystemHealthManager.getGpuHeadroom` for GPU-bound shaders (call limits unknown).
  - `isPowerSaveMode` plus the runtime-registered broadcast.
  - `ValueAnimator.areAnimatorsEnabled()` for the accessibility "remove animations" setting.
  - Display state: refresh rate, `hasArrSupport()`, HDR/SDR ratio.
  - Lifecycle and visibility.
  - Rolling frame statistics from JankStats/FrameMetrics (`frameDurationCpuNanos`, `frameOverrunNanos` on API 31+; see Q5).
- **Tier ladder:**

  | Tier | Condition | Content |
  |---|---|---|
  | T3 | Headroom < 0.7, no power save | 120 Hz on interaction, full-res AGSL, HDR highlights, full particles, sensor parallax |
  | T2 (default) | Normal | 60 Hz, full or 0.75x resolution, no HDR |
  | T1 | Headroom > 0.85 (LIGHT), or overruns on > 5% of frames over 5 s | 30 Hz, 0.5x resolution, fewer octaves and particles, parallax at `SENSOR_DELAY_UI` |
  | T0 | Headroom > 0.95 / MODERATE or worse, power save, or "remove animations" | Static pre-rendered frame with cross-fades on weather changes |

  Step down immediately. Step up only after a sustained cool, stable period (for example 30–60 s) to avoid oscillating, in line with Google's "find a sustainable quality level" advice.
- **Pause when not visible:**
  - Stop the frame loop and unregister sensors below `Lifecycle.State.RESUMED`, or `STARTED` if you want to keep animating in multi-window.
  - Also stop when the sky is fully covered (full-screen sheets or dialogs), and when the screen is off.
  - With Compose, key the frame loop on lifecycle (`LifecycleResumeEffect`/`repeatOnLifecycle`) so no invalidations are scheduled while paused.
- **Performance Hint API** is worth using only if the sky moves to a custom SurfaceView render thread. Create a session with that thread's TID and the frame budget as the target, report actual durations each frame, and on API 35+ call `setPreferPowerEfficiency(true)` in ambient tiers. For the normal HWUI/Compose path the app has no RenderThread TID to register (see Gaps).
- **Game Mode** doesn't apply unless the app declares itself a game, which is inappropriate for a weather app.

### Gaps
- I could not verify from a primary source whether HWUI's own RenderThread already uses ADPF hint sessions. This matters because it would mean Compose apps benefit automatically.
- API levels for `PerformanceHintManager` (believed API 31) and `addThermalStatusListener` (believed API 29) were not stated in the fetched text.
- Rate limits and OEM availability for the Android 16 `getCpuHeadroom`/`getGpuHeadroom` APIs are not documented in the sources found.
- Not researched: device-tier bootstrapping signals such as Android performance class / Jetpack Core Performance and `ActivityManager.isLowRamDevice()`.

---

## Q5. Measuring: Macrobenchmark frame timing, JankStats, Android vitals slow/frozen frame thresholds, Perfetto GPU/CPU tracing, Baseline Profiles & shader startup impact

### Takeaway
Measure in three layers:
- **Lab:** Macrobenchmark `FrameTimingMetric` (P50–P99 `frameDurationCpuMs` and `frameOverrunMs`; overrun needs API 31+) and the experimental `PowerMetric` (physical Pixel 6+ only).
- **Diagnosis:** Perfetto FrameTimeline (Android 12+), which classifies jank as app-side or SurfaceFlinger-side.
- **Field:** JankStats, where jank defaults to 2x the frame period, plus Android vitals (slow frames over 16 ms, frozen frames over 700 ms; the games-only "slow sessions" metric is over 25% of frames slower than 50 ms).

Baseline Profiles speed up ART code by about 30% from first launch, but the docs cover only code, not GPU shader compilation.

### Cited Findings
**Macrobenchmark**
- `FrameTimingMetric`:
  - `frameOverrunMs` is "the amount of time a given frame misses its deadline by". Positive values mean dropped frames. "This metric is available only on Android 12 (API level 31) and later."
  - `frameDurationCpuMs` is CPU time across the UI thread and RenderThread.
  - Reported as P50/P90/P95/P99. Look at P95/P99, where positive overrun "indicates that recompositions are stalling the main thread".
  — [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- `PowerMetric` (experimental):
  - Measures system-wide energy and power per rail (CPU, DISPLAY, GPU, MEMORY, NETWORK, etc., in µW / µWs).
  - Only works on "physical Google Pixel 6, Pixel 6 Pro, and newer physical devices".
  - Advice: "lock screen brightness to a fixed value, maintain a stable device temperature, and close competing background processes".
  — [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- Other metrics:
  - `StartupTimingMetric`: time to initial display and time to full display. In Compose, signal full display with `ReportDrawn`/`ReportDrawnWhen`/`ReportDrawnAfter`.
  - `TraceSectionMetric` (experimental): counts and times custom `trace()` sections.
  — [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)

**JankStats**
- How it measures: `FrameMetrics` on API 24+, `OnPreDrawListener` on older versions.
- **Default jank threshold:** "a frame taking twice as long to render as the current refresh rate", adjustable through `jankHeuristicMultiplier`.
- **State tagging:** `PerformanceMetricsState` attaches app state to frames (e.g. `putState("Scene","Storm")`).
- **FrameData fields:** `frameDurationUiNanos`, `isJank` and `states`; `frameDurationCpuNanos` (API 24+); `frameOverrunNanos` (API 31+).
- **Usage caveats:** the `FrameData` object is reused, so copy it. Return quickly from the callback, which runs on the FrameMetrics thread on API 24+.
- Artifact: `androidx.metrics:metrics-performance:1.0.0`. There is a `JankStatsAggregator` sample for batching reports.
— [JankStats](https://developer.android.com/topic/performance/jankstats)

**Android vitals thresholds**
- Slow frames take 16–700 ms. Frozen frames take 700 ms–5 s: "No frames in your app should ever take longer than 700ms to render." ANRs are over 5 s. Slow and frozen frames are tracked separately because first frames after launch or navigation are expected to be slow. — [Slow rendering](https://developer.android.com/topic/performance/vitals/render)
- **Slow sessions (games only):**
  - "A slow session is a session in which more than 25% of the frames are slow". A frame is slow if it is not presented within 50 ms of the previous frame (about 20 FPS).
  - A second variant uses a 34 ms (about 30 FPS) target.
  - Monitoring starts after the game has run for one minute.
  — [Slow Sessions (games only)](https://developer.android.com/topic/performance/issues/slow-session); [Games: Slow sessions](https://developer.android.com/games/optimize/vitals/slow-session) (search snippet)
- Excessive frozen frames is tracked as the "percentage of daily sessions with more than 0.1% of frames with a time longer than 700 ms". — (search snippet; the exact source page was not fetched)
- Slow rendering and frozen frames do not appear among the core vitals with bad-behavior thresholds. Those thresholds cover crashes (1.09%), ANRs (0.47%), excessive battery usage (1%) and partial wake locks (5%). — [Android vitals](https://developer.android.com/topic/performance/vitals)

**Perfetto, profiling triggers and Baseline Profiles**
- **Perfetto FrameTimeline** (Android 12+):
  - Adds Expected and Actual timelines per app.
  - Jank types: AppDeadlineMissed, BufferStuffing, SurfaceFlingerCpuDeadlineMissed, SurfaceFlingerGpuDeadlineMissed, DisplayHAL, PredictionError, Unknown.
  - Present types: early, on-time or late.
  - Enable it with the data source `android.surfaceflinger.frametimeline`.
  — [Perfetto FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline)
- **Android 17 `ProfilingManager` triggers** for field diagnostics: `TRIGGER_TYPE_COLD_START`, `TRIGGER_TYPE_OOM`, `TRIGGER_TYPE_KILL_EXCESSIVE_CPU_USAGE` and `TRIGGER_TYPE_ANOMALY` (excessive binder calls or memory). The callback fires "prior to any system imposed enforcements". — [Android 17 features](https://developer.android.com/about/versions/17/features)
- **Baseline Profiles:** "improve code execution speed by about 30% from the first launch by avoiding interpretation and just-in-time (JIT) compilation steps for included code paths". They can cover startup, navigation, scrolling and whole flows. Startup Profiles are a separate build-time DEX-layout optimization. The page does not mention GPU shader compilation. — [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview)

### Inferences
- **Lab benchmarks (CI):**
  - One Macrobenchmark per weather scene: launch, then sit idle on the animated scene for 10–20 s, then do a parallax drag.
  - Collect `FrameTimingMetric`, plus `PowerMetric` on a Pixel 6+ with brightness locked, for each quality tier. This gives a GPU/display energy budget per tier, e.g. a video loop vs AGSL vs a Filament sky.
  - Gate regressions on P95/P99 `frameOverrunMs` staying at or below 0.
- **Field monitoring:**
  - JankStats with `PerformanceMetricsState` tags for `scene`, `qualityTier`, `hdr`, `refreshRate` and `arr`, sampled and aggregated.
  - The same frame stream can feed the Q4 governor.
  - On Android 17, register `ProfilingManager` triggers (especially excessive CPU and anomaly) to catch runaway animation loops.
- **Shader warm-up.** Baseline Profiles won't pre-compile GPU work. Watch for a first-appearance hitch when each weather shader is used (RenderThread time in Perfetto), and pre-construct and pre-draw shaders during launch or scene prefetch. Put the shader-setup Kotlin paths (scene composition, RuntimeShader creation) into the Baseline Profile journeys.
- **Metric choice.** For an always-animated app, JankStats' 2x-period rule and vitals' 16 ms slow-frame rule both miss small hitches at 120 Hz. A 12 ms frame on a 120 Hz panel misses its 8.3 ms deadline (a visible hitch), yet it is neither "jank" under JankStats' default (over 16.7 ms at 120 Hz) nor a vitals "slow frame" (over 16 ms). Pick internal SLOs per refresh rate, for example the share of frames with positive `frameOverrun`, rather than a single ms threshold.

### Gaps
- The exact Play Console definition and threshold for "slow rendering" for non-game apps (for example the percentage of sessions with more than 50% of frames over 16 ms) was not confirmed from a fetched page.
- GPU-side tooling wasn't researched: Android GPU Inspector counters and Perfetto GPU frequency/render-stage data sources. No sourced numbers were found for AGSL compile time or first-draw pipeline creation cost.

---

## Q6. Sensors for parallax: rotation vector vs game rotation vector, power cost, sampling rates

### Takeaway
For device-tilt parallax, prefer `TYPE_GAME_ROTATION_VECTOR`. It fuses the accelerometer and gyroscope, must not use the magnetometer, and so avoids magnetic jumps. Sample at `SENSOR_DELAY_GAME` (20 ms, 50 Hz) or slower, smooth the values to the display frame, and unregister whenever you're not visible. Apps targeting API 31+ are capped at 200 Hz without a special permission, and background apps get no continuous sensor events (API 28+).

### Cited Findings
- **Delay constants:** `SENSOR_DELAY_NORMAL` 200,000 µs, `SENSOR_DELAY_UI` 60,000 µs, `SENSOR_DELAY_GAME` 20,000 µs, `SENSOR_DELAY_FASTEST` 0 µs. Since API 11 you can pass an absolute microsecond delay. — [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- **Choose the slowest workable rate:** "specify the largest delay that you can because the system typically uses a smaller delay than the one you specify... Using a larger delay imposes a lower load on the processor and therefore uses less power." — [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- **Rate limits (target API 31+):**
  - `registerListener()` is limited to **200 Hz**.
  - `SensorDirectChannel` is limited to `RATE_NORMAL` ("usually about 50 Hz").
  - Faster rates need `HIGH_SAMPLING_RATE_SENSORS`, otherwise a `SecurityException` is thrown.
  — [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- **Background:** on Android 9+ (API 28), background apps don't receive events from continuous sensors such as the accelerometer and gyroscope. — [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- **Unregister:** unregister in `onPause`; otherwise the sensor keeps acquiring data and using battery. — [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- **Power per sensor:** `Sensor.getPower()` returns a sensor's power requirement, and `getMinDelay()` identifies streaming sensors. — [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- **Sensor composition (AOSP):**
  - `TYPE_ROTATION_VECTOR` uses accelerometer, magnetometer and gyroscope (if present).
  - `TYPE_GAME_ROTATION_VECTOR` uses accelerometer and gyroscope and "MUST NOT USE magnetometer".
  - `TYPE_GEOMAGNETIC_ROTATION_VECTOR` uses accelerometer and magnetometer, must not use the gyroscope, and is marked **low power**.
  - All three are continuous and non-wake-up.
  — [AOSP sensor types](https://source.android.com/docs/core/interaction/sensors/sensor-types)

### Inferences
- **Parallax recipe:**
  - Register `TYPE_GAME_ROTATION_VECTOR` at `SENSOR_DELAY_GAME` (50 Hz) in top tiers and `SENSOR_DELAY_UI` (~16.7 Hz) in lower tiers.
  - Convert to a rotation matrix or pitch/roll relative to the orientation captured when the screen was entered. Low-pass filter it (a critically damped spring), then interpolate per vsync in the draw lambda.
  - Only the sky's uniform/offset changes, so there is no recomposition.
  - Clamp tilt to a few degrees of parallax.
- **Fallbacks:** without a gyroscope, use `TYPE_GEOMAGNETIC_ROTATION_VECTOR` (low power but noisy) or accelerometer/gravity-only tilt.
- **When to turn parallax off:** in battery saver, when "remove animations" is on, and in T0/T1 tiers. Treat it as a motion-sensitive effect for accessibility.
- **Don't oversample:** for UI parallax there is no benefit above about 60 Hz. The 200 Hz cap is irrelevant, and requesting it would only cost power.

### Gaps
- I found no authoritative mA or mW figures for the gyroscope-based fused rotation vector on modern SoCs, where many devices fuse on a low-power sensor hub. Query `Sensor.getPower()` per device and measure with `PowerMetric`.

---

## Q7. Live wallpapers and widgets as extensions of a cinematic app (RemoteViews limits, WallpaperService with GL, battery rules)

### Takeaway
Widgets can't host a live animated renderer. RemoteViews and Glance allow only a fixed set of framework views, no custom views, and only touch and vertical-swipe gestures. The realistic approach is condition-specific pre-rendered imagery, with Remote Compose (`RemoteViews.DrawInstructions`) as an emerging but still unsettled path to richer drawn widgets. A live wallpaper (`WallpaperService.Engine` drawing to its own `SurfaceHolder` with GL/Vulkan or a hardware Canvas) can reuse the sky renderer. It must stop drawing whenever it's invisible and should run at low, fixed frame rates, because it is visible for far longer than the app.

### Cited Findings
**Widgets**
- "You can't use custom views or subclasses of the views that are supported by `RemoteViews`." `ViewStub` is supported, and Android 12 (API 31) added stateful `CheckBox`/`Switch`/`RadioButton` (set their state with `setCompoundButtonChecked`). — [Create a simple widget](https://developer.android.com/develop/ui/views/appwidgets)
- "The only gestures available for widgets are touch and vertical swipe". Jetpack Glance is the recommended Compose-based way to build widgets. — [App widgets overview](https://developer.android.com/develop/ui/views/appwidgets/overview)
- Android 15 (API 35) adds generated widget previews: `AppWidgetManager.setWidgetPreview(ComponentName, category, RemoteViews)` replaces the static picker image with a `RemoteViews` preview showing real data. — [Android 15 features](https://developer.android.com/about/versions/15/features)
- **Remote Compose** (secondary source):
  - `RemoteViews.DrawInstructions` and a binary "RemoteCompose" format serialize drawing instructions that the host renders. There's no view inflation.
  - "RemoteCompose documents have some amount of interactivity (animations, layouts, click callbacks)".
  - An AndroidX `androidx.compose.remote` library exists.
  - The article says both "Android 16 ships" DrawInstructions and that the framework player exists "starting with API 35". The two statements are inconsistent.
  — [Luca Fioravanti, Medium (secondary)](https://medium.com/@fioravanti.luka/glance-remoteviews-and-remotecompose-what-actually-changed-in-android-16-4afc4b63b0ad); [RemoteViews.DrawInstructions reference](https://developer.android.com/reference/kotlin/android/widget/RemoteViews.DrawInstructions) (the page exists; details didn't render in fetch)

**Live wallpapers**
- `WallpaperService.Engine` provides:
  - Visibility and state: `onVisibilityChanged(boolean)`, `isVisible()`, `isPreview()`.
  - Home-screen scrolling and zoom: `onOffsetsChanged(...)`, `onZoomChanged(float)`, `shouldZoomOutWallpaper()`.
  - Theming: `onComputeColors()`/`notifyColorsChanged()`.
  - Other states: `onAmbientModeChanged(boolean)`, `onDimAmountChanged(float)`, `getWallpaperFlags()`, `onApplyWindowInsets`, `onSurfaceRedrawNeeded`.
  - Setup: `setOffsetNotificationsEnabled`, `setTouchEventsEnabled`, `getSurfaceHolder()`, `onCreate`/`onDestroy`.
  - The reference stresses using CPU only while the wallpaper is visible, i.e. suspend rendering when `onVisibilityChanged(false)`.
  — [WallpaperService.Engine reference](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine)
- Android 16 (API 36) adds `WallpaperDescription` and `WallpaperInstance`. They identify separate instances of one live wallpaper service, e.g. different content on home and lock screen, and the picker and `WallpaperManager` use this metadata. — [Android 16 features](https://developer.android.com/about/versions/16/features)
- "Users expect a live wallpaper to stop consuming the battery when it gets sent to the background and quickly start back up when they return to the home screen." — [OpenGL ES 2 for Android (Pragmatic Bookshelf, via O'Reilly), search snippet](https://www.oreilly.com/library/view/opengl-es-2/9781941222560/f_0128.html)
- Frame-rate hints apply to wallpaper surfaces like any other `Surface`: `Surface.setFrameRate` (API 30). SurfaceView/TextureView explicit frame rates are respected under ARR. — [Frame rate guide](https://developer.android.com/media/optimize/performance/frame-rate); [ARR](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate)
- Excessive battery usage is now an Android vitals core metric (1% bad-behavior threshold). — [Android vitals](https://developer.android.com/topic/performance/vitals)

### Inferences
- **Widget strategy:**
  - Build with Glance, which produces RemoteViews and inherits their limits.
  - Show a pre-rendered sky image per condition × time of day, generated in-app (for example by rendering the AGSL sky offscreen to a bitmap in a Worker) and pushed on weather updates.
  - Rely on system cross-fades or layout changes rather than continuous animation.
  - Use Android 15 generated previews to show the real current sky in the picker.
  - Treat Remote Compose/`DrawInstructions` as a watch item for lightweight animated widgets once its public API level and launcher support are confirmed.
- **Wallpaper strategy:**
  - Render in `WallpaperService.Engine` on its own render thread into `getSurfaceHolder()`. Options: Filament (UiHelper/SurfaceView-style), a custom GLES/Vulkan renderer, or `Surface.lockHardwareCanvas()` + RuntimeShader to reuse the AGSL code (Canvas path not verified here).
  - Call `surface.setFrameRate(30f, FRAME_RATE_COMPATIBILITY_DEFAULT)` for the ambient loop.
  - Stop the loop on `onVisibilityChanged(false)`, in ambient mode, in battery saver and on thermal escalation.
  - Use `onOffsetsChanged`/`onZoomChanged` for launcher parallax instead of sensors, which avoids the sensor's power cost.
  - Call `notifyColorsChanged()` when the weather scene changes, so Material You and theming follow the sky.
  - Use `isPreview()` to run a richer demo only in the picker.
  - On API 36+, use `WallpaperDescription` for distinct home and lock variants.
  - A wallpaper runs whenever the launcher or lock screen is visible, so default it to the T1/T2 budget (Q4) and never to HDR.
  - The wallpaper service runs in the app's own package, so its drain very likely counts toward the app's battery attribution and the "excessive battery usage" vital. This is an inference; how vitals attributes wallpaper usage was not confirmed.

#### Cross-cutting API-level matrix (synthesized from the cited findings above; "believed" = not verified this session)

| Capability | Min API / version |
|---|---|
| `RuntimeShader`/AGSL, AGSL `RenderEffect` | 33 (all target devices) |
| Compose progressive blur | Compose 1.13.0-alpha03 + API 33 |
| `RenderEffect` blur | 31 (believed) |
| Ultra HDR display via `COLOR_MODE_HDR`, `Bitmap.hasGainmap()`, `Display.getHdrSdrRatio()` | 34 |
| `Window.setDesiredHdrHeadroom()`, `Canvas.clipShader()`, `Matrix44` | 35 |
| `RuntimeColorFilter`, `RuntimeXfermode`, `HEIC_ULTRAHDR` | 36 |
| `RenderNode.setBackdropRenderEffect` | 37.2 (Android 17 QPR2) |
| `Surface.setFrameRate` | 30 |
| `View.setRequestedFrameRate`/categories, touch-boost and ARR window toggles | 35 (ARR active on 15 QPR1+ with HAL support) |
| `Display.hasArrSupport()`, `getSuggestedFrameRate()` | 36 |
| Compose `Modifier.preferredFrameRate` | Compose 1.9 |
| `getThermalHeadroom` | 30 |
| `getThermalHeadroomThresholds`, hint-session power efficiency, `WorkDuration` | 35 |
| `SystemHealthManager` CPU/GPU headroom | 36 |
| Game Mode | 31, games only |
| `FrameTimingMetric.frameOverrunMs`, JankStats `frameOverrunNanos`, Perfetto FrameTimeline | 31 |
| Sensor 200 Hz cap | Apps targeting 31+ |
| Generated widget previews | 35 |
| `WallpaperDescription`/`WallpaperInstance` | 36 |
| New `ProfilingManager` triggers | 37 |
| Lock-free `MessageQueue` | Apps targeting 37 |

### Gaps
- The complete list of RemoteViews-supported widget classes (e.g. `AnalogClock`, `Chronometer`, `ViewFlipper`, `AdapterViewFlipper`, which allow limited motion) and the `updatePeriodMillis` minimum (commonly documented as 30 minutes) did not appear in the fetched text. Verify both in the "Create a simple widget" guide.
- The public API level and stability of `RemoteViews.DrawInstructions`/Remote Compose for third-party widgets are unresolved: the sources conflict (API 35 flagged vs Android 16), and launcher support is unknown.
- There is no official Android guidance on live-wallpaper frame-rate or battery budgets beyond "only use CPU while visible". It is also unconfirmed whether battery saver or OEM power managers throttle or suspend wallpaper services, and which API level `onZoomChanged` needs (believed API 30).
