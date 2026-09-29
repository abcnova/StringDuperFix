# String Duper Fix

**String Duper Fix** restores classic tripwire string farms on modern Paper servers with bounded, event-driven checks. It runs entirely on the server; players do not need a client mod.

[Source code](https://github.com/abcnova/StringDuperFix) · [Releases](https://github.com/abcnova/StringDuperFix/releases) · [Report an issue](https://github.com/abcnova/StringDuperFix/issues)

## What it does

- Restores shears-based tripwire disarming.
- Supports controlled hook-to-hook string farms, including pistonless lever builds.
- Uses bounded, event-driven checks instead of scanning worlds or chunks continuously.
- Caches the nearest hopper and creates one stacked item entity per output cycle.
- Stops its shared active-farm task immediately when no valid farm remains.
- Automatically displays commands in English, German, French, or Spanish based on the player's Minecraft language.

## Administration

- `/stringduper on|off|status`
- `/stringduper sounds` — toggle sounds for yourself
- `/stringduper sounds on|off` — set your persistent personal preference
- `/stringduper sounds status` — inspect your sound status (admin/OP only)
- `/stringduper reload`

Personal sound on/off commands are available to every player. Mechanic controls, reload, and both status commands require `stringduper.admin` (OP by default).

Legacy aliases `/tripwirerevival` and `/twrevival` are included.

## Compatibility

- Minecraft 1.21.2 through 26.3 with one JAR
- Compatible Paper forks such as Leaf where they preserve the public Paper API
- Java 21+ on 1.21.3–1.21.11; Java 25+ on 26.x
- Server-side only
- No dependencies

Mojang fixed native tripwire string duplication in Java 1.21.2. The JAR is compiled against Paper API 1.21.1 for 1.21.2-compatible forks. Paper did not publish a 1.21.2 server JAR, so direct official Paper boot tests start with 1.21.3. Every published Paper release from 1.21.3 onward was boot-tested. Paper does not publish a separate 26.1 server line.

Some Paper/Leaf unsupported-mechanics settings require one additional full server restart after the first installation. Configuration files include detailed English comments and safe bounded defaults.

## Privacy

String Duper Fix contains no telemetry and performs no network requests.

## License

Open source under the MIT License.
