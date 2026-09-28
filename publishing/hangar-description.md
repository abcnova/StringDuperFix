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

The same JAR supports every published Paper release from 1.21.3 through 26.3
and was boot-tested against every listed official Paper server line. Mojang
fixed native tripwire string duplication in Java 1.21.2; Paper's first release
after that fix is 1.21.3.

Open source under the MIT License.
