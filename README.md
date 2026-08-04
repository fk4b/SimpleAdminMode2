# Pick Me Anime

Mindustry Java mod — pictologic tool: convert images to logic display schematics.

**Author:** FD  
**Version:** 1.1  
**Min game version:** 157  
**Main class:** `fallen.Main`  
**Hidden:** true (servers do not kick for client tools)

## Build

Requires JDK 17+.

```bat
gradlew jar
```

Desktop jar: `build/libs/PickMeAnimeDesktop.jar`

Full multiplatform (needs Android SDK):

```bat
gradlew deploy
```

## Structure

```
src/fallen/     Java sources
assets/         sprites and other assets
mod.hjson       mod metadata
```

## Install

Put the built jar into Mindustry `mods/` folder.
