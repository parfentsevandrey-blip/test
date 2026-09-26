# Commercial Weather Apps: Cinematic Visuals, Effects, Animation and Interaction Benchmark (state 2024–2026)

Scope note for the report writer: "(search excerpt)" marks a claim taken from a search-engine summary of the linked page that I could not open myself (403/blocked). Treat those as lower confidence. Everything else was read on the linked page. Items before 2024 are labelled HISTORICAL.

## 1. Apple Weather (iOS 15/16 → iOS 26, plus iOS 27): per-condition animated backgrounds, transitions, cinematic details, interactions, technical approach

### Takeaway
Apple's benchmark is a full-screen, real-time animated "sky" introduced with the iOS 15 redesign (2021) and carried through iOS 16–27. It has "thousands of variations" driven by sun position, clouds and precipitation, and it runs only on A12-class or newer hardware. Its best-known touch is that rain and snow particles physically hit the top edges of the translucent forecast cards. Later releases (iOS 16 Lock Screen/iPad/Mac, iOS 26 Liquid Glass chrome, iOS 27 Highlights) layered new surfaces and information on top. None of them visibly rebuilt the sky engine, and Apple has never publicly documented how it is rendered.

### Cited Findings
**Background system (baseline since iOS 15, 2021)**
- Apple rebuilt Weather with animations that reflect current conditions, in "thousands of variations that Apple says more accurately represent sun position, clouds and rain"; the backgrounds show wind, rain and sun positioning that change with real conditions — [TechCrunch, Jun 2021](https://techcrunch.com/2021/06/07/apple-finally-updates-its-weather-app-with-dynamic-backgrounds-maps-and-way-more-data/)
- Backgrounds give information on "sun position, rain, clouds, storms, and other weather phenomena" and "change throughout the day and the night and shift based on weather patterns" — [MacRumors iOS 15 Weather guide](https://www.macrumors.com/guide/ios-15-weather-app/)
- Hardware gating: "Animated backgrounds are available on all devices with an A12 Bionic chip or later. Earlier iPhones will not have access to the more detailed animations." — [MacRumors iOS 15 Weather guide](https://www.macrumors.com/guide/ios-15-weather-app/)
- The module layout itself reacts to conditions: when rain is present or approaching, the layout moves the relevant forecast information up. Backgrounds and animations "are designed to represent the sun position, clouds, and precipitation." — [9to5Mac hands-on, Sep 2021](https://9to5mac.com/2021/09/24/ios-15-weather-app-hands-on/)
- The iOS 15 redesign drew on Dark Sky, which Apple acquired on 31 March 2020. Dark Sky support ended at the beginning of 2023, and since iOS 16 Apple has used its own forecast data — [Wikipedia: Weather (Apple)](https://en.wikipedia.org/wiki/Weather_(Apple)); [Android Police, Jun 2021](https://androidpolice.com/2021/06/07/revamped-ios-15-weather-app-shows-off-a-whole-lot-of-dark-sky/amp)
- Full-screen maps use "high resolution images that animate the progress of rain and clouds" — [TechCrunch, Jun 2021](https://techcrunch.com/2021/06/07/apple-finally-updates-its-weather-app-with-dynamic-backgrounds-maps-and-way-more-data/)

**Weather interacting with the UI (signature detail)**
- Rain and snow fall and "hit the top of the info box, as if it was a real solid obstacle" — user thread "Apple's attention to detail: the Weather app" (2021) — [MacRumors Forums (search excerpt)](https://forums.macrumors.com/threads/apples-attention-to-detail-the-weather-app.2299694/)
- A third-party teardown course that rebuilds the iOS 15+ Weather visuals has these episodes: clouds; a day/night cycle ("the Weather app smoothly transitions between day and night"); tinting clouds by time of day; a star field; rain and snow in two parts, the second on "particle interaction with UI elements"; forking lightning bolts; the sun; and a bonus meteor shower. The course frames Apple's app as balancing "functional, fact-driven" information with "sparks of joy" — [Hacking with Swift+ "Remaking Weather"](https://www.hackingwithswift.com/plus/remaking-weather)

**iOS 16 / iPadOS 16 / macOS Ventura (2022)**
- A dedicated Weather Lock Screen "depicts the current temperature and shows the artwork from the Weather app for your location … if it's raining, you'll see rain, the same as you would in the animated Weather app." iOS 16 also added severe-weather notifications for storms, floods, hurricanes, heat waves and tornadoes — [MacRumors iOS 16 Weather guide](https://www.macrumors.com/guide/ios-16-weather/)
- iPad launch: "Weather comes to iPad with immersive animations, detailed maps, and tappable forecast modules, designed to take full advantage of the stunning display"; the animated backgrounds "represent the sun position, clouds, and precipitation" — [Apple Newsroom, Oct 2022 (search excerpt)](https://www.apple.com/newsroom/2022/10/ipados-16-is-available-today/)
- Weather came to iPadOS 16 and macOS Ventura; before that, Apple weather data on those platforms was only a widget or Siri — [Wikipedia: Weather (Apple)](https://en.wikipedia.org/wiki/Weather_(Apple))

**iOS 26 (2025): Liquid Glass**
- "There's no more bottom bar in the Weather app, and instead, there are Liquid Glass buttons for changing locations and accessing settings." Liquid Glass uses "real-time rendering to dynamically react to movement with reflective highlights" and lets "light and color to filter through". MacRumors judged that Apple "hasn't managed to strike enough of a balance to satisfy everyone" on legibility versus translucency — [MacRumors Liquid Glass guide](https://www.macrumors.com/guide/ios-26-liquid-glass/)
- iOS 26.2 added Enhanced Safety Alerts: a rich map inside the notification showing affected areas, plus official local guidance — [9to5Mac, Apr 2026 (search excerpt)](https://9to5mac.com/2026/04/16/apples-weather-app-recently-overhauled-one-of-its-most-important-features)

**iOS 27 (released Sep 2026)**
- New "Highlights" section at the top with "need-to-know weather information for the day". The Conditions section toggles between temperature, Precipitation ("hour-by-hour chance of rain") and Wind views, and the 10-day list follows the selected view. There is a new extra-large widget. "There are no new AI features in the iOS 27 Weather app." — [MacRumors iOS 27 Weather guide](https://www.macrumors.com/guide/ios-27-weather/); [9to5Mac, Jun 2026 (search excerpt)](https://9to5mac.com/2026/06/17/apple-weather-gets-two-brand-new-features-in-ios-27/)

**Accessibility**
- Apple's own Reduce Motion documentation: "Animation and effects in certain apps are disabled. For example, weather animations in the Weather app." It also says screen transitions "use the dissolve effect instead of zoom or slide effects" and that the tilt parallax on wallpaper, apps and alerts is disabled — [Apple Support HT202655](https://support.apple.com/en-us/HT202655)
- With Reduce Motion on, the rain, snow, cloud, thunderstorm, clear-night and sunny animations become static in the city view and stop in the list view. Users can override this for Weather alone under Accessibility > Per-App Settings — [Gadget Hacks (search excerpt)](https://ios.gadgethacks.com/how-to/your-iphones-weather-app-has-crazy-number-customization-options-you-probably-didnt-know-about-0384907/)

### Inferences
- Apple's premium feel rests on three things. The sky is data-driven, with the real sun position and precipitation state. It is continuous: time-of-day tinting and smooth day/night transitions rather than swapped images. And the UI is treated as physical, with precipitation colliding with the cards. The collision trick is the most transferable signature detail. In Compose/AGSL it could be done by rendering the top edges of the Liquid Glass cards into a mask/outline buffer that the rain and snow shaders sample. This mirrors the `outlineBuffer`/`snow_accumulation` approach Google ships in AOSP (see §2).
- The A12 gate strongly suggests GPU-heavy real-time rendering (likely Metal) rather than pre-rendered video, but Apple has never confirmed this. It is an inference, not a documented fact.
- Coverage of iOS 16–27 describes new surfaces (Lock Screen, iPad, Mac, Liquid Glass chrome, Highlights) but no rebuilt sky engine. This suggests Apple has treated the 2021 background system as mature, with the "new" premium layer in iOS 26 being glass controls floating over the animated sky.
- Apple leans on Reduce Motion (fully static) and per-app overrides rather than a Weather-specific "animations" toggle. An Android equivalent would respect the system animator-duration scale / "Remove animations" and also offer an in-app toggle.

### Gaps
- No official Apple source (WWDC session, engineering blog, newsroom) says how the backgrounds are built (Metal, particle systems, volumetrics, pre-rendered assets). I found none.
- I could not verify per-condition specifics for fog, wind-only, hail or sleet, or whether sun glare/lens flare or rain-on-lens effects exist in Apple Weather. Lightning and stars are supported only indirectly, by the third-party recreation and the Reduce Motion excerpt listing "thunderstorms" and "clear nights".
- A search summary claimed the animations scale with precipitation intensity; I could not tie this to a specific page.
- I found no source describing the exact transition when swiping between locations, or how the background reacts to scrolling (blur or dim when modules scroll over it).
- One site (allthings.how) claimed an iOS 26 Weather "Astronomy tab" driving Lock Screen lighting. It is uncorroborated and looks unreliable, so I excluded it.
- Instagram/Facebook posts claim iOS 27 "brings a new design to the Weather app". MacRumors describes only layout and information changes, not new backgrounds, so this is unverified.

## 2. Google Pixel Weather (2024 redesign → Material 3 Expressive 2025) and Pixel/Android 16 weather wallpaper effects

### Takeaway
Pixel Weather launched with the Pixel 9 (Aug 2024) with a deliberately restrained look: condition-tinted gradients plus subtle AI-generated skyline/landmark art for 300+ cities, with the frog mascot dropped. It has since added real-time background animations: sun rays glinting "off your screen", drifting clouds, falling rain and snow flurries. The standout "premium" layer is multi-sensory. "Immersive weather vibrations" land in sync with raindrops, there is a hidden rain soundscape, and haptics are automatically suppressed in dangerous weather. The 2025 Material 3 Expressive refresh changed the chrome (pill cards, springier motion, vibrant color, native widgets). Separately, Android 16 QPR1 ships Pixel lock-screen "Live Effects" (Weather: Rain/Snow/Fog/Sun/Local) built on open-source AGSL shaders in AOSP. These are directly relevant to an AGSL-based app.

### Cited Findings
**Launch design (2024)**
- The pre-release build had "a simple gradient, running from the top to the bottom of the screen, changing its color to match the current weather conditions". The temperature is the biggest element, with a condition icon in place of the degree symbol. Cards "(except the hourly forecast) can be permanently repositioned by holding them down". The pre-release showed "the lack of animations of any kind" — [Android Authority, Jul 2024](https://www.androidauthority.com/google-pixel-9-weather-app-3467819/)
- The shipping app shows AI-generated imagery of landmarks and skylines for 300+ major cities. It was kept "subtle in the background" to "keep that data front and center". Google called it a "premium experience", noting weather apps rank "third as the most used and most important application on your device" — [9to5Google, Oct 2024](https://9to5google.com/2024/10/31/pixel-weather-froggy-design/)
- Froggy was dropped because "half the population loves him, and half the population has a different feeling of him. And we didn't want to only cater to one side." — [9to5Google, Oct 2024](https://9to5google.com/2024/10/31/pixel-weather-froggy-design/)
- Easter egg: during precipitation, pressing the condition icon next to the temperature plays "a beautiful soundscape of the rain". Animation haptics give a "vibration that matches the density of the rain" — [9to5Google, Oct 2024](https://9to5google.com/2024/10/31/pixel-weather-froggy-design/)

**Animations and haptics (late 2024–2025)**
- "Immersive weather vibrations" were announced with the October 2024 Feature Drop and rolled out server-side in November 2024. They are on by default, turn off automatically in dangerous weather, are unavailable in Battery Saver, and require a Pixel 8 or newer — [9to5Google, Nov 2024](https://9to5google.com/2024/11/19/pixel-weather-vibrations-pollen/)
- Official help page: "You can add vibration to the Pixel Weather background animations"; "This feature is normally on"; "the system will automatically disable any haptics in potentially dangerous weather conditions"; "Animations and vibrations are supported on Pixel 8 and later"; "When your phone is in Battery Saver mode, animations and vibrations won't be available." Blocks can be dragged but "cannot be moved higher than the 'Weather Brief' and 'Hourly Forecast' blocks". The AI "Weather Brief" summary is on Pixel 9+ and some Pixel 8 models, in English, German and Japanese — [Google Pixel Help](https://support.google.com/pixelphone/answer/15266029?hl=en)
- Vibrations mimic the "pitter-patter" of rain, synced to the in-app animation, and reportedly also cover thunderstorms and snow — [Android Central (search excerpt)](https://www.androidcentral.com/apps-software/google-pixel-weather-app-gains-immersive-weather-vibrations)
- What animates: "the sun's rays glinting off your screen when there are clear skies"; clouds slowly drifting past the city skylines; "raindrops will fall down your screen, making immersive vibrations as they land, and snow flurries float by during a blizzard". A hidden control lets you tap the upper area to pause, which shows a small play button at the bottom-right of current conditions — [Android Police, Mar 2025](https://www.androidpolice.com/google-pixel-weather-hidden-play-pause-button-animations/)

**Material 3 Expressive refresh (Aug 2025) and widgets**
- The home list uses "much taller pill-shaped cards that go from 10 to six cities" and shows highs/lows. The circular centered search FAB became a rounded square on the right, some city-view text is centered, and colors are more vibrant — [9to5Google, Aug 2025](https://9to5google.com/2025/08/25/pixel-weather-expressive-redesign/)
- Across M3 Expressive, "animations are springier, buttons grow to show emphasis" — [Android Police (search excerpt)](https://www.androidpolice.com/pixel-weather-material-3-expressive/)
- Pixel Weather now owns its home-screen widgets. They are denser, show up to six hours, and expand to five days — [9to5Google, Aug 2025 (search excerpt)](https://9to5google.com/2025/08/22/pixel-weather-native-widgets/)
- A later update gave condition icons bolder colors for contrast — [PhoneArena (search excerpt)](https://www.phonearena.com/news/pixel-weather-app-gets-fresh-coat-of-paint_id178715)

**Android 16 QPR1 Pixel wallpaper "Live Effects": Weather**
- The Effects section has Weather with "Fog", "Rain", "Snow", "Sun" and a default "Local" that follows real conditions. It leaves "your wallpaper in its default setting, but applies an animated weather effect on top". Sibling effects are "Shape" (subject cut-out) and "Cinematic" (3D depth). A sparkle icon implies AI — [9to5Google, May 2025](https://9to5google.com/2025/05/20/google-pixel-wallpaper-effects-android-16-qpr1/)
- Rain shows as "tiny water droplets slide on your screen"; snow as "small, soft circular snowflakes that fall from the top of your screen". Effects play briefly on unlock and when the device moves: "Unlocking the device will show a transition animation … you'll briefly see them while unlocking and as you move your device". Cinematic adds "3D motion to the subject". Effects cannot be combined — [Beebom](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones)
- Effects make "your wallpaper subject … getting pelted by rain or snow" — [MakeUseOf (search excerpt)](https://www.makeuseof.com/android-add-rain-to-photos/)

**AOSP implementation (open source, AGSL)**
- AOSP has a `weathereffects` module under `platform/frameworks/libs/systemui` with `debug/`, `graphics/`, `res/`, `src/` and `tests/` — [AOSP weathereffects](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/)
- `graphics/assets/shaders/` holds 18 AGSL files: `color_grading_lut`, `constants`, `fog_effect`, `glass_rain`, `lens_flare`, `outline`, `rain_constants`, `rain_glass_layer`, `rain_shower`, `rain_shower_layer`, `rain_splash`, `simplex2d`, `simplex3d`, `snow`, `snow_accumulation`, `snow_effect`, `sun_effect`, `utils` — [AOSP shaders dir](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/)
- `snow_accumulation.agsl` (Apache 2.0, © 2023 AOSP) detects edges in the `foreground` alpha by sampling above and below each pixel. The vertical difference is turned into a build-up mask (`smoothstep(0.1, 1.8, dY * 5.0)`) and varied with `simplex2d` noise. Uniforms include `snowThickness`, `scale` and `screenWidth`. Output channels encode accumulation, randomness and noise for later passes — [snow_accumulation.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/snow_accumulation.agsl)
- `glass_rain.agsl` (Apache 2.0) handles drops on the lens. It uses a grid of cells (`rainGridSize`), vertical motion from a "Fourier Series-Sawtooth Wave", horizontal "Wiggle" constrained to each cell, teardrop shaping and trailing droplets. `generateStaticGlassRain()` adds stationary drops with expanding ripples. It outputs masks so the background can be blurred or refracted under the drops. `rainIntensity` sets drop probability and `time` drives the animation — [glass_rain.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/glass_rain.agsl)
- `rain_shower_layer.agsl` works in depth layers. Two distant rain layers use grid scales 20×2 and 30×4, then the foreground photo subject is composited. `drawSplashes()` adds splashes "around the outline of the given image" from `outlineBuffer`. A final near layer (8×3) is drawn at 0.7 visibility because "closer rain drops are less visible", with `rainVisibility = 0.4` and a contrast/brightness grade. Uniforms include `foreground`, `background`, `outlineBuffer`, `time`, `gridScale`, `intensity`, `transformMatrixBitmap` and `transformMatrixWeather` — [rain_shower_layer.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/rain_shower_layer.agsl)
- `sun_effect.agsl` builds god rays "like a fourier series" from five sine/cosine terms anchored near (0.57, −0.8). It adds a lens flare (`addFlare()`) and grades shadows and highlights by blending `shadowColor`/`highlightColor` through luminance masks. Separate `transformMatrixBitmap`/`transformMatrixWeather` uniforms decouple image and effect transforms. It contains a TODO to "fix the uv position of the sun" — [sun_effect.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/sun_effect.agsl)

### Inferences
- Google's in-app philosophy is data first, atmosphere second: gradients, subtle skylines and light particle animation. The "premium" moments are sensory, not visual spectacle: haptics synced to drop impacts and a hidden rain soundscape. The safety rule of suppressing playful haptics in dangerous weather is a transferable pattern.
- The AOSP `weathereffects` shaders are a production-grade AGSL reference for exactly the effects an AGSL weather app needs: layered depth rain, rain on glass, splashes and snow build-up on silhouettes via an outline buffer, fog, sun god rays and lens flare, and color grading. They are Apache-2.0 per the file headers read, so they can likely be studied or adapted with license notices kept. Legal review is still advisable. The separate `transformMatrixWeather`/`transformMatrixBitmap` uniforms show how to parallax the effect layer independently of the image, for example against gyro or scroll.
- Both Apple (A12+) and Google (Pixel 8+, off in Battery Saver) gate animations by device tier and power state. A premium Android app should have similar tiers: full shaders, a reduced particle count, and static.
- The lock-screen effects play in short bursts on unlock and device motion rather than continuously. That is probably a battery-driven choice (inference), and it points to "play on entry/interaction, then settle" as an alternative to always-on loops.

### Gaps
- I found no Google statement on how the in-app Pixel Weather background animations are rendered, or whether they share code with the AOSP wallpaper `weathereffects`.
- Which exact conditions trigger vibrations (thunder, snow) is only in a search excerpt. The shipping status of the rain soundscape easter egg after Oct 2024 was not confirmed.
- No first-party details on how the M3 Expressive redesign changed background animations (versus chrome only).
- I did not read `fog_effect.agsl`, `lens_flare.agsl`, `color_grading_lut.agsl` or the Kotlin effect classes in `src/` (e.g., how foreground segmentation masks are produced).
- No battery measurements for either the in-app animations or Live Effects.

## 3. Samsung Weather / One UI weather effects (One UI 6 → 8.5)

### Takeaway
Samsung has moved from illustrated widgets with time-of-day colors (One UI 6, 2023) to AI "Photo ambient" lock-screen wallpapers that overlay live rain and snow on the user's photo (One UI 8, 2025). In One UI 8.5 (Galaxy S26, early 2026) those wallpapers became depth-aware, with effects falling behind people and objects. This closely mirrors Pixel Live Effects. A leaked One UI 8 Weather-app redesign with full-screen animated landscapes and a walking character dressed for the weather was widely reported. I could not confirm it shipped as leaked, and the April 2026 app update coverage does not mention it.

### Cited Findings
- One UI 6 (2023): "Enhanced illustrations in the Weather widget and app have been enhanced to provide better information about the current weather conditions"; "Background colors also change depending on the time of the day". There is a new Weather insights widget with alerts for "severe thunderstorms, snowfall, or other precipitation", plus more data (snowfall, moon phases, pressure, visibility, dew point, wind direction) and a map-based location view — [Samsung US One UI 6 features](https://www.samsung.com/us/apps/one-ui/features/)
- One UI 8 leak (22 Apr 2025, developer Gerwin van Giessen): "fresh, full-screen animations that more accurately reflect real-time weather conditions", though "There only seem to be a couple of animations in the current build". Commentary: "Weather apps thrive on eye-catching animations" — [Yahoo Tech / Android Police](https://tech.yahoo.com/articles/samsungs-weather-app-could-animated-114231632.html)
- Leak details: a person walks in from the right to the center wearing clothes that match the weather (summer clothes and a cap for hot clear skies, a raincoat or umbrella in rain, warmer clothes when cold) over realistic landscape backgrounds — [SamMobile (search excerpt)](https://www.sammobile.com/news/one-ui-8-weather-app-desing-first-look-leak/); [Sammy Fans (search excerpt)](https://www.sammyfans.com/2025/04/22/one-ui-8-revamps-samsung-weather/)
- The real-time weather lock-screen wallpaper needs "One UI 8 or later". It first appeared on Galaxy Z Fold7, Z Flip7, Z Flip7 FE, S25 FE and Tab S11/S11 Ultra and later expanded to S21 FE through S26, Z Flip4 and later, Tab S8 and later, and A15–A56. Setup: Wallpaper and style → Change wallpapers → "Photo ambient" under "Create with AI" → preview with the Play icon. Samsung recommends "a clear outdoor photo taken during the day" ("effects may be less noticeable on dark or indoor photos"). Effects cover only "selected weather conditions" such as rain and snow, and changes "may not appear immediately" — [Samsung Canada support](https://www.samsung.com/ca/support/mobile-devices/real-time-weather-lock-screen-wallpaper-on-your-samsung-galaxy/)
- One UI 8.5: "effects like rain or snowfall realistically move behind visible objects or people, rather than simply sitting on top of the image". The phone "generates animated weather effects using AI" (Settings > Wallpaper & style > Lock screen > Weather). The feature resembles the "Photo Ambient Wallpaper option … previously experimented with in Labs", it borrows Pixel's Live Effects idea, and it debuted with the Galaxy S26 — [Android Authority, Jan 2026](https://www.androidauthority.com/samsung-one-ui-8-5-lock-screen-weather-effect-3630836/)
- Samsung Weather v1.7.30.8 (Apr 2026) brought solid pollen icons, modernized wind and pressure icons, moonrise and moonset side by side, and quick radar layer buttons (6-hour forecast, radar, clouds, temperature). The coverage mentions no full-screen animations or characters — [Sammy Fans, Apr 2026](https://www.sammyfans.com/2026/04/03/samsung-weather-app-redesign-adds-new-icons-radar-controls/)

### Inferences
- Samsung's "cinematic" push is at the system wallpaper level (AI segmentation plus weather overlay) rather than inside the Weather app. Together with Pixel, this makes photo-plus-depth-aware weather compositing an OEM standard on Android in 2025–26. A standalone app competing on "cinematic" should at least match depth-aware layering (effects behind foreground elements, accumulation on silhouettes).
- The walking, weather-dressed character is a personality/storytelling device, a softer cousin of Google's polarizing Froggy. Whether it shipped matters for benchmarking but is unconfirmed.

### Gaps
- There is a conflict or ambiguity: Samsung's support page says the real-time weather wallpaper arrived with One UI 8 on the Fold7/Flip7 generation, while Android Authority (Jan 2026) presents the depth-aware version as new in One UI 8.5. My reading is that the depth-aware layering is the 8.5 addition, but no single source confirms it.
- I could not confirm whether the leaked full-screen animations and walking character shipped in stable One UI 8/8.5.
- No sources on Samsung Weather haptics, sounds, Reduce-Animations handling, or battery impact of Photo ambient.

## 4. Third-party and historical apps: CARROT, (Not Boring) Weather, Weawow, Hello Weather, Mercury, Clime, Weather Up, AccuWeather, The Weather Channel app, live-wallpaper apps (YoWindow), Yahoo Weather and Dark Sky (historical)

### Takeaway
The third-party market splits into four "premium feel" strategies:
- **Game-engine-style 3D plus sound and haptics:** (Not Boring) Weather, an Apple Design Award winner.
- **Personality and humor:** CARROT, also an Apple Design Award winner.
- **Photography:** historically Yahoo Weather, which won a 2013 Apple Design Award with Flickr photos matched to place, time and conditions plus blur and parallax. Today Weawow, and The Weather Channel's condition-triggered background images.
- **Restrained color systems:** Mercury's and Hello Weather's gradients and themes.

Live-wallpaper apps such as YoWindow add time-scrubbable animated landscapes. The big data brands (AccuWeather, TWC) invest in radar, maps and alerts more than cinematic backgrounds.

### Cited Findings
**(Not Boring) Weather (iOS)**
- Uses "3D modeling and lighting effects" from gaming tech: "feel the rain, hear the thunder, and watch the wind blow across your screen". Collectible skins and app icons "change the design of the entire app to match your mood", with regular collaborations. The app is free with !Weather Plus at $14.99/yr, Super !Boring at $7.99/mo or $29.99/yr, and skins at $4.99–$9.99; paid tiers unlock real-time widgets, faster forecasts and more customization. It holds Editors' Choice and an Apple Design Award, rates 4.8★ from about 18k ratings, and v3.51 is updated for iOS 27. Some users note "decreased minimalism" — [App Store](https://apps.apple.com/us/app/not-boring-weather/id1531063436)
- Every metric (clouds, sun, moon phases) gets an interactive 3D model, and an interactive "forecast bar" swipes "like a music track". "Every tap, swipe, and scrub is accompanied by a distinct sound effect" — [screensdesign showcase (search excerpt)](https://screensdesign.com/showcase/not-boring-weather); [spark.mwm.ai (search excerpt)](https://spark.mwm.ai/en/apps/not-boring-weather/1531063436)
- Sound design principles from the developer:
  - Make "8-12 variations by varying pitch, volume, timing, or mix" so repetition becomes "texture", and layer sounds.
  - Similar actions share an "auditory family"; !Weather uses directional sound pairs for entering and exiting detail views.
  - Sound and haptics "should almost always be paired together".
  - Users get granular control: all sounds, background music only, or a volume independent of the device.
  - [!Boring: "The Sound of Software"](https://notbor.ing/words/the-sound-of-software)

**CARROT Weather (iOS/Android)**
- Personality is the product: "hilarious dialogue" and "delightful animations". Achievements unlock by "experiencing weather events, traveling around the world". It also has AR, a "full customization suite" for layouts and data points, and switchable data sources (AccuWeather, Apple Weather, Foreca). Premium Club gates widgets, notifications, data-source switching, weather maps and custom alerts — [meetcarrot.com](https://www.meetcarrot.com/weather/)
- There are five personalities from "professional" to profanity-laden "overkill", and political leanings (Centrist/Liberal/Conservative/No Politics) — [YourStory (search excerpt)](https://yourstory.com/2022/12/carrot-weather-app-personality-political-opinions)
- Motion catalog: "springy multi-step sliders, staggered text reveals, and pulsing illustrations" — [60fps.design (search excerpt)](https://60fps.design/apps/carrot)
- Winner of Apple's App of the Year, Apple Design Award and Editors' Choice. Premium Ultra adds rain, lightning and storm-cell notifications, a weather maps widget and quick data-source switching — [App Store (search excerpt)](https://apps.apple.com/us/app/carrot-weather-alerts-radar/id961390574)
- Carrot Weather is among the apps in Apple's Liquid Glass design gallery — [MacRumors, Apr 2026 (search excerpt)](https://www.macrumors.com/2026/04/06/apple-liquid-glass-design-gallery-update/)

**Weawow (iOS/Android/web)**
- A free, ad-free, tracking-free app "enhanced by beautiful weather-related photos taken by photographers around the world". Community-uploaded photos match the current weather, photographers are credited, and a marketplace sells their photos. Widget backgrounds can adapt automatically to conditions — [Weawow About (search excerpt)](https://weawow.com/i/aboutus); [Gizmodo download page (search excerpt)](https://gizmodo.com/download/weather-widget-weawow)

**Hello Weather**
- Automatic color themes (cold/warm/hot plus dark), automatic night mode, "sweet secret extras" and user-chosen app-icon colors. Reviewers call it "the best looking of all the weather apps" — [App Store (search excerpt)](https://apps.apple.com/us/app/hello-weather/id978393692); [Cult of Mac (search excerpt)](https://www.cultofmac.com/571437/hello-weather-app-review/)

**Mercury Weather (Apple platforms)**
- "uses beautiful gradient backgrounds to convey the temperature and conditions, along with a modern layout and clear typography". The design is consistent across iPhone, iPad, Mac (menu bar) and Watch. Pricing is $1.99/mo, $9.99/yr or $34.99 lifetime; the paywall covers widgets, the Watch app and multiple locations (review dated 17 Aug 2023) — [MacStories](https://www.macstories.net/reviews/mercury-weather-a-crystal-clear-design-for-every-apple-device/)
- Apple ran a Mac App Store editorial story, "A Weather App Worth Looking At", on it — [App Store story](https://apps.apple.com/us/mac/story/id1849020130)

**Clime: NOAA Weather Radar Live**
- Radar-first: interactive maps you scrub through time, an animated wind map with a time slider, and a precipitation forecast up to 26 h ahead. Onboarding ends in a paywall with weekly and yearly plans — [screensdesign UI breakdown (search excerpt)](https://screensdesign.com/showcase/clime-noaa-weather-radar-live); [App Store](https://apps.apple.com/us/app/clime-noaa-weather-radar-live/id749133753)

**Weather Up (iOS)**
- Widget-centric: an interactive widget (tap a day to focus on it), a high-to-low line with blue rain segments, icons that "really pop", and animated Radar and Clouds map layers — [iPhone J.D., Mar 2024 (search excerpt)](https://www.iphonejd.com/iphone_jd/2024/03/review-weather-up.html); [TapSmart (search excerpt)](https://www.tapsmart.com/apps/review-weather-modern-powerful-weather-app-ios/)

**AccuWeather**
- The 27 Aug 2025 relaunch brought 50+ new or enhanced features, a "streamlined Today Screen", a MinuteCast dial and new AQI, Smoke and Wind Flow maps. Premium Plus adds an ad-free experience, longer-range forecasts and 12 locations (versus 9) — [PR Newswire, Aug 2025](https://www.prnewswire.com/news-releases/accuweather-launches-improved-app-with-over-50-new-and-enhanced-features-302539456.html)
- AccuWeather's own story claims "entertaining animations", but the press release gives no specifics — [AccuWeather (search excerpt)](https://www.accuweather.com/en/weather-news/accuweather-launches-improved-app-with-over-50-new-and-enhanced-features/1809513)

**The Weather Channel app**
- The Feb 2024 redesign shows the temperature "overlaid on a weather triggered background image that is locally relevant to the current conditions", with live tiles (radar, videos, pollen) and health sections — [The Weather Company (search excerpt)](https://www.weathercompany.com/news/the-weather-channel-app-unveils-new-experience-as-weather-becomes-more-disruptive-amid-a-rapidly-changing-climate/); [weather.com](https://weather.com/news/news/2024-02-04-new-weather-channel-app)

**Live-wallpaper weather: YoWindow**
- Animated landscapes with "living objects like smoke, grass, trees, animals" that change "along with the weather, season and sunlight". A picture is "covered by rain, snow or mist depending on the weather". "Scroll the time to experience different light and weather conditions". Users can build a landscape from their own photo. The paid Android version removes ads — [YoWindow FAQ](https://yowindow.com/help.php)
- Sunsets happen at real local times, the moon phase is real, and there are four stock "YoLandscapes" (Village, Seaside, Airport, Oriental) — [Google Play listing (search excerpt)](https://play.google.com/store/apps/details?id=yo.app.free&hl=en_US)

**Yahoo Weather (HISTORICAL 2013; still live in 2026)**
- 2013 Apple Design Award winner — [Flickr Blog, Jun 2013](http://blog.flickr.net/en/2013/06/13/you-led-yahoo-weather-to-an-apple-design-award); [Macworld](https://www.macworld.com/article/221206/wwdc-2013-the-apple-design-award-winners.html)
- Crowdsourced photos from Flickr's Project Weather are matched to "the time of day and type of weather" (e.g., "the Golden Gate Bridge at mid-day, rain pouring"), falling back to nearby cities and then generic images, with photographer attribution. Praised details: "the blurring of the background as you dive into the grittier details", "the subtle parallax scroll when flipping between cities", and windmills that "spin faster or slower based on the actual current windspeed" — [TechCrunch, Apr 2013](https://techcrunch.com/2013/04/18/yahoos-surprisingly-gorgeous-new-ios-weather-app-centers-around-crowdsourced-photos)
- Android (Aug 2013): photos are "the main attraction, expanding to fill the screen in just about every part of the interface". "Pull up from the weather view to access advanced information, or pull down for a manual refresh" — [Android Police, 2013](https://www.androidpolice.com/2013/08/15/yahoo-weather-android-app-gets-a-facelift-focusing-on-flickr-photos/)
- Jul 2026 iOS overhaul: enhanced radar and maps, minute-by-minute precipitation, AQI and pollen, moon phase, a daily narrative summary, free. The coverage does not say whether photo backgrounds remain — [9to5Mac, Jul 2026](https://9to5mac.com/2026/07/30/yahoo-weather-ios-update/)

**Dark Sky (HISTORICAL)**
- Acquired by Apple on 31 Mar 2020; support ended at the start of 2023. Its capabilities fed into Apple Weather — [Wikipedia](https://en.wikipedia.org/wiki/Weather_(Apple)); [TechCrunch, 2021](https://techcrunch.com/2021/06/07/apple-finally-updates-its-weather-app-with-dynamic-backgrounds-maps-and-way-more-data/)

### Inferences
- The strongest third-party "premium" benchmarks for interaction are (Not Boring) and CARROT. (Not Boring) shows that sound design (varied, layered, paired with haptics, user-controllable) is the biggest untapped lever compared with Apple and Google, who use sound barely or not at all.
- Photography-based apps (Yahoo, Weawow, TWC) buy realism and locality cheaply but can't animate physically. Yahoo's 2013 solution was motion in the chrome (blur on pull-up, parallax between cities, animated windmills keyed to real wind speed). That remains a good low-cost pattern: animate data-bound details, not just decoration.
- Mercury and Hello Weather show that "premium" can come from a disciplined color system (temperature and condition gradients, night mode) without particles. This is a useful fallback tier for Reduce Motion and low-power states.
- Monetization splits: visuals are the product and are sold ((Not Boring) skins and subscriptions), personality is free but utility is paywalled (CARROT), or visuals are free and ads or extras are paid (AccuWeather, YoWindow, Weawow is fully free).

### Gaps
- No verified details on CARROT's sound effects or haptics, any animated weather backgrounds or scenes, or the years of its ADA and App of the Year awards.
- (Not Boring) Weather's ADA year and category were not verified.
- No details found for Weather Live Wallpapers (skysky) or Apalon's "Weather Live" (Play listing fetch failed), or other live-wallpaper apps (Overdrop etc.).
- AccuWeather's and TWC's in-app animations were not described concretely in sources I could read. I found nothing on any 2025–26 TWC app visual redesign.
- Whether Yahoo Weather's 2026 app still uses Flickr or photo backgrounds is unknown.

## 5. Design patterns that make weather apps feel "cinematic" (incl. per-condition benchmark)

### Takeaway
Across the leaders, "cinematic" comes from a small set of repeatable techniques rather than any one style:
1. Data-bound, continuous skies (real sun position, time-of-day tint, precipitation state).
2. Depth layering: far rain, subject, near rain, lens drops, with segmentation or outline masks.
3. Weather physically interacting with foreground elements: splashes and snow on silhouettes, precipitation hitting UI cards.
4. Lens and camera language: glass drops, god rays, lens flare, parallax, 3D "Cinematic" motion.
5. Color grading per condition and time.
6. Multi-sensory sync: haptics on drop impact, rain soundscapes, paired sound and haptics.
7. Restraint so data stays legible.
8. Safety-aware storytelling: layout reprioritization, alerts, suppressing playfulness in dangerous weather.

### Cited Findings
**Background approach**
- Real-time rendered, data-driven: Apple's "thousands of variations" keyed to sun position, clouds and rain — [TechCrunch](https://techcrunch.com/2021/06/07/apple-finally-updates-its-weather-app-with-dynamic-backgrounds-maps-and-way-more-data/); (Not Boring)'s 3D models and lighting — [App Store](https://apps.apple.com/us/app/not-boring-weather/id1531063436)
- Generated or illustrated art plus light animation: Pixel's AI skylines for 300+ cities kept "subtle in the background" — [9to5Google](https://9to5google.com/2024/10/31/pixel-weather-froggy-design/); Samsung's One UI 6 illustrations with time-of-day colors — [Samsung](https://www.samsung.com/us/apps/one-ui/features/)
- Photographic: Yahoo (Flickr) — [TechCrunch 2013](https://techcrunch.com/2013/04/18/yahoos-surprisingly-gorgeous-new-ios-weather-app-centers-around-crowdsourced-photos); Weawow (community) — [Weawow (search excerpt)](https://weawow.com/i/aboutus); TWC's condition-triggered images — [The Weather Company (search excerpt)](https://www.weathercompany.com/news/the-weather-channel-app-unveils-new-experience-as-weather-becomes-more-disruptive-amid-a-rapidly-changing-climate/)
- Photo plus live effects composited with AI depth: Pixel Live Effects — [9to5Google](https://9to5google.com/2025/05/20/google-pixel-wallpaper-effects-android-16-qpr1/); Samsung Photo ambient / One UI 8.5 — [Android Authority](https://www.androidauthority.com/samsung-one-ui-8-5-lock-screen-weather-effect-3630836/); YoWindow's sky replacement on your own photo — [YoWindow FAQ](https://yowindow.com/help.php)

**Depth, camera and lens language**
- Multi-depth rain (two far layers, the subject, a near layer at reduced visibility), splashes on the subject outline, rain on glass with refraction masks, sun god rays plus lens flare, and separate image and effect transform matrices — [AOSP rain_shower_layer](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/rain_shower_layer.agsl); [AOSP glass_rain](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/glass_rain.agsl); [AOSP sun_effect](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/sun_effect.agsl)
- Pixel's "Cinematic" wallpaper adds "3D motion to the subject" as the device moves — [Beebom](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones)
- Liquid Glass chrome reacts "to movement with reflective highlights" — [MacRumors](https://www.macrumors.com/guide/ios-26-liquid-glass/)
- Yahoo used parallax between cities — [TechCrunch 2013](https://techcrunch.com/2013/04/18/yahoos-surprisingly-gorgeous-new-ios-weather-app-centers-around-crowdsourced-photos)

**Weather ↔ UI or subject interaction**
- Apple's rain and snow hit the top of the info cards — [MacRumors Forums (search excerpt)](https://forums.macrumors.com/threads/apples-attention-to-detail-the-weather-app.2299694/); [Hacking with Swift+](https://www.hackingwithswift.com/plus/remaking-weather)
- AOSP snow builds up on foreground edges — [snow_accumulation.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/snow_accumulation.agsl)
- Samsung 8.5 effects move behind people and objects — [Android Authority](https://www.androidauthority.com/samsung-one-ui-8-5-lock-screen-weather-effect-3630836/)

**Color grading and time**
- AOSP ships `color_grading_lut.agsl` and grades shadows and highlights in its sun and rain shaders — [AOSP shaders dir](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/)
- Apple tints clouds by time of day with smooth day/night transitions — [Hacking with Swift+](https://www.hackingwithswift.com/plus/remaking-weather)
- Mercury uses gradients that "convey the temperature and conditions" — [MacStories](https://www.macstories.net/reviews/mercury-weather-a-crystal-clear-design-for-every-apple-device/)
- Hello Weather has cold/warm/hot themes plus auto night mode — [App Store (search excerpt)](https://apps.apple.com/us/app/hello-weather/id978393692)

**Time-lapse and scrubbing**
- YoWindow: "Scroll the time to experience different light and weather conditions" — [YoWindow FAQ](https://yowindow.com/help.php)
- Clime scrubs radar through time — [screensdesign (search excerpt)](https://screensdesign.com/showcase/clime-noaa-weather-radar-live)
- (Not Boring)'s forecast bar swipes "like a music track" — [spark.mwm.ai (search excerpt)](https://spark.mwm.ai/en/apps/not-boring-weather/1531063436)

**Haptics and sound**
- Pixel's vibrations fire as raindrops "land" and match rain density; there is also a rain soundscape easter egg — [Android Police](https://www.androidpolice.com/google-pixel-weather-hidden-play-pause-button-animations/); [9to5Google](https://9to5google.com/2024/10/31/pixel-weather-froggy-design/)
- !Boring pairs sound with haptics and uses 8–12 variations per sound — [notbor.ing](https://notbor.ing/words/the-sound-of-software)

**Personality and characters**
- CARROT's selectable personalities — [meetcarrot.com](https://www.meetcarrot.com/weather/)
- Samsung's leaked weather-dressed walker — [SamMobile (search excerpt)](https://www.sammobile.com/news/one-ui-8-weather-app-desing-first-look-leak/)
- Google dropped Froggy as polarizing — [9to5Google](https://9to5google.com/2024/10/31/pixel-weather-froggy-design/)

**Transitions and entry moments**
- Pixel Live Effects play a transition animation on unlock — [Beebom](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones)
- Yahoo blurred the background on pull-up to details — [TechCrunch 2013](https://techcrunch.com/2013/04/18/yahoos-surprisingly-gorgeous-new-ios-weather-app-centers-around-crowdsourced-photos)
- M3 Expressive uses "springier" animations — [Android Police (search excerpt)](https://www.androidpolice.com/pixel-weather-material-3-expressive/)

**Storytelling and safety**
- Apple moves relevant modules up when rain is present or approaching — [9to5Mac](https://9to5mac.com/2021/09/24/ios-15-weather-app-hands-on/)
- iOS 27 adds Highlights — [MacRumors](https://www.macrumors.com/guide/ios-27-weather/)
- Pixel turns off haptics in dangerous weather — [Google Help](https://support.google.com/pixelphone/answer/15266029?hl=en)
- TWC uses photoreal IMR storm surge to drive evacuations (§7) — [Kotaku](https://kotaku.com/hurricane-milton-storm-surge-simulation-unnreal-florida-1851668336)

**Per-condition benchmark (verified details only)**
- **Clear day / sun**
  - Apple: sun position from real location/time — [TechCrunch](https://techcrunch.com/2021/06/07/apple-finally-updates-its-weather-app-with-dynamic-backgrounds-maps-and-way-more-data/)
  - Pixel app: "sun's rays glinting off your screen" — [Android Police](https://www.androidpolice.com/google-pixel-weather-hidden-play-pause-button-animations/)
  - AOSP: Fourier god rays, lens flare and shadow/highlight grade — [sun_effect.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/sun_effect.agsl)
  - Samsung (leak): walker in summer clothes and a cap — [SamMobile (search excerpt)](https://www.sammobile.com/news/one-ui-8-weather-app-desing-first-look-leak/)
  - YoWindow: real local sunset times — [Play listing (search excerpt)](https://play.google.com/store/apps/details?id=yo.app.free&hl=en_US)
- **Clear night**
  - Apple: star field and "clear nights" animation — [Hacking with Swift+](https://www.hackingwithswift.com/plus/remaking-weather); [Gadget Hacks (search excerpt)](https://ios.gadgethacks.com/how-to/your-iphones-weather-app-has-crazy-number-customization-options-you-probably-didnt-know-about-0384907/)
  - YoWindow: real moon phase — [Play listing (search excerpt)](https://play.google.com/store/apps/details?id=yo.app.free&hl=en_US)
- **Clouds**
  - Apple: layered clouds tinted by time of day — [Hacking with Swift+](https://www.hackingwithswift.com/plus/remaking-weather)
  - Pixel app: clouds drift past city skylines — [Android Police](https://www.androidpolice.com/google-pixel-weather-hidden-play-pause-button-animations/)
- **Rain**
  - Apple: particles collide with the card tops — [MacRumors Forums (search excerpt)](https://forums.macrumors.com/threads/apples-attention-to-detail-the-weather-app.2299694/)
  - Pixel app: falling drops with density-matched vibrations on landing, plus a soundscape easter egg — [Android Police](https://www.androidpolice.com/google-pixel-weather-hidden-play-pause-button-animations/); [9to5Google](https://9to5google.com/2024/10/31/pixel-weather-froggy-design/)
  - AOSP: 3-depth rain, outline splashes and glass drops with trails and refraction — [rain_shower_layer.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/rain_shower_layer.agsl); [glass_rain.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/glass_rain.agsl)
  - Samsung 8.5: rain behind subjects — [Android Authority](https://www.androidauthority.com/samsung-one-ui-8-5-lock-screen-weather-effect-3630836/)
  - (Not Boring): "feel the rain" — [App Store](https://apps.apple.com/us/app/not-boring-weather/id1531063436)
- **Thunderstorm**
  - Apple: storms, with forking lightning per a third-party recreation — [MacRumors guide](https://www.macrumors.com/guide/ios-15-weather-app/); [Hacking with Swift+](https://www.hackingwithswift.com/plus/remaking-weather)
  - (Not Boring): "hear the thunder" — [App Store](https://apps.apple.com/us/app/not-boring-weather/id1531063436)
  - Pixel: haptics reportedly also for thunderstorms — [Android Central (search excerpt)](https://www.androidcentral.com/apps-software/google-pixel-weather-app-gains-immersive-weather-vibrations)
- **Snow**
  - Apple: flakes hit the card tops — [MacRumors Forums (search excerpt)](https://forums.macrumors.com/threads/apples-attention-to-detail-the-weather-app.2299694/)
  - Pixel app: "snow flurries float by during a blizzard" — [Android Police](https://www.androidpolice.com/google-pixel-weather-hidden-play-pause-button-animations/)
  - Pixel Live Effects: "small, soft circular snowflakes" — [Beebom](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones)
  - AOSP: snow builds up on silhouette edges — [snow_accumulation.agsl](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/snow_accumulation.agsl)
  - Samsung: snow on the lock-screen photo — [Samsung CA](https://www.samsung.com/ca/support/mobile-devices/real-time-weather-lock-screen-wallpaper-on-your-samsung-galaxy/)
- **Fog / mist**
  - AOSP: `fog_effect.agsl` exists; Pixel offers a "Fog" effect — [AOSP shaders dir](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/weathereffects/graphics/assets/shaders/); [9to5Google](https://9to5google.com/2025/05/20/google-pixel-wallpaper-effects-android-16-qpr1/)
  - YoWindow: covers the picture with "mist" — [YoWindow FAQ](https://yowindow.com/help.php)
- **Wind**
  - Apple: wind in the backgrounds — [TechCrunch](https://techcrunch.com/2021/06/07/apple-finally-updates-its-weather-app-with-dynamic-backgrounds-maps-and-way-more-data/)
  - (Not Boring): "watch the wind blow across your screen" — [App Store](https://apps.apple.com/us/app/not-boring-weather/id1531063436)
  - Yahoo (2013): windmills spin at the real wind speed — [TechCrunch 2013](https://techcrunch.com/2013/04/18/yahoos-surprisingly-gorgeous-new-ios-weather-app-centers-around-crowdsourced-photos)
  - Clime: animated wind map — [screensdesign (search excerpt)](https://screensdesign.com/showcase/clime-noaa-weather-radar-live)
  - iOS 27: dedicated Wind view — [MacRumors](https://www.macrumors.com/guide/ios-27-weather/)
- **Hail / sleet**: nothing found in any source (see Gaps).

### Inferences
- The most "cinematic" results come from compositing rules rather than asset quality. Place precipitation at several depths, let it interact with something solid (a subject, card edges), add one lens-level cue (glass drops or flare), and grade the whole frame per condition. AOSP already encodes all of this in AGSL.
- Data binding (real sun position, real wind speed driving motion speed, precipitation density driving particle count and haptic rate) is what separates "premium" from "decorative". Yahoo's windmills and Pixel's density-matched haptics are small, cheap examples.
- For a Liquid Glass UI, the sky must stay legible behind translucent cards. Pixel's "subtle" principle and Apple's legibility controversy both argue for dimming or blurring the scene under text and letting the effect "breathe" in empty regions.
- Safety moments should change tone: less playful and more informative. Examples are Pixel's haptic suppression and Apple's module reprioritization and Highlights. A cinematic app could reserve its most dramatic rendering (TWC-style) for explainers of severe weather rather than for ambience.

### Gaps
- No verified hail or sleet treatments in any benchmarked app.
- No first-party description of any app's location-change transition (Apple, Pixel), and no evidence of launch-screen "hero" transitions.
- I found no reviewer-sourced criticism that the cinematic backgrounds hurt legibility, apart from general Liquid Glass legibility debates.

## 6. Performance/battery reputation, accessibility handling (reduce motion) and monetization of premium visuals

### Takeaway
The platform leaders manage cost with hardware and power-state gating rather than a published optimization story. Apple enables full animations only on A12+ and makes them static under Reduce Motion, with a per-app override. Google enables animations and haptics only on Pixel 8+, turns them off in Battery Saver, offers a tap-to-pause control, and plays lock-screen effects in short bursts. I found no independent battery measurements for any app. Premium visuals are free on system apps. Among third parties they are either the paid product ((Not Boring) skins and subscriptions) or free while utility features are paywalled (CARROT, Mercury, Clime, AccuWeather).

### Cited Findings
**Performance and power**
- Apple: animated backgrounds need an A12 Bionic or later — [MacRumors](https://www.macrumors.com/guide/ios-15-weather-app/)
- Pixel: "Animations and vibrations are supported on Pixel 8 and later"; "When your phone is in Battery Saver mode, animations and vibrations won't be available" — [Google Pixel Help](https://support.google.com/pixelphone/answer/15266029?hl=en)
- Pixel Live Effects show "briefly … while unlocking and as you move your device" — [Beebom](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones)
- Samsung: weather-driven changes "may not appear immediately" and effects are less noticeable on dark or indoor photos — [Samsung CA](https://www.samsung.com/ca/support/mobile-devices/real-time-weather-lock-screen-wallpaper-on-your-samsung-galaxy/)

**Accessibility and motion control**
- Apple Reduce Motion disables "weather animations in the Weather app" — [Apple Support](https://support.apple.com/en-us/HT202655)
- Apple has a per-app Reduce Motion override for Weather — [Gadget Hacks (search excerpt)](https://ios.gadgethacks.com/how-to/your-iphones-weather-app-has-crazy-number-customization-options-you-probably-didnt-know-about-0384907/)
- Pixel: tap the upper area to pause animations — [Android Police](https://www.androidpolice.com/google-pixel-weather-hidden-play-pause-button-animations/)
- Pixel: vibration toggle, with haptics auto-off in dangerous weather — [Google Pixel Help](https://support.google.com/pixelphone/answer/15266029?hl=en)
- !Boring: granular sound controls (all sounds, background music only, independent volume) — [notbor.ing](https://notbor.ing/words/the-sound-of-software)
- Liquid Glass legibility remains contested — [MacRumors](https://www.macrumors.com/guide/ios-26-liquid-glass/)

**Monetization**
- (Not Boring): skins $4.99–$9.99, !Weather Plus $14.99/yr, Super !Boring $7.99/mo or $29.99/yr — [App Store](https://apps.apple.com/us/app/not-boring-weather/id1531063436)
- CARROT Premium Club gates widgets, notifications, maps, source switching and custom alerts — [meetcarrot.com](https://www.meetcarrot.com/weather/)
- Mercury: $1.99/mo, $9.99/yr or $34.99 lifetime for widgets, Watch and multiple locations — [MacStories](https://www.macstories.net/reviews/mercury-weather-a-crystal-clear-design-for-every-apple-device/)
- Clime: onboarding paywall with weekly and yearly plans — [screensdesign (search excerpt)](https://screensdesign.com/showcase/clime-noaa-weather-radar-live)
- AccuWeather Premium Plus: ad-free and more locations — [PR Newswire](https://www.prnewswire.com/news-releases/accuweather-launches-improved-app-with-over-50-new-and-enhanced-features-302539456.html)
- YoWindow: paid version removes ads — [YoWindow FAQ](https://yowindow.com/help.php)
- Weawow: free, no ads, no tracking — [Weawow (search excerpt)](https://weawow.com/i/aboutus)

### Inferences
- A defensible Android policy is three render tiers:
  - **Full:** layered AGSL, haptics and sound.
  - **Reduced:** fewer layers, lower resolution or frame rate, no glass refraction.
  - **Static:** graded gradient or photo.
  - Choose the tier by device class (GPU and RAM, and RuntimeShader availability on API 33+), Battery Saver/power state, thermal state, and the system "Remove animations" / animator-duration-scale setting. Add an in-app pause and toggle, following Pixel's pattern.
- Selling visual "skins" or scene packs is proven only for niche, design-led apps ((Not Boring)). Mainstream players keep visuals free and charge for alerts, widgets, ad removal and data. That suggests keeping the core cinematic sky free and monetizing extras (scene packs, sound packs, widget styles) if needed.

### Gaps
- No quantitative battery or thermal data (independent tests) for Apple Weather, Pixel Weather, Live Effects, Samsung Photo ambient or any live-wallpaper app.
- Unknown whether Apple Weather reduces animations in Low Power Mode, or whether Pixel Weather respects Android's "Remove animations" setting.
- No documented reviewer complaints about battery drain from these animations (absence of evidence, not evidence of absence).

## 7. The Weather Channel's immersive mixed reality (IMR) as a "cinematic weather" benchmark

### Takeaway
TWC turned photoreal, data-driven, real-time game-engine rendering into a safety storytelling tool. It started in 2018 with The Future Group on Unreal Engine: storm surge, tornado, lightning and ice-storm segments that won an Emmy, with one surge video passing 22M views. It moved to a permanent Zero Density "Reality" Unreal virtual studio with tracked cameras, image-based keying and a physically consistent virtual set. By Hurricane Milton (Oct 2024) it was showing FloodFX surge rising from 3 ft to 9+ ft around a live meteorologist, using Niagara VFX, Sequencer and live data.

### Cited Findings
- TWC used AR for science explainers from 2015. IMR arrived in early 2018 ("It wasn't until the early part of last year that the technology allowed us to visualize things in this hyper-realistic way"), using The Future Group's Unreal Engine-based system. About 30 experts across departments worked on it, described as "all hands on deck". Segments covered hurricane storm surge (Florence and Michael, 2018), lightning, tornadoes and ice storms, shot in a full green-screen environment with real-time data. A storm-surge video drew 22M+ views on Facebook, Twitter and YouTube, and viewers said they "won't stay". "We're saving lives with this effort." — [Television Academy](https://www.televisionacademy.com/features/news/mix/immersed-story)
- The physical camera's position and rotation are synchronized with the CG background's viewing angle in Unreal Engine, so "each take is a finished segment with no post production required". Unreal produced the "physically accurate water, wind, and floating car" in the surge reports, and one segment won an Emmy — [Unreal Engine spotlight (search excerpt)](https://www.unrealengine.com/en-US/spotlights/the-weather-channel-s-new-studio-brings-immersive-mixed-reality-to-daily-live-broadcasts)
- A separate Future Group / TWC project created lightning and tornadoes in Unreal — [Unreal Engine spotlight (title)](https://www.unrealengine.com/spotlights/the-future-group-and-the-weather-channel-create-lightning-and-tornadoes-with-unreal-engine?lang=en-US)
- Florence 2018 depicted flood heights with AR — [Slate](https://slate.com/technology/2018/09/weather-channel-hurricane-florence-flood-simulation.html)
- The permanent virtual studio was built by Zero Density and Myreze on Unreal Engine with multiple Mo-Sys StarTracker camera trackers. Zero Density's Reality Engine and Reality Keyer run alongside a traditional camera and green-screen setup. Image-based keying against a clean plate keeps "subtle transparent details and shadows" and allows a virtual cyclorama. The debut date is given as June 2 (2020) — [TV Tech (search excerpt)](https://www.tvtechnology.com/news/weather-channel-debuts-reality-virtual-studio); [Mo-Sys (search excerpt)](https://www.mo-sys.com/the-weather-channel-launch-immersive-mixed-reality-studio/)
- Hurricane Milton (Oct 2024): Stephanie Abrams presented FloodFX for Tampa as water rose from 3 ft ("water is already life-threatening. It's too late to evacuate"), through 6 ft (vehicles buoyant, structures failing), to 9–15 ft ("first floors … completely flooded"). It used Zero Density Reality Engine and Reality Keyer (Unreal-based), Unreal's Niagara VFX for the water, Sequencer for animation, and live weather data. The segment went viral as "terrifying". A TWC VP called such visuals "critical tools in motivating people to take action, evacuate when asked, and ultimately save lives" — [Kotaku, Oct 2024](https://kotaku.com/hurricane-milton-storm-surge-simulation-unnreal-florida-1851668336)
- Zero Density's current platform is Reality 5, a template-based UE5 broadcast graphics workflow. TWC is a listed client — [Panorama Audiovisual (search excerpt)](https://www.panoramaaudiovisual.com/en/2025/04/07/unreal-engine-5-define-futuro-zero-density-nab-2025/); [Zero Density Reality 5.4 (search excerpt)](https://www.zerodensity.io/news/product-news/reality5-4-is-released/)

### Inferences
- TWC's lessons that transfer to a phone app:
  - Scale and reference objects (a person, cars, doors) make weather legible and visceral.
  - Driving the scene from real data (surge height, wind) builds credibility.
  - The camera move is part of the story (a tracked, continuous camera, no cuts).
  - Drama is reserved for consequential weather, with a clear narrative (3 ft → 6 ft → 9 ft) tied to an action ("evacuate").
- In a mobile app this maps to a "severe weather story" mode: a short scripted sequence (camera push-in, rising water or wind debris, grading shift, haptic crescendo) triggered by official alerts. This is inferred design guidance, not something any benchmarked phone app is documented to do.

### Gaps
- I could not open the Unreal Engine spotlight pages (403) or the Zero Density case study (empty), so studio-launch details (exact 2020 date, UE version, number of cameras) rest on search excerpts.
- No verified details on specific rendering techniques for clouds or lightning in TWC IMR (volumetrics, particle counts), or on TWC reusing IMR assets in its mobile app.
