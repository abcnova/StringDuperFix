# String Duper Fix 1.0.0 compatibility matrix

Mojang fixed native tripwire string duplication in Java Edition 1.21.2 under
MC-59471. Paper did not publish a 1.21.2 server build, so the plugin's supported
Paper range begins with 1.21.3.

The same Java 21 release JAR was compiled against Paper API 1.21.3 and tested on
every published Paper release from 1.21.3 through 26.3. Each test downloaded the
latest official Paper server JAR, verified its SHA-256 checksum, started a clean
server, verified the plugin's successful enable message, exercised the admin
status, personal-sound console guard, and safe reload paths, checked for plugin
exceptions and invalid configuration or sound warnings, and performed a clean
shutdown.

| Minecraft / Paper | Paper build | Channel | Server started | Plugin enabled | Commands passed |
| --- | ---: | --- | --- | --- | --- |
| 1.21.3 | 83 | Stable | Yes | Yes | Yes |
| 1.21.4 | 232 | Stable | Yes | Yes | Yes |
| 1.21.5 | 114 | Alpha | Yes | Yes | Yes |
| 1.21.6 | 48 | Stable | Yes | Yes | Yes |
| 1.21.7 | 32 | Stable | Yes | Yes | Yes |
| 1.21.8 | 60 | Stable | Yes | Yes | Yes |
| 1.21.9 | 59 | Alpha | Yes | Yes | Yes |
| 1.21.10 | 130 | Stable | Yes | Yes | Yes |
| 1.21.11 | 132 | Stable | Yes | Yes | Yes |
| 26.1.1 | 29 | Alpha | Yes | Yes | Yes |
| 26.1.2 | 74 | Stable | Yes | Yes | Yes |
| 26.2 | 129 | Stable | Yes | Yes | Yes |
| 26.3 | 133 | Alpha | Yes | Yes | Yes |

Paper does not publish separate 1.21.2 or 26.1 server lines. Java 21 or newer is
required for the 1.21.x servers, while the 26.x servers require Java 25 or newer.

This matrix verifies binary compatibility, startup, plugin enable, command and
reload paths, log health, and shutdown. It does not replace an in-world farm behavior test or a
simulated-player TPS/MSPT benchmark.
