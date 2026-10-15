# Experience log: web data collection

Two registries:
- **❌ Failed decisions** — what was tried and why it didn't work.
- **✅ Verified working** — what was checked by a test or on the live site.

The machine-written pass log (results, last action, request rate, config) is kept separately in `experience.jsonl`. When `experience.jsonl` shows a new pattern, record it here.

Status labels:
- `ПРОВЕРЕНО` — reproduced on the live site or in a test, with the date.
- `ГИПОТЕЗА` — not confirmed yet.

---

## ❌ Failed decisions

| # | Date | What I did | What happened | Real cause | Lesson |
|---|---|---|---|---|---|
| F1 | 2026-10-15 | Concluded "the site blocks a foreign IP" after the first 403 | Gave up too early | The cause was my bot-like behaviour, not the IP. Once the fingerprint was fixed, the home page opened from the same IP range | Don't put a block down to IP or geography until the fingerprint and behaviour are clean. Check the fingerprint first |
| F2 | 2026-10-15 | Headless Chromium (Playwright/Patchright) | 403 and a "turn the picture" slider captcha | The site's JS check (the `oirutpspjs` parameter) collects the fingerprint. It contained `HeadlessChrome`, a mismatch between UA Chrome/156 and Client Hints 153, and the SwiftShader GPU | Don't use headless on sites with fingerprint checks |
| F3 | 2026-10-15 | Set `locale='ru-RU'` only at the Scrapling context level | `navigator.language`=ru-RU but `Intl`=en-US | Context emulation doesn't change the browser process locale | Set `LANG/LC_ALL=ru_RU.UTF-8` (generate it with `localedef`) and the `--lang=ru-RU` flag |
| F4 | 2026-10-15 | Default StealthySession options | Window larger than the screen (outer 1928×1211 on a 1920×1080 screen), DPR=2 | Scrapling sets `device_scale_factor: 2` and a 1920×1080 viewport, which doesn't fit the Xvfb screen | Pass `additional_args`: viewport 1920×947, screen 1920×1080, DPR 1 |
| F5 | 2026-10-15 | Headful under Xvfb without GPU flags | WebGL = null | No GPU means no WebGL. Scrapling's own docs say WAFs check WebGL | `--use-gl=angle --use-angle=swiftshader --enable-unsafe-swiftshader` |
| F6 | 2026-10-15 | Opened a deep link (catalog with `?search=`) and called the internal API `/сервисы/api/kn/object` via `page.evaluate(fetch)` | Captcha straight away | A visitor doesn't land deep inside with a query string or call the API by hand | Home page → click links → fill in the form |
| F7 | 2026-10-15 | Typed into the search box one character at a time (`press_sequentially`) for 7 queries | About 100 XHR in a few minutes, 403 on the 7th query, then a home-page captcha for about 30+ min | **Every keystroke fires 2–3 API requests** (`commissioning`, `meta`, `object/map`) | Count the requests a single UI action generates. Use `fill()` (one input event, one batch). Keep an XHR budget per minute |
| F8 | 2026-10-15 | A 10-minute "cooldown" as the fix | Didn't fix anything | The problem was the way I was acting, not timing | A cooldown only helps after a block. It doesn't replace a correct method |
| F9 | 2026-10-15 | Relied on raising an exception inside `page_action` | The workflow didn't stop on a block | **Scrapling swallows exceptions raised in `page_action`** and only logs `Error executing page_action` | Save the exception inside the action and re-raise it after `fetch` (`guarded_action`) |
| F10 | 2026-10-15 | Checked for the block text with an exact `"не робот"` match | Could miss it | The site writes it with non-breaking spaces (`не\xa0робот`) | Normalize: replace `\xa0`, lowercase, collapse spaces |
| F11 | 2026-10-15 | `StealthyFetcher.fetch('data:text/html,...')` for the preflight | `RuntimeError: Failed to get response` | Scrapling expects a network response | Run the preflight on a neutral page (example.com), never on the target site |
| F12 | 2026-10-15 | Went to `t.me/s/...` with `Fetcher.get` | Intermittent `connection reset` and a redirect to 127.0.0.1 | Unstable tunnel through the agent proxy | Retry with a pause. `t.me/s/<channel>?before=<id>` works for public channels |
| F13 | 2026-10-15 | Downloaded investmoscow images from `investmoscow.ru` and `api.investmoscow.ru` | Empty files | The images are served from `torgi.mos.ru/objectimages/...` | Take the media host from the site's own HTML, don't guess it |
| F14 | 2026-10-15 | **Wrong conclusion:** "detectors are clean but the probe is blocked, so it must be IP or time" | I nearly told the user the block was IP-based | The probe was made from a **fresh empty profile**, while the clean-from-the-same-IP test was run with the **cleared profile** — I never isolated the "profile/cookies" variable. With the cleared profile, the same IP passes (W12) | Before blaming IP or time, list **all** differences between a passing and a blocked run, profile and cookies included. Change one variable at a time |
| F15 | 2026-10-15 | Ran `python -I trial.py` with `import ndr_workflow` | ModuleNotFoundError | `-I` drops the script's own folder from `sys.path` | In your own scripts, add your folder to `sys.path` explicitly |
| F16 | 2026-10-15 | On a block, rotated the profile (`profile_gen += 1`) "so flagged cookies aren't reused" | Every next run started without clearance and hit the captcha on the home page | The site's anti-bot clearance lives in cookies `spid/spjs/spsc` (valid about 13 months). Throwing out the profile throws out the clearance | **Never throw away a profile that has clearance cookies.** Replace only a profile with no clearance (e.g. only `rndcaptcha`) |
| F17 | 2026-10-15 | Changed browser flags (SwiftShader → llvmpipe) for an existing session | (risk) The fingerprint no longer matches the one `spjs` was issued for | The clearance is bound to the fingerprint the check saw | A session = profile **plus** its exact config, stored in `state.session`. New flags apply only to a new session |
| F18 | 2026-10-15 | `pkill -f "<pattern>"` inside a bash command that itself contains `<pattern>` | Exit 144, the rest of the command never ran | pkill killed its own shell | Stop processes by PID (`kill $!`) |
| F19 | 2026-10-15 | Discovered objects through the site's catalog search, even with `fill()` | With the cleared session, the home page and catalog opened, but the **search's background request** `/api/kn/object/map?limit=999999` (the whole map, 1.9 MB) returned 403 on the first search | This heavy endpoint is throttled separately per session, and this session had already overused it (F7) | Don't use the site's search to discover objects. Take object IDs from open sources (`seed`) and open only the project pages (`USE_SITE_SEARCH=False`) |
| F20 | 2026-10-15 | Started a mock server for a test while the previous mock was still running | A false "BLOCKED" in the test: the old server serving the "не робот" page held the port | The test environment wasn't cleaned up (consequence of F18) | Before a test: check the port is free and stop old servers by PID |
| F21 | 2026-10-15 | Parsed the sold share with a regex over the page text | Picked up the header figure (by area) instead of units, and gave nothing for a business centre | The text is presentation; the data sits in the page's own JSON | Take numbers from the page's JSON (`page.on("response")`), and use text only as a fallback |
| F22 | 2026-10-15 | Treated the DDU figure for a month as monthly sales without checking the cumulative total | Overrated "Sezar Budushchee": 36 DDU "in September" equals the cumulative total since sales opened in June | Monthly and cumulative counts need to be cross-checked | Always compare the monthly DDU with the cumulative realised on наш.дом.рф |
| F23 | 2026-10-15 | One network error (`net::ERR_CONNECTION_RESET` on `page.goto`) ended the whole pass | The pass ended after 14 of 15 pages. Not a block: the agent proxy log shows 12 `ws_closed_mid_exchange` to this host between 10:57 and 11:24 | The tunnel through the agent proxy drops sometimes. The browser usually reconnects on its own, but not if the drop lands on a navigation | One retry after 20–30 s only for `net::ERR_*`. Never retry on 403/429/captcha. Diagnose with `$HTTPS_PROXY/__agentproxy/status` → `recentRelayFailures` |
| F24 | 2026-10-15 | Treated a 403 on **any** of the page's background requests as a block | The workflow stopped on a 403 from `/portal-kn/api/sales/portal/table/exists?externalId=73789` and set a 45-min cooldown. A repeat visit to 73789 a few minutes later opened normally with no 403 | A single 403 on a secondary request isn't a block of the session. On a block, the page text wasn't saved, so there was nothing to diagnose from | Separate signals: **hard** = 403/429 on the document or captcha text → stop; **soft** = 403 on an XHR while the page rendered → save the page, pause 2–3 min, 2 soft signals per pass = block. Always save the page context on a block (`block_context` in experience.jsonl) |
| F25 | 2026-10-15 | Fetched the MKAD outline via Overpass with plain `curl`, then started building a polygon from points and interchanges "from memory" | `connection reset` / 504 / 500 / 502 from every Overpass mirror; the from-memory polygon is approximate | Overpass was overloaded on the server side (confirmed via Scrapling: the request reaches the server, the server returns 5xx). Going to approximate data was premature | For network calls, use Scrapling `Fetcher` (`retries`, `retry_delay`, `impersonate`) — it separates "our network" from "their server". If a service is down, look for another source of **the same exact data** instead of approximating |
| F26 | 2026-10-15 | Ran the session with `retries=1` (one attempt on the first request of a pass) | A tunnel drop (`ERR_CONNECTION_RESET`) on the home page wasted a whole pass | The agent proxy's tunnel sometimes drops connections | Use Scrapling's own retries for session requests: `retries=3, retry_delay=10` (only network errors are retried; 403/captcha are handled by the workflow logic) |

## ✅ Verified working

| # | Date | What | Evidence |
|---|---|---|---|
| W1 | 2026-10-15 | **наш.дом.рф home page and catalog open without a captcha** with: `real_chrome=True` (Google Chrome 155), `headless=False` under `xvfb-run -s "-screen 0 1920x1080x24"`, `LANG/LC_ALL=ru_RU.UTF-8`, `TZ=Europe/Moscow`, `--lang=ru-RU`, WebGL via ANGLE/SwiftShader, `additional_args` = viewport 1920×947 + screen 1920×1080 + DPR 1 + light theme, `google_search=False`, a 9–12 s pause on the home page, and getting to the catalog by clicking "Каталог новостроек" | ПРОВЕРЕНО on the live site: home 200 OK, catalog with "589 ЖК" loaded, and the site's own XHR returned 200 |
| W2 | 2026-10-15 | The catalog loads its data from `/сервисы/api/kn/{meta,commissioning,object/map,developers,...}` and `/portal-kn/api/...`. Object cards link to `/объект/<id>` | ПРОВЕРЕНО: captured with `page.on('response')` |
| W3 | 2026-10-15 | Fingerprint preflight (`ndr_workflow.py preflight`): UA = Client Hints, lang = Intl = ru, tz = Moscow, webdriver = false, WebGL present, window ≤ screen, DPR = 1 | ПРОВЕРЕНО: OK |
| W4 | 2026-10-15 | Block handling: captcha text → stop → 45-min cooldown (exponential, capped at 12 h) → new profile → next run skips while the cooldown is active | ПРОВЕРЕНО in an offline test (mock page with `не\xa0робот`) |
| W5 | 2026-10-15 | Full cycle with resume: action budget per pass (6), search via `fill()`, project pages through links, sold share parsed from text, state in `state.json` | ПРОВЕРЕНО in an offline test: 7/7 objects over 3 passes |
| W6 | 2026-10-15 | Public Telegram channels readable without an account: Scrapling `Fetcher.get('https://t.me/s/<channel>?before=<id>')`, selectors `.tgme_widget_message`, `.tgme_widget_message_text`, `time[datetime]`. A single post: `t.me/<channel>/<id>?embed=1&mode=tme` | ПРОВЕРЕНО: 360 posts from @propertyinsider |
| W7 | 2026-10-15 | investmoscow / torgi.mos.ru: the lot list is `POST api.torgi.mos.ru/investmoscow/tender/v2/filtered-tenders/searchtenderobjects` (seen in the page's XHR after clicking the category tab); images are on `https://torgi.mos.ru<url>` | ПРОВЕРЕНО |
| W8 | 2026-10-15 | Installing the Patchright browser: `patchright install chromium` and, for `real_chrome`, `patchright install chrome` | ПРОВЕРЕНО |
| W9 | 2026-10-15 | WebGL via `--use-gl=angle --use-angle=gl --ignore-gpu-blocklist` (Mesa llvmpipe, nothing spoofed) beats SwiftShader: sannysoft 0 failed vs 1; overall score 4.4 vs 12. BrowserScan "Normal", CreepJS headless 0%, lies 0 | ПРОВЕРЕНО with `trial.py detect`. Moved into the workflow |
| W10 | 2026-10-15 | headless mode (even with real Chrome) raises CreepJS headless from 0% to 33%. `--use-gl=egl` turns WebGL off entirely | ПРОВЕРЕНО with `trial.py detect` |
| W11 | 2026-10-15 | **Trial-and-error method without idle time:** iterate on neutral detectors (sannysoft, CreepJS, BrowserScan) with no limits; send at most one target-site probe per candidate, and only if it beats the baseline, with a ≥10 min gap. Every trial and probe goes to `experience.jsonl` | ПРОВЕРЕНО: 6 candidates in about 10 minutes, 1 probe on the target site |
| W12 | 2026-10-15 | **Session with clearance:** profile `profile3` (cookies `spid/spjs/spsc`, earned at 09:05 with the W1 config) plus the same W1 config opens the home page **from the same IP range** at 09:4x, while fresh profiles get the captcha | ПРОВЕРЕНО on the live site: `probe_p3.py` — home 200, no block |
| W13 | 2026-10-15 | Session model in the workflow: `state.session` = {profile, flags, context}; on a block, `clearance_cookies()` reads the profile's cookie DB locally — clearance present → keep the session and back off; no clearance → replace the session | ПРОВЕРЕНО offline with a mock server, both branches |
| W14 | 2026-10-15 | Mode without site search: `./run.sh seed <query> <id...>` → home page → `goto` straight to `/сервисы/каталог-новостроек/объект/<id>`, no catalog, no search | ПРОВЕРЕНО offline: 4/4 objects in one pass, 4 actions |
| W15 | 2026-10-15 | **Live pass with no blocks:** cleared session (W12) + seeded IDs (W14) + home page → `goto` to project pages, 45–75 s apart, 6 actions per pass, peak XHR 4/min | ПРОВЕРЕНО on the live site: 6/6 objects, 0 captchas, 0 × 403 (09:54–10:04 UTC) |
| W16 | 2026-10-15 | The data source is the page's own request `/сервисы/api/object/<id>/sales_agg`: `apartmentsAggData.total/realised/perc` (units), plus non-residential and parking. The % in the page header is a different figure (probably by area): Stone Blik 10% in the header vs 14% by units. `saleNotStartedFlg:1` means sales haven't started | ПРОВЕРЕНО: realised matches the Telegram post's DDU counts (Ridge 75 vs 73, Stone 109 vs 80+28, Sezar 36/33 vs 36/33, ParkView 20 vs 21) |
| W17 | 2026-10-15 | Faster pace (trial step): 15 pages per pass, 30–50 s between pages, 5–8 min between passes, same session | ПРОВЕРЕНО: pass 10:44–11:00 UTC, 15/15, 0 blocks, peak XHR 3/min. About 65 s per page in practice |
| W18 | 2026-10-15 | **Monthly series without extra requests:** `__NEXT_DATA__ → props.initialState.kn.objectCard.salesDynamic.monthlySalesByType.{residential,nonResidential,parking}` — 12 months of `realised` (units), `contracted` (number of contracts), `soldArea`, `priceAvg` (actual ₽/m² of deals). `realised/contracted ≫ 1` = a bulk deal. The card is `mainInfo.data` (name, class, developer, coordinates, flatsCount, averagePrice, soldOutPercent) | ПРОВЕРЕНО: PRIME PARK — the series sums to 38, the same as the page («38 квартир из 299») |
| W19 | 2026-10-15 | Finding competitors without the site's search: the full map of buildings (`kn/object/map`, saved earlier) → neighbours by radius from coordinates → only their pages. Queue in round-robin order across projects (nearest first) so interim analysis covers every project | ПРОВЕРЕНО |
| W20 | 2026-10-15 | Controlled re-test of a disputed signal: after the fix, the first page in the queue is the one the "block" happened on | ПРОВЕРЕНО: 73789 opened at 12:31 UTC with no 403, which settled the question in one visit |
| W21 | 2026-10-15 | Exact MKAD outline: OSM API `api.openstreetmap.org/api/0.6/relation/2094222/full.json` (route «Московская кольцевая автомобильная дорога», 624 ways, 2,037 nodes). The relation id comes via Nominatim (ways) → `/way/<id>/relations.json`. Polar model with 1° bins, outer carriageway (`moscow_scan/mkad.py`) | ПРОВЕРЕНО: 337/360 degrees covered by data, 417 of 589 Moscow buildings inside MKAD |
| W22 | 2026-10-15 | Trial step 3 for pace: 20 pages per pass, 20–35 s between pages, 3–5 min between passes (MKAD scan, 305 pages) | ГИПОТЕЗА → result goes in `experience.jsonl`; on soft/hard signals the workflow backs off automatically |

## Trial-and-error method (`trial.py`)

1. **Hypothesis = a single change** to the config, recorded as a candidate in `CANDIDATES`.
2. **`trial.py detect`** runs every candidate through 3 detectors. A score is built from failed tests, % headless/like-headless/stealth, lies, and a SwiftShader/no-WebGL flag. No pauses, no penalties.
3. **`trial.py rank`** — the leader is the lowest score.
4. **`trial.py probe <name>`** — one home-page load on the target site. Only allowed when the candidate beats the baseline, and no more than once every 10 minutes.
5. **Reading the result:**
   - detectors clean + probe passed → move the config into the workflow (`FLAGS`/`CONTEXT`);
   - detectors clean + probe blocked → **first** compare the session: does the profile have clearance cookies, and does the config match the one they were issued for? Only after the profile, cookies and behaviour variables are ruled out should you suspect IP or time (see F14)
   - detectors dirty → keep iterating on detectors, don't touch the site.
6. Every step is recorded automatically in `experience.jsonl`. Conclusions go into this file.

## Rules (in force for every new site)

1. **Preflight before the first request.** The fingerprint must be internally consistent; if it isn't, don't go to the site.
2. **Start like a visitor:** home page → wait for the anti-bot JS check to finish (≥ 9 s) → navigate by clicking.
3. **Before automating any UI action, measure how many requests it generates** (`page.on('request')`). Search, autocomplete and filters are the most expensive.
4. **XHR budget per minute** and a minimum gap between actions (45–75 s). One visitor, one tab.
5. **Never solve a captcha.** On a 403, 429 or captcha: stop, apply an exponential cooldown, use a new profile, and record it in `experience.jsonl`.
6. **Don't call the internal API directly.** Read the responses the page makes itself (`page.on('response')` / `capture_xhr`).
7. **Exceptions inside `page_action` don't propagate in Scrapling.** Pass them out yourself.
8. **Check every new mechanism offline first** (mock site on `python -m http.server`), then do one controlled pass on the live site.

## Open hypotheses

- H1 (`ПРОВЕРЕНО частично`): SwiftShader really is visible to detectors (sannysoft fails it); fixed in W9. But the current block didn't lift after the fix (F14), so it isn't the cause of this block.
- H2 (`ГИПОТЕЗА`): the block after F7 is time-limited (rate-limit) rather than permanent. To confirm: one controlled pass after the cooldown expires.
  - Update: with the cleared session (W12) the site lets us through at the same time, so the "block" was tied to profiles without clearance, not to IP or time.
  - Observations: the W1 config passed at 09:05 UTC; the F7 burst was at ~09:12; after that the home page showed a captcha at 09:15 and 09:29 UTC (0 actions, on the very first request), so the penalty lasts at least 17 min. Next check: the workflow pass at 10:14 UTC. If that pass is also blocked on the home page with 0 actions, raise the priority of H1 (SwiftShader) and IP reputation.
