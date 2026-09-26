# Motion, Interaction, Haptic & Sound Design for a Cinematic Weather App (Android / Jetpack Compose, as of Sept 2026)

Research date: 2026-09-26. Guidance older than 2024 is labelled with its year. "Derived" numbers in Inferences are my own arithmetic from cited constants, not published values.

## 1. Motion systems: M3 Expressive physics, Apple fluid interfaces and Liquid Glass, gesture continuity, interruptibility, momentum, rubber-banding, and the springs and durations premium apps use

### Takeaway
Google and Apple now both build motion on springs with two perceptual controls: speed (stiffness or response) and bounce (damping). Both keep velocity when a gesture hands off to an animation or when an animation is interrupted. M3 Expressive publishes exact spring tokens. Its expressive default spatial spring is damping 0.8 with stiffness 380, and its "effects" springs (colour, opacity) never overshoot. Apple's rule of thumb (WWDC18/WWDC23) is to start with no bounce, add bounce only when the gesture carries momentum, and avoid bounce above about 0.4.

### Cited Findings
**Material 3 Expressive motion physics (2025)**
- M3 Expressive replaces the older duration/easing motion system with a physics (spring) system. A spring is defined by stiffness ("how quickly the animation resolves") and damping ratio ("how quickly the bounce decays; lower values allow more overshoot"). — [M3 blog: M3 Expressive motion](https://m3.material.io/blog/m3-expressive-motion-theming)
- The motion scheme has two kinds of spec. **Spatial** specs animate position, orientation, size and shape. **Effects** specs animate properties such as colour and opacity, "where there shouldn't be any overshoot". The Expressive scheme "overshoots the final values to add bounce". — [M3 blog](https://m3.material.io/blog/m3-expressive-motion-theming)
- Spatial and effects tokens each come in three speeds: default, fast and slow. Most motion uses default, small elements may use fast and large elements may use slow. The exact token values differ by device (wearable, phone, tablet) so motion feels fast in context. — [M3 Motion: how it works](https://m3.material.io/styles/motion/overview/how-it-works)
- Exact phone token values in Compose Material 3 source (damping ratio / stiffness):
  - **Expressive:** DefaultSpatial 0.8/380, FastSpatial 0.6/800, SlowSpatial 0.8/200, DefaultEffects 1.0/1600, FastEffects 1.0/3800, SlowEffects 1.0/800. — [ExpressiveMotionTokens.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt)
  - **Standard:** DefaultSpatial 0.9/700, FastSpatial 0.9/1400, SlowSpatial 0.9/300. The effects tokens are the same as Expressive (1.0/1600, 1.0/3800, 1.0/800). — [StandardMotionTokens.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/StandardMotionTokens.kt)
- The Compose API is `MotionScheme`, with `defaultSpatialSpec()`, `fastSpatialSpec()`, `slowSpatialSpec()`, `defaultEffectsSpec()`, `fastEffectsSpec()` and `slowEffectsSpec()`, plus the factories `MotionScheme.standard()` and `MotionScheme.expressive()`. Each spec is a `spring(dampingRatio, stiffness)`. — [MotionScheme.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MotionScheme.kt)
- Release status:
  - The latest stable Compose Material 3 is **1.4.0**. `MaterialExpressiveTheme`, `MotionScheme.expressive` and `LoadingIndicator` are still experimental there and need `@ExperimentalMaterial3ExpressiveApi`.
  - **1.5.0-alpha29** (23 Sep 2026) promotes many Expressive components to stable: buttons, FAB and FAB menu, toggle buttons, menus, top and bottom app bars, and list items.
  - 1.5.0-alpha27 removed `LocalMotionScheme` in favour of `MaterialTheme.motionScheme`.
  - Source: [Compose Material 3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3). A search snippet claiming "Material3 1.5.0 is stable" conflicts with the official release page. Trust the release page.
- The older, pre-Expressive M3 duration and easing tokens are still in Compose:
  - Durations: Short1–4 = 50/100/150/200 ms, Medium1–4 = 250/300/350/400 ms, Long1–4 = 450/500/550/600 ms, ExtraLong1–4 = 700/800/900/1000 ms.
  - Easings: Emphasized = cubic(0.2, 0, 0, 1); EmphasizedDecelerate = (0.05, 0.7, 0.1, 1); EmphasizedAccelerate = (0.3, 0, 0.8, 0.15); Standard = (0.2, 0, 0, 1); StandardDecelerate = (0, 0, 0, 1); StandardAccelerate = (0.3, 0, 1, 1); Legacy = (0.4, 0, 0.2, 1).
  - Source: [MotionTokens.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/MotionTokens.kt)
- Compose's own spring presets:
  - Stiffness: `StiffnessHigh` 10,000; `StiffnessMedium` 1,500; `StiffnessMediumLow` 400; `StiffnessLow` 200; `StiffnessVeryLow` 50.
  - Damping ratio: `DampingRatioHighBouncy` 0.2; `MediumBouncy` 0.5; `LowBouncy` 0.75; `NoBouncy` 1.0.
  - Source: [VectorizedAnimationSpec.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/VectorizedAnimationSpec.kt)
  - `spring()` defaults to NoBouncy and StiffnessMedium. — [AnimationSpec.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/AnimationSpec.kt)

**Apple, "Designing Fluid Interfaces" (WWDC 2018; older but still the canonical reference)**
- Springs are described by **damping** ("from 100% damping, where there will be no overshoot to 0% damping where the spring would oscillate indefinitely") and **response** ("how quickly the value will try and get to the target"). The technical terms are damping ratio and frequency response. — [WWDC18 #803](https://developer.apple.com/videos/play/wwdc2018/803/)
- "We recommend starting with 100% damping, or no overshoot". "If the gesture that's driving the motion itself has momentum, then you should reward that momentum with a little bit of overshoot." — [WWDC18 #803](https://developer.apple.com/videos/play/wwdc2018/803/)
- Music app example: tapping to open Now Playing uses 100% damping because the tap has no momentum. Swiping to dismiss uses **80% damping** "to have a little bit of bounce and squish". — [WWDC18 #803](https://developer.apple.com/videos/play/wwdc2018/803/)
- "Remember to project momentum." The FaceTime PiP window uses release velocity and a deceleration rate (UIScrollView's) to compute an imaginary projected position, then snaps to the corner nearest that projection. The same projection works for scale and rotation. — [WWDC18 #803](https://developer.apple.com/videos/play/wwdc2018/803/)
- Other principles from the same session ([WWDC18 #803](https://developer.apple.com/videos/play/wwdc2018/803/)):
  - **Interruptible and redirectable:** users must be able to change their mind mid-gesture. The iPhone X home/multitasking gesture detects a "pause" from a spike in finger acceleration because a timer was "too slow".
  - **1:1 tracking:** use the touch *history* (velocity) at release, not just the last position.
  - **Spatial consistency:** things leave and return along symmetric paths.
  - **Continuous feedback** on touch-down.
  - Detect all candidate gestures from the start. Double-tap recognisers delay single taps (the talk cites about half a second in one example).
  - Gesture hysteresis is "usually 10 points in iOS".
- Rubber-banding: stopping dead at a hard edge "would feel super harsh and disconcerting". The UI should always tell users they have reached the edge. Motion blur and "motion stretching" (stretching content with velocity) add smoothness: "It's not just about framerate. It's what's in the frames." — [WWDC18 #803](https://developer.apple.com/videos/play/wwdc2018/803/)

**Apple, "Animate with springs" (WWDC 2023)**
- SwiftUI's spring `duration` is a **perceptual duration**, "chosen to be predictable and not move around, even as the other parameters of a spring change". It differs from the unpredictable settling duration. Trigger follow-up UI when a spring is "mostly done" rather than waiting for it to settle. Conversion: mass = 1, stiffness = (2π ÷ duration)². — [WWDC23 #10158](https://developer.apple.com/videos/play/wwdc2023/10158/)
- "When you're not sure, use a spring with bounce 0." Bounce "can also make sense ... if it's going to be used at the end of a gesture". "Be cautious about using values higher than around 0.4." — [WWDC23 #10158](https://developer.apple.com/videos/play/wwdc2023/10158/). The session describes bounce around 0.15 as slight and 0.3 as noticeably bouncy. — [WWDCNotes](https://wwdcnotes.com/documentation/wwdc23-10158-animate-with-springs/)
- On retargeting, "a spring animation uses the velocity it had when it was retargeted as the initial velocity towards its new destination". SwiftUI automatically tracks gesture velocity. — [WWDC23 #10158](https://developer.apple.com/videos/play/wwdc2023/10158/)

**Apple, Liquid Glass (WWDC 2025)**
- "Instead of fading, Liquid Glass objects materialize in and out by gradually modulating the light bending and lensing". — [WWDC25 #219 Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/)
- It "responds to interaction by instantly flexing and energizing with light" and has "an inherent gel-like flexibility". On touch it "illuminates from within", with the glow starting under the fingertip and spreading to nearby glass elements. Elements can "lift up into Liquid Glass temporarily ... the resting state stay[s] visually quiet". — [WWDC25 #219](https://developer.apple.com/videos/play/wwdc2025/219/)
- Controls "dynamically morph" between app states on "a singular floating plane", and menus "pop open" in place. As content scrolls under glass, it "gently dissolves ... into the background". Specular highlights travel around the material, and "in some cases, the lighting responds to device motion". Shadows raise their opacity over text. — [WWDC25 #219](https://developer.apple.com/videos/play/wwdc2025/219/)
- Glass is "best reserved for the navigation layer that floats above the content". "Always avoid glass on glass." — [WWDC25 #219](https://developer.apple.com/videos/play/wwdc2025/219/)
- System settings apply automatically. Reduced Transparency makes glass "frostier". Increased Contrast makes elements "predominantly black or white" with a border. Reduced Motion "decreases the intensity of some effects and disables any elastic properties". — [WWDC25 #219](https://developer.apple.com/videos/play/wwdc2025/219/)

### Inferences
- **Converting between the two systems (derived).** With mass = 1, perceptual duration ≈ 2π/√stiffness and bounce ≈ 1 − dampingRatio. The bounce relation follows from ζ = c/(2√(k·m)) combined with Apple's stiffness formula. Applied to the tokens above:

  | Spring | ζ / k | ≈ perceptual duration | ≈ bounce | Peak overshoot from rest, e^(−ζπ/√(1−ζ²)) |
  |---|---|---|---|---|
  | M3 Standard DefaultSpatial | 0.9 / 700 | 237 ms | 0.10 | ~0.15% (invisible) |
  | M3 Standard Fast / Slow Spatial | 0.9 / 1400, 300 | 168 / 363 ms | 0.10 | ~0.15% |
  | M3 Expressive DefaultSpatial | 0.8 / 380 | 322 ms | 0.20 | ~1.5% |
  | M3 Expressive FastSpatial | 0.6 / 800 | 222 ms | 0.40 | ~9.5% |
  | M3 Expressive SlowSpatial | 0.8 / 200 | 444 ms | 0.20 | ~1.5% |
  | M3 Effects Default / Fast / Slow | 1.0 / 1600, 3800, 800 | 157 / 102 / 222 ms | 0 | 0 |
  | Compose `spring()` default | 1.0 / 1500 | 162 ms | 0 | 0 |
  | Compose StiffnessLow / VeryLow | – / 200, 50 | 444 / 889 ms | – | – |

- What the table shows:
  - Expressive FastSpatial sits exactly at Apple's "be cautious above ~0.4" bounce limit, which fits M3's advice to use it only for small elements.
  - Apple Music's 80% damping swipe-dismiss equals M3 Expressive's default spatial damping of 0.8.
  - Expect more visible overshoot when a gesture hands off real velocity.
- **Weather-app mapping.** Use Expressive spatial springs for gesture-driven moves: dismissing a card, flinging between locations, snapping a day or hour scrubber. Use effects springs (no overshoot) for sky colour, cloud opacity, temperature-number cross-fades and scrims, because a sky that overshoots its colour looks like a glitch. For large, slow "camera" moves (a full-screen sky pan between locations), use SlowSpatial or StiffnessLow/VeryLow (roughly 0.45–0.9 s) so the move feels heavy and cinematic.
- **Tap versus swipe.** Following Apple, a tap-to-open detail should use a critically damped spring (ζ = 1). A swipe or fling that carries velocity should get ζ ≈ 0.8 with the release velocity passed in as initial velocity. The Compose mechanics for this are in section 2.
- **Rubber-banding on Android** (daily horizon, radar scrubber): let the edge stretch with tension, then spring back with an effects-style (no-bounce) or ζ ≈ 0.8 spring, rather than hard-stopping.
- **Liquid Glass ideas that fit an Android weather UI without copying iOS:**
  - Morph controls in place instead of fading them.
  - Materialize overlays by ramping blur/refraction intensity rather than alpha.
  - Brighten surfaces on touch.
  - Keep glass on the navigation or chrome layer only, never glass on glass.
  - On Android, Material 3 Expressive shapes, springs and blur should carry the brand instead of an imitation of iOS.

### Gaps
- m3.material.io pages render client-side and could not be fetched directly, so the device-specific (tablet/wear) token values M3 mentions were not verified. Compose's phone source has a single set, and Wear Compose has its own scheme (not checked).
- The exact default values of SwiftUI's `.smooth`, `.snappy` and `.bouncy` presets were not in the fetched transcript.
- WWDC18's exact momentum-projection code was not reliably retrieved (the fetch tool produced an inconsistent formula), so I did not quote a formula. Section 2 gives the Compose equivalent (`splineBasedDecay().calculateTargetValue`).
- Apple publishes no numeric spring constants for Liquid Glass.
- The WWDC23 damping formula text came back garbled from the fetch tool. The ζ = 1 − bounce relation above is derived, not quoted.

## 2. Jetpack Compose capabilities for cinematic transitions (2025–2026): shared elements, AnimatedContent, predictive back, scroll- and gesture-driven animation, graphicsLayer/RenderEffect/AGSL, Lottie/Rive, and performance caveats

### Takeaway
Compose in 2026 can do "cinematic" natively:
- shared-element hero and container transforms;
- predictive-back-driven, two-stage (deferred) transitions with velocity hand-off (stable in Compose 1.12, Aug 2026);
- gesture physics via `Animatable` with decay and springs;
- mesh gradients, P3/HDR colour and AGSL shaders (API 33+).

Keep frame-rate work in the layout and draw phases (lambda `graphicsLayer`/`offset`), cache shaders and brushes, and budget for offscreen buffers, which alpha < 1 and RenderEffect trigger.

### Cited Findings
**Versions and timeline**
- **Compose 1.10 + Material 3 1.4 (BOM 2025.12.00, 3 Dec 2025):**
  - Pausable composition in lazy prefetch is on by default; it lets the runtime pause work if frame time runs out.
  - `SharedContentConfig.isEnabled` allows conditional shared elements, for example by navigation direction.
  - New `Modifier.skipToLookaheadPosition()` supports "reveal" transitions.
  - `prepareTransitionWithInitialVelocity()` passes gesture velocity into shared-element transitions.
  - Experimental "veil" (scrim) options for Enter/ExitTransition.
  - New `retain` API; Material `Text` autoSize.
  - Material 3 Expressive APIs "continue development in alpha".
  - Source: [Android Developers Blog, Dec '25](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html)
- **Compose 1.12 (BOM 2026.08.00, 12 Aug 2026; compileSdk 37, AGP ≥ 9.1.1):**
  - `DeferredTargetAnimation` is now stable.
  - `DeferredAnimatedContent` and `DeferredAnimatedVisibility` give two-stage, gesture-driven transitions (for example predictive back) with manual control of scale and offset in the deferred phase and "seamless handoff and velocity transfer".
  - `permitTransformDuringDeferredTransition` in `SharedContentConfig`.
  - **`MeshGradientPainter`** for "multi-point, organic color gradients".
  - **Wide colour gamut (P3) and HDR rendering** across the pipeline.
  - `LayerOutsets` lets a `graphicsLayer` draw beyond its measured bounds without implicit clipping.
  - Time to initial display is now comparable to Views.
  - Source: [Android Developers Blog, Aug '26](https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html)
- Android 17 (API 37) was released 16 Jun 2026. — [daily.dev summary](https://daily.dev/posts/qwbiliq3y). Its official features page lists no new animation, haptics or graphics APIs. — [Android 17 features](https://developer.android.com/about/versions/17/features)

**Shared elements**
- APIs:
  - `SharedTransitionLayout` wraps everything.
  - `Modifier.sharedElement()` is for identical content (hero).
  - `Modifier.sharedBounds()` is for visually different content in the same area (container transform) and takes `enter`/`exit` and `resizeMode = ScaleToBounds()`.
  - `rememberSharedContentState(key)` with `isMatchFound`; also `boundsTransform`, `placeHolderSize`, `skipToLookaheadSize()`, `renderInSharedTransitionScopeOverlay`, `clipInOverlayDuringTransition = OverlayClip(shape)` and `sharedElementWithCallerManagedVisibility()`.
  - Source: [Compose shared elements docs](https://developer.android.com/develop/ui/compose/animation/shared-elements)
- Limitations:
  - No `AndroidView`, `Dialog` or `ModalBottomSheet` interop.
  - `ContentScale` snaps rather than animating.
  - Shape clipping is not animated automatically; the workaround is `sharedBounds` plus `animateEnterExit`.
  - Modifier order matters: size modifiers *before* the shared modifier constrain it, and those *after* it are animated.
  - Use data-class keys, not strings.
  - Source: [shared elements docs](https://developer.android.com/develop/ui/compose/animation/shared-elements)
- As extracted, the docs page (updated 2026-09-22) still shows `@OptIn(ExperimentalSharedTransitionApi::class)`. — [shared elements docs](https://developer.android.com/develop/ui/compose/animation/shared-elements). Shared elements work with Navigation Compose (Nav 2 and 3), including predictive back. — [Navigation with shared elements](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation)

**Predictive back**
- Predictive back animations are enabled by default on Android 15 (API 35) and higher. — [Set up predictive back (Compose)](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
- Design spec for custom in-app predictive back:
  - The exiting full-screen surface scales **100% → 90%**; an entering surface goes **110% → 100%**.
  - Keep an **8 dp** margin from the edge. Maximum x-shift is `(screenWidth/20) − 8 dp`, and y-shift likewise with height.
  - A **fade-through at 35% progress**: the exiting screen is fully faded by 35%, and the entering screen fades in from 35%.
  - Interpolator is `PathInterpolator(0, 0, 0, 1)` (STANDARD_DECELERATE), or (0.1, 0.1, 0, 1) to match SystemUI.
  - On commit, the system absorbs fling velocity. On cancel, the surface "swiftly returns".
  - Don't suggest the item is dismissed in the direction of the back swipe.
  - Source: [Predictive back design guide](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back)
- **Conflict:** the design guide says shared-element predictive back "does not work with FragmentManager, Navigation Component, or Navigation Compose". The newer Compose docs say it does with Navigation 2 and 3. — [design guide](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back) vs [Compose nav + shared elements](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation). The design guide is likely older.

**Gesture-driven animation**
- `Animatable` can be driven by both touch and animation:
  - call `stop()` on touch-down;
  - call `snapTo(value + delta)` during drag;
  - record positions in `VelocityTracker`;
  - on release, use `splineBasedDecay<Float>(density).calculateTargetValue(value, velocity)` to project the fling end-point, then either `animateDecay(velocity, decay)` or `animateTo(target, initialVelocity = velocity)`.
  - `animateTo` called during a running animation "interrupts and maintains the velocity".
  - `updateBounds()` clamps the value.
  - Source: [Compose advanced animation (gestures)](https://developer.android.com/develop/ui/compose/animation/advanced)
- Performance: apply animated values in `offset { }` (layout phase) or `graphicsLayer { }` (draw phase) so each frame skips recomposition. — [Compose advanced animation](https://developer.android.com/develop/ui/compose/animation/advanced)
- Rubber-banding: `OverscrollEffect` decorates scroll and fling events through `applyToScroll`/`applyToFling`. `rememberOverscrollEffect()` returns the platform default, and `LazyColumn`/`verticalScroll` accept a custom `OverscrollEffect`. — [OverscrollEffect reference](https://composables.com/docs/androidx.compose.foundation/foundation/interfaces/OverscrollEffect). Wolt published a production case study of a custom Compose overscroll. — [Wolt engineering](https://careers.wolt.com/en/blog/engineering/jetpack-compose-custom-overscroll-effect)

**Graphics layers, RenderEffect, AGSL**
- Prefer the lambda `graphicsLayer { }` for animated properties (scale, translation, rotationX/Y/Z, alpha, clip, shape, renderEffect). `graphicsLayer` affects only drawing, not layout, so content may draw outside its bounds. — [Compose graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)
- Offscreen costs:
  - With the default `CompositingStrategy.Auto`, alpha < 1 or any `RenderEffect` renders into an offscreen buffer.
  - "Setting a `RenderEffect` or overscroll always renders content into an offscreen buffer."
  - `ModulateAlpha` avoids the buffer for non-overlapping content.
  - `Offscreen` is required for BlendMode masking.
  - `drawWithCache` caches Brush, Shader and Path objects until the size or read state changes.
  - Source: [Compose graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)
- AGSL / `RuntimeShader` need **Android 13+**. — [AGSL overview](https://developer.android.com/develop/ui/views/graphics/agsl)
  - In Compose, wrap the shader in `ShaderBrush(RuntimeShader(src))`, draw it in a `Canvas`, and update uniforms (`setFloatUniform("iTime", t)`, `iResolution`) per frame.
  - `RenderEffect.createRuntimeShaderEffect(shader, "background")` filters existing content; it is "more expensive than drawing a custom View".
  - Create shaders once and reuse them.
  - Source: [Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)

**Lottie / Rive**
- A Callstack benchmark (Jan 2023; **React Native** on a Sony Xperia Z3, so old and not Compose) measured:
  - frame rate: Lottie "roughly 17 FPS", Rive "roughly 60 FPS";
  - total memory: Lottie 246 MB, Rive 276 MB (graphics memory 123 vs 184 MB);
  - file size: one cited comparison of 24.37 KB (Lottie) vs 2 KB (Rive).
  - Source: [Callstack](https://www.callstack.com/blog/lottie-vs-rive-optimizing-mobile-app-animation)

**Haptics hook in Compose**
- Compose 1.8 added `HapticFeedbackType.Confirm`, `Reject`, `ContextClick`, `GestureEnd`, `GestureThresholdActivate`, `SegmentTick`, `SegmentFrequentTick`, `ToggleOn`, `ToggleOff` and `VirtualKey`. Fire them with `LocalHapticFeedback.current.performHapticFeedback(...)`. — [HapticFeedbackType reference](https://composables.com/docs/androidx.compose.ui/ui/classes/HapticFeedbackType)

### Inferences
- **Recommended Compose "cinematic stack" for a weather app:**
  - **Sky:** `MeshGradientPainter` (1.12) or an AGSL sky shader (API 33+), whose uniforms (sun elevation, cloud cover, time) are animated with effects springs. Fall back to `Brush.verticalGradient` below API 33.
  - **Location change:** `AnimatedContent` with a spatial spring in `transitionSpec`, plus `sharedBounds` for the city card ↔ full screen (container transform).
  - **Back:** `DeferredAnimatedContent` or `PredictiveBackHandler` following the 90% scale / 35% fade-through spec.
  - **Particles** (rain, snow): one `Canvas` driven by `withFrameNanos`, with pre-allocated particle arrays and cached brushes. Do not use one composable per drop.
- **HDR/P3 sunsets** (1.12) can be spectacular. Treat HDR highlights like lightning: large, bright luminance jumps increase photosensitivity risk (see section 6).
- **Performance budget:** each blurred or RenderEffect layer is an extra offscreen pass. Limit glass surfaces to one or two navigation-layer elements, which also matches Apple's "no glass on glass" rule, and avoid animating alpha on large overlapping trees (use `ModulateAlpha` where layers don't overlap).
- **Lottie vs Rive:** use Rive (state machines, small files) for interactive illustrated elements such as a mascot or weather icons that react to touch. Lottie is fine for simple one-shot icons. Avoid both for full-screen weather rendering, where shaders and Canvas are cheaper and fully dynamic.
- **Momentum projection:** `splineBasedDecay().calculateTargetValue()` is the Compose analogue of Apple's projection function. Use it to pick the snap target (next hour, next location page) and let a spring finish the move with the release velocity.

### Gaps
- Compose 1.11 (around spring 2026) release contents were not researched.
- Current stability of the shared-element APIs (experimental vs stable) should be confirmed against the BOM in use; the docs extraction may lag.
- Exact target-SDK rules for predictive-back defaults on Android 16/17 (for example when `onBackPressed` stops being called) were not verified in this session.
- `Modifier.blur`/`RenderEffect` minimum API (believed to be API 31) and blur fallbacks were not re-verified here. Libraries such as Haze were not researched.
- There is no recent Compose-native Lottie vs Rive benchmark; the only numbers found are the 2023 React Native test.
- The AOSP `weathereffects` source (believed to hold Pixel's rain, snow and fog shaders) was unreachable (googlesource returned 503), so it could not be checked as a reference implementation.

## 3. Cinematic app patterns: launch and intro, location-change transitions, time scrubbing as time-lapse, gyroscope parallax, depth and layering, storytelling moments, onboarding, loading and empty states, and delight without distraction

### Takeaway
The best weather apps make the sky itself the interface:
- Backgrounds reflect real sun position and conditions (Apple Weather).
- Particle density follows real intensity and wind (!Boring Weather).
- Scrubbing a day plays like a time-lapse.
- Sound and haptics are reserved for meaningful moments such as thunder and taps through the week.

Loading should use layout-matching skeletons for 1–10 s waits, and everything decorative must be optional and quiet by default.

### Cited Findings
- **Apple Weather (iOS 15, 2021 onward):**
  - "Thousands of new animated backgrounds" that "provide more information on sun position, rain, clouds, storms"; they change through day and night and shift with the weather.
  - Animated backgrounds are limited to devices with an **A12 Bionic or later**, which is performance tiering.
  - Precipitation maps are animated to show storm paths and intensity.
  - Sources: [MacRumors iOS 15 Weather guide](https://www.macrumors.com/guide/ios-15-weather-app/); [TechCrunch 2021](https://techcrunch.com/2021/06/07/apple-finally-updates-its-weather-app-with-dynamic-backgrounds-maps-and-way-more-data/)
- **(Not Boring) Weather** (Andy Allen, co-creator of the Apple Design Award–winning Paper):
  - "Rain that falls as particles based on how heavy it's falling outside. Strong winds blow rain sideways and clouds move faster."
  - "We procedurally generate clouds based on the current % cloud coverage"; "Tap the clouds to build your own".
  - "Scrub through a day and experience the shifts in weather over the course of a day like a timelapse shot."
  - "Tap anywhere on the bar to preview the weather at that time"; the wind arrow "points in the physical direction the wind is blowing".
  - "Accurate moon phases using a true model of the moon"; seasonal skins and artist collaborations.
  - "You can hear the rain and feel the thunder."
  - Source: [!Boring Weather product page](https://notbor.ing/product/weather)
- The App Store editorial story on the app says it uses gaming-industry tech (3D modelling, lighting). Sound designer Thomas Williams made original audio for each weather event (rain, snow, thunder, hail), and the app has "just-noticeable haptic feedback you'll feel as you tap through the week's forecast". — [App Store story](https://apps.apple.com/us/story/id1556321408)
- **Pixel wallpaper weather effects (Android 16 QPR1, 2025):** Fog, Rain, Snow and Sun effects over the user's wallpaper, with a "Local" option that follows the current weather. — [9to5Google](https://9to5google.com/2025/05/20/google-pixel-wallpaper-effects-android-16-qpr1/); [Beebom](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones)
- **CARROT Weather** is personality-driven: Professional, Friendly, Snarky, Homicidal and Overkill modes, with selectable voices and sound effects. Users can turn off speech and sound effects, and haptics can be disabled in Personality settings. — [iMore](https://www.imore.com/carrot-weather-everything-you-need-know); [CARROT What's New](https://support.meetcarrot.com/weather/css/whatsnew-mobile.html); [MacStories](https://www.macstories.net/reviews/carrot-weather-adds-new-carrot-voices-weather-underground-improvements-and-more/)
- **Skeletons vs spinners** (NN/g, Tankala, Jun 2023, reviewed Sep 2026):
  - Skeletons are for **full-page loads**.
  - Under about 1 s, no indicator is needed. From 2 to 10 s, skeletons or spinners both work. Over 10 s, use a progress bar.
  - Avoid "frame-display" skeletons that only show header and footer.
  - Animated (pulse or shimmer) skeletons "can potentially be distracting, annoying, or even create accessibility problems".
  - Source: [NN/g Skeleton Screens 101](https://www.nngroup.com/articles/skeleton-screens/)
- An ECCE 2018 study reports that pages with skeleton screens scored higher on perceived speed and ease of navigation (per the search summary; full text not read). — [ACM DL](https://dl.acm.org/doi/10.1145/3232078.3232086)
- **Storm warnings, Android 17:** Live Update notifications gain a semantic colour API, `Notification.createSemanticStyleAnnotation()`. The styles are Green (safe), Orange (caution/physical hazard), Red (danger/urgent) and Blue (informational). — [Android 17 features](https://developer.android.com/about/versions/17/features)
- **Onboarding by motion (WWDC 2018):** "By keeping the discrete animation and the gesture aligned, we can use one to teach the other". For example, Safari's close-tab animation slides the tab left, which teaches the swipe. — [WWDC18 #803](https://developer.apple.com/videos/play/wwdc2018/803/)
- **Delight without distraction:**
  - Google's sound and haptics team: "Not every button you press ... results in a haptic or sound"; silence works like visual negative space. — [Google Design, 2024](https://design.google/library/ux-sound-haptic-material-design)
  - Apple's "utility" principle: reserve feedback for significant moments. — [WWDCNotes, WWDC19 #810](https://wwdcnotes.com/documentation/wwdcnotes/wwdc19-810-designing-audiohaptic-experiences/)

### Inferences
Pattern catalogue, with concrete recipes:
- **Launch and intro (~600–900 ms, once per cold start).** Show the sky at the right time of day immediately; the gradient is cheap and needs no data. Then let the condition layer (clouds, rain) fade in with an effects spring once data arrives, and settle the headline temperature with a spatial spring. Never block interaction for an intro, and skip it entirely when reduce-motion is on. Apple's "not about frame rate, what's in the frames" suggests one rich hero move beats many small ones.
- **Location change.** Treat the sky as a persistent layer and morph it (colours, sun position, particle density) with effects springs while the content layer pages horizontally with a spatial spring and fling projection. Prefer a Liquid-Glass-style in-place morph over a cross-fade of the whole screen. For card → detail, use `sharedBounds` container transform.
- **Time scrubbing as a time-lapse** (the !Boring pattern):
  - Map scrubber position to a continuous time value that drives sun elevation, sky gradient, cloud cover and precipitation particles.
  - Fire `SegmentTick` haptics on each hour boundary, or `SegmentFrequentTick` when scrubbing fast.
  - Cross-fade the ambient audio bed between conditions.
  - On release, snap to the nearest hour with velocity projection.
- **Gyroscope parallax / depth.** Use two or three layers (far sky, mid clouds, near particles) with small offsets (a few dp), low-pass filtered, and lighting that shifts with device tilt, the way Liquid Glass specular highlights respond to motion. Disable it under reduce-motion (see section 6), because parallax is a known vestibular trigger.
- **Storytelling moments** (rare by design):
  - Sunrise and sunset: a slow sky transition plus a gentle haptic swell (envelope rise and fall) the first time the user opens the app near golden hour.
  - First snow of the season: a special particle intro once, with a soft `SLOW_RISE` haptic.
  - Storms: lightning flash budgeted per WCAG, then thunder audio delayed to imply distance (sound travels about 1 km per ~3 s), with a haptic rumble locked to the audio.
  - Rate-limit all of these (for example once per day per event type) so they stay special.
- **Loading.** On a cold start with no cache, use a static skeleton that matches the final layout, with the sky already rendered. Avoid shimmer, or keep it very subtle and disable it under reduce-motion. On refresh, keep stale data visible and show a small indicator, such as M3 Expressive `LoadingIndicator` (experimental) or pull-to-refresh with `GestureThresholdActivate` haptics.
- **Empty and error states.** Use the sky and scene as the illustration (for example a calm night with a single cloud) plus one clear action. Avoid looping animations that compete with the retry button.
- **Tier by device**, as Apple does with A12+ backgrounds. Choose particle counts, shader complexity and blur by device class and thermal state, and keep a static-image fallback.

### Gaps
- No design case study or talk from the Google Pixel Weather team or the Samsung Weather team was found.
- No quantitative data on how cinematic weather backgrounds affect engagement or retention was found.
- The "30% faster perceived" and "Facebook 300 ms" skeleton claims in secondary blogs could not be traced to primary sources and are excluded.
- The Android 12+ SplashScreen API exit-animation specifics were not researched.

## 4. Haptics: Android principles, Composition primitives, Android 16 envelope effects, capability detection, pairing with animation and sound, Apple's Core Haptics principles, and fatigue

### Takeaway
On Android, prefer semantic `HapticFeedbackConstants` / Compose `HapticFeedbackType` for UI. Use `VibrationEffect.Composition` primitives (API 30+) for rich moments, and Android 16 envelope effects (`BasicEnvelopeBuilder`, `WaveformEnvelopeBuilder`) for continuous textures such as thunder, always behind capability checks with a tiered fallback. Never use buzzy one-shot vibrations. Keep haptics causal, harmonious with sound and visuals, and rare.

### Cited Findings
**Android design principles**
- Android's three categories:
  - **Clear** haptics are "crisp and clean sensations associated with a discrete event".
  - **Rich** haptics need wide-bandwidth actuators, "are supported by fewer devices", and need a fallback.
  - **Buzzy** haptics are "noisy, sharp and penetrating". "Given the choice of buzzy haptics or no haptics for touch feedback, choose no haptics."
  - Source: [Android haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
- API order of preference: `HapticFeedbackConstants`, then predefined `VibrationEffect`, then `Composition` primitives. Avoid `createOneShot` and `vibrate(long)`. A target signal is **10–20 ms**, but actuator ring-out adds **20–50 ms**, which can feel buzzy. — [Android haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
- Match strength to importance and frequency. Very frequent events (scrolling) should be "very subtle"; for drag or snap, "gradually increasing the amplitude of a sequence of ticks". Be consistent within the app and with the system. "We strongly recommend co-design of visual, audio, and haptic effects". Out-of-sync haptics "may suggest a broken actuator". "Less is more ... too much vibration can be annoying and even numbing." — [Android haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
- `performHapticFeedback` with `HapticFeedbackConstants` needs no `VIBRATE` permission and has the widest support. All haptic feedback methods respect the user's touch-feedback settings. `CONFIRM` is "a short and light vibration" and `REJECT` "a stronger feedback to signal failure". Other constants: `GESTURE_START`/`END`, `SEGMENT_TICK`, `SEGMENT_FREQUENT_TICK`, `TOGGLE_ON`/`OFF`, `GESTURE_THRESHOLD_ACTIVATE`/`DEACTIVATE`, `DRAG_START`, `CLOCK_TICK`, `CONTEXT_CLICK`, `KEYBOARD_TAP`, `LONG_PRESS` and `NO_HAPTICS`. — [Add haptic feedback to events](https://developer.android.com/develop/ui/views/haptics/haptic-feedback)

**Composition primitives (API 30+) and capability checks**
- The primitives are `CLICK`, `THUD` (strong, low-frequency impact), `TICK`, `LOW_TICK`, `SPIN`, `QUICK_RISE`, `SLOW_RISE` and `QUICK_FALL`. `addPrimitive(id, scale 0–1, delayMs)`. — [Custom haptic effects](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects)
  - Scale: use ratios of **≥1.4** for a perceptible difference; typical levels are 0.5, 0.7 and 1.0. Scale 0 means the minimum perceivable vibration, not off.
  - Gaps between primitives: 5–10 ms is undetectable, 50 ms+ is discernible, and 100 ms+ feels disconnected.
- Capability checks:
  - `areAllPrimitivesSupported()` / `arePrimitivesSupported()` (API 31)
  - `getPrimitiveDurations()`
  - `hasAmplitudeControl()`
  - API 36: `areEnvelopeEffectsSupported()`, `getEnvelopeEffectInfo()`, `frequencyProfile.getFrequencyRange(minGs)` and `resonantFrequency`
  - If a primitive is unsupported, disable the whole effect family that uses it.
  - Source: [Custom haptic effects](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects)
- Documented example recipes, all from [Custom haptic effects](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects):
  - **Resist:** `LOW_TICK`s whose scale and interval track drag distance.
  - **Expand:** `SLOW_RISE` 0.3 + `QUICK_FALL` 0.3; collapse is `SLOW_RISE` 1.0.
  - **Wobble:** repeated `SPIN` with ±0.1 random scale.
  - **Bounce:** `THUD` at scale `0.7^bounceCount`.
  - **Four-tier fallback:** envelope → primitives → amplitude waveforms → on/off.

**Android 16 (API 36) envelope effects**
- `BasicEnvelopeBuilder` is hardware-agnostic: `setInitialSharpness()` and `addControlPoint(intensity 0–1, sharpness 0–1, durationMs)`, and it must end at intensity 0. `WaveformEnvelopeBuilder` is frequency-aware: `addControlPoint(amplitude, frequencyHz, durationMs)`, and frequencies must be inside `getFrequencyRange()`. The docs include a "rocket launch" chirp from the minimum frequency up to the resonant frequency over 2.1 s and back down. — [Custom haptic effects](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects)
- `BasicEnvelopeBuilder` values are normalised intensities in sensation-level space (dB SL) that Android converts to output acceleration. The start intensity is fixed at 0 and the builder throws if the end is not 0. — [AOSP PWLE docs](https://source.android.com/docs/core/interaction/haptics/haptics-pwle); [Microsoft Learn API mirror](https://learn.microsoft.com/en-us/dotnet/api/android.os.vibrationeffect.basicenvelopebuilder?view=net-android-36.0)
- Supporting devices must handle ≥ **20 ms** between control points and **≥ 16** control points. — [Android haptics docs (per search summary)](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects)
- The Android 17 features page lists no new haptics APIs. — [Android 17 features](https://developer.android.com/about/versions/17/features)

**Audio-coupled haptics**
- Android 12 added `HapticGenerator`, an audio effect that generates haptics from an audio session in real time. It is created only on devices that support audio-coupled haptics. Audio files with haptic channels need `AudioAttributes.Builder().setHapticChannelsMuted(false)`, because haptic channels are muted by default. — [XDA](https://www.xda-developers.com/android-12-audio-coupled-haptic-effect/); [Lofelt on Medium](https://medium.com/lofelt/exploring-new-haptics-features-in-android-12-27844dba9635); [Microsoft Learn HapticGenerator](https://learn.microsoft.com/en-us/dotnet/api/android.media.audiofx.hapticgenerator?view=net-android-34.0)

**Apple and Google design principles**
- Apple's audio-haptic principles (WWDC 2019, older but still current practice):
  - **Causality:** it must be obvious what caused the feedback.
  - **Harmony:** "it should feel the way it looks and the way it sounds"; small objects feel and sound small.
  - **Utility:** "don't add feedback just because you can"; reserve it for significant moments.
  - Source: [WWDCNotes, WWDC19 #810](https://wwdcnotes.com/documentation/wwdcnotes/wwdc19-810-designing-audiohaptic-experiences/); [WWDC19 #810](https://developer.apple.com/videos/play/wwdc2019/810)
- Google's sound and haptics team:
  - Premium haptics are built from primitives (click, tick, low tick) played in rapid succession at varying amplitudes.
  - Describe haptics with texture metaphors ("sliding on ice, dragging through sand") and use rhythm and texture instead of melody.
  - Layered taps (2, 3, 4) signal progression.
  - "The more frequently an interaction occurs, the less intrusive" the feedback should be.
  - Source: [Google Design, 2024](https://design.google/library/ux-sound-haptic-material-design)

**Timing and synchrony research**
- Virtual-button study: audio-tactile delay detection thresholds were **179 ms** (audio first) and **451 ms** (haptic first). — [PubMed 37093720](https://pubmed.ncbi.nlm.nih.gov/37093720/)
- Driving-context study: mean thresholds were 123.4 ms (haptic first) and 87.7 ms (audio first). About **20% of participants noticed 50–65 ms** asynchrony. — [arXiv 2307.05451](https://arxiv.org/pdf/2307.05451)

**Real apps**
- !Boring Weather uses "just-noticeable" haptics when tapping through days and "feel the thunder". — [App Store story](https://apps.apple.com/us/story/id1556321408); [product page](https://notbor.ing/product/weather)
- CARROT lets users disable haptics. — [CARROT What's New](https://support.meetcarrot.com/weather/css/whatsnew-mobile.html)

### Inferences
Weather haptic palette (effect → API → fallback):
- **Hour or day scrub:** `SegmentTick` on each boundary, `SegmentFrequentTick` when flinging. Fall back to no haptics rather than buzz.
- **Pull-to-refresh threshold:** `GestureThresholdActivate`/`Deactivate`. Refresh success is `Confirm`; failure or offline is `Reject`.
- **Unit and setting toggles:** `ToggleOn`/`ToggleOff`. Adding a location is `Confirm`.
- **Thunder rumble:**
  - API 36+ with envelope support: `WaveformEnvelopeBuilder` near the bottom of the frequency range, as a fast attack (~20–40 ms to amplitude ~0.8), then a decaying tail over ~600–1200 ms using ≤ 16 points spaced ≥ 20 ms. Alternatively use `BasicEnvelopeBuilder` with low sharpness (~0.1–0.3).
  - API 30–35: `THUD` 1.0, then `THUD` at ~0.5 after ~80 ms, then 2–3 `LOW_TICK`s decaying by ×0.7.
  - Otherwise: nothing.
  - If the thunder is an audio asset, `HapticGenerator` (API 31+, device-dependent) or haptic channels in the audio file keep sync automatically.
- **Synchrony:** trigger the haptic from the audio clock, not from the flash, compensating for known output latency. Aim for < ~30–50 ms offset (about 20% of people notice 50–65 ms), with the haptic never noticeably *before* the sound it belongs to.
- **Rain touch texture:** only while the user touches the rain layer, sparse randomised `LOW_TICK` at ~0.2–0.3 scale. Never continuous ambient vibration, which drains battery, causes "numbing" and risks buzz on weak actuators.
- **Fatigue guardrails:**
  - Cap special effects per session.
  - Never vibrate for background data refreshes.
  - Provide an in-app haptics toggle (as CARROT does), on top of the system setting.
  - Keep the same effect for the same action everywhere.

### Gaps
- Per-constant API levels for `HapticFeedbackConstants` (for example which were added in API 30 and which in API 34) were not captured by the fetch tool.
- Compose's fallback behaviour for new `HapticFeedbackType`s on older OS versions was not verified.
- No peer-reviewed numbers on haptic habituation or fatigue in mobile apps were found beyond platform guidance.
- Apple Core Haptics AHAP examples of weather-like effects (rain, thunder) were not retrieved.
- No research quantifying the share of Android devices that support envelope effects or all primitives was found.

## 5. Sound design: ambient soundscapes, UI sound cues, silent/DND and audio focus, low-latency playback, spatial audio, and user controls

### Takeaway
Sound can make a weather app feel alive: rain, wind and distant birds make the world feel "much larger and inhabited". It is also the most intrusive channel, and a large share of users keep phones on vibrate or silent. Treat sound as opt-in and meaningful:
- SoundPool for short UI cues, respecting ringer mode.
- Media3/ExoPlayer for ambient beds, with correct audio focus. Android 15+ refuses focus to apps that are not foreground.
- Variation and layering to avoid repetition.
- Spatial audio only where headphones support it.

### Cited Findings
**Craft principles**
- !Boring's "The Sound of Software" (Andy Allen & Thomas Williams, 25 Apr 2024):
  - Use sound to "communicate something" or to "juice a moment".
  - Break repetition by creating "8–12 variations by varying pitch, volume, timing, or mix and randomly play one variation" per event.
  - Layer natural, synthesised and instrument sources.
  - Use relational sound design: similar actions share tone, and opposite actions contrast (for example entering and exiting a detail view in !Weather).
  - Sounds need not be literal.
  - In !Weather, "the sound of distant birds chirping ... opens up the world to something that feels much larger and inhabited".
  - Test on device speakers, not studio headphones.
  - The article also discusses spatial audio in its !Vibes app. Its stance on silent mode was only paraphrased by the fetch tool (use user controls) and is unverified.
  - Source: [The Sound of Software](https://notbor.ing/words/the-sound-of-software)
- Google's sound and haptics team (Harrison Zafrin & Conor O'Sullivan, 2024):
  - Aim for sounds that feel "comfortable, familiar, and human".
  - Ask "Does this action need sound or haptics, and why?"
  - "The more frequently an interaction occurs, the less intrusive audio should be."
  - Hero moments stay short (under a second).
  - The Pixel camera shutter moved from melodic to realistic to reduce distraction.
  - Use strategic silence.
  - Source: [Google Design](https://design.google/library/ux-sound-haptic-material-design)
- !Boring Weather has custom sounds for every weather event. — [product page](https://notbor.ing/product/weather); [App Store story](https://apps.apple.com/us/story/id1556321408)
- CARROT lets users choose sound effects and voices, or turn speech and sound effects off. — [iMore](https://www.imore.com/carrot-weather-everything-you-need-know); [MacStories](https://www.macstories.net/reviews/carrot-weather-adds-new-carrot-voices-weather-underground-improvements-and-more/)

**Android audio focus and system behaviour** (source for all: [Android audio focus docs](https://developer.android.com/media/optimize/audio-focus))
- Request focus immediately before playback and check the result is `AUDIOFOCUS_REQUEST_GRANTED`.
- **Android 15+:** apps cannot get audio focus unless they are the top (foreground) app or run a foreground service; otherwise the call returns `AUDIOFOCUS_REQUEST_FAILED`.
- **Android 12+:** the system forcibly fades out a `USAGE_MEDIA` or `USAGE_GAME` player when another app requests `AUDIOFOCUS_GAIN`. Such players are muted during incoming calls.
- **Android 8+:** the system can duck automatically on `GAIN_TRANSIENT_MAY_DUCK`, and `setWillPauseWhenDucked` opts out.
- Usages: `USAGE_MEDIA`, `USAGE_GAME`, `USAGE_ASSISTANCE_SONIFICATION` (UI sounds) and others.
- Media3 ExoPlayer with `handleAudioFocus = true` manages focus automatically.
- Handling focus changes: pause on `LOSS` and `LOSS_TRANSIENT`; duck or pause on `LOSS_TRANSIENT_CAN_DUCK`.
- Android 17 adds a dedicated Assistant volume stream (`USAGE_ASSISTANT`). — [Android 17 features](https://developer.android.com/about/versions/17/features)

**Ringer mode and user habits**
- `AudioManager.getRingerMode()` returns `RINGER_MODE_NORMAL`, `RINGER_MODE_VIBRATE` or `RINGER_MODE_SILENT`. — [GeeksforGeeks](https://www.geeksforgeeks.org/audiomanager-in-android-with-example/)
- Android Authority reader poll (self-selected, n = 1,426): 48.3% keep ring mode on, 31.3% vibrate and 14.8% silent. — [Android Authority](https://www.androidauthority.com/sound-profile-vibrate-ring-silent-poll-results-1224669/)
- Microsoft Research field study (small sample): 42% vibration-only, 36.2% normal, 13% sound-only and 8.7% silent. — [Chang et al., MSR](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/p6-chang.pdf)

**Low latency and spatial audio**
- SoundPool pre-loads decoded samples for low-latency UI or game sounds. Oboe (C++, AAudio on 8.1+) gives the lowest latency across devices. — [Android low-latency audio](https://developer.android.com/games/sdk/oboe/low-latency-audio); [Ackee](https://www.ackee.agency/blog/android-high-performance-audio-apis)
- One developer measured higher latency after moving to Oboe than with SoundPool (204 ms vs 93 ms on a Galaxy S9) until configuration was fixed, so measure on real devices. — [google/oboe #386](https://github.com/google/oboe/issues/386)
- Spatial audio (Android 13+/API 33) goes through the `Spatializer` class (capability queries, `isHeadTrackerAvailable()`). Media3 ExoPlayer 1.0+ avoids downmixing multichannel audio and handles spatialisation-aware track selection. Head tracking needs a compatible headset. — [Android spatial audio](https://developer.android.com/media/grow/spatial-audio); [AOSP spatial audio](https://source.android.com/docs/core/audio/spatial); [Android Developers Blog 2023](https://android-developers.googleblog.com/2023/04/delivering-immersive-sound-experience-with-spatial-audio.html)

### Inferences
- **Defaults.** Ambient sound should be **off by default**, with an obvious opt-in: a speaker toggle on the sky or a "Listen to the weather" card. UI sound cues should also default off, or be very subtle and suppressed when the ringer is in vibrate or silent mode. Haptics stay on, following the system touch-feedback setting.
- **Ambient playback.**
  - Use Media3 ExoPlayer with `USAGE_MEDIA`, seamless looping of 30–90 s stems, and 1–2 s cross-fades between condition stems (drizzle → heavy rain → thunderstorm), with mix levels driven by live data: intensity, wind speed, thunder probability.
  - Because `AUDIOFOCUS_GAIN` will pause or fade the user's music, never auto-start ambient audio when other media is playing.
  - Pause on focus loss and when the app goes to background. Android 15+ would refuse focus anyway, so do not run a foreground service just for ambience.
- **UI cues.** Use SoundPool with `USAGE_ASSISTANCE_SONIFICATION`, 8–12 randomised variants per frequent cue, under ~300 ms, low in the mix. Use contrasting pairs for opposite actions (open/close, add/remove location).
- **Thunder.** Use distance-aware timing: flash, then a delay, then the rumble, with low-pass filtering for distant strikes. Pan thunder toward the storm's bearing when headphones are connected. Use the spatializer for head-tracked immersion only when `Spatializer` reports support.
- **Controls to ship.** A master "Sound" switch, a separate ambient volume slider, "Haptics" and "Thunder & flashes" toggles (tied to accessibility), and a sleep timer if ambient mode is promoted as a relaxation feature (a Calm-style use case).

### Gaps
- No reliable data on user reception of ambient soundscapes specifically in weather apps (CARROT, !Boring) was found. The Calm and rain-sound app ecosystem (retention, usage) was not researched.
- No peer-reviewed evidence on nature-sound benefits was gathered in this session.
- Android's official guidance on whether in-app UI sounds must honour ringer mode, and on DND interaction with `USAGE_MEDIA`, was not retrieved. The inference above is a conservative design choice.
- The YouGov UK ringer-mode poll appeared in search results but was not read.

## 6. Accessibility and comfort: reduce motion, photosensitivity for lightning, vestibular triggers from parallax, reduced transparency and contrast

### Takeaway
Treat every cinematic layer as optional:
- Honour Android's "Remove animations" (`ANIMATOR_DURATION_SCALE = 0`). Compose ≥ 1.2 honours it for its own animations, but custom frame loops, shaders and sensors need manual checks.
- Keep lightning at or below 3 flashes per second (WCAG 2.3.1), and preferably far fewer and dimmer.
- Disable gyroscope parallax under reduced motion (WCAG 2.3.3 names parallax as a vestibular trigger).
- Replace blur with opaque surfaces when the new Android "reduce blur effects" setting or high contrast is on.

### Cited Findings
- **Android "Remove animations"** sets the animation duration scale to 0; read it with `Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)`, which returns 0f when enabled. Jetpack Compose animation classes follow the setting from version 1.2.0. If a Lottie animation shows only its first frame under this setting, consider a static image instead. (Blog dated Dec 2022, older guidance.) — [Eevis Panula](https://eevis.codes/blog/2022-12-12/android-animations-and-reduced-motion/). Moving content can be a barrier, and apps should let users "pause, stop or hide content which moves, blinks or scrolls automatically". — [Appt](https://appt.org/en/docs/jetpack-compose/samples/reduced-animations)
- **WCAG 2.3.1, Three Flashes or Below Threshold (Level A):** nothing flashes more than three times in any one-second period, unless the flashes are below the general and red flash thresholds.
  - A general flash is "a pair of opposing changes in relative luminance of 10% or more" where the darker image is below 0.80.
  - The area threshold is 25% of any 10° visual field, taken as a 341 × 256 CSS px rectangle (87,296 px²).
  - Red flashes involve saturated red, R/(R+G+B) ≥ 0.8.
  - For mobile, analyse "at the largest scale at which a user may view the content".
  - Source: [W3C Understanding 2.3.1](https://www.w3.org/WAI/WCAG22/Understanding/three-flashes-or-below-threshold.html)
- **WCAG 2.3.3, Animation from Interactions (AAA):** "Motion animation triggered by interaction can be disabled, unless the animation is essential". Vestibular disorders cause "dizziness, nausea and headaches", and parallax scrolling is the cited example. Colour, blur and opacity changes without perceived size, shape or position change are *not* motion animation. — [W3C Understanding 2.3.3](https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html)
- **Apple:**
  - Reduce Motion disables the tilt-parallax effect and replaces zoom and slide transitions with dissolves.
  - "Prefer Cross-Fade Transitions" reduces sliding controls.
  - "Dim Flashing Lights" dims video when flashes or strobes are detected.
  - Source: [Apple Support: Reduce screen motion](https://support.apple.com/en-us/HT202655); [iPhone User Guide, Motion](https://support.apple.com/mr-in/guide/iphone/iph0b691d3ed/16.0/ios/16.0)
  - Liquid Glass automatically adapts to Reduced Transparency, Increased Contrast and Reduced Motion (details in section 1). — [WWDC25 #219](https://developer.apple.com/videos/play/wwdc2025/219/)
- **Android "reduce blur effects"** (Settings > Accessibility > Color & motion; seen in the Android 16 QPR2 Canary 2509 build) disables system background blur. It replaces the "allow window-level blurs" developer option. The article names no app-facing API. — [Android Authority, 26 Sep 2025](https://www.androidauthority.com/android-reduce-blur-effects-setting-3601579/). A Google comment relayed by Mishaal Rahman says the toggle exists because system blur "might not meet the needs of all users". — [Threads](https://www.threads.com/@mishaal_rahman/post/DWy-TkxlHTS/we-recognize-that-these-system-blur-effects-might-not-meet-the-needs-of-all)
- **Contrast (API 34+):** `UiModeManager.getContrast()` returns 0 for standard, 0.5 for medium and 1.0 for high (range −1 to 1), and `addContrastChangeListener` notifies changes. The Material Components `ColorContrast` utility applies contrast automatically on API 34+ when dynamic colour is used. — [UiModeManager reference](https://developer.android.com/reference/android/app/UiModeManager); [Microsoft Learn IContrastChangeListener](https://learn.microsoft.com/en-us/dotnet/api/android.app.uimodemanager.icontrastchangelistener?view=net-android-34.0); [ColorContrast](https://developer.android.com/reference/com/google/android/material/color/ColorContrast)
- **Loading states:** animated skeletons can "create accessibility problems". — [NN/g](https://www.nngroup.com/articles/skeleton-screens/)
- **Haptics:** consistent `HapticFeedbackConstants` usage is "particularly valuable as an accessibility consideration". — [Android haptics principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles)

### Inferences
- **Build a single "comfort profile"** from system signals: animator scale 0, contrast ≥ 0.5, reduce blur, and ringer mode. Add in-app overrides: Reduce motion, Reduce flashing, Reduce transparency, Sound, Haptics. Apply it everywhere:
  - **Reduce motion on:**
    - No gyroscope parallax or camera moves.
    - Particles become static frames, or very slow drift under ~1–2 dp/s.
    - Location changes become cross-fades, which WCAG does not count as motion.
    - Springs become instant or short fades.
    - Shimmer is off.
    - Lottie/Rive show poster frames.
  - **Custom loops need manual checks.** Compose honours the scale for its own animations, but `withFrameNanos` particle loops, shader `iTime` uniforms and sensor-driven offsets will likely keep running unless the app checks the setting. Verify this on device (see Gaps).
- **Lightning rules** (on a phone, a full-screen flash exceeds the 341×256 px area threshold, so the frequency limit applies directly):
  - Never exceed 3 flashes in any 1 s window. A practical budget is ≤ 1 flash per ~2 s, and ≤ 2 flashes in a burst.
  - Soften each flash: a partial-screen, radial or cloud-internal glow with limited luminance delta and a ≥ 100–150 ms decay, rather than a hard white frame.
  - Never use saturated red.
  - Beware HDR/P3 highlights (Compose 1.12), which amplify luminance jumps.
  - Offer "Reduce flashing" (default on when reduce-motion is on). It replaces flashes with a slow brightening of clouds while thunder audio and haptics still convey the storm.
- **Gyroscope parallax:**
  - Keep displacement small, a few dp, and low-pass filtered.
  - Pause when the app is not visible to save battery.
  - Recentre slowly rather than snapping.
  - Disable under reduce-motion.
  - Never couple it to essential information.
- **Transparency and contrast:**
  - When reduce blur is on, contrast ≥ 0.5, or the device is below API 31 (no RenderEffect blur), render navigation surfaces as opaque tonal surfaces.
  - Add scrims behind text over animated skies, and choose M3 medium or high-contrast schemes from `getContrast()`.
  - Temperature text must stay legible over all sky states, including bright snow and sunset.
- **Essential vs decorative.** Severe-weather state must always be conveyed statically (text, icon, semantic colour) and never only through animation, flash or sound.

### Gaps
- No public Android API was found for detecting the new "reduce blur effects" toggle. `WindowManager.isCrossWindowBlurEnabled()` might reflect it, since it replaced the window-level blur developer option, but this is unverified.
- Android has no system equivalent of Apple's "Dim Flashing Lights" (none found).
- Behaviour of Compose infinite transitions and `withFrameNanos` loops under `ANIMATOR_DURATION_SCALE = 0` was not verified in current Compose sources; test on device.
- Whether Google Play policy or the Android accessibility guidance has specific rules on flashing content was not researched.
- Vestibular-safe magnitudes for parallax (degrees or dp of shift) were not found in authoritative sources; the "few dp" guidance is an inference.
