# DiPlay 0.3.12 — Personal maintenance

VersionCode 132, same package and personal signing certificate as 0.3.10/0.3.11.

- Add an opt-in main-screen operating-rate resource hint with configure/runtime fallback.
- Make video queue removal and budget accounting atomic; keep bounded frames and bytes.
- Synchronize decrypt statistics snapshots and expose source-frame, decrypt-queue and stage timing.
- Correct output-only windows, Surface gap reset and recovery-window CPU/GC accounting.
- Move session diagnostic file writes off the UI thread using the existing bounded writer.
- Measure touch executor queue wait separately from send time without changing touch event ordering.
- Avoid constructing discarded raw protocol TRACE strings by default; explicit internal trace remains opt-in.
- Add export-time memory/swap/declared-display context without root or new permissions.

See the local performance analysis for measurement definitions and A/B instructions. No physical
head-unit FPS, stability, or hardware acceleration gain has been established by JVM tests.
