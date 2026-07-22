# SuomiTierTagger

SuomiTierTagger is a client-side Fabric mod that displays [Suomi Tier List](https://suomitierlist.com/) tiers in player nametags, the player list, and chat.

## Features

- Shows current or peak tiers with kit-specific icons.
- Supports world nametags, the player list, and chat independently.
- Includes configurable tier colors, position, separator, and refresh interval.
- Provides player lookup and data sync status through client commands.
- Checks Modrinth for compatible updates and verifies downloaded files by checksum.

## Requirements

- Minecraft 26.1
- Fabric Loader 0.16.0 or newer
- Fabric API
- Java 25

## Installation

1. Install Fabric Loader and Fabric API for Minecraft 26.1.
2. Download SuomiTierTagger from [Modrinth](https://modrinth.com/mod/suomi-tier-tagger).
3. Place the mod JAR in your Minecraft `mods` directory.

Press `K` in game to open settings. You can change the key binding in Minecraft controls.

## Commands

- `/stt <player>` — show a player's tiers.
- `/stt refresh` — refresh tier data.
- `/stt status` — show data sync status.

## Building from source

Install JDK 25, then run:

```shell
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

Built JARs are written to `build/libs/`. Run tests with `./gradlew test`.

## External services

The mod downloads public tier data from `data.suomitierlist.com`. When automatic update checks are enabled, it also contacts the Modrinth API. No API keys or accounts are required.

## Contributing

Bug reports and pull requests are welcome. Before submitting code, run `./gradlew test` and `./gradlew build`.

## License

Licensed under the [MIT License](LICENSE).
