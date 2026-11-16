# 3D simulation assets and QA

Vehicle meshes are generated from `CabinProfile` v1 in Kotlin. No third-party
model or texture is bundled. Profiles are illustrative standard interiors, not
scans or certified fleet dimensions. Coordinates in metres: +X right, +Y up,
-Z forward. City buses use left-single/right-pair seats; coaches use 2+2.
Region-level advice maps to a representative valid seat, never an invented seat.

Compile materials with **Filament 1.80.1**, matching the Maven runtime:

```powershell
matc.exe -a opengl -p mobile -o app/src/main/assets/simulation/cabin_lit.filamat tools/simulation/cabin_lit.mat
matc.exe -a opengl -p mobile -o app/src/main/assets/simulation/cabin_unlit.filamat tools/simulation/cabin_unlit.mat
```

Download matching tools from https://github.com/google/filament/releases/tag/v1.80.1.
Committed binaries allow CI builds without the compiler. Android uses the default
OpenGL backend. Filament is Apache-2.0; see its repository LICENSE.

## Device fixtures

`SimulationFixtureActivity` only exists in debug builds. It uses synthetic public
coordinates, the actual map renderer and production simulation screen. No route
API request is needed. The release manifest has no fixture entry point.

```powershell
adb shell am start -S -W -n com.sunseat.app/.SimulationFixtureActivity --es mode CAR
adb shell am start -S -W -n com.sunseat.app/.SimulationFixtureActivity --es mode TAXI
adb shell am start -S -W -n com.sunseat.app/.SimulationFixtureActivity --es mode BUS
adb shell am start -S -W -n com.sunseat.app/.SimulationFixtureActivity --es mode SUBWAY --ez dark true
```

Add `--ez underground true` or `--ez night true` for excluded sunlight states.
Capture GPU output with adb screencap. Roborazzi checks controls, text and fallback;
its 3D placeholder does not verify GPU rendering.

## Calculation and rendering contract

`TripTimeline` evaluates solar position at the interpolated location and playback
instant. #55 remains the source of recommendations and seat-switch policy.
`CabinSimulation` casts rays toward the sun from 6x6 cushion/backrest samples.
The shell exit must be inside a window and no blocking primitive may precede it.
The roof remains a calculation blocker even in a cutaway view. Incidence angle is
between the outward window normal and the direction toward the sun (normal = 0°).

Filament draws structure and unlit token-colored patches of the CPU direct-light
verdict. Camera position, display brightness and ambient shading never determine
seat exposure. This is an explanatory surface sampling, not precision photometry.
Shadow maps on visible structure supplement rather than replace the geometric
roof/window test. Do not claim centimetre accuracy, human-body exposure or UV dose.
Weather, buildings, curtains, glass tint, passengers and precise optical effects
are excluded. User seat selection stays independent of advice and is saved per leg.
Approximate #55 recommendations may differ from the instantaneous standard model;
do not hide light or re-recommend to manufacture agreement.

## Lifecycle

Filament is confined to the UI thread. Lifecycle stop removes its frame callback.
The surface detaches before engine disposal. Entities, materials and buffers are
released; overlay buffers are reused while capacity permits. Rendering is capped
near 30fps. Re-entry, background/foreground and release loading require device QA.
