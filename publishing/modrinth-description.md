# String Duper Fix

Bring classic tripwire string farms back to modern Paper and Leaf servers—without permanent world scans, telemetry, or client mods.

## What it does

- Restores shears-based tripwire disarming.
- Supports controlled hook-to-hook string farms, including pistonless lever builds.
- Uses bounded, event-driven checks instead of scanning worlds or chunks continuously.
- Caches the nearest hopper and creates one stacked item entity per output cycle.
- Stops its shared active-farm task immediately when no valid farm remains.
- Automatically displays commands in English, German, French, or Spanish based on the player's Minecraft language.

## Administration

- `/stringduper on|off|status`
- `/stringduper sounds`
- `/stringduper sounds on|off|status`
- `/stringduper reload`

Permission: `stringduper.admin` (OP by default).

Legacy aliases `/tripwirerevival` and `/twrevival` are included.

## Compatibility

- One JAR for Minecraft/Paper 1.21.11, 26.1.1, 26.1.2, 26.2, and 26.3
- Compatible Paper forks such as Leaf where they preserve the public Paper API
- Java 21+ on 1.21.11; Java 25+ on 26.x
- Server-side only
- No dependencies

Every listed Paper line was boot-tested with its official server JAR. Paper does not publish a separate 26.1 server line. Builds 26.1.1 and 26.3 were alpha at release time.

Some Paper/Leaf unsupported-mechanics settings require one additional full server restart after the first installation. Configuration files include detailed English comments and safe bounded defaults.

## Privacy

String Duper Fix contains no telemetry and performs no network requests.

## License

Open source under the MIT License.

[Source code, checksums, and releases](https://github.com/abcnova/StringDuperFix)
