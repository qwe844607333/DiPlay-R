# DiPlay 0.3.11 — Personal maintenance

VersionCode 131; package and personal signing identity are unchanged from 0.3.10.

The new head-unit log repeatedly entered the old frame-age drop path. That path could drop reference
frames while continuing to submit dependent frames. It also treated short delayed bursts as work to skip.

- Backport the upstream VideoBacklogRecovery policy from commit 615b32ce0b40cd0b99ce94ba23a512001553684f.
- Preserve short delayed bursts; recover sustained growth or a hard-limit backlog as one reference chain.
- Keep a working hardware codec during backlog recovery and resume dependent pictures only after an IDR.
- Preserve queued config/surface work and future frames, including queue byte/frame accounting.
- Remove the obsolete stale-frame drop counter and its repeated log messages.

Two decoder regression tests fail on 0.3.10 and pass after the fix. Seven policy boundary tests and two
queue boundary/accounting tests supplement them. No Android swap, GPU or startup policy is changed.

These checks establish queue/reference correctness, not sustained 60 fps on the physical head unit.
Use the same H.264 / 60 fps / 80% configuration to compare logs; reduce to 30 fps separately if a more
stable daily profile is needed. Preserve the personal signing key for future upgrades.
