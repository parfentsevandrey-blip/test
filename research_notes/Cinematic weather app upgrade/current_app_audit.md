# Rosa Weather 2.6.1: audit of what the codebase does today for cinematic sky, weather effects, motion and interaction

Scope and method: read-only code audit of `/home/user/test/rosa-weather` at HEAD `f9392f8` (2026-09-26, "Tab bar: a layer of its own…", versionName 2.6.1 / versionCode 24, minSdk 33, target/compile SDK 37). Nothing was built or run. Visual judgements come from my own viewing of the committed renders in `docs/images/` and of the Robolectric gallery PNGs that were regenerated on 2026-09-26, the same day as HEAD: `core/designsystem/build/weather/*.png` and `app/build/screens/*.png`. Citation prefixes, all relative to the repo root:
- **DS** = `core/designsystem/src/main/kotlin/app/rosa/weather/core/designsystem`
- **MODEL** = `core/model/src/main/kotlin/app/rosa/weather/core/model`
- **APP** = `app/src/main/kotlin/app/rosa/weather`
- **WID** = `widget/src/main/kotlin/app/rosa/weather/widget`
- **DATA** = `core/data/src/main/kotlin/app/rosa/weather/core/data`

Line numbers refer to HEAD.

## 1. Sky/background renderer: architecture, layers, parameters, and how they respond to time of day, WMO code and Appearance mode

### Takeaway
The whole app sits on one shared full-screen `SkyScene`. It chains three AGSL RuntimeShaders: SKY, then PRECIPITATION, then the WINDOW pane. An interpolable `SkyParams` drives it: 6 colours and 15 scalars, plus an isSun flag. `SkyParams` is built from a `ForecastMoment` (real sun and moon ephemeris, a WMO-derived `WeatherVisual`, and pane frost and mist derived from temperature and humidity) and from a `SkyPalette` keyed on sun elevation. The result is a flat, screen-space "view through a window pane":
- a vertical 2-colour gradient with radial glows
- a small sun or moon confined to a box beside the temperature numerals
- cell-based twinkling stars
- two 2D fbm cloud decks
- an fbm fog bank
- a lightning flash and bolt
- rain streaks and snowflakes at 4 depths
- a pane with sliding drops, frost, condensation and a tap ripple

There is no landscape or ground, no volumetrics, and no post-processing stack.

### Cited Findings
**Architecture and render graph**
- One sky sits behind the entire app. `RosaAppRoot` wraps the Navigation-3 `NavDisplay` in `SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = sky.transitionMillis)`, and `SkyController` "tunes" it per screen — [RosaAppRoot.kt L52-L104](APP/ui/RosaAppRoot.kt#L52-L104); [SkyController.kt L17-L84](APP/ui/common/SkyController.kt#L17-L84).
- `SkyBackdrop` records `SkyScene` into a shared `Backdrop` via `backdropSource`, which every glass surface then refracts. It also picks the `SceneQuality` and passes in tilt, haptics, the motion flag and the `GlassEnvironment` — [SkyBackdrop.kt L101-L131](DS/component/SkyBackdrop.kt#L101-L131).
- Per frame, `SkyScene` works in four steps — [SkyScene.kt L270-L374](DS/sky/SkyScene.kt#L270-L374):
  1. It draws SKY_SHADER into an offscreen layer at `quality.scale`.
  2. It draws PRECIPITATION into that same layer when the pane is off. When the pane is on, it draws PRECIPITATION into a second layer at the finer `windowScale`.
  3. It applies WINDOW_SHADER as a `RenderEffect.createRuntimeShaderEffect`.
  4. It "bakes" the result into a third layer, so the effect is not re-applied under every glass element, then scales it up to the screen.
- The shaders share value-noise helpers: a 4-hash value noise, a 5-octave `fbm`, `fbmCoarse` (which also returns octaves 1–3) and a 3-octave `fbm3` — [SkyShaders.kt L5-L61](DS/sky/SkyShaders.kt#L5-L61).

**SKY_SHADER layers** ([SkyShaders.kt L63-L220](DS/sky/SkyShaders.kt#L63-L220))
- **Gradient.** `mix(zenith, horizon, pow(uv.y, 1.25))` depends on y only. There is no azimuthal variation, no scattering model and no horizon line — L107.
- **Glow.** `glow × (0.26·exp(−3.2·dist)·(0.35+0.65·veil) + 0.16·h³)`: a radial glow around the body plus a band along the bottom of the screen — L114-L115.
- **Stars.** One candidate per 26-px cell at render resolution. About 28% of cells hold a star and about 3% hold a "big" one. Stars twinkle on a sine, shift by tilt×18 px and dim toward the bottom — L117-L130.
- **Sun.** A smoothstep disc with a two-exponential bloom, attenuated by `veil²` where `veil = 1 − 0.8·cloudCover` — L136-L141.
- **Moon.** A phase terminator (the lit side depends only on waxing or waning, so the orientation is fixed), a ±5% value-noise "crater" tint and a faint halo — L142-L152.
- **Clouds.** There are two decks — L154-L183:
  - Deck 1 is domain-warped `fbmCoarse` with a coverage threshold of `mix(0.74, 0.22, cloudCover)`. Its lit side is estimated from one extra `fbm3` sample offset toward the sun. It gets a "silver lining" on thin edges near the body, but only when a sun is up (`sunUp = isSun·…`).
  - Deck 2 is a finer fbm with flat shading, mixed in at 0.55.
  - Drift is `time·(0.006 + 0.02·wind)` along +x only.
- **Fog.** An fbm "bank" plus faster `fbm3` wisps, mixed toward a pearl colour at up to 0.86 — L185-L193.
- **Lightning.** A whole-sky flash of `(0.7, 0.72, 1.0)·flash·(0.25+0.75·cloudMask)`. On a near strike the bolt is one jagged channel built from 3 noise octaves, with a single fork, reaching 55–85% of the way down the screen — L195-L214.
- **Film grain** of ±0.009 — L216-L217.

**PRECIPITATION_SHADER** ([SkyShaders.kt L222-L323](DS/sky/SkyShaders.kt#L222-L323))
- **Rain.** Four depth layers of cell-based streaks — L239-L302:
  - Far streaks are thin and slow (0.85 screen-heights/s); near streaks are thick and fast (2.4 screen-heights/s), with a motion-blur-shaped profile.
  - Slant is `0.1 + 0.32·wind ± 0.06` gust noise, always leaning the same way.
  - Density is modulated by moving "sheet" noise, and a distance veil is capped at 0.3.
- **Snow.** Four layers, from specks up to bokeh discs with a brighter rim. Each flake sways on its own and has a shaded underside. Drift is `0.4 + 1.6·wind ± 0.4` — L262-L317.
- The precipitation tint is `lerp(horizon, cloudLight, 0.5)` — [SkyScene.kt L333-L341](DS/sky/SkyScene.kt#L333-L341).

**WINDOW_SHADER, the pane** ([SkyShaders.kt L325-L554](DS/sky/SkyShaders.kt#L325-L554))
- **Drop field.** Adapted from Martijn Steinrucken's "Heartfelt": static beads plus 2 sliding drop layers with trails. `Drops()` is evaluated 3× per pixel to get the height gradient — L360-L466.
- **Beads as lenses.** Each bead shows an inverted sample of the scene, with a darkened refracting rim, a sky reflection at the top, a caustic at the foot and a specular highlight — L484-L507.
- **Defocus.** While it rains, the scene behind the beads gets a 9-tap disc defocus — L418-L431, L492-L493.
- **Tap ripple.** An expanding refracting ring — L468-L478.
- **Condensation.** A 9-tap defocus plus a micro-droplet cell pattern, cut away by a CPU wipe mask — L512-L522.
- **Frost.** Grows in from the frame: ridged-noise veins, grain and glints — L524-L550.
- **Wipe mask.** A 90×180 CPU bitmap with an 11-px round brush, faded by 5/255 every 200 ms, so wiped mist returns in about 11 s — [SkyScene.kt L409-L461](DS/sky/SkyScene.kt#L409-L461).

**Parameterization**
- **`SkyParams` fields.** zenith, horizon, glow, sun, cloudLight, cloudShade, bodyPath, bodyLift, isSun, bodyVisible, moonPhase, cloudCover, cloudDark, fog, wind, stars, rain, snow, lightning, frost, condensation. `lerp` is linear per field, and `isSun` flips at t = 0.5 — [SkyScene.kt L67-L104](DS/sky/SkyScene.kt#L67-L104).
- **`SkyParams.from(moment, palette, appearance)`** — [SkyScene.kt L106-L142](DS/sky/SkyScene.kt#L106-L142):
  - It uses the sun when its elevation is above −5°, otherwise the moon.
  - `path = (1 − sin(azimuth))/2` (east to west) and `lift = elevation/50`, clamped to [−0.25, 1].
  - The night factor is `((−sunElev − 6)/8)` in Auto.
  - `bodyVisible = (elev+2)/5`, in Auto only.
  - `stars = night·(1 − 0.85·cloudCover)`.
  - `frost = paneFrost` and `condensation = paneMist`.
- **Pane inputs.** `paneFrost = clamp((0.5 − T°C)/8, 0, 0.9)`. `paneMist = max(0.8·fog, 0.6·humid, 0.25 if rain > 0.2)`, where humid = (RH − 88)/12 — [ForecastMoment.kt L40-L51](MODEL/ForecastMoment.kt#L40-L51).
- **WMO to `WeatherCondition`** (21 values in 7 families). Unmapped codes 4..49 become Fog and anything else becomes Overcast. `WeatherVisual.from` then gives each condition fixed or clamped values of cloudCover, cloudDarkness, rain, snow, fog, lightning, hail and wind (see Q2) — [WeatherCondition.kt L8-L120](MODEL/WeatherCondition.kt#L8-L120). Wind is `windSpeed/18 m/s`, and the humidity mist is `((RH−85)/15)·0.25` — L90-L94.
- **`SkyPalette`**:
  - 9 clear-sky keys by sun elevation (−18, −12, −7, −3, 0, 4, 10, 25, 60°), each giving zenith, horizon, glow and sun, with a smoothstep bracket.
  - A moonlight lift for clear nights.
  - Overcast greys (day or night, darkened by `cloudDarkness`), lilac-milk for snow, pearl for fog and violet for lightning.
  - cloudLight and cloudShade derived from daylight and darkness.
  - Ink flips at a computed brightness above 0.36, and the accent is chosen per condition.
  - The palette is described as "a filmic, slightly warm grade".
  - [SkyPalette.kt L3-L160](MODEL/SkyPalette.kt#L3-L160)
- **Sun and moon placement** (a `SkyStage` box):
  - The default box is x 0.73–0.90, y 0.17–0.25 of the scene. The moon's radius is 0.03 of scene height and the sun's is 0.022 — [SkyStage.kt L8-L35](DS/sky/SkyStage.kt#L8-L35); [SkyScene.kt L377](DS/sky/SkyScene.kt#L377).
  - Home replaces it with a box ±40 dp around the weather-glyph slot to the right of the numerals, measured at rest — [HomeScreen.kt L641-L685](APP/ui/home/HomeScreen.kt#L641-L685).
  - The README frames this as the sun "never going behind the digits" — [README.md L16-L22](README.md#L16-L22).
- **Glyph hand-off.** Under a real clear sky with the body higher than 3°, the hero weather glyph steps aside so the real sun or moon takes its slot — [HomeScreen.kt L458-L464](APP/ui/home/HomeScreen.kt#L458-L464).

**Appearance modes**
- **Auto** is the real sky. **Light** uses the 30° palette "bleached" to porcelain, with dark ink and an amber accent. **Evening** uses the −1.5° blue hour. **Dark** uses −16° with moon illumination of at least 0.4 — [SkyPalette.kt L29-L59](MODEL/SkyPalette.kt#L29-L59).
- Fixed moods draw no sun or moon (`bodyVisible = 0`) and fix the night factor at Dark 1, Evening 0.3, Light 0. Weather stays real in every mode — [SkyScene.kt L114-L128](DS/sky/SkyScene.kt#L114-L128); [Settings.kt L12-L17](MODEL/Settings.kt#L12-L17).
- Changing mood tweens the sky over 900 ms — [SkyController.kt L47-L53](APP/ui/common/SkyController.kt#L47-L53). UI colours derived from the palette animate over 1200 ms — [RosaTheme.kt L49-L56](DS/theme/RosaTheme.kt#L49-L56).

### Inferences
- This is a compact, interpolable, well-factored parameter space, which makes a good base for upgrades. It is, however, a 2D screen-space composite. The only depth cues are 4 precipitation depths, 2 cloud decks, the pane defocus and tiny tilt offsets.
- The "body in a box by the numerals" rule (about an 80 dp box on Home) trades composition for legibility. The sun never sits on a horizon, and the sunset glow at the bottom of the screen and the sun near the top-right are spatially disconnected.
- `SkyParams` has no fields for hail, wind direction, gusts, visibility, aerosols, cloud layers or types, or a ground/landscape. Those would be the first schema extensions.

### Gaps
- How the scene actually looks and performs on device is unknown. The README says the app was never run on a physical device or emulator — [README.md L577-L601](README.md#L577-L601).
- I could not determine how soft the 0.5× bilinear-upscaled sky looks on real high-dpi panels.

## 2. Coverage per weather condition and time of day: what renders today and how distinct it is

### Takeaway
Rain in all its forms, snow, fog, frost and condensation get genuinely rich treatment, mostly thanks to the window pane. Clear, partly cloudy and overcast skies, and every time of day, rely on the palette gradient plus 2D noise clouds and read as flat. Hail is not rendered in the app at all: `SkyParams` has no hail field. Freezing rain and sleet are just rain with a few flakes. Wind is only a scalar. Dust, haze and smoke can never occur, because Open-Meteo never sends those codes and aerosol data isn't fetched. Many conditions differ only in one or two scalars, so they share a generic look.

### Cited Findings
Per-condition inputs come from [WeatherCondition.kt L95-L117](MODEL/WeatherCondition.kt#L95-L117), where `rainOf(b) = b + 0.5·min(mm/6, 1)`. What each condition renders follows from the SKY, PRECIPITATION and WINDOW shaders and the pane rules in Q1.

| Condition (WMO) | cloudCover / dark / rain / snow / fog / lightning / hail | What the app draws | Distinctness |
|---|---|---|---|
| Clear (0) | ≤0.12 / 0 / 0 / 0 / humid-mist ≤0.25 / 0 / 0 | Gradient, sun disc and bloom (or moon and stars), rare wisps | Generic; flat |
| MostlyClear (1) | 0.12–0.35 / 0.05 | Scattered fbm puffs, silver lining near the sun | Close to Clear |
| PartlyCloudy (2) | 0.35–0.65 / 0.12 | Two fbm decks; palette partly greyed | Medium |
| Overcast (3) | ≥0.85 / 0.35 | Near-solid deck; greys; sun veiled to about 10% | Flat grey |
| Fog (45) | 0.7 / 0.2, fog 0.85 | Pearl palette, fbm bank and wisps; pane misted (0.68) so the whole scene is defocused | Soft uniform haze |
| RimeFog (48) | 0.7 / 0.15, snow 0.05, fog 0.95 | As Fog plus sparse flakes | Near-identical to Fog; no rime/hoar crystals unless T < 0.5 °C (frame frost) |
| Drizzle (51–55) | 0.9 / 0.35, rain 0.2+ | Same streak renderer, sparse; small beads | Weakly distinct from light rain |
| FreezingDrizzle (56–57) | as Drizzle + snow 0.1 | Streaks, a few flakes; frame frost only via temperature | No glaze/ice look |
| LightRain / Rain / HeavyRain (61/63/65) | 0.9–1 / 0.4–0.65, rain 0.35 / 0.6 / 0.9+ | 4-depth streaks, sheets, veil; pane beads, trails, defocus; condensation 0.25 | Strong effect, but intensity steps are subtle (renders below) |
| FreezingRain (66–67) | 0.95 / 0.5, rain 0.55, snow 0.15, hail 0.1 | Rain plus sparse flakes; the hail value is ignored | No ice pellets or glaze |
| Light/Snow/Heavy snow (71/73/75) | 0.9–1 / 0.25–0.4, snow 0.35 / 0.65 / 0.95, fog 0.15–0.35 | Lilac-milk palette, 4-depth flakes, frost from the frame when T < 0.5 °C | Good; heavy vs light is a density change only |
| SnowGrains (77) | snow 0.4, hail 0.1 | Same as light snow | Not distinct |
| Rain/Heavy showers (80/81–82) | 0.75–0.85 / 0.45–0.6, rain 0.5 / 0.85 | Broken deck plus rain; the sun can show | No rainbow, no sun-shower treatment |
| SnowShowers (85–86) | 0.8 / 0.3, snow 0.6 | As snow | Not distinct |
| Thunderstorm (95) | 1 / 0.8, rain 0.75+, lightning 1, wind ≥0.4 | Violet-dark palette, rain and pane, flashes every 3.5–11 s, 55% with a visible bolt, thunder haptic | Distinct |
| ThunderstormHail (96/99) | 1 / 0.85, rain 0.6, lightning 1, **hail 0.7**, wind ≥0.5 | Same as Thunderstorm with less rain; **hail is never drawn** | Hail missing |
| Wind (no code) | scalar 0..1 = m/s ÷ 18 | Faster cloud drift (+x only), rain slant and gust wobble, snow drift | No wind visual on dry days |
| Dust/haze/smoke/sand | WMO 4–44 would map to Fog | Would look like fog | Never produced (next bullets) |

**Missing inputs and effects behind the table**
- **Hail.** Not in `SkyParams`, so the app sky ignores `WeatherVisual.hail` — [SkyScene.kt L67-L94](DS/sky/SkyScene.kt#L67-L94), L112-L141. Hail appears only:
  - in the pictogram (`hail()` stones) — [WeatherGlyphPainter.kt L163-L167, L582-L585](DS/glyph/WeatherGlyphPainter.kt#L582-L585);
  - as small specks in the widget picture — [WidgetWeather.kt L116-L125](WID/render/WidgetWeather.kt#L116-L125);
  - through rain tiles in live widget motion — [LiveWeather.kt L141](WID/motion/LiveWeather.kt#L141).
- **Wind.** Wind direction and gusts are fetched (`wind_direction_10m`, `wind_gusts_10m`) — [OpenMeteoClient.kt L76-L81](DATA/network/OpenMeteoClient.kt#L76-L81) — and carried into `ForecastMoment`: gusts are blended, and direction is taken from the current observation or the hour's value — [ForecastMoment.kt L123-L124](MODEL/ForecastMoment.kt#L123-L124). But `WeatherVisual.wind` is speed only — [WeatherCondition.kt L93](MODEL/WeatherCondition.kt#L93) — and no sky shader reads direction or gusts. A code search of the sky package for windDirection/gust finds only comments.
- **Visibility and air quality.** `visibility` (current and hourly) and air quality (`european_aqi, us_aqi, pm2_5, pm10, ozone`, current only) are fetched — [OpenMeteoClient.kt L47-L56, L76-L81](DATA/network/OpenMeteoClient.kt#L47-L81). The sky uses neither: there is no haze, smog or dust rendering.
- **Open-Meteo codes.** The documented WMO table covers only 0–3, 45/48, drizzle, rain, snow, showers and thunderstorm codes. There are no haze, dust, smoke or sand codes, so the `4..49 → Fog` branch is effectively dead for real data — [Open-Meteo forecast API docs](https://open-meteo.com/en/docs). My fetch of that page also listed a code 97, which I could not confirm, so it is omitted here.
- **Data the sky could use but doesn't.** The forecast API also offers hourly `cloud_cover_low/mid/high`, `snowfall`, `showers`, `rain`, `snow_depth`, `freezing_level_height`, `cape`, `sunshine_duration`, `direct_radiation` and a 15-minutely `lightning_potential` (HRRR/European models). None is requested — [Open-Meteo forecast API docs](https://open-meteo.com/en/docs); [OpenMeteoClient.kt L76-L85](DATA/network/OpenMeteoClient.kt#L76-L85).
- **Aerosols.** The Air Quality API offers `dust` (Saharan dust near the surface) and `aerosol_optical_depth` ("to indicate haze") as hourly and current values worldwide via CAMS Global (45 km). Neither is requested — [Open-Meteo Air Quality API docs](https://open-meteo.com/en/docs/air-quality-api); [OpenMeteoClient.kt L47-L56](DATA/network/OpenMeteoClient.kt#L47-L56).

**Time of day (Auto)**
- **Palette keys** from [SkyPalette.kt L63-L74](MODEL/SkyPalette.kt#L63-L74):
  - Deep night: −18° (#05081A / #121637).
  - −12° indigo.
  - −7° violet with a mauve glow (#9C6696).
  - −3° blue-lilac with a coral glow (#F0928A).
  - 0°: horizon #E88C7A, glow #FFB077.
  - 4° apricot: horizon #F4B08A.
  - 10–60° blues, with a white sun above 25°.
- **Night.** Stars start at −6° and reach full strength at −14°, scaled down by cloud — [SkyScene.kt L123-L136](DS/sky/SkyScene.kt#L123-L136). Moonlight lifts a clear night by `0.18·illumination·night·(1−cloud)` — [SkyPalette.kt L86-L91](MODEL/SkyPalette.kt#L86-L91).
- **Night clouds** are dark (cloudLight about #2F3448 at zero daylight) — [SkyPalette.kt L124-L128](MODEL/SkyPalette.kt#L124-L128). They have **no moonlit edges**, because the silver lining is gated by `sunUp` — [SkyShaders.kt L159, L174-L176](DS/sky/SkyShaders.kt#L159-L176).
- **Twilight gap.** Between −5° and −2° neither body is drawn: `useSun` is already true but `bodyVisible` is 0 — [SkyScene.kt L117-L133](DS/sky/SkyScene.kt#L117-L133).
- **Golden hour** has no special logic. The model's `DayPhase` enum (Night, AstronomicalTwilight, BlueHour, GoldenHour, Day) is never consumed by the renderer — [Astronomy.kt L79-L91](MODEL/Astronomy.kt#L79-L91). Warmth comes only from the palette keys, from cloudLight lerping 25% toward glow, and from coloured glass glints.
- **Moon orientation** is fixed in the shader (the lit side depends on waxing or waning only), and the glyph painter states "Northern-hemisphere orientation". Southern-hemisphere moons are therefore mirrored wrong — [SkyShaders.kt L143-L147](DS/sky/SkyShaders.kt#L143-L147); [WeatherGlyphPainter.kt L305-L307](DS/glyph/WeatherGlyphPainter.kt#L305-L307).

**Render observations** (my viewing)
- `home-sunny.jpg` (MostlyClear, midday) is a flat blue gradient with a small white sun disc and soft bloom at the upper right. Clouds are barely visible, and the glass cards dominate the frame — [docs/images/home-sunny.jpg](docs/images/home-sunny.jpg).
- `home-morning.png` (about 09:13 Moscow) and `home-evening.png` (about 17:40) are nearly identical blue skies. The evening's warmth appears only as a faint peach band at the bottom edge — [app/build/screens/home-morning.png, home-evening.png](app/build/screens/).
- `home-rainy.jpg`, `weather-rain.png` and `weather-downpour.png` show convincing lens-like beads, trails and a defocused grey sky. Streaks are faint, and "rain" (0.45) and "downpour" (1.0 with wind 0.6) are hard to tell apart — [docs/images/home-rainy.jpg](docs/images/home-rainy.jpg); [core/designsystem/build/weather/](core/designsystem/build/weather/).
- In `weather-storm.png` the flash washes the whole frame lavender-white, and the bolt is a soft blurred smear with one short fork — [core/designsystem/build/weather/weather-storm.png](core/designsystem/build/weather/weather-storm.png).
- `weather-snow.png` and `weather-frost.png` are nearly identical. Flakes are small faint specks, the frame frost reads as white "scratches", and the cloud shapes are very low-contrast — [core/designsystem/build/weather/](core/designsystem/build/weather/). The home snow screen is similarly milky — [docs/images/home-snow.jpg](docs/images/home-snow.jpg).
- `weather-fog.png` is a uniform milky haze with a visible sun disc. No bank structure reads — [core/designsystem/build/weather/weather-fog.png](core/designsystem/build/weather/weather-fog.png).
- `home-night.jpg` is a flat indigo gradient with sparse, tiny stars: no Milky Way, no moonlit cloud — [docs/images/home-night.jpg](docs/images/home-night.jpg).
- `home-mode-evening.png` is a violet-pink gradient, and the sun is only the glyph, because fixed moods draw no body — [app/build/screens/home-mode-evening.png](app/build/screens/home-mode-evening.png).
- The README promises clouds that "billow and slowly change shape, thin edges glowing near the sun", fog as "a dense bank below and fast wisps in front", and a storm bolt that is "branched and ragged" — [README.md L23-L39](README.md#L23-L39). The renders only partly live up to the fog and bolt claims.
- The shader gallery test covers just 6 scenes (rain, downpour, storm, snow, fog, frost) — [WeatherShaderGalleryTest.kt L137-L163](core/designsystem/src/test/kotlin/app/rosa/weather/core/designsystem/sky/WeatherShaderGalleryTest.kt#L137-L163). The sample data has 6 scenarios (SunnyMild, RainyAfternoon, SnowyCold, StormyWarm, FoggyMorning, ClearNight), with none for hail, sleet, freezing rain, wind or golden hour — [SampleForecast.kt L14-L57](MODEL/SampleForecast.kt#L14-L57).

### Inferences
- The strongest assets are the pane effects: beads, trails, frost, mist and the wipe. The weakest are the sky itself (clear, cloudy and overcast skies, and every time of day), lightning, fog structure and snow density. Those are exactly the states users see most often.
- Intensity steps within a family (drizzle, rain, heavy; light snow, snow, heavy) are density changes rather than qualitative ones. For example, there is no transition to sheets, splashes, whiteout or ground mist.
- Southern-hemisphere users would see a mirrored moon.

### Gaps
- No renders exist for partly cloudy, overcast day, drizzle, freezing rain, hail, showers-with-sun, windy clear or golden hour in the real Auto palette, so those were judged from code only.
- The GIFs (`widget-live.gif`, `tab-bar.gif`, `calendar-live.gif`) were not frame-inspected.

## 3. Transitions: between cities, through time, on weather changes; camera, parallax and depth

### Takeaway
Every state change is handled as a linear lerp of `SkyParams`:
- a 1400 ms tween for weather or time changes
- 900 ms for a change of mood
- per-frame, finger-following blends for the city pager
- instant cuts while scrubbing the timeline

Nothing is choreographed: no clouds rolling in, no rain building, no "travel" move between cities, no camera. Depth motion is limited to a subtle gravity-sensor tilt parallax and a 0.35× scroll parallax on the hero numerals. The sky itself never moves with scrolling.

### Cited Findings
- **Weather and time changes.** `LaunchedEffect(params, transitionMillis)` restarts from the current interpolated state and tweens `progress` 0→1 over `transitionMillis` (default 1400, Compose's default FastOutSlowIn easing). A value of 0 or less snaps — [SkyScene.kt L197-L212](DS/sky/SkyScene.kt#L197-L212). `isSun` flips at t = 0.5, so the body's size and type pop mid-blend — [SkyScene.kt L100](DS/sky/SkyScene.kt#L100).
- **City pager.**
  - `snapshotFlow { currentPage + offsetFraction }` calls `sky.blend(a, b, fraction)` with `transitionMillis = 0`, so the sky follows the finger exactly. Once settled, `sky.show(…, immediate = isScrollInProgress || scrubbing)` applies — [HomeScreen.kt L195-L212](APP/ui/home/HomeScreen.kt#L195-L212); [SkyController.kt L55-L70](APP/ui/common/SkyController.kt#L55-L70).
  - The UI palette switches at fraction 0.5 and then animates over 1200 ms — [SkyController.kt L80-L82](APP/ui/common/SkyController.kt#L80-L82).
  - The city name rolls up or down with a gel spring — [HomeScreen.kt L522-L531](APP/ui/home/HomeScreen.kt#L522-L531).
- **Programmatic city switch.** Choosing a city in Places, adding one from Search, or tapping a widget calls `pagerState.animateScrollToPage(target)` — [HomeScreen.kt L218-L222](APP/ui/home/HomeScreen.kt#L218-L222).
- **Timeline scrubbing.** Each change calls `sky.show(forecast.momentAt(now + hours·3600), immediate = dragging || hours > 0.01f)`, so there is no tween at any non-zero offset — [HomeScreen.kt L241-L249](APP/ui/home/HomeScreen.kt#L241-L249).
  - `momentAt` interpolates temperature, cloud, wind and humidity continuously. But the weather *code* switches discretely at the nearest hour, or when the current observation's weight crosses 0.5 — [ForecastMoment.kt L94-L107](MODEL/ForecastMoment.kt#L94-L107).
  - `WeatherVisual.from` assigns step values per condition — [WeatherCondition.kt L95-L117](MODEL/WeatherCondition.kt#L95-L117).
  - The timeline is a 48-hour ribbon with snap fling — [HourlyTimeline.kt L70-L178](APP/ui/home/HourlyTimeline.kt#L70-L178).
- **Sun/moon stage.** A newly measured stage glides on a very-low-stiffness spring — [SkyScene.kt L194-L195](DS/sky/SkyScene.kt#L194-L195).
- **Tilt.**
  - Tilt comes from `Sensor.TYPE_GRAVITY`, falling back to the accelerometer. It is **not** gyroscope rate data: `SENSOR_DELAY_UI`, a low-pass factor of 0.12, publishing only moves over 0.004, and listening only while the lifecycle is STARTED — [Tilt.kt L21-L64](DS/sensor/Tilt.kt#L21-L64).
  - It is gated by Settings → tilt lighting and by system motion — [SkyBackdrop.kt L52-L67](DS/component/SkyBackdrop.kt#L52-L67).
- **Sky parallax from tilt.**
  - Sun position shifts by tilt·(0.02, 0.012) of the scene, stars by tilt·18 px, cloud noise by tilt·0.04 and 0.08 — [SkyShaders.kt L109, L120, L161, L179](DS/sky/SkyShaders.kt#L109-L179).
  - Precipitation shifts by tilt·0.015 screen-heights — [SkyShaders.kt L287](DS/sky/SkyShaders.kt#L287).
  - SkyScene samples tilt without read observation, so it appears on the next ambient tick — [SkyScene.kt L276-L277](DS/sky/SkyScene.kt#L276-L277).
- **Glass parallax from tilt.** The scene behind the glass shifts 5 dp at full tilt, and world-fixed reflections slide 260 dp across (0.7× vertically) — [LiquidGlass.kt L377-L382, L461-L465](DS/glass/LiquidGlass.kt#L377-L465). The light angle swings up to ±0.6 rad (about ±34°) from Apple's resting 45°/−135° pair — [Tilt.kt L66-L74](DS/sensor/Tilt.kt#L66-L74).
- **Scroll.** The hero numerals translate at 0.35× the scroll and fade out over 700 px. The sky layer has no scroll input — [HomeScreen.kt L332-L343](APP/ui/home/HomeScreen.kt#L332-L343).
- **Scroll edge.** Content fades into the live sky under the top bar and above the tab bar, using a strip redrawn from the sky's layer — [ScrollEdge.kt L28-L99](DS/component/ScrollEdge.kt#L28-L99); [TabBarScaffold.kt L92-L98](APP/ui/common/TabBarScaffold.kt#L92-L98).
- **Screen transitions** over the fixed sky — [RosaAppRoot.kt L114-L124](APP/ui/RosaAppRoot.kt#L114-L124):
  - Tabs: fade in 220 ms with scale 0.98→1 on a gel spring, fade out 150 ms.
  - Push: slide ⅓ of the width, fade 260 ms, scale 0.96.
  - Pop: the mirror of push.
  - Predictive back is wired.
- **Launch.**
  - The splash is an animated-vector drop that falls and sends out a ring, 900 ms on a night background — [themes.xml L10-L14](app/src/main/res/values/themes.xml#L10-L14); [splash_drop.xml](app/src/main/res/drawable/splash_drop.xml).
  - On exit the icon zooms 1→7× and fades over 520 ms with PathInterpolator(0.4, 0, 0.2, 1) — [MainActivity.kt L28-L43](APP/MainActivity.kt#L28-L43).
  - The sky starts from `SkyController.placeholder(now)`, which is the SunnyMild sample at Moscow coordinates at the real time, despite a comment calling it "a calm dusk sky" — [SkyController.kt L90-L92](APP/ui/common/SkyController.kt#L90-L92); [SampleForecast.kt L16-L23](MODEL/SampleForecast.kt#L16-L23).
  - The first real `sky.show(…, immediate = false)` then tweens over 1400 ms — [HomeScreen.kt L196-L207](APP/ui/home/HomeScreen.kt#L196-L207).
  - Cards rise in a cascade (details in Q5), and glass "materialises" once — [Glass.kt L111-L121](DS/component/Glass.kt#L111-L121).

### Inferences
- **Scrubbing cuts.** Scrubbing through a weather change produces hard cuts: rain, snow and lightning appear or vanish in a single frame at hour boundaries, because `immediate = true` and the code-based visuals are step functions.
- **Multi-page jumps.** `animateScrollToPage` across several pages probably flashes through the skies of every intermediate city. The blend follows the pager position, not an A→B cross-fade.
- **Accidental launch intro.** Every cold start plays an unintended 1.4 s morph from "sunny Moscow now" to the local sky. This could be turned into a designed intro. As it stands it can show a sun gliding and changing into a moon, or a clear sky filling with rain.
- **What's missing overall:**
  - No weather-change choreography: fronts arriving from the wind's side, first drops, a storm building, clearing with light breaking through.
  - No "time-lapse" feel while scrubbing (cloud streaking, sun arc sweep, exposure changes).
  - No location-change "journey".
  - No camera language (dolly/zoom on scroll, focus pulls, drift).
- **Tilt strength.** The parallax is subtle: 2% of the screen width for the sun at full tilt. Because it comes from the gravity sensor rather than the gyroscope, it responds to device attitude, not to quick hand motion.

### Gaps
- Whether Compose's system animator-scale handling shortens these tweens when animations are disabled system-wide was not verified. The code only gates lightning and ambient motion explicitly.

## 4. Glass UI and how its lighting couples to the sky

### Takeaway
The coupling is the most sophisticated system in the app. Each frame the sky publishes the light source's screen position, colour and power, plus the sky colour, lightning flash and frost, into a shared `GlassEnvironment`. Every Liquid Glass pane (and the glass numerals) computes its own light direction toward the sun or moon, with a glint, rim lighting, Fresnel sky reflection, lightning and frost. All glass refracts one shared sky RenderNode; frosted cards share one quarter-resolution blur. Widgets imitate the same lighting on bitmaps.

### Cited Findings
- **`publishScene`.** Called in the sky's draw pass — [SkyScene.kt L302-L321](DS/sky/SkyScene.kt#L302-L321):
  - Position is in root px.
  - Colour is the sun colour, or MOONLIGHT #D3DCF0.
  - Power: for the sun, `bodyVisible·clear`; for the moon, `bodyVisible·0.55·clear·(0.35+0.65·moonlight)`, where clear is a smoothstep over cloudCover 0.3–0.95.
  - The environment writes a value only when it visibly changed — [LiquidGlass.kt L130-L191](DS/glass/LiquidGlass.kt#L130-L191).
- **Per-pane light (`lightFor`).** Angle toward the source from the pane centre, followed by up to `1.6·power`, plus the tilt swing. Strength is `power·(0.45 + 0.55/(1+(d/640dp)²))` — [LiquidGlass.kt L201-L218, L458-L459](DS/glass/LiquidGlass.kt#L201-L218).
- **LIQUID_GLASS_SHADER** — [GlassShaders.kt L5-L296](DS/glass/GlassShaders.kt#L5-L296):
  - bevel lensing that samples outward along the edge normal
  - chromatic dispersion at the bezel, strongest at the corners
  - vibrance and tint
  - sheen from the lit side
  - a rim lit at the facing corner and the opposite corner, with a floor of 0.4 around the sides
  - a Fresnel sky band split into a faint rainbow
  - "held light" thickness
  - a specular streak with bloom
  - a glint marched onto the rim from the light direction
  - world-fixed window reflections
  - a lightning flash (strongest at the rim)
  - frost veins from the rim
  - touch-press lens swell and glow
  - a materialise sweep
  - grain
- **Materials.** Presets Regular, Clear, Frosted (18 dp, shared blur), Lens and Sheet — [LiquidGlass.kt L87-L128](DS/glass/LiquidGlass.kt#L87-L128). The shared frost is an 18 dp blur at 0.25× resolution, baked once per sky frame — [LiquidGlass.kt L48-L82, L480-L504](DS/glass/LiquidGlass.kt#L480-L504).
- **Effect caching.** The effect is rebuilt only when a `LensKey` changes; light angle is quantised to 0.004 rad and power to 0.01 — [LiquidGlass.kt L333-L393, L411-L436](DS/glass/LiquidGlass.kt#L333-L436).
- **Glass numerals.** The hero numerals are liquid glass built on a signed distance field. They use the same `lightFor` and flash and cast a soft shadow, and values "melt" by blending distance fields over a 560 ms tween — [GlassText.kt L63-L112, L368-L393, L472-L495](DS/glass/GlassText.kt#L63-L495).
- **Tint and legibility.**
  - Glass tint comes from the palette.
  - `tintBoost` densifies glass over bright skies.
  - The system Contrast setting (Android 14+) makes panes denser and is observed live.
  - [SkyBackdrop.kt L48-L96](DS/component/SkyBackdrop.kt#L48-L96)
- **Glass never on glass.** Nested surfaces become "platters" — [Glass.kt L75-L184](DS/component/Glass.kt#L75-L184).
- **Materialisation** plays once per element via `rememberSaveable`, using a gel spring — [Glass.kt L111-L121](DS/component/Glass.kt#L111-L121).
- **Glass lab.** A test render of every material and control over a bright day, night, sunset and a busy backdrop shows noon, golden, moon, overcast, lightning, frost, touch and materialise variants — [docs/images/glass-dynamic.jpg](docs/images/glass-dynamic.jpg); [README.md L405-L432](README.md#L405-L432). A test enforces that the rim reads on every side — [README.md L373-L403](README.md#L373-L403).
- **Rain drops on the pane belong to the backdrop.** Cards therefore refract drops, and drops never run over the glass UI — [SkyScene.kt L349-L370](DS/sky/SkyScene.kt#L349-L370); [SkyBackdrop.kt L114-L130](DS/component/SkyBackdrop.kt#L114-L130).
- **Fixed moods.** In Light, Evening and Dark, `bodyVisible = 0`, so power is 0 and the glass falls back to the tilt-driven resting light — [SkyScene.kt L133, L308-L312](DS/sky/SkyScene.kt#L133-L312); [LiquidGlass.kt L206-L209](DS/glass/LiquidGlass.kt#L206-L209).

### Inferences
- This coupling is a genuine differentiator, and it is the natural hook for more cinematic lighting: rim light shifting with sunset colour, lightning, and possibly light from lens flare or god-ray sources. Any new light source (a bolt position, a sun on the horizon) would need to be published through `GlassEnvironment` to stay coherent.
- **Cost.** The sky re-renders at 30 fps (Q6), so the refraction in every glass element is recomputed at 30 fps even when nothing visible changes. The shared-layer design keeps this affordable, but it is a standing cost.

### Gaps
- Actual GPU cost of many glass panes over a 30 fps sky on mid-range devices is unmeasured.

## 5. Motion system, micro-interactions, haptics and sound

### Takeaway
The app has a coherent spring vocabulary built on Apple's "gel" spring, and a lot of tactile micro-motion: liquid tab bar and segmented drops, a lens magnifier on the timeline, liquid pull-to-refresh, hops, entrance cascades, melting glass numerals and live instruments. Haptics are semantic and envelope-aware on Android 16+. There is **no sound at all**. Interactions with the sky itself are limited to a tap ripple and wiping mist, and these may be unreachable under full-screen scroll containers.

### Cited Findings
- **Spring tokens** — [RosaMotion.kt L13-L30](DS/motion/RosaMotion.kt#L13-L30):
  - `gel` (damping 0.7, stiffness 158 ≈ Apple duration 0.5 / bounce 0.3)
  - `press` (0.5, 300)
  - `snappy` (0.85, MediumLow)
  - `lazy` (1, VeryLow)
- **Tab bar lens** — [TabBar.kt L73-L272](DS/component/TabBar.kt#L73-L272):
  - A drop with a leading edge (`Follow` spring 1/1400 while dragging, gel otherwise) and a trailing edge (`TabTail` 0.55/110) that stretches it.
  - Scale `(1+stretch)·(1+0.12·lift)` × `(1−0.14·stretch)·(1+0.3·lift)`.
  - Icons swell 16% under the lens.
  - A haptic `press` on touch and `tick` per tab.
  - It opens the tab only on lift-off.
- **Segmented drop, toggle and slider.** The segmented control uses the same two-edge drop (`DropTail` 0.55/110). The toggle knob turns into a glass lens only while pressed. The slider knob squashes with velocity and ticks per step — [Controls.kt L67-L397](DS/component/Controls.kt#L67-L397).
- **Page dots** stretch and merge as droplets — [Controls.kt L356-L391](DS/component/Controls.kt#L356-L391).
- **Hourly lens magnifier.** A fixed glass lens sits over a 48-hour LazyRow with snap fling. Under the lens, `focus = 1 − |i − offset|/1.6` scales the glyph +30% (rasterised at 1.3×), the label +5%, the temperature label +14%, and adds a halo to the curve dot. There is a haptic `tick` per hour and `milestone` at sunrise, sunset and midnight — [HourlyTimeline.kt L76-L178, L197-L300, L304-L312](APP/ui/home/HourlyTimeline.kt#L76-L312).
- **Liquid pull-to-refresh** — [HomeScreen.kt L687-L810](APP/ui/home/HomeScreen.kt#L687-L810):
  - Resistance is 0.45× and the threshold 96 dp.
  - `thresholdReached` haptic when armed; `splash` haptic on release.
  - The glass-lens drop grows from 18 to 48 dp and stretches past the threshold.
  - While refreshing, it breathes (`sin(π·t/0.7)`, a 1.4 s period, ±8%) and waits for the refresh with a 20 s timeout.
  - The sky does not react.
- **Hero numerals** — [HomeScreen.kt L388-L456](APP/ui/home/HomeScreen.kt#L388-L456):
  - A tap toggles "feels like" for 4.5 s.
  - A squash spring (0.35/420) scales x +5% and y −4%.
  - The value melts over 560 ms ([GlassText.kt L104-L112](DS/glass/GlassText.kt#L104-L112)).
  - A pulsing headline dot is driven by the ambient clock — [HomeScreen.kt L495-L503](APP/ui/home/HomeScreen.kt#L495-L503).
- **Entrance cascade.** Cards stagger in 70 ms apart on a gel spring: they rise 36 dp, scale 0.96→1 and fade in. This happens only within 900 ms of a page first appearing, once per visit — [HomeScreen.kt L549-L576, L638-L639](APP/ui/home/HomeScreen.kt#L549-L639).
- **Hop and press.** Icon hop: `rotationZ −16°·v`, scale +20%, spring (0.32, 380). Glass buttons swell about 8 px under the finger on the `press` spring. A touch glow follows the finger — [Glass.kt L186-L357](DS/component/Glass.kt#L186-L357).
- **Live instruments** on the ambient clock — [DetailsGrid.kt L57-L343](APP/ui/home/DetailsGrid.kt#L57-L343):
  - a compass needle that springs to the wind and flutters on gusts, one swing every 0.3–1.6 s
  - humidity as sloshing liquid (2.6 s period)
  - a UV arc
  - a barometer needle
  - an AQI marker
  - an animated precipitation glyph
- **Daily rows** unfold with `expandVertically(gel)` — [DailyForecast.kt L62-L157](APP/ui/home/DailyForecast.kt#L62-L157).
- **Places** cards swipe-to-delete on the gel spring with a `confirm` haptic — [PlacesScreen.kt L149-L214](APP/ui/places/PlacesScreen.kt#L149-L214).
- **Appearance picker.** Four mini "living skies" with breathing glow and twinkling stars — [AppearancePicker.kt L55-L220](APP/ui/settings/AppearancePicker.kt#L55-L220).
- **Animated weather glyphs** — [WeatherGlyphPainter.kt L25-L60, L189-L193, L459-L585](DS/glyph/WeatherGlyphPainter.kt#L25-L585); [Icons.kt L205-L237](DS/component/Icons.kt#L205-L237):
  - Only the parts that move are drawn live on the ambient clock: sun-ray rotation at 0.12 rad/s, falling drops, swaying flakes and hail.
  - Clouds are cached bitmaps from a 12 MB LRU — [GlyphRaster.kt](DS/glyph/GlyphRaster.kt).
- **Sky interactions** — [SkyScene.kt L243-L268, L235-L241](DS/sky/SkyScene.kt#L235-L268):
  - A tap starts a 2.5 s refracting ripple on the pane and a `raindrop` haptic.
  - A drag wipes condensation and frost, with a `raindrop` haptic every 48 px, and the mist returns.
- **Haptics.** `RosaHaptics` uses envelope effects (`BasicEnvelopeBuilder`, API 36 "BAKLAVA"), composition primitives, and `HapticFeedbackConstants` as a fallback. Levels are Off, Subtle and Rich (default Rich) — [RosaHaptics.kt L18-L198](DS/haptics/RosaHaptics.kt#L18-L198); [Settings.kt L24](MODEL/Settings.kt#L24). Effects:
  - `tick`, `milestone`, `press`, `confirm`, `reject`, `toggle`, `snap`
  - `thresholdReached` (an envelope when Rich)
  - `splash` (thud + quick fall)
  - `raindrop` (a randomised low tick, Rich only)
  - `thunder(distance)`: crack plus a long rumble. It is an envelope with 4 control points, softer and longer with distance. It falls back to thud, spin and low tick, then to a waveform or LONG_PRESS. Rich only.
- **Thunder timing.** Flashes come every 3.5–11 s at random. Distance is random, and the bolt is visible when distance < 0.55. The thunder haptic lands 250–2450 ms after the flash. The flash envelope is 0.5–0.9 → 0.15 (70 ms) → 0.4–0.7 (50 ms) → 0 (420 ms) — [SkyScene.kt L214-L233](DS/sky/SkyScene.kt#L214-L233).
- **No sound.** A repo-wide search finds no audio APIs (MediaPlayer, SoundPool, AudioTrack, ExoPlayer, media3), and `app/src/main/res` holds only drawable, mipmap-anydpi, values, values-ru and xml, with no raw or audio assets — [grep over app/, core/, widget/](app/src/main/res).

### Inferences
- **Possible dead sky interactions.** The tap ripple and wipe are probably unreachable on Home, and possibly on other list screens too: `SkyScene`'s `pointerInput` is a sibling *below* the content in `SkyBackdrop`'s Box, while the full-screen `HorizontalPager` and `LazyColumn` take the hit. By default, Compose stops hit-testing siblings once one is hit, and `awaitFirstDown(requireUnconsumed = false)` does not change which node receives the event. Nothing tests sky touch; the only `performTouchInput` uses are a scroll swipe and tab-bar gestures. This needs on-device confirmation. The README advertises the finger wipe — [README.md L22](README.md#L22).
- **Haptic range.** Haptics cover UI and thunder well. There is no ambient texture: no continuous rain patter, no hail rattle, no wind gust surges, no rumble scaled to storm intensity, and no rumble at all in Subtle.
- **Sound.** The absence of audio is the biggest single gap against "cinematic": no rain on glass, thunder, wind or ambient beds, and no audio-haptic coupling.

### Gaps
- The feel of any haptic on real actuators is unverified (the README says as much).

## 6. Performance, quality tiers, safeguards and accessibility

### Takeaway
Performance engineering is thoughtful: one 30 fps ambient clock, reduced render scales, a shared baked frost and pane, effect-key caching, raster caches, and a FrameMetrics-based downgrade. But the budgets are implicit. There are no device measurements, no benchmarks and no baseline profile. "Auto" never selects Cinematic, power and thermal state aren't observed live, and fast precipitation is capped at 30 fps. Accessibility covers system animation-off and Contrast only. There is no in-app reduce-motion or flash-reduction option.

### Cited Findings
- **Scene tiers** ([SkyScene.kt L145-L154](DS/sky/SkyScene.kt#L145-L154)):

  | Tier | Sky scale | Pane | Pane scale |
  |---|---|---|---|
  | Battery | 0.33 | off (no drops, frost, mist or ripple; precipitation drawn at 0.33) | 0.33 |
  | Balanced | 0.5 | on | 0.75 |
  | Cinematic | 0.75 | on | 1.0 |

  The pane runs only when rain > 0.05, frost > 0.02, condensation > 0.05 or a ripple is active — [SkyScene.kt L323-L331](DS/sky/SkyScene.kt#L323-L331).
- **User setting.** `EffectsQuality` offers Auto, Battery, Balanced and Cinematic, default Auto — [Settings.kt L8-L10, L25](MODEL/Settings.kt#L8-L25); [SettingsScreen.kt L125-L131, L211-L216](APP/ui/settings/SettingsScreen.kt#L125-L216).
- **Auto** gives Balanced, or Battery under power saver, thermal status ≥ MODERATE, or frame struggle. It never gives Cinematic. The check sits inside `remember(effects, struggling)`, so saver and thermal changes are not observed live — [SkyBackdrop.kt L133-L151](DS/component/SkyBackdrop.kt#L133-L151).
- **Frame struggle.** A frame counts as late when `TOTAL_DURATION > DEADLINE`. After a 5 s warm-up, if more than 25% of a 120-frame window is late, the app downgrades once for the rest of the session. A background HandlerThread listens — [FramePacing.kt L19-L73](DS/motion/FramePacing.kt#L19-L73). `JankWatchTest` covers this logic only — [JankWatchTest.kt L10-L26](core/designsystem/src/test/kotlin/app/rosa/weather/core/designsystem/motion/JankWatchTest.kt#L10-L26).
- **Ambient clock.** `AmbientClock.FPS = 30`: a `withFrameNanos` tick followed by `delay(27 ms)`. The sky and every glass surface refracting it redraw at 30 Hz regardless of refresh rate. Taps, flashes and transitions run at the full rate. The clock is frozen when motion is off — [AmbientClock.kt L13-L53](DS/motion/AmbientClock.kt#L13-L53); [SkyScene.kt L184-L186](DS/sky/SkyScene.kt#L184-L186); [README.md L479-L482](README.md#L479-L482).
- **Continuous rendering.** The sky draw reads `clock.seconds`, and the film grain is re-seeded every frame — [SkyShaders.kt L217](DS/sky/SkyShaders.kt#L217). So a static-looking clear sky is still re-rendered at 30 fps whenever motion is enabled.
- **Caching and baking** — [SkyScene.kt L365-L370](DS/sky/SkyScene.kt#L365-L370); [LiquidGlass.kt L48-L82, L278-L280](DS/glass/LiquidGlass.kt#L48-L280); [GlyphRaster.kt](DS/glyph/GlyphRaster.kt); [GlassText.kt L151-L186](DS/glass/GlassText.kt#L151-L186); [SkyBackdrop.kt L60-L67](DS/component/SkyBackdrop.kt#L60-L67); [LiquidGlass.kt L168-L175](DS/glass/LiquidGlass.kt#L168-L175):
  - the pane effect baked once per sky frame
  - shared frost at quarter resolution
  - glass `LensKey` rebuild-on-change
  - `GlyphRaster` 12 MB LRU with 48 moon phases
  - GlassText distance fields built off the main thread, with a prefetch of every scrubbable temperature
  - tilt and scene publishing gated by thresholds
- **Previous jank fixes** appear in history: 1.2.1 "Fix scroll jank from glass pictograms and live widget previews", 1.2.2 "no per-card blurs, baked frost", and "Smooth out rendering: one ambient clock, shared frost, lighter sky" — [git log: 35670d5, 21c53a2, a33c311](.git).
- **Tooling.** The app depends on `androidx.profileinstaller`, but the repo has no baseline or startup profile file and no macrobenchmark module — [app/build.gradle.kts L57](app/build.gradle.kts#L57); [gradle/libs.versions.toml L24](gradle/libs.versions.toml#L24).
- **Validation.** Everything was checked in Robolectric renders only. The README states that shader performance, launcher behaviour and vibration "require verification on hardware" — [README.md L577-L601](README.md#L577-L601).
- **Motion accessibility.** `LocalMotionEnabled` comes from `Settings.Global.ANIMATOR_DURATION_SCALE > 0`, read once per context in `remember`, so it is not live — [RosaMotion.kt L32-L43](DS/motion/RosaMotion.kt#L32-L43). When it is false:
  - the ambient clock freezes
  - lightning stops (`animate`)
  - no entrance cascade, materialise, hop or touch glow
  - tilt is off
  - sky taps and wipes are *not* gated (`interactive` stays true)
  - [SkyBackdrop.kt L52-L54, L113-L126](DS/component/SkyBackdrop.kt#L52-L126); [SkyScene.kt L215-L216](DS/sky/SkyScene.kt#L215-L216); [HomeScreen.kt L555-L556](APP/ui/home/HomeScreen.kt#L555-L556); [Glass.kt L104-L134](DS/component/Glass.kt#L104-L134)
- **Other accessibility.** The system Contrast setting densifies glass. The temperature is a polite live region, and hour cells carry content descriptions. Ink flips for legibility, and the tab bar was contrast-tested at ≥4.8:1 — [SkyBackdrop.kt L83-L96](DS/component/SkyBackdrop.kt#L83-L96); [HomeScreen.kt L438](APP/ui/home/HomeScreen.kt#L438); [HourlyTimeline.kt L198-L200](APP/ui/home/HourlyTimeline.kt#L198-L200); [README.md L46-L75](README.md#L46-L75).

### Inferences
- **Approximate per-pixel work** (my count from the shader source):
  - Sky, worst case (cloudy + fog + bolt): about 23 value-noise evaluations of 4 hashes each at sky resolution.
  - Pane: 3× `Drops()`, each a static layer plus 2 drop layers, then up to three 9-tap blurs (bead defocus, mist, frost scatter), each tap re-evaluating the scene.
  - Precipitation: 8 layer evaluations.
  - On Cinematic, all of this runs at 1080×2340 for the pane at 30 fps. It is plausible on flagship GPUs but unmeasured; mid-range thermals are a real risk.
- **30 fps judder.** The nearest rain layer moves about 0.08 screen-heights (≈190 px on a 2340-px screen) per 30 fps tick. On 90/120 Hz screens, fast precipitation and lightning-lit rain will look steppy or strobed compared with display-rate animation. A cinematic upgrade would likely want the precipitation, or all of it, at the display rate, with a budget.
- **Missing budget infrastructure:**
  - GPU-tier detection that would enable Cinematic automatically
  - live saver and thermal listeners (`PowerManager.addThermalStatusListener`)
  - dynamic resolution scaling
  - an idle mode that stops redrawing when nothing visible moves
  - explicit frame-time budgets per layer
- **Missing accessibility options:** a dedicated in-app reduce-motion or "calm sky" toggle, and a photosensitivity option for lightning (flashes up to 0.9 additive, double-pulsed). Both would be expected in a commercial release.

### Gaps
- No frame-time, GPU-time, battery or thermal data exists for any tier.
- I could not verify whether Compose rendering keeps ticking when the activity is stopped. Only tilt is explicitly lifecycle-gated.

## 7. Home-screen widgets: how cinematic the live weather is, and its limits

### Takeaway
Widgets are static Canvas bitmaps: Sky, Glass, Clear, Tonal and Paper styles, with a CPU-drawn sky, glass bevel lighting and pane effects. Live motion is limited to AnimatedVectorDrawable tiles for rain, snow and storm, which the launcher plays inside a ProgressBar. Clouds, sun, fog and stars never move on weather widgets. Separately, the calendar widget contains a much richer "matte painting" engine (landscapes, god rays, rainbow, Milky Way, water reflections, bloom, vignette) that the app's live sky doesn't use.

### Cited Findings
- **Live tiles.**
  - Tiles are 180×90 dp (180×180 for seasonal ones). Weather mapping: storm if lightning > 0.1; SnowLight or SnowHeavy (≥0.55) if snow ≥ rain; RainLight or RainHeavy (≥0.5) for rain or hail; otherwise nothing.
  - Paper style and the "live weather" or "weather art" toggles disable it. Storm bolts appear only in the top row, and all flashes are synchronised.
  - Stable view ids keep drops running across updates.
  - [LiveWeather.kt L19-L180](WID/motion/LiveWeather.kt#L19-L180)
- **Tile timing.** The generated tiles loop on about 3.3–5.6 s cycles for rain streaks and drops, and on 12 s cycles for storm flashes — [motion_rain_heavy_a.xml](widget/src/main/res/drawable/motion_rain_heavy_a.xml); [motion_storm_a.xml](widget/src/main/res/drawable/motion_storm_a.xml).
- **What the tiles show and how they're built.** Streaks in 3 depths, drops landing and splashing, heavy drops sliding, and snow drifting in 3 depths. They are generated by `MotionResources` in tests, which check that they use only render-thread-animatable properties and closed loops. Under "Remove animations" the launcher shows a still frame — [README.md L145-L169](README.md#L145-L169).
- **Sky style (static)** — [WidgetBackground.kt L350-L521](WID/render/WidgetBackground.kt#L350-L521):
  - a vertical gradient with a radial horizon glow toward the sun's side
  - random stars (10–90)
  - a sun as two radial gradients, or a moon as the glyph
  - clouds as 2 + 7×cover blurred ovals with 3 circles each
  - fog as 3 blurred horizontal bars
  - a static blurred polyline bolt (only when not live)
  - a legibility veil and grain
- **Pane on the widget.** Beads with inverted sky, trails, frame frost and humid mist. Hail is drawn as 0.7–1.3 dp specks — [WidgetWeather.kt L19-L125, L205-L244](WID/render/WidgetWeather.kt#L19-L244).
- **Glass lighting on widgets.** Bevel lighting comes from the real sun or moon side and colour, with a glint in real sunlight and reflections — [WidgetBackground.kt L44-L57, L212-L334](WID/render/WidgetBackground.kt#L44-L334); [README.md L172-L205](README.md#L172-L205).
- **Renders.** `widgets-scenarios-sky.jpg` and `widgets-weather.jpg` show readable, pretty but static scenes: blob clouds, a thin-line bolt, and good drops and frost — [docs/images/widgets-scenarios-sky.jpg](docs/images/widgets-scenarios-sky.jpg); [docs/images/widgets-weather.jpg](docs/images/widgets-weather.jpg).
- **Freshness.**
  - Redraws happen at "significant moments": hour starts, sunrise and sunset, every 5 minutes for nowcast headlines, and exactly at the next scene change found by `Forecast.nextSceneChange` with a 5-minute step and a 30-second bisection — [ForecastMoment.kt L53-L89](MODEL/ForecastMoment.kt#L53-L89); [README.md L206-L232](README.md#L206-L232).
  - The widget studio shows the app's live `SkyBackdrop` behind its previews (non-interactive) — [WidgetStudioActivity.kt L138](WID/studio/WidgetStudioActivity.kt#L138).
- **Calendar painting engine.**
  - `Painting.kt` builds a landscape "the way a matte painter builds one": sky, glow, sun with streak, moon with halo, stars, `milkyWay`, `clouds`, `fog`, `rays`, `rainbow`, `ridge` (aerial perspective), `water` (mirror, ripple, glint), `bloom` and `vignette` — [Painting.kt L33-L39, L79-L675](WID/render/calendar/Painting.kt#L33-L675).
  - It adds per-pixel tree crowns, spruces with snow load and forest walls — [Shaders.kt L14-L318](WID/render/calendar/Shaders.kt#L14-L318).
  - It adds snow drifts, meadows, rye waves, thaw puddles and leaf litter — [SeasonScene.kt L21-L250](WID/render/calendar/SeasonScene.kt#L21-L250).
  - The calendar render package is about 9.1k lines of CPU/bitmap code.
  - Seasonal motion tiles add birds, leaves, petals, fireflies, meteors, mist, embers, blizzard and more — [LiveWeather.kt L45-L100](WID/motion/LiveWeather.kt#L45-L100).
- **Unverified on launchers.** Playback on real launchers has not been verified — [README.md L577-L601](README.md#L577-L601).

### Inferences
- Widget "cinematics" are capped by the platform. RemoteViews can't run shaders or code between updates, so only looping AVDs are possible. The biggest widget gaps are:
  - moving clouds, fog and mist on weather faces
  - twinkling stars and meteors on clear nights (the calendar already has meteor and twinkle tiles that could be reused)
  - sun glints and god rays
  - wind-driven variants (direction, gusts)
  - hail- and sleet-specific tiles
- The calendar's painting engine shows the team can produce landscape, god-ray, rainbow, Milky Way, water and bloom imagery. Porting its ideas, or pre-rendered layers from it, into the app's sky is an obvious lever.

### Gaps
- Launcher compatibility, battery impact of AVD tiles and the look on real devices are all unknown.

## 8. Gaps and weaknesses against a rich, commercial-level "cinematic" weather app

### Takeaway
Rosa already has premium-grade glass, interaction physics and window-pane rain, frost and mist, plus sky-coupled lighting. What keeps it from feeling cinematic is the sky itself: flat 2D gradients and noise clouds, a tiny boxed sun, no horizon or landscape, and weak lightning, fog and snow. Beyond that: no light shafts or post-processing, no hail, sleet, wind or haze visuals, no choreographed transitions, launch or location moves, no camera, no sound, thin ambient haptics, 30 fps precipitation, and zero on-device validation. Several of these capabilities already exist in the codebase's calendar painter.

### Cited Findings
Evidence of absence comes from reading the renderer; each item cites where the missing capability would live.

**Sky and light**
- The gradient is vertical and 2-colour. There is no atmospheric scattering, no azimuth-dependent sunset glow, no Earth shadow or Belt of Venus, and no horizon, ground or landscape layer — [SkyShaders.kt L102-L115](DS/sky/SkyShaders.kt#L102-L115).
- The sun is a small disc with an exponential bloom and nothing else: no corona, no lens flare or ghosts, no god or crepuscular rays, no HDR. It is confined to about an 80 dp box, so there is never a sunrise or sunset on a horizon — [SkyShaders.kt L136-L141](DS/sky/SkyShaders.kt#L136-L141); [HomeScreen.kt L670-L685](APP/ui/home/HomeScreen.kt#L670-L685).
- There is no HDR or wide-gamut usage: a search for `COLOR_MODE_HDR`, `hdrSdrRatio`, `setDesiredHdrHeadroom`, `DisplayP3` and `EXTENDED_SRGB` finds nothing — [grep over app/, core/, widget/](app/src/main).
- The moon has a flat disc, a ±5% noise texture and a fixed-orientation terminator. There is no halo or corona ring and no moonlit cloud edges — [SkyShaders.kt L142-L152, L174-L176](DS/sky/SkyShaders.kt#L142-L176).
- Stars are 1–2 px cells with sine twinkle only: no Milky Way, meteors, colour or constellations. The calendar painter has `milkyWay`, and the calendar tiles have meteors — [SkyShaders.kt L117-L130](DS/sky/SkyShaders.kt#L117-L130); [Painting.kt L188](WID/render/calendar/Painting.kt#L188).
- The app has no aurora, rainbow (for showers with sun), heat shimmer, haze, dust, smoke or pollen. The calendar painter has `rainbow` and `rays`; aerosols aren't fetched — [Painting.kt L294-L313](WID/render/calendar/Painting.kt#L294-L313); [OpenMeteoClient.kt L47-L56](DATA/network/OpenMeteoClient.kt#L47-L56).

**Clouds and fog**
- Clouds are two 2D fbm decks. They have no volumetric or raymarched structure and no self-shadowing depth. There are no cloud types (cirrus, cumulus, stratus, cumulonimbus anvil, mammatus), no layered cover from `cloud_cover_low/mid/high`, no underlit sunset cloud bases, no cloud shadows and no wind-direction drift — [SkyShaders.kt L154-L183](DS/sky/SkyShaders.kt#L154-L183); [Open-Meteo docs](https://open-meteo.com/en/docs).
- Fog is a single 2D bank with wisps. It has no depth layering, no light shafts through fog, and no ground mist hugging a horizon — [SkyShaders.kt L185-L193](DS/sky/SkyShaders.kt#L185-L193).

**Precipitation and storms**
- Rain has no splashes, puddles or ground, no drops running over the UI glass, no lightning-lit rain curtains beyond the global flash, and no wind direction. Intensity steps are subtle — [SkyShaders.kt L239-L302](DS/sky/SkyShaders.kt#L239-L302); [SkyScene.kt L349-L370](DS/sky/SkyScene.kt#L349-L370).
- Snow does not accumulate on the pane edge, the UI or cards. There are no whiteout gusts and no glitter of settled snow — [SkyShaders.kt L262-L317](DS/sky/SkyShaders.kt#L262-L317).
- Lightning is one soft channel with one fork. It is rendered at sky resolution (0.5×) and then defocused by the pane, so it looks smeared. There is no branching tree, no in-cloud sheet-lightning variation and no afterglow. Timing is random only — [SkyShaders.kt L195-L214](DS/sky/SkyShaders.kt#L195-L214); [SkyScene.kt L214-L233](DS/sky/SkyScene.kt#L214-L233).
- Hail is not rendered in the app; freezing rain and sleet have no glaze or bouncing pellets — [SkyScene.kt L67-L94](DS/sky/SkyScene.kt#L67-L94); [WeatherCondition.kt L107, L116](MODEL/WeatherCondition.kt#L107-L116).
- Wind has no visual on dry days: no leaves, debris, grass or tree sway, and no gust events. Direction and gusts are unused — [WeatherCondition.kt L93](MODEL/WeatherCondition.kt#L93).

**Composition, camera and grading**
- There is no camera: no scroll-linked sky parallax, dolly or zoom, and no depth-of-field pulls except the pane's rain defocus. Tilt parallax is tiny — [HomeScreen.kt L332-L343](APP/ui/home/HomeScreen.kt#L332-L343); [SkyShaders.kt L109](DS/sky/SkyShaders.kt#L109).
- Colour grading is the palette only. There is no LUT, tone mapping, bloom, vignette or chromatic aberration in the app sky, only ±0.009 grain. The calendar painter has `bloom` and `vignette` — [SkyShaders.kt L216-L217](DS/sky/SkyShaders.kt#L216-L217); [Painting.kt L652-L675](WID/render/calendar/Painting.kt#L652-L675).

**Transitions, audio, haptics, widgets, engineering**
- Transitions are linear parameter lerps: 1400 ms for weather, instant cuts while scrubbing, and pager-position blends. There is no designed launch intro beyond the splash zoom and the accidental placeholder morph, and no weather-event choreography — [SkyScene.kt L197-L212](DS/sky/SkyScene.kt#L197-L212); [HomeScreen.kt L195-L249](APP/ui/home/HomeScreen.kt#L195-L249); [MainActivity.kt L28-L43](APP/MainActivity.kt#L28-L43).
- There is no audio of any kind (Q5).
- Haptics include thunder but no ambient rain, hail or wind textures — [RosaHaptics.kt L126-L163](DS/haptics/RosaHaptics.kt#L126-L163).
- Weather widgets are static apart from precipitation and lightning tiles — [LiveWeather.kt L135-L144](WID/motion/LiveWeather.kt#L135-L144).
- On the engineering side:
  - ambient motion is capped at 30 fps
  - Auto never picks Cinematic
  - saver and thermal state aren't observed live
  - there are no benchmarks or baseline profiles
  - nothing has been run on a device
  - [AmbientClock.kt L27-L29](DS/motion/AmbientClock.kt#L27-L29); [SkyBackdrop.kt L133-L151](DS/component/SkyBackdrop.kt#L133-L151); [README.md L577-L601](README.md#L577-L601)
- Minor correctness issues:
  - the pop at `isSun` t = 0.5 during blends ([SkyScene.kt L100](DS/sky/SkyScene.kt#L100))
  - no body drawn between −5° and −2° ([SkyScene.kt L117-L133](DS/sky/SkyScene.kt#L117-L133))
  - the northern-only moon orientation
  - the stale "calm dusk" comment on the placeholder ([SkyController.kt L90-L92](APP/ui/common/SkyController.kt#L90-L92))
  - `DayPhase` unused by the renderer ([Astronomy.kt L79-L91](MODEL/Astronomy.kt#L79-L91))

**Strengths to preserve and build on** (the upgrade plan should keep these):
- the physically inspired pane: Heartfelt drops as lenses, frost, wipeable mist
- sky-lit Liquid Glass with a per-pane sun direction, glints and lightning on glass
- one shared backdrop RenderNode with baked frost
- the interpolable `SkyParams` schema and a single `ForecastMoment` source shared by app and widgets
- rich spring and haptic micro-interactions
- real ephemeris for sun and moon
- quality tiers with FrameMetrics fallback
- the calendar's matte-painting toolkit

These are documented throughout Q1–Q7.

### Inferences
Ranked by impact on how cinematic the app feels, based on the renders and code above:
1. A real sky model: scattering-style gradients with azimuthal sunset glow, bigger and lit sun and moon moments with an HDR-style bloom, flare and rays, and a horizon or landscape silhouette layer. The calendar `Painting` ideas could supply the landscape.
2. Volumetric-looking, layered, typed clouds driven by low/mid/high cover and wind direction.
3. Stronger storms: crisp branching bolts rendered at pane resolution after the defocus, cloud-interior flicker, and a haptic rumble plus audio.
4. An ambient audio layer tied to condition and intensity, with coupled haptics.
5. Choreographed transitions: weather fronts, a designed launch reveal, a location "journey", time-lapse scrubbing with tweened condition changes instead of cuts.
6. Missing conditions: hail, sleet or glaze, windy-dry, haze or dust via `aerosol_optical_depth`/`dust`, rainbow after showers, aurora at high latitudes, fog layering.
7. A post stack: bloom, vignette, LUT grade per time of day, optional HDR headroom.
8. Display-rate precipitation, GPU-tier auto-Cinematic and live thermal adaptation, validated with on-device benchmarks.

Each item would also need an accessibility counterpart: reduce motion, flash reduction, and a way to mute audio.

### Gaps
- There is no competitor benchmark in these notes: which commercial apps have which of these features is outside this audit's scope.
- There is no measured GPU headroom on target devices to size the upgrades.
- The sky touch-reachability defect (Q5) is inferred, not reproduced.
