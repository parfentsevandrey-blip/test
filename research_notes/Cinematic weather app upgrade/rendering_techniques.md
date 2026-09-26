# Real-Time Cinematic Weather & Sky Rendering for Mobile Fragment Shaders (Android AGSL / GLSL ES)

Labels used below: **[Classic]** = older foundational reference, still useful; **[Production]** = shipped in AAA engines/games; **[SOTA 2021–2026]** = current state of the art or current platform API. "Inference" bullets are my synthesis (not stated by the sources) and are meant for the report writer to present as recommendations, not facts. Cost numbers in Inferences are rough estimates unless tied to a cited finding.

---

## 1. Sky and light: atmospheric scattering, sun/glare/flares, golden & blue hour, Belt of Venus, moon/earthshine, stars/Milky Way/meteors, aurora

### Takeaway
The production reference for a physically based sky is Hillaire 2020 (EGSR, used in Unreal Engine). It relies on tiny lookup tables (LUTs): transmittance 256×64, multi-scattering 32×32 and, on mobile, a 96×50 sky-view LUT. On an iPhone 6s the whole sky cost about 1 ms in Fortnite. An AGSL background can get most of that look for about one bilinear lookup per pixel plus an analytic sun disc, as long as the LUTs are rebuilt off the per-frame path and only when the sun moves. Twilight realism needs two things. The first is ozone absorption (Chappuis band), which makes the blue hour blue. The second is the anti-solar Belt of Venus with Earth's shadow beneath it. Night scenes need stars, a moon with phase and earthshine, and optionally an aurora. Aurora emission colours and altitudes are well documented, and there is a proven "2D footprint × height profile" factorisation that makes it cheap.

### Cited Findings

**Analytic/fitted sky models (cheapest physically-motivated options)**
- [Classic, 1999] Preetham was the "de facto standard analytic skydome model": simple closed-form Perez formula fitted to outputs of the Nishita model; inputs (solar angle, turbidity) set once per scene. Listed issues: only a small subset of atmospheric conditions can be captured, lack of a localised aureole at higher turbidities, spectral data obtained by conversion from tristimulus data — [Hošek, SIGGRAPH 2012 presentation](https://cgg.mff.cuni.cz/projects/SkylightModelling/Hosek_SG_2012_Presentation.pdf)
- [Classic, 2012] Hošek–Wilkie "An analytic model for full spectral sky-dome radiance" (ACM TOG 31(4)) was fitted to a brute-force Monte-Carlo path tracer (with Mie scattering). It improves sunsets and high-turbidity skies, which were Preetham's weak points, adds ground albedo, and handles each spectral component independently. It is described as a drop-in replacement for Preetham — [SIGGRAPH history page](https://history.siggraph.org/learning/an-analytic-model-for-full-spectral-sky-dome-radiance-by-hosek-and-wilkie/); [ACM DL](https://dl.acm.org/doi/10.1145/2185520.2185591). Fitted parameter range shown in the talk: turbidity 1–10, ground albedo 0–1 — [Hošek slides](https://cgg.mff.cuni.cz/projects/SkylightModelling/Hosek_SG_2012_Presentation.pdf). A Shadertoy port exists: ["Hosek-Wilkie Skylight Model"](https://www.shadertoy.com/view/wslfD7)
- [SOTA 2021–2022, fitted] The Prague Sky Model (Wilkie, Vévoda, Bashford-Rogers, Hošek et al., SIGGRAPH 2021, ACM TOG 40(4)) adds features no earlier fitted model had: post-sunset radiance patterns, in-scattered radiance and attenuation for finite viewing distances, an observer-altitude-resolved model that includes downward views, and polarisation. It replaces the Perez-style hemispherical functions with reference data compressed by tensor decomposition. The reference implementation is C++17 — [CGG project page](https://cgg.mff.cuni.cz/publications/skymodel-2021/); [GitHub pragueskymodel](https://github.com/PetrVevoda/pragueskymodel). Wide-spectral-range follow-up (CGF 2022) — [Vévoda et al. 2022](https://onlinelibrary.wiley.com/doi/abs/10.1111/cgf.14677)

**Precomputed / LUT scattering**
- [Classic, 2008] Bruneton & Neyret: per Hillaire's measurements on an NVIDIA 1080, the BN08 model renders in 0.22 ms "but this is without all the LUTs being updated". Updating all LUTs with the provided code costs 250 ms, and 99% of that is the multiple-scattering iterations. The update can be time-sliced over frames, at the cost of latency — [Hillaire 2020 paper](https://sebh.github.io/publications/egsr2020.pdf)
- [Production, SOTA 2020] Hillaire "A Scalable and Production Ready Sky and Atmosphere Rendering Technique" (EGSR 2020 / CGF). It is physically based, works from ground to space, needs no high-dimensional LUTs, supports dynamic time of day and atmosphere changes, runs from "a low-end Apple iPhone 6s to consoles and high-end gaming PCs", and is used in Unreal Engine — [paper PDF](https://sebh.github.io/publications/egsr2020.pdf); [Wiley abstract](https://onlinelibrary.wiley.com/doi/abs/10.1111/cgf.14050)
  - Per-step timings (Table 2) — [paper](https://sebh.github.io/publications/egsr2020.pdf):

    | LUT | PC (NVIDIA 1080): resolution, steps, time | Mobile (iPhone 6s): resolution, steps, time |
    |---|---|---|
    | Transmittance | 256×64, 40, 0.01 ms | 256×64, 40, 0.53 ms |
    | Sky-View | 200×100, 30, 0.05 ms | 96×50, 8, 0.27 ms |
    | Aerial perspective | 32³, 30, 0.04 ms | 32²×16, 8, 0.11 ms |
    | Multi-scattering | 32², 20, 0.07 ms | 32², 20, 0.12 ms |
  - PC final on-screen sky plus aerial perspective takes 0.14 ms, and the total is 0.31 ms at 1280×720. For Fortnite, "the total sky rendering cost was roughly 1ms on iPhone 6s". On mobile, "Visual differences, due to lower LUT quality, are not noticeable to the naked eye", but the transmittance LUT was kept identical on both platforms to preserve a matching look — [paper](https://sebh.github.io/publications/egsr2020.pdf)
  - "Given the overall low visual frequency of the sky, we should be able to render the sky at a lower resolution and upsample it." The sky-view LUT is a latitude/longitude texture oriented to the local up vector, so the horizon is always a horizontal line in it. The latitude is mapped non-linearly to put more texels near the horizon: `v = 0.5 + 0.5*sign(l)*sqrt(|l|/(π/2))`. "The sun disk is not rendered as part of that texture… It is composited after applying the Sky-View LUT" — [paper](https://sebh.github.io/publications/egsr2020.pdf)
  - Atmosphere constants (Table 1, ×10⁻⁶ m⁻¹) — [paper](https://sebh.github.io/publications/egsr2020.pdf):
    - Rayleigh: scattering (5.802, 13.558, 33.1), no absorption, density `exp(-h/8 km)`.
    - Mie: scattering 3.996, absorption 4.40, density `exp(-h/1.2 km)`.
    - Ozone: no scattering, absorption (0.650, 1.881, 0.085), with a tent density profile centred at 25 km, width 30 km.
    - Rayleigh phase `3(1+cos²θ)/(16π)`. Mie uses the Cornette–Shanks phase function with default g = 0.8, and "it is also appropriate to use the simpler Henyey-Greenstein phase function".
    - Ground albedo 0.3, atmosphere 100 km thick.
  - Limitations of the multi-scattering approximation: light is assumed isotropic after the second bounce, and hue can drift at very high scattering coefficients — [paper](https://sebh.github.io/publications/egsr2020.pdf)
  - Volumetric shadowing of the atmosphere (by mountains or clouds) needs ray marching with jitter and temporal reprojection. With 32 samples the total rises to 1.0 ms on PC — [paper](https://sebh.github.io/publications/egsr2020.pdf)
  - A Shadertoy titled "Production Sky Rendering" appears in search results for the paper — [Shadertoy slSXRW](https://www.shadertoy.com/view/slSXRW)
- [Production, 2016 precursor] Hillaire, "Physically Based Sky, Atmosphere & Cloud Rendering in Frostbite" (SIGGRAPH 2016 PBS course) — [Frostbite](https://www.frostbite.com/frostbite/news/physically-based-sky-atmosphere-and-cloud-rendering) (reference only; I did not read its contents)

**Twilight colour science (golden hour → blue hour)**
- Chappuis absorption bands lie between 400 and 650 nm, with two maxima of similar height at 575 and 603 nm. They are noticeable when light travels a long path through the atmosphere and "only ha[ve] a significant effect on the color of the sky at dawn and dusk, during the so-called blue hour" — [Wikipedia: Chappuis absorption](https://en.wikipedia.org/wiki/Chappuis_absorption)
- Hillaire includes ozone as a pure absorber because it "is key to achieving sky-blue colors when the sun is at the horizon" (citing Kutz 2013) — [Hillaire 2020](https://sebh.github.io/publications/egsr2020.pdf)
- Belt of Venus: "a pinkish glow that surrounds the observer, extending roughly 10–20° above the horizon", seen shortly before dawn or after dusk. It is caused by Rayleigh-scattered light of the rising or setting Sun backscattered by particulates. As twilight progresses it is separated from the horizon by the dark band of Earth's shadow (the "twilight wedge"). It is more vivid in winter (lower absolute humidity) — [Wikipedia: Belt of Venus](https://en.wikipedia.org/wiki/Belt_of_Venus)
- Hillaire's mobile/PC comparison includes a sunset rendered with "5× higher Rayleigh scattering coefficients", an art-directed exaggeration that the model supports without heavy LUT updates — [Hillaire 2020](https://sebh.github.io/publications/egsr2020.pdf)

**Sun disc, glare, flares**
- Screen-space "pseudo lens flare" (Chapman):
  - Steps: downsample and threshold; generate ghosts by sampling along a vector through the image centre; add a halo ring; add chromatic distortion by sampling each colour channel at a different offset; blur (to reduce coherence with the source image); composite at full resolution with a lens-dirt texture and a starburst texture.
  - "Smaller render targets make the feature generation and blur steps cheaper, however you may need more blur to hide blocky artefacts".
  - Recommends a sprite-based approach for "hero" flares on important light sources.
  - Source: [John Chapman, Screen Space Lens Flare (2017)](https://john-chapman.github.io/2017/11/05/pseudo-lens-flare.html); [2013 original](http://john-chapman-graphics.blogspot.com/2013/02/pseudo-lens-flare.html)

**Moon, earthshine, stars, Milky Way**
- [Classic, 2001] Jensen et al., "A Physically-Based Night Sky Model" (SIGGRAPH 2001) — [project page](http://graphics.stanford.edu/~henrik/papers/nightsky/); [ACM DL](https://dl.acm.org/doi/10.1145/383259.383306):
  - The Moon is a geometric model lit by the Sun, using measured elevation and albedo maps and a specialised BRDF.
  - Stars are drawn from their position, magnitude and temperature.
  - The Milky Way and nebulae come from a processed photograph.
  - Zodiacal light, galactic light and airglow are simulated from measured data.
- Earthshine ("ashen glow", "Da Vinci glow") is sunlight reflected from Earth onto the Moon's unlit part. It is visible a few days either side of new moon, during crescent phases, and is much dimmer than the lit part because the light is reflected twice. Earth's albedo is about 0.3 and the Moon's about 0.12. Earthshine brightness tracks Earth's cloudiness — [Wikipedia: Planetshine](https://en.wikipedia.org/wiki/Planetshine); [EarthSky](https://earthsky.org/astronomy-essentials/what-is-earthshine/); [NASA Earth Observatory](https://earthobservatory.nasa.gov/images/83782/earthshine)

**Aurora**
- Emission and altitude — [Wikipedia: Aurora](https://en.wikipedia.org/wiki/Aurora):
  - Atomic oxygen emits red at 630 nm at the highest altitudes.
  - Lower down, the green 557.7 nm emission dominates.
  - Lower still, molecular nitrogen dominates, with blue at 428 nm.
  - Størmer found no auroras below 70 km, only 6.5% above 150 km, and a peak near 100 km.
  - Auroras change from barely noticeable to sub-second timescales. Pulsating auroras have periods of 2–20 s.
- [Classic, 2010–2011] Lawlor & Genetti, "Interactive Volume Rendering Aurora on the GPU" — [slides](https://www.cs.uaf.edu/~olawlor/2011/aurora_WSCG_2011.pdf):
  - The 3D aurora is factored into a 2D electron-intensity "curtain footprint" map (a 16384² polar texture) and a height-dependent deposition function (a 1024² texture).
  - Curtains are rendered with distance-field acceleration (jump flooding), worth 3.5×.
  - The factorisation alone gave 2×. Overall speed went from 10 minutes/frame to 20–60 fps.
  - The auroral atmosphere spans 50–500 km.
- nimitz's "Auroras" Shadertoy is the widely ported real-time reference — [Shadertoy XtGGRt](https://www.shadertoy.com/view/XtGGRt)

### Inferences
- **Recommended sky architecture for AGSL (Tier 2 / flagship):** port Hillaire's pipeline in reduced form.
  - The transmittance LUT (256×64) and multi-scattering LUT (32²) depend only on the atmosphere constants. Compute them once, on CPU at startup or offline and bundled, and pass them as child shaders.
  - Rebuild the sky-view LUT (96×50, 8 steps, per the iPhone 6s settings) only when the sun moves noticeably (for example every 10–60 s of wall-clock time, or when sun elevation changes by more than ~0.1°). That is only 4,800 texels × 8 steps, trivial even on CPU in Kotlin.
  - The per-frame full-screen cost is then one bilinear LUT fetch, an analytic sun disc (limb-darkened) and a Mie aureole term. This is the cheapest "cinematic" physically based sky possible and naturally yields golden hour, blue hour (via ozone) and art-directable sunsets (scale Rayleigh/Mie coefficients).
- **Tier 1:** evaluate a fitted model (Preetham/Hošek–Wilkie-style Perez formula, coefficients recomputed on CPU per sun position/turbidity) into a small bitmap, or directly per pixel. It costs a few `exp`/`pow` per pixel, but twilight after sunset is weak (Prague adds post-sunset patterns but is far too data-heavy for a phone shader).
- **Tier 0 (battery saver):** a cached vertical gradient bitmap keyed by sun elevation, plus a sun sprite.
- **Twilight extras:**
  - A Belt of Venus band can be approximated analytically as a pink band 10–20° above the anti-solar horizon, with a blue-grey "Earth shadow" band beneath that rises as the sun sinks. Keep it faint.
  - Ozone absorption (Hillaire's ozone constants) is what keeps the zenith blue rather than grey-yellow at twilight.
  - Warm horizon haze via sun-coloured fog (see iq fog in section 4) is almost free.
- **Sun:** composite the disc after the LUT, as Hillaire does. Use a Cornette–Shanks/HG aureole (g ≈ 0.8 per Hillaire's default) for the halo. Chapman-style ghosts can be done analytically, because the sun's screen position is known: draw 3–6 soft discs along the sun→centre axis with per-channel offsets. This avoids any image-space downsample/threshold/blur passes. Save sprite "hero" flares for the sun and lightning.
- **Night:**
  - Stars: hashed-grid star field (1 cell lookup per pixel per layer, 2–3 layers for depth). Colour from a temperature ramp and size/brightness from a hashed magnitude, following Jensen's inputs of position, magnitude and temperature. Twinkle (scintillation) by modulating brightness with per-star temporal noise, stronger near the horizon.
  - Milky Way: a small panoramic texture (Jensen used a photograph) or a procedural band. Either can be cached, because stars and Milky Way only rotate slowly.
  - Moon: a sphere-lit disc with the terminator from the Sun–Moon angle, plus a dim earthshine fill on the dark side for crescents (brighter the thinner the crescent).
  - Meteors: occasional short additive streaks with a head and fading tail, triggered on CPU. This is purely art-driven; no source was found.
- **Aurora cheap path:**
  - Use Lawlor's factorisation in 2.5D: a 2D noise "footprint" (curtain ribbons, domain-warped) times a vertical emission profile (green lower edge → red upper fringe, a faint blue/purple lowest).
  - March 8–16 slices at fixed count, which AGSL requires (bounded loops).
  - Add slow drift plus 2–20 s pulsation.
  - Render at 1/4 resolution. Aurora is soft, so upscaling hides the reduced resolution.

### Gaps
- No Android/Adreno/Mali timings for Hillaire-style LUTs were found; only iPhone 6s numbers exist in the paper.
- The Frostbite 2016 course contents, and the SOTA clouds and atmosphere work presented at SIGGRAPH 2023–2025, were not reviewed.
- A search snippet claimed the blue colour at sunset is roughly 1/3 Rayleigh and 2/3 ozone, and that without ozone twilight would look "pale green or straw yellow". I could not verify this against the fetched Wikipedia text, so treat it as unverified.
- No sources were found for procedural Milky Way generation, star scintillation modelling, or meteor rendering.
- The nimitz Auroras licence was not read directly. Search results indicate CC BY-NC-SA 3.0; see the licensing note in section 3.

---

## 2. Clouds: layered 2D noise vs ray-marched volumetric vs 2.5D tricks (self-shadowing, silver lining, god rays)

### Takeaway
Volumetric ray-marched clouds (Guerrilla's Nubis) are the AAA benchmark. Even on a PS4 they needed quarter-resolution rendering, updating 1 of every 16 pixels per frame, plus reprojection to reach about 2 ms; without that, the same shader cost about 20 ms. That is out of reach for a full-screen 120 Hz phone background. The right mobile tier is 2D/2.5D: fBm layers built from moving octaves (iq's 2002 dynamic clouds), a few density samples toward the sun for self-shadowing, and HZD's cheap lighting terms (Beer's law, "powder" dark edges, Henyey–Greenstein silver lining). Everything should render at reduced resolution. God rays can be a radial-blur post effect (GPU Gems 3), or faked analytically.

### Cited Findings
- **[Production, 2015] Horizon Zero Dawn / Nubis** (Schneider & Vos, SIGGRAPH 2015) — [Guerrilla page](https://www.guerrilla-games.com/read/the-real-time-volumetric-cloudscapes-of-horizon-zero-dawn); [slides PDF](https://d3d3g8mu99pzk9.cloudfront.net/AndrewSchneider/The-Real-time-Volumetric-Cloudscapes-of-Horizon-Zero-Dawn.pdf):
  - Budget: renders "in about 2 milliseconds, takes 20 mb of ram". The target was "GPU performance of 2ms".
  - Noise textures:
    - a 128³ texture with Perlin–Worley in one channel and Worley at increasing frequencies in the other three;
    - a 32³ Worley detail texture;
    - a 128² curl-noise texture, "non divergent… used to fake fluid motion", which distorts detail noise for swirly shapes.
  - Lighting model:
    - Beer's law for primary scattering.
    - Henyey–Greenstein phase for the silver lining.
    - A "powdered sugar" effect: dark edges facing the light, combined with Beer as a "Beer's-Powder" term and made view-dependent (strongest when looking away from the sun).
    - Rain clouds are "artificially darken[ed]".
  - Sampling:
    - Cheap samples until a cloud may be present, then full samples; switch back after several zero-density samples.
    - 64–128 potential march samples.
    - 6 light samples per march step in a cone (the talk also refers to "5 cone samples"). Light samples switch from full to cheap beyond a certain depth.
  - Performance history: the approach initially cost about 20 ms. The fix was "a quarter res buffer… to update 1 out of 16 pixels for each 4x4 pixel block" with reprojection of the previous frame, substituting low-res results where reprojection fails. That made it "10x faster or more".
- **[Production, SOTA 2022] Nubis Evolved** (Horizon Forbidden West, PS5, SIGGRAPH 2022) — [Guerrilla page](https://www.guerrilla-games.com/read/nubis-evolved):
  - Extended to fly-through cloud environments and VFX clouds such as "fast spinning superstorms with internal lighting flashes".
  - Introduced a way to mitigate temporal artefacts in fast-moving clouds.
  - Introduced "a near zero cost method to add internal lighting effects to clouds".
  - The page summary states it renders at 1080p without temporal upscaling.
- **[Classic, 2002 → GPU] iq "2D dynamic clouds"** (from the 64KB demo *Paradise Is Coming*) — [iquilezles.org/articles/dynclouds](https://iquilezles.org/articles/dynclouds/):
  - Octaves: fBm is decomposed into octaves stored as separate textures (9 for 512²), which "reduce[s] the storage by a factor of 50". Moving "each octave into the 2D plane with different velocities" gives a dynamic fBm.
  - Shading: density is sampled at four positions along the sun direction, and "the proportion of samples inside the cloud was used as grey level for the shading". It assumes the viewer looks up at the clouds.
  - GPU version: four noise textures with channel packing, no dependent texture reads, "don't render slower than a thousand frames per second in a normal graphics card".
- **iq's article index** covers the building blocks: FBM, gradient-noise derivatives, value-noise derivatives (analytic normals without screen-space derivatives), domain warping, Voronoise/smooth Voronoi (Worley-like), filtering and band-limiting procedural textures, simple colour palettes, and 2D dynamic clouds — [iquilezles.org/articles](https://iquilezles.org/articles/) (e.g., [fbm](https://iquilezles.org/articles/fbm/), [warp](https://iquilezles.org/articles/warp/), [morenoise](https://iquilezles.org/articles/morenoise/), [gradientnoise](https://iquilezles.org/articles/gradientnoise/), [bandlimiting](https://iquilezles.org/articles/bandlimiting/))
- **[Classic, 2007] God rays as a post-process** (Kenny Mitchell, EA, GPU Gems 3 ch. 13) — [NVIDIA GPU Gems 3](https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-13-volumetric-light-scattering-post-process):
  - Method: for each pixel, step along the screen-space vector to the light and accumulate samples of an occlusion pre-pass (occluders rendered black).
  - Controls: NUM_SAMPLES, Density (sample spacing), Weight, Decay (exponential falloff along the ray) and Exposure.
  - Known artefacts: shafts from background objects can appear in front of foreground objects, and shafts flicker as occluders cross the image boundary.
- **Curl noise** [Classic, 2007]: the curl of a noise potential gives an exactly divergence-free (incompressible-looking) turbulent velocity field. Multiple octaves are used for turbulence, and amplitude can be modulated spatially — [Bridson et al., SIGGRAPH 2007](https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf). [SOTA 2025] follow-up: "Improving Curl Noise" (SIGGRAPH Asia 2025) — [ACM DL](https://dl.acm.org/doi/10.1145/3757377.3763980) (not read)

### Inferences
- **Cost tiers for clouds on a phone (full-screen):**
  - **Tier 0:**
    - 1–2 scrolling pre-baked tileable cloud textures (BitmapShader children with LINEAR filtering; see section 6), each at a different speed and scale for parallax.
    - Cost: 1–2 texture fetches per pixel.
  - **Tier 1 (default mid-range):**
    - iq-style dynamic fBm: 3–5 octaves where each octave is either one fetch from a tileable noise texture or cheap ALU value noise, each octave scrolling at its own velocity.
    - Coverage via threshold/remap. Self-shadowing via 2–4 extra density taps offset toward the sun (iq's four-sample trick), turned into Beer transmittance `exp(-k·Σdensity)`.
    - Render at half or quarter resolution into a cached layer, then upscale bilinearly.
  - **Tier 2 (flagship):**
    - 2–4 depth-sorted 2.5D layers (high cirrus, mid cumulus, low scud) with parallax against device tilt.
    - Per layer: Beer–Powder term, HG forward-scattering "silver lining" boost near the sun (the Hillaire/HZD phase functions), darker rain-cloud bases, curl-noise distortion for wispy motion.
    - Optional cheap volumetric: march 4–8 slices through a thin slab of 2D noise, not a 3D texture. AGSL has no 3D textures, so 3D noise would be ALU-only.
  - **Full volumetric (Nubis-style) is not advisable** for a battery-conscious always-on background. Guerrilla needed a console-class GPU, reprojection and about 2 ms per frame.
- **Silver lining / rim light:** brighten edges where density is low and the view direction is close to the sun direction: `edge = 1 - density`, weighted by `HG(dot(view, sun), g≈0.6–0.8)`. The powder term darkens sun-facing thick regions. Together these give a "cinematic backlit cumulus" look for a few ALU ops.
- **Normal-mapped cloud sprites:** a pre-rendered cloud atlas with baked normals and thickness, lit per-pixel with the current sun direction. This gives artist-controlled hero clouds for a single fetch per sprite and suits a small number of large hero clouds over a noise backdrop. It is my suggestion; no source was found (see Gaps).
- **God rays in AGSL:** a RuntimeShader cannot read its own output, so a GPU Gems 3 radial blur needs an occlusion/cloud mask rendered to a bitmap first, then a second RuntimeShader with that bitmap as a child doing 16–32 taps at 1/4 resolution. Cheaper alternatives:
  - analytic "rays" by modulating brightness with low-frequency noise in polar angle around the sun, masked by cloud density sampled once;
  - reuse the cloud layer's own shadow taps.
  Use rays only at golden hour or through broken cloud; they sell "cinematic" strongly.
- **Temporal amortisation** in the HZD spirit (1/16 pixels per frame) is hard in AGSL because there is no framebuffer feedback. The practical equivalents are rendering at lower resolution and at lower update rates for slow layers (clouds drift slowly, so updating a cached cloud layer at 15–30 Hz while compositing at 60–120 Hz is usually invisible).

### Gaps
- No published mobile timings for 2D/2.5D cloud shaders were found.
- Nubis Cubed (2023) and newer Guerrilla or UE5 cloud talks were not verified.
- No authoritative reference was found for "normal-mapped cloud sprites" in weather rendering.
- GDC weather-system talks for Far Cry, Forza Horizon 5 and Microsoft Flight Simulator were not reviewed, except Forza's captured skies (section 5).

---

## 3. Precipitation: rain (layers, streaks, splashes, ripples, wet surfaces), rain on glass, drizzle/heavy/sleet/freezing rain/hail, snow (bokeh, turbulence, accumulation, frost)

### Takeaway
Two classic production references cover rain:
- Tatarchuk's ToyShop (2006) renders many depth layers of rain in a single full-screen composite pass with one texture fetch, adds motion parallax, blurs for motion blur and mist, and biases drops toward white (the Hollywood "milk" trick).
- Lagarde's rain work (2013) adds procedural ripples, wet-surface darkening/gloss and puddles within about a 3 ms console budget.

BigWings' "Heartfelt" (2017) is the reference for rain on glass: grid-cell drops with trails, refraction from the drop normal, and drops cutting clear trails through fogged glass. Snow is best done as multiple parallax layers with depth-dependent blur ("Just snow"). **Important for a commercial app:** the famous Shadertoys checked here carry CC BY-NC-SA (non-commercial) licences, so they must be re-implemented from the technique, not copied.

### Cited Findings
- **[Classic/Production, 2006] Tatarchuk & Isidoro, "Artist-Directable Real-Time Rain Rendering in City Environments"** (ATI ToyShop) — [SIGGRAPH 2006 course chapter PDF](https://advances.realtimerendering.com/s2006/Chapter3-Artist-Directable_Real-Time_Rain_Rendering_in_City_Environments.pdf); [EG NPH 2006](https://diglib.eg.org/items/b4d11d22-3e3d-4ac5-b33c-6b091143add1):
  - It is a hybrid system: image-space rainfall, particle-based drips and splashes, simulated puddle ripples, drops trickling on glass panes, and view-dependent warped reflections.
  - Rain is a composite layer rendered "as a full-screen pass before the final post-processing", using an 8-bit rainfall texture. The authors warn that "every pixel on the screen will go through this shader", so the pass must stay cheap.
  - Multiple layers of rain "in a single pass with a single texture fetch" are made by using "the rain parallax value… multiplied by a distribution value… as the w parameter for a projective texture fetch". A single shared direction vector for all drops "is crucial for creating a consistent rainfall effect".
  - Perception: individual drop motion cannot be tracked by eye, so rain frames can be treated as temporally independent. However, "purely random movement of raindrops does not yield satisfactory results (generating excessive visual noise)".
  - Shading: per-drop reflection and air-to-water refraction from a tangent-space normal map that matches the rainfall texture, both attenuated toward drop edges with Fresnel. Motion blur and "multiple-scattering glow" come from a post-process blur after compositing.
  - Hollywood trick: film crews add milk to rain water, so drop colour and opacity are biased toward white "to create a perception of stronger rainfall".
  - Raindrops become more transparent as lightning strikes, driven by the lightning brightness parameter.
  - Wet-street reflections are "streaky": the reflection buffer is streaked vertically only.
- **[Classic/Production, 2013] Lagarde, "Water drop 2b – Dynamic rain and its effects"** — [blog](https://seblagarde.wordpress.com/2013/01/03/water-drop-2b-dynamic-rain-and-its-effects/):
  - Ripples: a procedural circle map. R stores inverted normalised distance from the circle centre, GB store perturbation direction, and A stores a random time offset. Four animated layers at different frequencies and time shifts are blended in by rain intensity, and each animates as a damped sine.
  - Wet surfaces: diffuse is multiplied by `lerp(1.0, 0.3, WetLevel)`, and gloss by `lerp(1.0, 2.5, WetLevel)` (clamped).
  - Puddles: four regions (dry, wet, drenched, puddle), filled by a `FloodLevel` against painted masks or heightmaps.
  - Budget: about 2.8 ms on PS3 and 2.86 ms on Xbox 360 at 1280×720. This covers depth map, two raindrop passes (0.74–1.67 ms), a 256² ripple texture (0.14–0.15 ms), splashes (0.25–0.33 ms) and camera droplets (0.32–0.54 ms). The post calls dynamic rain "a costly feature".
  - Physically based wet surfaces follow-up: [Water drop 3b](https://seblagarde.wordpress.com/2013/04/14/water-drop-3b-physically-based-wet-surfaces/)
- **[Classic Shadertoy, 2017] "Heartfelt" by Martijn Steinrucken (BigWings / The Art of Code)** — [Shadertoy ltffzl](https://www.shadertoy.com/view/ltffzl); a source copy in a GitHub repo (a port, slightly modified) — [heartfelt.glsl](https://github.com/sanxincao/shadertoy/blob/master/heartfelt.glsl):
  - Header: "1. The glass gets foggy. 2. Drops cut trails in the fog on the glass. 3. The amount of rain is adjustable". Licence: "Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported".
  - Drop layers: a static-drops layer on a 40× grid, plus two moving layers (`DropLayer2` on a 12×2-cell grid with 6:1 cell aspect, the second layer at uv×1.85).
  - Per cell: hashed randoms, a sawtooth slide-down (`Saw(.85, ti)`), a sine "wiggle", a trail and small trailing droplets.
  - Normals: "expensive normals" from finite differences, i.e. three evaluations of the drop field. The `dFdx/dFdy` alternative is commented as "cheap normals (3x cheaper, but 2 times shittier ;))".
  - Blur: a per-pixel blur level `focus = mix(maxBlur - trail, minBlur, dropMask)` with `maxBlur = mix(3., 6., rainAmount)`. Drops and trails are sharp, fogged glass is blurred. The background is sampled at `UV + n` (normal offset = refraction).
- **"Rain drops on screen" (eliemichel) walkthrough** — [greentec blog](https://greentec.github.io/rain-drops-en/):
  - Screen divided into grids at several scales (r = 1–4 layers), with per-cell random values from a noise texture.
  - Drop shape from sines; refraction by offsetting background UVs with the drop normal.
  - Fogged-glass look from a mip bias (`texture2D(tex, uv, 1.5)`).
- **Snow, "Just snow" (Andrew Baldwin, Shadertoy 2013):** multiple parallax layers of randomly positioned flakes with per-layer directions, plus a depth-of-field effect. The licence is Attribution-NonCommercial-ShareAlike, and it has been adapted, e.g. in libretro's border shaders — [libretro snow.glsl](https://github.com/libretro/glsl-shaders/blob/master/borders/resources/snow.glsl)
- **Turbulent drift for snow and particles:** curl noise gives divergence-free, fluid-like motion ([Bridson 2007](https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf)). HZD uses a 2D curl-noise texture "to fake fluid motion" ([HZD slides](https://d3d3g8mu99pzk9.cloudfront.net/AndrewSchneider/The-Real-time-Volumetric-Cloudscapes-of-Horizon-Zero-Dawn.pdf)).
- Frosted-glass shaders exist on Shadertoy (e.g. ["Frosted Glass Shader"](https://www.shadertoy.com/view/WdSGz1)), mostly as a blur/noise distortion of the background (not reviewed in depth).

### Inferences
- **Rain streaks in AGSL (Tier 1–2):**
  - Grid-cell hashed streaks in 3–5 depth layers inside one shader. For each layer, scale UVs by a depth factor, scroll along a shared wind-tilted direction at depth-dependent speed, and hash one streak per cell (x-jitter, phase, length).
  - Streak length ≈ fall speed × a virtual "shutter" time. This bakes motion blur, so 60 Hz looks as smooth as 120 Hz.
  - Near layers: longer, wider, softer (defocused), more transparent. Far layers: thin, dense, dim, blending into a fog veil.
  - Bias colour toward white ("milk trick") and brighten drops where they cross bright sky or during lightning.
  - Cost is O(layers) of cheap hash math per pixel and independent of drop count. Tatarchuk's single-fetch parallax trick shows it can be very cheap.
- **Intensity mapping:**
  - Drizzle: 1–2 far layers, short, thin, low opacity, strong mist/fog, static droplets on glass.
  - Moderate rain: 3 layers plus splashes.
  - Heavy rain: 4–5 layers plus large-scale "rain curtains" (low-frequency noise sweeping density horizontally), heavier fog/haze, darker clouds, fully fogged glass with trails.
  - Freezing rain / glaze: rain layers plus glassy specular glints and an icy blue-white tint on UI edges.
  - Sleet: mix short, bright rain streaks with small round pellets (fewer, faster than snow, slight bounce).
  - Hail: few, large, fast, bright pellets with CPU-side bounce physics (restitution, random spin) drawn as Canvas sprites/points, with small impact flashes or splash puffs at a ground line. Hail counts are low, so CPU/Canvas is fine.
- **Ground effects** (only if the scene shows a ground or horizon strip):
  - A Lagarde-style ripple texture (4 animated layers, damped sine) procedurally generated in-shader or from a small 256² tile.
  - Splash sprites spawned at random positions near the bottom edge.
  - Wet darkening and gloss (`×0.3` diffuse, `×2.5` gloss at full wetness).
  - Vertical "streaky" reflections of bright lights (Tatarchuk).
- **Rain on glass (hero effect) in AGSL:**
  - Re-implement Heartfelt's idea: a static-drops grid plus two sliding-drop grids with trails.
  - AGSL has no `dFdx/dFdy` and no `textureLod`, so either:
    - (a) use finite-difference normals (3× drop-field cost, as Heartfelt's "expensive normals"), or
    - (b) compute analytic gradients of the drop SDFs, which is cheap because drops are circles/ellipses.
  - For fog/blur, pass two child shaders, the sharp background and a pre-blurred copy (e.g. a downsampled bitmap of the sky layer, or `RenderEffect` blur). Then mix by the `focus`/trail mask and offset the lookup by the drop normal for refraction.
  - Budget-wise this is one of the most expensive per-pixel effects. Consider running the drop field at half resolution, or limiting it to "window mode".
- **Snow:**
  - 3–6 parallax layers. Near flakes are big, soft and translucent (bokeh-like DoF), far flakes are small and sharp. Speed and size scale with depth.
  - Lateral drift from a sine or curl-noise field that varies slowly in time; occasional gust bursts.
  - Flakes brighter than the background at night, subtly shaded by day.
  - Accumulation: a frosty white rim on the bottom edges of UI cards (a noise-thresholded mask that grows with snow duration). Frost growth on glass: a noise- or Voronoi-based dendritic pattern revealed by a slowly advancing threshold from the screen edges inward, plus a blurred background under frosted regions (the same two-child blur trick).
  - Snow flakes are round and slow, so Canvas-drawn particles (a few hundred `drawCircle`/`drawPoints` calls) are also viable. Shader grids scale better for dense storms.
- **Licensing:** Heartfelt's licence was confirmed from its header, and "Just snow" is reported as NC-SA. Assume any Shadertoy code is non-commercial unless stated otherwise. Study the technique, then write original code.

### Gaps
- No published techniques or costs were found specifically for hail, sleet or freezing rain; the suggestions above are inference.
- No references were found for snow accumulation on UI, or for procedural frost growth on glass (only generic frosted-glass blur shaders).
- The Shadertoy site-wide default licence page (`/terms`) returned HTTP 403, so the default licence could not be confirmed directly. The per-shader headers checked were NC.
- "Rain streaks" physical appearance databases (e.g. Garg & Nayar) were not reviewed.
- The GitHub Heartfelt copy is a modified port (it samples with plain `texture2D`), so the exact blur-sampling call in the Shadertoy original was not verified.

---

## 4. Storms and atmosphere: lightning (bolt generation, in-cloud flashes, afterglow), rain shafts, fog/mist, haze/dust/sandstorm, heat shimmer, wind, rainbows and halos

### Takeaway
- **Lightning:** cheap to generate on the CPU per strike. Midpoint displacement with the offset halved each generation, plus occasional branch splits, produces a polyline that can be drawn with glow.
- **Storms:** most of the drama comes from the flash lighting clouds, rain and scene (Tatarchuk's lightning lightmaps, Nubis's near-zero-cost internal cloud flashes).
- **Fog:** iq's analytic height fog with sun-coloured in-scattering is nearly free per pixel.
- **Heat shimmer:** Far Cry-style UV perturbation (GPU Gems 2).
- **Wind turbulence:** curl noise.
- **Rainbows and 22° halos:** well-defined angular geometry, so they can be drawn analytically for a few ALU ops.

### Cited Findings
- **Lightning bolt generation:**
  - [Classic, 2009] Midpoint displacement: split each segment at its midpoint, offset the midpoint perpendicular by a random amount, and halve the maximum offset each generation. For branches, occasionally add a third segment when splitting — [Drilian (Josh Jers), "Lightning Bolts"](https://www.drilian.com/posts/2009.02.25-lightning-bolts/)
  - [Classic, 1994] Reed & Wyvill, "Visual simulation of lightning": uses a particle system to generate and animate the channel path, aimed at aesthetic images rather than physical accuracy — [SIGGRAPH history](https://history.siggraph.org/learning/visual-simulation-of-lightning-by-reed-and-wyvill/)
  - [Classic, 2004] Kim & Lin, "Physically Based Animation and Rendering of Lightning" — [PDF](http://gamma.cs.unc.edu/LIGHTNING/lightning.pdf) (reference only)
  - L-system lightning — [RPI student project](https://www.cs.rpi.edu/~cutler/classes/advancedgraphics/S09/final_projects/lapointe_stiert.pdf)
  - A thesis on tessellation-generated bolts used blur and pixel shaders with a 7×7 filter kernel for the glow — [DiVA thesis](http://www.diva-portal.org/smash/get/diva2:1061502/FULLTEXT02)
- **Storm lighting:**
  - ToyShop computes lightning illumination via special "lightning lightmaps" that encode the flash from several directions in a single 8-bit channel. It propagates lightning brightness to all rain shaders, and raindrops become more transparent during strikes — [Tatarchuk 2006](https://advances.realtimerendering.com/s2006/Chapter3-Artist-Directable_Real-Time_Rain_Rendering_in_City_Environments.pdf)
  - Nubis Evolved added "a near zero cost method to add internal lighting effects to clouds", used for superstorms "with internal lighting flashes" — [Guerrilla](https://www.guerrilla-games.com/read/nubis-evolved)
- **Fog** ([iq, "Better fog"](https://iquilezles.org/articles/fog/)):
  - Basic fog: `fogAmount = 1 - exp(-t*b)`.
  - Sun-aware fog colour: `sunAmount = max(dot(rd, lig), 0)`, blending bluish to yellowish fog via `pow(sunAmount, 8.0)`. This gives "glowing/blooming and other light scattering effects without actually doing any multipass or render to texture".
  - Analytic exponential height fog `d(y) = a·e^(-b·y)`: `fogAmount = (a/b) * exp(-ro.y*b) * (1.0-exp(-t*rd.y*b))/rd.y`, "adding no more than one division".
  - Per-channel (RGB) extinction and in-scattering coefficients give "a very powerful and simple fog system".
- **Haze/aerosols:**
  - Hillaire models aerosols with Mie scattering 3.996 and absorption 4.40 (×10⁻⁶ m⁻¹), with a 1.2 km scale height.
  - Preetham and Hošek–Wilkie expose turbidity (Hošek–Wilkie fitted 1–10), which drives hazy or dusty skies.
  - Sources: [Hillaire 2020](https://sebh.github.io/publications/egsr2020.pdf); [Hošek slides](https://cgg.mff.cuni.cz/projects/SkylightModelling/Hosek_SG_2012_Presentation.pdf)
- **Heat shimmer / refraction** [Classic, 2005]: Tiago Sousa (Crytek), GPU Gems 2 ch. 19 "Generic Refraction Simulation" perturbs the texture coordinates used to look up an image of the non-refractive scene. It is an expansion of Far Cry's water, heat haze and sniper-scope effects — [NVIDIA GPU Gems 2](https://developer.nvidia.com/gpugems/gpugems2/part-ii-shading-lighting-and-shadows/chapter-19-generic-refraction-simulation)
- **Wind:** curl noise gives incompressible turbulent velocity fields with spatially modulated amplitude — [Bridson 2007](https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf)
- **Rainbow** ([Wikipedia: Rainbow](https://en.wikipedia.org/wiki/Rainbow)):
  - Primary bow brightest at about 42° from the anti-solar point, red outside and violet inside.
  - Secondary bow about 10° outside at 50–53°, with colours reversed.
  - "Alexander's band" is the darker sky between the two bows.
  - Supernumerary bows are faint pastel (pink/purple/green) bands inside the primary.
  - Needs sunlight from behind the observer at low altitude; no rainbow when the sun is above 42°.
  - Fogbows are almost white, with faint red outside and blue inside.
  - [Classic, 2011] physically based simulation (Lorenz–Mie-matching ray tracing with dispersion, polarisation, interference and diffraction; supernumeraries; non-spherical drops) — [Sadeghi et al., ACM TOG](https://cs.dartmouth.edu/~wjarosz/publications/sadeghi11physically.html)
- **22° halo:** a radius of about 22° around the Sun or Moon, from refraction by hexagonal ice crystals in thin cirrus or cirrostratus. The inner edge is reddish and the outer edge bluish (red refracts at about 21.54°, blue at about 22.37°). The sky is darker inside the ring — [Wikipedia: 22° halo](https://en.wikipedia.org/wiki/22%C2%B0_halo)

### Inferences
- **Lightning pipeline (cheap, cinematic):**
  - On each strike, generate the bolt on the CPU with midpoint displacement: 5–7 generations, 32–128 segments, with 1–3 branches whose brightness decays with depth.
  - Draw it with Canvas as a stroked `Path`: a thin, near-white core plus 1–2 wider, blurred, bluish-violet glow strokes.
  - Evaluating distance-to-N-segments per pixel across the full screen in AGSL is O(N) per pixel. If done in-shader, restrict it to the bolt's bounding box.
  - Animate as real strikes look: a leader reveal over ~50–100 ms, then 2–4 flickering return strokes, then an exponential afterglow fade of ~0.3–1 s. These timings are illustrative; no source was consulted.
- **In-cloud flash:**
  - Add a radial light term centred at a random point inside the cloud layer, modulated by the cloud density field (dense areas glow, thin edges rim). This is the 2D analogue of Nubis's internal lighting.
  - Briefly raise global exposure, fog brightness and rain streak brightness (Tatarchuk: drops brighter and more transparent).
  - Cost: a few ALU ops per pixel, gated by a uniform, so it is zero when inactive.
- **Rain shafts / virga under distant storm cells:** vertically stretched, low-frequency noise columns below the cloud base, blended with fog colour and animated slowly sideways. This is cheap because it is a single noise evaluation at low resolution.
- **Fog/mist:** iq analytic height fog plus the sun-direction colour term is almost free. For drifting mist banks, add 1–2 low-resolution scrolling noise layers modulating fog density near the horizon, and god rays through fog at golden hour.
- **Haze, dust, sandstorm:**
  - Raise the turbidity or Mie density in the sky model, and use per-channel extinction with warm (ochre/orange) in-scatter.
  - Dim and redden the sun disc through the transmittance term.
  - For sandstorms, add fast horizontal streaky noise (anisotropic fBm, stretched in x) and flying grit particles; reduce contrast strongly.
- **Heat shimmer in AGSL:** perturb the lookup coordinates of the layers beneath with a scrolling noise gradient, restricted to a band near the horizon or ground. With a single-pass procedural sky this means evaluating the underlying layer at offset coordinates, which is cheap only if that layer is a cached child bitmap. Otherwise use `RenderEffect.createRuntimeShaderEffect` on the content, which Android documents as more expensive (section 6).
- **Wind:**
  - Drive every layer's velocity from a single wind vector (cloud drift, rain tilt, snow drift, fog advection) so the scene reads as one coherent weather system.
  - Add curl-noise turbulence for particles.
  - Swaying foreground elements (leaves, grass silhouettes) via sine-based vertex- or UV-offset "sway" proportional to wind speed.
- **Rainbow/halo (analytic):**
  - Needs a virtual camera (FOV, pitch) to map each pixel to a view direction, then the angle θ to the anti-solar point (rainbow) or the sun (halo).
  - Rainbow: a coloured ring at θ ≈ 40–42° (ramp red outside to violet inside), a faint reversed secondary at 50–53°, a slightly darker band between (Alexander), optional pastel supernumeraries.
  - Only show it after rain, with the sun behind the viewer and below 42° elevation.
  - 22° halo: red-inside ring, with the sky slightly darker inside, shown with cirrostratus / high thin cloud around the sun or moon.
  - Cost: a handful of ALU ops.

### Gaps
- No timing data for lightning return strokes or afterglow was collected from atmospheric-science sources.
- No sources specifically on rendering rain shafts/virga, dust storms or sandstorms were found.
- Physically based lightning (Kim & Lin) was not read in detail.
- Leaf/vegetation wind references (e.g. Crysis vegetation, Ghost of Tsushima grass) were not verified.

---

## 5. Cinematic post-processing on mobile: bloom, grain, vignette, chromatic aberration, LUT-free grading/tone curves, DoF, camera motion/parallax/gyro, time-lapse, HDR highlights

### Takeaway
Image-space post-processing is bandwidth-expensive on tile-based mobile GPUs. In one Arm case study, bloom alone cost 3 ms, and texture-based alternatives cost under 1 ms. When full-screen blur is needed, Bjørge's "dual filter" (downsample then upsample chain) is the mobile-optimal kernel, using about 7% of the bandwidth of a naive linear-sampling blur. For a procedural sky app, most "post" can be folded into the final shader for near-zero cost: analytic glow around known bright sources, dithered film grain, vignette, and an analytic tone curve (Narkowicz ACES fit, Khronos PBR Neutral 2024, or AgX-like). Android 15 adds window HDR headroom APIs, with official guidance to use modest headroom (1.5–3×) for mixed UI.

### Cited Findings
- **Bloom/blur:**
  - [Classic/Production, 2015] Marius Bjørge (Arm), "Bandwidth-Efficient Rendering" — [SIGGRAPH 2015 notes](https://community.arm.com/cfs-file/__key/communityserver-blogs-components-weblogfiles/00-00-00-20-66/siggraph2015_2D00_mmg_2D00_marius_2D00_notes.pdf):
    - Separable box filters cut a 5×5 blur from 25 to 10 samples, at extra write-out bandwidth.
    - Threshold and first blur pass can be combined, trading bandwidth for ALU.
    - The "Dual filter" is derived from Kawase but downsamples then upsamples. It was set up as 8 passes (4 down, 4 up).
    - On a Mali-T760 MP8 it was the fastest, "closely followed by the kawase filter". It needs only 7% of the total bandwidth of a linear-sampling filter and "less than half" of Kawase, with balanced read/write.
    - PSNR differences versus a Gaussian reference were very minor.
  - Arm case study ([Attilio Provenzano, Arm Community, 17 Apr 2018](https://developer.arm.com/community/arm-community-blogs/b/mobile-graphics-and-gaming-blog/posts/post-processing-effects-on-mobile-optimization-and-alternatives)):
    - Standard bloom cost 3 ms.
    - Dual filtering gave smoother bloom at the same cost; gains were limited because the Gaussian was already downscaled.
    - Texture-based and plane-based alternatives cost under 1 ms.
    - Rendering terrain at 720p instead of 1080p saved about 4.3 ms.
    - Post-processing "is not 'banned' on mobile".
- **Lens flare, CA, dirt, starburst:** Chapman's pipeline includes per-channel chromatic distortion, lens-dirt modulation "used heavily in the Battlefield games", and a starburst texture that rotates with the camera — [Chapman 2017](https://john-chapman.github.io/2017/11/05/pseudo-lens-flare.html)
- **Tone curves (LUT-free grading):**
  - [Production, 2016] Narkowicz's ACES fit is `(x*(a*x+b))/(x*(c*x+d)+e)` with a=2.51, b=0.03, c=2.43, d=0.59, e=0.14 (published 6 Jan 2016). It slightly oversaturates bright colours versus better fits — [64.github.io tone-mapping overview](https://64.github.io/tonemapping/)
  - [SOTA 2024] Khronos PBR Neutral tone mapper (spec and sample, May 2024), supported in Blender 4.2 — [Khronos press release](https://www.khronos.org/news/press/khronos-pbr-neutral-tone-mapper-released-for-true-to-life-color-rendering-of-3d-products); [GitHub README](https://github.com/KhronosGroup/ToneMapping/blob/main/PBR_Neutral/README.md). Khronos positions it as not a competitor to filmic mappers: filmic (ACES, AgX) "should be used in strongly HDR scenes… or to achieve specific artistic looks" — [README](https://github.com/KhronosGroup/ToneMapping/blob/main/PBR_Neutral/README.md)
- **Banding, dither and grain** (Mikkel Gjøl, Playdead, "Banding in Games: A Noisy Rant", rev. 5; [PDF](https://loopit.dk/banding_in_games.pdf)). Smooth sky gradients band visibly on 8-bit output. Recommendations:
  - Dither r, g and b separately.
  - Dither spatially and temporally ("change dither pattern per frame").
  - Use a triangular noise distribution, found empirically to beat uniform or Gaussian. Hindsight: "GPUs round, so dither-range should be [-1;1[" LSB.
  - High-pass (blue) noise is less noticeable.
  - Example code adds `hash42n(seed)/255.0` before 8-bit output, with care for sRGB conversion order.
- **DoF:** "Just snow" uses a DoF effect across its parallax layers ([libretro snow.glsl](https://github.com/libretro/glsl-shaders/blob/master/borders/resources/snow.glsl)). Heartfelt varies a per-pixel blur level between sharp drops and fogged glass ([Heartfelt source copy](https://github.com/sanxincao/shadertoy/blob/master/heartfelt.glsl)).
- **Rain motion blur/glow via post blur** — [Tatarchuk 2006](https://advances.realtimerendering.com/s2006/Chapter3-Artist-Directable_Real-Time_Rain_Rendering_in_City_Environments.pdf)
- **Time-lapse skies** [Production, 2017]: Playground Games shot high-resolution 24-hour HDR time-lapse photography with a custom camera rig, on location, and projected the evolving captures onto the in-game sky in Forza Horizon 3. GDC 2017 talk by Jamie Wood — [GDC Vault](https://www.gdcvault.com/play/1024091/Shoot-for-the-Sky-The); [Game Developer](https://www.gamedeveloper.com/design/video-go-behind-the-scenes-of-i-forza-horizon-3-s-i-mesmerizing-skies)
- **HDR highlights on Android** [SOTA 2024–2025]:
  - Android 15+ lets apps request window HDR headroom (`window.desiredHdrHeadroom`). Google's guidance: 0 for SDR only, 1.5 for mixed but mostly SDR, 3 for mixed but mostly HDR, 5 for HDR only.
  - "Don't artificially brighten SDR content… This will cause your application to be too bright". Simultaneous contrast makes SDR UI look dimmer next to HDR content.
  - Source: [Android Developers Blog, Alec Mouri, 10 Sep 2025](https://android-developers.googleblog.com/2025/09/hdr-and-user-interfaces.html)
  - Ultra HDR images display with `ActivityInfo.COLOR_MODE_HDR`. Google recommends switching the window colour mode dynamically rather than in the manifest. Screenshots are tone-mapped to SDR — [Android: Display Ultra HDR](https://developer.android.com/media/grow/ultra-hdr/display)
- **Mobile upscalers** [SOTA 2023–2025] (relevant if any layer is rendered at reduced resolution through a GPU pipeline):
  - Snapdragon GSR 1: a single pass combining a 12-tap Lanczos-like upscale with adaptive sharpening — [GitHub](https://github.com/SnapdragonGameStudios/snapdragon-gsr)
  - GSR 2: a temporal (TAAU) upscaler optimised for Adreno — [Qualcomm, Oct 2024](https://www.qualcomm.com/developer/blog/2024/10/introducing-snapdragon-game-super-resolution-2)
  - Arm Accuracy Super Resolution is open-source and was developed for mobile GPUs — [PCWorld](https://www.pcworld.com/article/2641632/arm-gpu-upscaling-could-be-just-the-thing-snapdragon-pcs-need.html)

### Inferences
- **Fold "post" into the final composite shader** (zero extra passes and zero extra bandwidth):
  - Tone curve: Narkowicz ACES for a filmic, contrasty "cinematic" look, or PBR Neutral for accurate UI colours.
  - Grading: LUT-free lift/gamma/gain and split-toning (warm highlights/cool shadows) as a few multiply-adds, keyed per condition and time of day (e.g. teal-orange at golden hour, desaturated steel-blue for storms, lifted blacks for fog).
  - Vignette: `1 - k·r²` or `smoothstep`, at 1–2 ops.
  - Film grain doubling as TPDF dither: a per-pixel hash animated per frame, amplitude about 1–2 LSB for dither and a little higher for visible grain at night. This fixes sky banding.
- **Bloom without image-space passes:** in a procedural sky the bright sources are known (sun, moon, lightning, city lights), so add analytic glow terms (`pow(max(dot(v,s),0), n)` stacks, or `1/(1+k·d²)`) instead of threshold-and-blur. Use image-space dual-filter bloom (e.g. chained `RenderEffect` blurs of a small thresholded bitmap) only on flagship tiers. Its Android cost was not measured.
- **Chromatic aberration** requires evaluating the scene three times at offset coordinates. Apply it only to cheap elements (analytic flare ghosts, edge-of-screen rain-on-glass refraction) or to a cached layer.
- **Depth of field:** assign each parallax layer a fixed blur/softness (a softer falloff in the hash-particle SDF) rather than doing a real DoF pass. Particle bokeh then costs almost nothing.
- **Camera motion:**
  - Parallax layers offset by device tilt from the gyroscope/rotation-vector sensors (sky at infinity barely moves, near rain/snow moves most).
  - Slow "dolly" drift and scale breathing.
  - A 1–2% overscan so parallax never reveals edges.
  - A "time-lapse feel" is mostly speed: cloud velocity ×20–60 in a transition, sun/sky LUT scrubbing, a star rotation trail. Forza's captured time-lapse skies are the high-end reference; a bundled short HDR time-lapse loop per condition is an alternative "cinematic" route, at larger APK size.
- **HDR highlights:** request a small headroom (≈1.5) only while a hero highlight such as the sun disc, sun glint or lightning flash is on screen, per Google's guidance. Whether a RuntimeShader/Canvas draw can output extended-range (>1.0) values that use that headroom is unverified (Gaps).

### Gaps
- No measured cost was found for Android's `RenderEffect.createBlurEffect` or for chained RuntimeShader passes on specific GPUs.
- Whether AGSL output drawn via Canvas can carry extended-range HDR values into the headroom was not confirmed (docs discuss Ultra HDR images and headroom, not shader output).
- No authoritative film-grain model reference (e.g. luminance-dependent grain) was collected.
- Gyroscope/rotation-vector APIs and their power cost were not researched.
- Forza Horizon 5's refinements to captured skies (e.g. "12K" captures) appeared only in weak sources and are unverified.

---

## 6. Mobile constraints: AGSL specifics, per-pixel budgets at 1080p–1440p and 60–120 Hz, reduced-resolution + upscaling, temporal tricks, caching static layers, shader particles vs Canvas drawing

### Takeaway
AGSL (Android 13+) is GLSL ES 1.0-level. It has:
- constant, unrollable `for` loops only;
- no preprocessor;
- no samplers (child shaders via `.eval()`);
- no `discard`;
- no documented derivatives or noise built-ins;
- `half` = mediump.

One critical gotcha: a BitmapShader used as a RuntimeShader input defaults to *nearest* filtering. Upscaled low-resolution layers therefore need `setFilterMode(FILTER_MODE_LINEAR)`.

Mid-range Adreno GPUs deliver roughly 80–520 FP32 GFLOPS. At FHD+ and 120 Hz, a background taking a quarter of the GPU gets only about 80–350 FLOPs per pixel, so per-pixel fBm at full resolution and 120 Hz is out of budget on mid-range phones. The practical recipe is:
- cached static layers;
- dynamic layers rendered at 1/4–1/16 resolution;
- animation at 30–60 Hz via Android 15's adaptive-refresh-rate APIs;
- fp16 math wherever precision allows;
- quality tiers compiled as `const`-flag shader variants.

### Cited Findings
- **AGSL language constraints:**
  - AGSL "fixes its GLSL feature set at GLSL ES 1.0… for maximum device reach". It does not support preprocessor directives ("Convert #define statements to const variables"). "AGSL's compiler supports constant folding and branch elimination for const variables". It adds `half`/`short` (medium precision) types. `main` receives the pixel position in local Canvas coordinates with a top-left origin. It provides `toLinearSrgb`/`fromLinearSrgb` intrinsics and `layout(color)` uniforms for colour-managed uniforms — [Android: AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl)
  - Precision guarantees:
    - highp: range ±2⁶², relative precision 2⁻¹⁶.
    - mediump: range ±2¹⁴, relative precision 2⁻¹⁰.
    - lowp: range ±2, absolute precision 2⁻⁸. "AGSL currently converts lowp to mediump."
    - Source: [AGSL quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)
  - Loops: "'for' loops are quite limited; the compiler must be able to unroll the loop… initializer, the test condition, and the next statement must use constants", and the next step is limited to `++`, `--`, `+=`, `-=`. `break` and `continue` are allowed — [AGSL quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)
  - Arrays and statements: arrays are 1-D with explicit size, indexed only by a constant or loop variable, and cannot be returned, copied or assigned. `discard` is not allowed — [AGSL quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)
  - Built-in functions: trigonometric, exponential, common, geometric, matrix and vector-relational functions, plus the colour functions `unpremul`/`toLinearSrgb`/`fromLinearSrgb`. The list has no derivative functions (`dFdx`/`dFdy`/`fwidth`) and no noise — [AGSL quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)
  - Texture sampling: "Sampler types aren't supported, but you can evaluate other shaders", e.g. a `BitmapShader` as `uniform shader` evaluated with `.eval(coord)`. Uniforms are set with `setFloatUniform`, `setIntUniform`, `setColorUniform`, `setInputShader` and `setInputBuffer` (the latter for raw, non-colour-managed data). Warning: "complex shaders can be expensive to evaluate, particularly in a loop" — [AGSL quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)
- **API level and usage:**
  - AGSL/`RuntimeShader` is available on Android 13 (API 33) and above — [Android: AGSL](https://developer.android.com/develop/ui/views/graphics/agsl); [AGSL-Playground README quoting docs](https://github.com/Carrieukie/AGSL-Playground)
  - Animation is typically a `ValueAnimator` calling `setFloatUniform("iTime", …)` each frame. A shader can be drawn via `Paint.shader` in a custom View, or applied to view content with `RenderEffect.createRuntimeShaderEffect`. Applying it to a parent View and all children "is more expensive than drawing a custom View" — [Android: Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)
- **Filtering gotcha:** `BitmapShader.FILTER_MODE_DEFAULT` respects `Paint#isFilterBitmap`, "The exception to this rule is when a Shader is attached as input to a RuntimeShader. In that case this mode will default to FILTER_MODE_NEAREST". `FILTER_MODE_LINEAR` gives bilinear interpolation, and `setMaxAnisotropy` overrides the filter mode — [AOSP BitmapShader.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/graphics/java/android/graphics/BitmapShader.java). These BitmapShader changes are listed in the API 33 diff — [API diff 33](https://developer.android.com/sdk/api_diff/33/changes/android.graphics.BitmapShader)
- **Frame rate / battery** [SOTA 2024–2025] ([Android: Adaptive refresh rate](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate)):
  - Adaptive refresh rate (ARR) arrived in Android 15 (QPR1+) on supporting devices (`hasArrSupport()`). "Currently, most Views default to a 'Normal' frame rate, which is often set to 60 Hz".
  - Views can call `setRequestedFrameRate(30f/60f/120f)` or set a category (NORMAL ≈ 60 Hz, HIGH for smoothness "but may also increase power usage", NO_PREFERENCE). Compose 1.9+ offers `Modifier.preferredFrameRate(...)`.
  - "A View votes only if it requires redrawing… the final frame rate is determined by the highest vote". Touch, fling, app-launch and movement animations get automatic boosts.
  - ARR is on by default "to enhance power efficiency".
- **Precision/ALU guidance from GPU vendors:**
  - Qualcomm: Adreno "can be twice as power-efficient and deliver twice the performance while processing a fragment shader" with mediump (fp16) instead of highp. fp16 compute capacity is twice fp32, and fp16 storage cuts memory traffic by 50%. "Use strict half-precision types as much as possible" — [Qualcomm Adreno best practices](https://docs.qualcomm.com/bundle/publicresource/topics/80-78185-2/mobile_best_practices.html) (content from indexed search snippet; the page did not render in my fetch)
  - Arm: prefer mediump; 16-bit interpolation is twice as fast as 32-bit, and highp samplers can be half speed — [Arm GPU Best Practices Developer Guide](https://documentation-service.arm.com/static/67a62b17091bfc3e0a947695); [Arm texture sampling performance](https://support.arm.com/documentation/101897/0304/Buffers-and-textures/Texture-sampling-performance)
- **Mid-range GPU throughput (FP32 peak)** — [Wikipedia: Adreno](https://en.wikipedia.org/wiki/Adreno):

  | GPU | FP32 peak | Example SoCs |
  |---|---|---|
  | Adreno 610 | 38.4–67.2 GFLOPS | SD 460/662/665 |
  | Adreno 619 | 83.2–115.2 GFLOPS | SD 480, 695, 4 Gen 1, 6s Gen 3 |
  | Adreno 642L | 140.8–184.1 GFLOPS | SD 778G/782G |
  | Adreno 710 | 346.1–517.1 GFLOPS | SD 6 Gen 1, 6 Gen 3, 7s Gen 2 |
  | Adreno 720 | 998.4 GFLOPS | SD 7 Gen 3 |
  | Adreno 740 | 2089–2209 GFLOPS | SD 8 Gen 2 |
  | Adreno 750 | 2774–3072 GFLOPS | SD 8 Gen 3 |
  | Adreno 830 | 3379–3686 GFLOPS | SD 8 Elite |
- **Reduced resolution and temporal amortisation in production:**
  - HZD rendered clouds into a quarter-resolution buffer, updating 1 of 16 pixels per 4×4 block per frame with reprojection, which made them "10x faster or more" — [HZD slides](https://d3d3g8mu99pzk9.cloudfront.net/AndrewSchneider/The-Real-time-Volumetric-Cloudscapes-of-Horizon-Zero-Dawn.pdf)
  - Hillaire renders the low-frequency sky into a small LUT and upsamples — [Hillaire 2020](https://sebh.github.io/publications/egsr2020.pdf)
  - Arm's case study saved about 4.3 ms by rendering terrain at 720p instead of 1080p — [Arm 2018](https://developer.arm.com/community/arm-community-blogs/b/mobile-graphics-and-gaming-blog/posts/post-processing-effects-on-mobile-optimization-and-alternatives)
- **Texture-octave noise instead of ALU noise** is the classic way to make fBm cheap (iq's pre-baked octave textures, avoiding dependent reads) — [iq dynclouds](https://iquilezles.org/articles/dynclouds/)
- **No derivatives means expensive normals:** Heartfelt's finite-difference normals need 3× the drop-field evaluations, versus the derivative path the author describes as "3x cheaper" — [Heartfelt source copy](https://github.com/sanxincao/shadertoy/blob/master/heartfelt.glsl). iq documents analytic noise derivatives as an alternative — [iq: value noise derivatives](https://iquilezles.org/articles/morenoise/)

### Inferences
- **Per-pixel budget arithmetic.** Assumptions: the background gets about 25% of peak FP32 FLOPS (leaving room for UI/compositor and thermal headroom); FLOPs count an FMA as 2; real sustained throughput is lower than peak.
  - Pixel rates:
    - FHD+ 1080×2400 = 2.59 MPix, so 155.5 MPix/s at 60 Hz and 311 MPix/s at 120 Hz.
    - QHD+ 1440×3200 = 4.61 MPix, so 276.5 MPix/s at 60 Hz and 553 MPix/s at 120 Hz.
  - Budget per pixel:

    | GPU (peak used) | FHD+ @60 Hz | FHD+ @120 Hz | QHD+ @120 Hz |
    |---|---|---|---|
    | Adreno 619 (~100 GFLOPS) | ~160 FLOP/px | ~80 FLOP/px | — |
    | Adreno 642L (~160) | ~257 | ~129 | — |
    | Adreno 710 (~430) | ~690 | ~345 | — |
    | Adreno 720 (~998) | — | ~800 | ~450 |
    | Adreno 740 (~2150) | — | — | ~970 (≈1,940 at 60 Hz) |

  - Rough op counts (my estimates): one hashed 2D value-noise octave is about 30–50 FLOPs, so a 5-octave fBm is about 150–250 FLOPs. A self-shadowed cloud layer (fBm plus 3–4 shadow taps) can exceed 800–1,000 FLOPs.
  - Conclusion: on mid-range devices, dynamic noise layers must run at 1/4 pixel count (half resolution per axis, 4× budget) or 1/16 (quarter resolution per axis, 16× budget), and/or at 30–60 Hz, and/or use texture-octave noise. fp16 can roughly double the budget on Adreno, per Qualcomm.
- **Recommended frame architecture (all tiers):**
  - **Static/slow layers**, cached into Bitmaps and re-rendered only on change:
    - sky gradient/LUT, rebuilt on sun movement or condition change;
    - stars and Milky Way, rotated via UV transform;
    - moon (on phase change);
    - distant cloud deck (at 5–15 Hz with crossfade).
  - **Dynamic low-frequency layers** (clouds, fog, aurora, god-ray mask) rendered at 1/4–1/16 resolution into offscreen bitmaps. Composite them with `FILTER_MODE_LINEAR` BitmapShader children; the default is nearest and would look blocky. For the lowest-frequency layers, add a cheap bicubic/B-spline 4-tap reconstruction in the composite shader.
  - **High-frequency dynamic layers** evaluated at full resolution in the composite pass: rain/snow particle grids, glass drops, grain/dither.
  - **One final composite RuntimeShader** doing layering, fog, analytic glows, tone curve, grading, vignette and TPDF dither.
- **Frame rate policy:**
  - Default the background view to `setRequestedFrameRate(60f)`, or `30f` for fog/overcast/night-without-precipitation.
  - Use 120 Hz only while the user interacts (ARR's touch boost already does this) or for fast precipitation on flagship tiers.
  - Pause animation when off-screen, in battery saver, or when thermal status is elevated (`PowerManager` thermal APIs; not researched here).
  - Streak-length motion blur lets 60 Hz rain look smooth.
- **AGSL coding rules that follow from the constraints:**
  - (a) Keep time uniforms in `float` (highp) and wrap them (e.g. `mod` by a loop period, or pass `sin`/`cos` of time from the CPU). With mediump's 2⁻¹⁰ relative precision, a raw `half` time loses about 1 s of resolution by t ≈ 1,000 s.
  - (b) Do hashes (`fract(sin(x)*43758.5)`-style or integer-free float hashes) in `float`, and colours, blending and lighting in `half`.
  - (c) Make every loop constant-bounded (ray-march slices, particle layers) and use `break` for early exit.
  - (d) Build quality tiers as separate shader strings with `const` tier flags (no `#define`), and let constant folding strip the unused code.
  - (e) Pass LUTs and noise tiles as BitmapShader children, or via `setInputBuffer` for raw non-colour-managed data. Set LINEAR filtering and REPEAT tiling for noise.
  - (f) Replace derivative-based tricks with analytic gradients or finite differences, and budget for them.
  - (g) Prefer drawing the background in a custom View/Canvas (`Paint.shader`) over `RenderEffect` on a view hierarchy.
- **Shader particles vs Canvas particles:**
  - Hashed-grid shader particles cost O(pixels × layers) regardless of particle count, and need only a time uniform (no CPU simulation). This suits dense rain, snow and drizzle.
  - Canvas-drawn particles (`drawPoints`/`drawLines`/small bitmaps) cost O(particles) in CPU simulation and recording plus small GPU fill. This suits sparse, physically interacting elements: hail bounces, splashes, leaves, meteors, lightning polylines.
  - A hybrid is usually best: dense background precipitation in the shader, and a few hero particles on Canvas.
- **Quality-tier matrix per condition** (my synthesis; T0 = low-end or battery saver at 30 Hz, T1 = mid-range at 60 Hz, T2 = flagship at 60–120 Hz):

  | Condition / time | T2 (hero) | T1 (default) | T0 (fallback) | Main cost driver |
  |---|---|---|---|---|
  | Clear day | Hillaire-style sky-view LUT, analytic sun disc + CS/HG aureole, analytic flare ghosts, subtle heat shimmer near horizon in hot weather | Fitted Preetham/Hošek-style sky into cached gradient, sun glow via pow terms | Cached gradient + sun sprite | LUT rebuild (rare) |
  | Golden hour | + low-res god rays through broken cloud, warm sun-coloured height fog, backlit cloud silver linings | Sun-coloured fog + cloud rim light | Warm gradient preset | God-ray taps |
  | Blue hour / twilight | Ozone-inclusive LUT, Belt of Venus + Earth-shadow bands, first stars fading in | Gradient + analytic Belt band | Gradient preset | None significant |
  | Clear night | 2–3 star layers with twinkle, Milky Way texture, moon phase + earthshine, occasional meteor | 1–2 star layers, moon | Cached star bitmap | Star hash layers |
  | Aurora (high latitude / Kp) | 8–16-slice 2.5D curtains (green → red fringe) at 1/4 res, pulsation | 3–4 noise ribbons | Static aurora texture scroll | Slice count |
  | Partly cloudy | 3–4 parallax fBm layers with sun-shadow taps, Beer–Powder, HG silver lining, at 1/4 res | 2 layers of texture-octave fBm + 2 shadow taps | 1–2 scrolling textures | Noise octaves × taps |
  | Overcast | Thick low-contrast layered fBm, darker bases, diffuse light, slow drift at 15–30 Hz | 1–2 layers | Static texture | Noise |
  | Fog / mist | Analytic height fog + 2 low-res noise banks + in-scatter toward sun | Analytic fog + 1 noise bank | Analytic fog only | Noise banks |
  | Drizzle | 2 thin streak layers, mist, static drops on glass | 1–2 streak layers | 1 layer | Hash layers |
  | Rain | 3–4 parallax streak layers (milk-biased, streak-length motion blur), splashes, Heartfelt-style glass drops with 2-child blur | 3 layers + static glass drops | 2 layers | Glass normals (3× eval) |
  | Heavy rain | + rain curtains, heavier fog, fully fogged glass with trails, darker clouds | + curtains | 3 layers | As above |
  | Thunderstorm | Canvas midpoint-displacement bolt + glow, in-cloud density-masked flashes, exposure flicker/afterglow, rain brightened during flash | Bolt + global flash | Global flash only | Flash is uniform-gated |
  | Snow | 4–6 depth layers with bokeh softness, curl/sine drift, frost-growth vignette on glass, UI edge accumulation | 3 layers + sine drift | 2 layers | Hash layers |
  | Sleet / freezing rain | Rain layers + pellet layer + icy glints/tint | Rain + pellets | Rain preset | Hash layers |
  | Hail | Canvas bouncing pellets + impact puffs over rain layers | Fewer pellets | Rain preset | CPU particles (few) |
  | Haze / dust / sandstorm | High turbidity/Mie sky, warm per-channel extinction, reddened sun, anisotropic streak noise + grit | Warm fog + streak noise | Tinted fog | Noise |
  | Windy | Shared wind vector drives all layers, curl-noise particle turbulence, swaying foreground silhouettes | Faster layers + sine sway | Faster scroll | Minimal |
  | After rain (sun low, behind viewer) | Analytic rainbow (42°, faint 50–53° secondary, Alexander band, supernumeraries) | Primary bow only | None | Few ALU ops |
  | Cirrostratus around sun/moon | 22° halo (red inside, darker interior) | Halo ring | None | Few ALU ops |

### Gaps
- No measured AGSL/RuntimeShader timings on real Android devices were found, and no official per-pixel budget guidance for Android backgrounds.
- Mali GPU GFLOPS figures were not collected; the budget table covers Adreno only.
- RuntimeShader compile time and caching behaviour are not documented in the pages read.
- The lack of derivative functions is established only by their absence from the AGSL function list; there is no explicit statement.
- The precise ARR behaviour for a View that invalidates every frame via uniform updates (whether it is treated as a "movement" animation and boosted) is not documented.
- Offscreen render-to-bitmap paths (`HardwareRenderer`/`RenderNode`/`HardwareBuffer`) for building cached low-resolution layers on the GPU were not researched. Neither was whether an RGBA_F16 bitmap child preserves >1.0 HDR values through AGSL colour management; a prototype is needed.
- Thermal throttling and battery impact of continuous full-screen shaders on mid-range phones: no data found.
