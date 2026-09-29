# String Duper Fix

**String Duper Fix** restores classic tripwire string farms on modern Paper servers with bounded, event-driven checks. It runs entirely on the server; players do not need a client mod.

[Source code](https://github.com/abcnova/StringDuperFix) · [Releases](https://github.com/abcnova/StringDuperFix/releases) · [Report an issue](https://github.com/abcnova/StringDuperFix/issues)

## Highlights

- Event-driven, bounded farm validation
- Shears-based tripwire disarming
- Pistonless lever-controlled farm support
- One shared task only while a validated farm is active
- English default plus automatic German, French, and Spanish messages
- Live mechanic, sound, and reload administration
- No dependencies, NMS, reflection, or network access

## Commands

```text
/stringduper on|off|status
/stringduper sounds [on|off]
/stringduper sounds status
/stringduper reload
```

Personal sound on/off commands are available to every player. Mechanic controls,
reload, and both status commands require `stringduper.admin` (OP by default).

The same JAR supports Minecraft 1.21.2 through 26.3 on Paper-compatible servers.
It is compiled against Paper API 1.21.1 for 1.21.2-compatible forks. Mojang fixed
native tripwire string duplication in Java 1.21.2, but Paper skipped that server
line; direct official Paper boot tests therefore start with 1.21.3.

Open source under the MIT License.
