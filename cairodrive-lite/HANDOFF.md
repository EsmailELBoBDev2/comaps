# CairoDrive Lite — Handoff & Build Prompt

A lightweight Egypt/Cairo driving + safety map app. Online-first, MapLibre-based,
fast to iterate. This doc has two parts: **(1) the full feature list** and
**(2) a copy-paste prompt** that makes Claude Code build it phase-by-phase with
verification, so it works as it goes instead of debugging a giant blob.

---

## PART 1 — FEATURE LIST (prioritized)

### MVP (build + verify these FIRST, in this order)
1. **OSM vector map** (MapLibre GL) centered on Cairo, with **3D buildings** + tilt/pitch.
2. **My location + follow mode**, large 3D heading arrow, day/night auto theme.
3. **Business/place search** via **Google Places API (New)** — tap a result → marker + fly-to.
4. **Speed cameras** from **Overpass (OSM)** as colored dots by type (fixed/red-light/average/mobile), refreshed as you pan.
5. **Speedometer** (current km/h) + **overspeed alert** (configurable +5/+10/+15 over limit).

### v1 (after MVP runs)
6. **Turn-by-turn navigation** (routing via Valhalla/OSRM/ORS) with **big turn arrows** + **lane guidance** (highlight the correct lane near bridges/forks).
7. **Route compare** — multiple alternatives, rank by fastest and by **fewest cameras**.
8. **Traffic-aware route coloring** (green/amber/red) from TomTom/HERE flow.
9. **Driving camera HUD** — distance countdown + type + audible alert while navigating (computed in app from the camera list — no offline map needed).
10. **Multi-source cameras** — Overpass + a **custom dataset URL** + **community reports**, merged with **dedupe** (same spot within ~25 m hidden; Overpass wins, keep most specific type).
11. **Community reporting** (Waze-style): camera, mobile radar, police (كمين), speed bump, pothole, hazard. Transient kinds expire (TTL). Mappable kinds → **OSM Notes**.
12. **Egypt maxspeed defaults**: motorway 100 / trunk 90 / rural 90 / urban 60.

### v2 (nice-to-have)
13. **Arabic TTS** voice guidance + Arabic UI/RTL.
14. **Average-speed (محور) section tracker.**
15. **Parking** (save where I parked) + **Trip recorder/stats** + **Share ETA**.
16. **Street view** via Mapillary.
17. **Hazards** overlay + **traffic incidents** overlay.
18. **Bluetooth auto-start** (launch on car connect) + **Android Auto**.
19. **Headlight / dark driving mode**, **school zones**, **dangerous curves**.

### Cross-cutting
- **Multi-provider with dedupe** for search (Google primary + HERE/Geoapify/Mapbox/LocationIQ/TomTom/ORS) and routing — query available ones, merge, dedupe, never fail the whole op if one provider errors.
- **Keys** via `--dart-define` only, **never committed**; scrub keys from logs; restrict keys provider-side (package + SHA / referrer).
- Default location **Cairo (30.0444, 31.2357)**.

---

## PART 2 — THE BUILD PROMPT (paste this into the new Claude Code chat)

> Build **CairoDrive Lite**, a Flutter app: a lightweight Egypt/Cairo driving &
> safety map. Online-first. Use **MapLibre GL** (`maplibre_gl`) for an OSM vector
> map, **Google Places API (New)** for business search, and **Overpass** for
> speed cameras. See the feature list I'll paste below.
>
> **HARD RULES — follow these or the project fails (learned the hard way):**
> 1. **Build in small phases and RUN the app after each phase. Do not write more
>    than one feature's worth of code before running it.** If you cannot run it,
>    STOP and tell me exactly what to run and what you expect to see, and wait
>    for my result before continuing.
> 2. **Phase 0 must be a blank runnable app** (`flutter run` shows a map of
>    Cairo). Verify that renders before anything else.
> 3. **Render markers with MapLibre's native layers** (`SymbolLayer`/`CircleLayer`
>    via GeoJSON sources) — do NOT use indirect/bookmark-style hacks. A camera
>    dot is a circle in a GeoJSON source; updating the source updates the map.
> 4. **Every network call**: defensive (try/catch, timeout), log the failure as a
>    one-liner, and degrade gracefully — one bad provider must never blank the UI.
> 5. **All API keys via `--dart-define`** (e.g. `String.fromEnvironment`). Never
>    hardcode or commit a key. Add a `.gitignore` and a `README` showing the
>    `flutter run --dart-define=GOOGLE_PLACES_KEY=... --dart-define=MAPTILER_KEY=...`
>    command. Scrub keys from any logs.
> 6. **Pin package versions** in `pubspec.yaml` and use well-known, current
>    packages. State the versions you chose.
> 7. After each phase, give me a **one-line manual test** ("type 'pharmacy',
>    expect a marker near Cairo") and a checklist item to tick.
> 8. Keep it **lightweight**: no state-management mega-frameworks, no codegen
>    unless needed. Plain widgets + a couple of services.
>
> **Build order (one phase per step, verified before the next):**
> - **Phase 0**: runnable app, MapLibre map of Cairo with 3D buildings + tilt.
> - **Phase 1**: my-location + follow mode + speedometer overlay.
> - **Phase 2**: Google Places (New API) search box → marker + fly-to.
> - **Phase 3**: Overpass speed cameras as colored CircleLayer, refresh on pan,
>   with a live "🎥 N nearby" badge.
> - **Phase 4**: overspeed alert + camera HUD (nearest camera distance/type).
> - **Phase 5+**: routing + turn/lane guidance, traffic coloring, community
>   reports, multi-source dedupe — each its own verified phase.
>
> **Tech**: Flutter (Dart), `maplibre_gl`, `geolocator`, `http`. OSM tiles via
> MapTiler free key (`--dart-define=MAPTILER_KEY`) or a keyless demo style as
> fallback. 3D buildings = a fill-extrusion layer on the `building` source layer.
>
> **Reusable logic to port** (I'll paste the originals): Overpass camera query +
> type classification, multi-source merge/dedupe (25 m + type-compatible),
> Google Places (New) request/response shape, Egypt maxspeed defaults.
>
> Start with **Phase 0 only**. Show me the files, the exact run command, and what
> I should see. Then stop and wait for me to confirm it renders before Phase 1.

---

## Reusable snippets to paste (already written, just port)
- `lib/places_service.dart` (in this folder) — Google Places New API, ready.
- Overpass QL (cameras): `node/way/relation["highway"="speed_camera"](bbox);`
  plus `["enforcement"~"maxspeed|average_speed|traffic_signals"]`, `out center tags;`
- Dedupe rule: two cameras are the same if within **25 m** AND types compatible
  (UNKNOWN matches anything); keep the more specific type + higher maxspeed.
- Camera types from tags: `highway=speed_camera`→fixed; `enforcement=average_speed`→average;
  `traffic_signals`+enforcement→red-light; `speed_camera=mobile`→mobile.
- Egypt maxspeed defaults: motorway 100, trunk 90, rural 90, urban 60 (km/h).
