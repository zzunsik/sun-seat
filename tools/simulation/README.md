# 3D simulation assets and QA

Vehicle meshes are generated from `CabinProfile` v1 in Kotlin. No third-party
model or texture is bundled. Profiles are illustrative standard interiors, not
scans or certified fleet dimensions. Coordinates in metres: +X right, +Y up,
-Z forward. City buses use left-single/right-pair seats; coaches use 2+2.
Region-level advice maps to a representative valid seat, never an invented seat.

Compile materials with **Filament 1.81.0**, matching the Maven runtime
(`filament` in `gradle/libs.versions.toml`):

```powershell
matc.exe -a opengl -p mobile -o app/src/main/assets/simulation/cabin_lit.filamat tools/simulation/cabin_lit.mat
matc.exe -a opengl -p mobile -o app/src/main/assets/simulation/cabin_unlit.filamat tools/simulation/cabin_unlit.mat
matc.exe -a opengl -p mobile -o app/src/main/assets/simulation/cabin_beam.filamat tools/simulation/cabin_beam.mat
```

Download matching tools (`filament-v<version>-windows.tgz`, `bin/matc.exe`) from
https://github.com/google/filament/releases/tag/v1.81.0 and compare the archive's
sha256 with the digest shown on that release page. Keep the toolchain outside the
repository. `matc.exe --version` prints the material version it writes.
Committed binaries allow CI builds without the compiler. Android uses the default
OpenGL backend. Filament is Apache-2.0; see its repository LICENSE.

### Upgrading the Filament runtime

The runtime only loads materials whose material version equals its own; a mismatch
is a native crash when the 3D view opens (#195), which Robolectric cannot show.
A runtime bump and the recompiled `*.filamat` therefore go in the same PR:

1. Change `filament` in `gradle/libs.versions.toml`.
2. Recompile all materials with that release's `matc.exe` (commands above)
   and update the version and release link above.
3. Add the release's material version (`MATERIAL_VERSION` in
   `libs/filabridge/include/filament/MaterialEnums.h` at that tag) to
   `FilamentMaterialVersionTest`, and cross-check it with that `matc.exe --version`.
   The test fails until the version table, the `.filamat` headers and this README agree.
4. Check the 3D overview and close-up on a real device.

Dependabot ignores Filament version updates for this reason (`.github/dependabot.yml`),
so nothing announces a new Filament release; check releases by hand. The ignore rule
does not block Dependabot security updates, but Gradle dependencies are not in the
dependency graph yet, so none arrive (#197). Any Filament bump follows the steps above.

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
Use `--es mode COACH` for a standard coach. `--ez unknown true` selects unknown
subway ground level. For a fixed instant use `--ef altitude 42 --ef azimuth 90
--es seat rear-RIGHT --ez inside true`; fixed solar fixtures pause initially.
Normal entry automatically plays the journey once. `--ez playing false` explicitly
pauses a fixture; saved manual pauses survive recreation. `--el elapsed 1200000`
selects an initial instant in milliseconds. Never interpret synthetic route fixtures
as real navigable roads; production uses the selected route API geometry.
Capture GPU output with adb screencap. Roborazzi checks controls, text and fallback;
its 3D placeholder does not verify GPU rendering.

## Calculation and rendering contract

`TripTimeline` evaluates solar position at the interpolated location and playback
instant. #55 remains the source of recommendations and seat-switch policy.
`CabinSimulation` casts rays toward the sun from 6x6 cushion/backrest samples.
The shell exit must be inside a window and no blocking primitive may precede it.
The roof remains a calculation blocker even in a cutaway view. Incidence angle is
between the outward window normal and the direction toward the sun (normal = 0°).
Overview uses a front/right view with the near walls cut away; close-up restores
the walls. Rounded seat borders are visual trim around the flat tested contact
planes. Windshield apertures remain vertical in this standard model; no angled
glass is added to the picture without changing the calculation geometry.
Parallel explanatory rays use each passing sample's actual window intersection.
The small sky icon and current altitude are shown above the map; no oversized sun
is placed next to the car. Only verified passing rays are drawn: no ribbon fills
the untested space between rays. Transparent vertex RGB is premultiplied by alpha
before it is passed to the Filament material (covered by a regression test).

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
