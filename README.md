# SimpleAdminMode2

Mindustry Java admin mod for tracking, bans, history and related tools.

**Display name:** SimpleAdminMode  
**Internal name:** sam-java-mod  
**Author:** FD  
**Version:** 1.82  
**Min game version:** 155  
**Main class:** `fallen.SimpleAdminMode`  
**Hidden:** true

## Features (summary)

- Player list / admin UI
- Action history & render
- Advanced ban dialog + evidence log
- Auto /rollback after ban
- Ban/kick message editor
- Anti-attem patcher
- Mobile-friendly free cam input
- Crash fix for FOO Events

## Build

Requires JDK 17+.

```bat
gradlew jar
```

Desktop jar: `build/libs/SimpleAdminMode2Desktop.jar`

Full multiplatform (needs Android SDK):

```bat
gradlew deploy
```

## Structure

```
src/fallen/           Java sources
assets/bundles/       EN / RU bundles
assets/sprites/       icons
mod.hjson             mod metadata
```

## Install

Put the built jar into Mindustry `mods/` folder.
