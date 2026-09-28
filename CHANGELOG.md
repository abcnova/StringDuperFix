# String Duper Fix changelog

## 1.0.0 - Initial release

- Restores controlled tripwire string-farm behavior from Paper 1.21.3 through 26.3.
- Supports pistonless, lever-controlled farms without permanent world scanning.
- Adds bounded event-driven compatibility checks and one shared active-farm ticker.
- Adds English, German, French, and Spanish player messages selected by locale.
- Adds persistent per-player sounds and `/stringduper reload`.
- Sends farm sounds only to nearby players who enabled their personal preference.
- Adds persistent mechanic control through `/stringduper on|off|status`.
- Adds compatibility aliases `/tripwirerevival` and `/twrevival`.
- Includes safe configuration limits, atomic file updates, and clean shutdown handling.
- Ships as one Java 21 JAR boot-tested on every published Paper release from 1.21.3 through 26.3.
