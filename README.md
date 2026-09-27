# String Duper Fix

String Duper Fix restores controlled, vanilla-style tripwire string farms on
Paper and compatible forks from 1.21.11 through 26.3. It is server-side,
dependency-free, and event-driven:
there is no permanent world or chunk scan.

[Source code and releases](https://github.com/abcnova/StringDuperFix)

## Highlights

- Restores shears-based tripwire disarming and compatible string-farm behavior.
- Supports pistonless lever-controlled farms with safe, bounded checks.
- Produces one stacked item entity per cycle and caches the nearby hopper.
- Stops its single shared ticker as soon as no active farm remains.
- Provides configurable sounds and persistent sound preferences.
- Automatically uses English, German, French, or Spanish for player messages.
- Includes a safe live reload that replaces listeners and runtime snapshots.
- Contains no telemetry, network requests, NMS, reflection, or external dependencies.

## Commands

| Command | Description |
| --- | --- |
| `/stringduper on\|off\|status` | Enable, disable, or inspect the mechanic. |
| `/stringduper sounds` | Toggle farm and command sounds. |
| `/stringduper sounds on\|off\|status` | Set or inspect sound status. |
| `/stringduper reload` | Reload configuration, languages, and sounds. |

Legacy aliases `/tripwirerevival` and `/twrevival` are included. Administrative
commands require `stringduper.admin` (OP by default).

## Configuration

- `config.yml`: farm detection, safety bounds, output timing, and compatibility.
- `sounds.yml`: sound names, volumes, pitches, and persistent enabled state.
- `messages_en.yml`, `messages_de.yml`, `messages_fr.yml`, `messages_es.yml`:
  editable MiniMessage translations selected from each player's Minecraft locale.

When String Duper Fix changes a Paper or Leaf unsupported-mechanics option, one
additional full server restart is required. The plugin never loads chunks for a
farm and only tracks validated hook-to-hook lines in already loaded chunks.

## Compatibility

The same release JAR was compiled against the oldest supported API and boot-tested
on each currently published Paper line:

| Minecraft / Paper | Tested build | Required server Java |
| --- | --- | --- |
| 1.21.11 | 132 (stable) | Java 21+ |
| 26.1.1 | 29 (alpha) | Java 25+ |
| 26.1.2 | 74 (stable) | Java 25+ |
| 26.2 | 129 (stable) | Java 25+ |
| 26.3 | 49 (alpha) | Java 25+ |

Paper does not publish a separate 26.1 server line. Compatible Paper forks such
as Leaf are supported where they preserve the public Paper API; fork-specific
behavior should still be tested by the server owner.

## Build

Run `mvn clean verify` with Java 21. The release JAR is written to
`target/StringDuperFix-1.0.0.jar`.

Run `scripts/Test-PaperMatrix.ps1` to download the latest official build of each
supported Paper line, verify its SHA-256 checksum, start it with the matching
Java runtime, verify that String Duper Fix enables, and stop it cleanly.

## Performance verification

The plugin is event-driven and creates no permanent world scan. Its optional
continuous output uses one shared task only while at least one validated farm is
active. The release matrix verifies startup, enable, and shutdown behavior and
checks the logs for plugin exceptions and invalid sound/config warnings. It is a
compatibility test, not a simulated-player TPS/MSPT benchmark; no unmeasured
"lag-free" claim is made.

## Privacy

String Duper Fix sends no analytics or server information. It performs no HTTP,
DNS, database, or other network access.
