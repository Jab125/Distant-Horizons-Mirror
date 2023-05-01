# <img src="https://gitlab.com/jeseibel/distant-horizons-core/-/raw/main/_logo%20files/LOD%20logo%20flat%20-%20with%20boarder.png" width="32"> Distant Horizons

> A mod that adds a Level of Detail System to Minecraft

## Please move to main branch, this branch is mainly used for porting to newer versions of Minecraft until main becomes stable

# What is Distant Horizons?

This mod adds a Level Of Detail (LOD) system to Minecraft.\
This implementation renders simplified chunks outside the normal render distance\
allowing for an increased view distance without harming performance.

Or in other words: this mod lets you see farther without turning your game into a slide show.\
If you want to see a quick demo, check out a video covering the mod here:

<a href="https://youtu.be/_04BZ8W2bDM" target="_blank">![Minecraft Level Of Detail (LOD) mod - Alpha 1.6.3](https://cdn.ko-fi.com/cdn/useruploads/png_ef4d209d-50d9-462f-b31f-92e42ec3e260cover.jpg?v=c1097a5b-029c-4484-bec3-80ff58c5d239)</a>

### Versions

This branch is for these versions of Minecraft
- 1.19.3
- 1.19.2
- 1.19.1 & 1.19
- 1.18.2
- 1.18.1 & 1.18
- 1.17.1 & 1.17
- 1.16.5 & 1.16.4

Architectury version: 3.4-SNAPSHOT\
Architectury loom version: 0.12.0-SNAPSHOT\
Java Compiler plugin: Manifold Preprocessor


#### 1.19.4 mods
Forge version: 45.0.0\
Fabric version: 0.14.17\
Fabric API version: 0.75.3+1.19.4\
Modmenu version: 6.1.0-rc.1

#### 1.19.3 mods
Forge version: 44.0.6\
Fabric version: 0.14.11\
Fabric API version: 0.68.1+1.19.3\
Modmenu version: 5.0.2

#### 1.19.2 mods
Forge version: 43.0.0\
Fabric version: 0.14.8\
Fabric API version: 0.58.5+1.19.1\
Modmenu version: 4.0.0

#### 1.19.1 mods
Forge version: 42.0.0\
Fabric version: 0.14.8\
Fabric API version: 0.58.5+1.19.1\
Modmenu version: 4.0.0

#### 1.18.2 mods
Forge version: 40.0.18\
Fabric version: 0.13.3\
Fabric API version: 0.48.0+1.18.2\
Modmenu version: 3.1.0

#### 1.18.1 mods
Forge version: 39.1.2\
Fabric version: 0.13.3\
Fabric API version: 0.42.6+1.18\
Modmenu version: 3.0.1

#### 1.17.1 mods
Forge version: 37.1.1\
Fabric version: 0.13.2\
Fabric API version: 0.46.1+1.17\
Modmenu version: 2.0.14

#### 1.16.5 mods
Forge version: 36.2.28\
Fabric vetsion: 0.13.2\
Fabric API version: 0.42.0+1.16\
Modmenu version: 1.16.22



Notes:\
This version has been confirmed to work in IDE and Retail Minecraft with ether the Fabric or Forge mod-loader.


## Source Code Installation

#### Nightlly builds
This mod has an autobuild system to automatically build the mod on each commit
- 1.19.1: https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_19_4
- 1.19.1: https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_19_3
- 1.19.1: https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_19_2
- 1.19.1: https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_19_1
- 1.19:   https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_19
- 1.18.2: https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_18_2
- 1.18.1: https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_18_1
- 1.17.1: https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_17_1
- 1.16.5: https://gitlab.com/jeseibel/minecraft-lod-mod/-/jobs/artifacts/stable/download?job=build_16_5

See the Fabric Documentation online for more detailed instructions:\
https://fabricmc.net/wiki/tutorial:setup

### Prerequisites

* A Java Development Kit (JDK) for Java 17 (recommended) or newer. Visit https://www.oracle.com/java/technologies/downloads/ for installers.
* Git or someway to clone git projects. Visit https://git-scm.com/ for installers.
* (Not required) Any Java IDE with plugins that support Manifold, for example Intellij IDEA.

**If using IntelliJ:**
0. Install Manifold plugin
1. open IDEA and import the build.gradle
2. refresh the Gradle project in IDEA if required

**If using Ecplise: (Note that Eclispe currently doesn't support Manifold's preprocessor!)**
1. run the command: `./gradlew geneclipseruns`
2. run the command: `./gradlew eclipse`
3. Make sure eclipse has the JDK 17 installed. (This is needed so that eclipse can run minecraft)
4. Import the project into eclipse

## Switching Versions
This branch support 9 built versions:
 - 1.19.4
 - 1.19.3
 - 1.19.2
 - 1.19.1
 - 1.19
 - 1.18.2
 - 1.18.1 (which also runs on 1.18)
 - 1.17.1 (which also runs on 1.17)
 - 1.16.5 (which also runs 1.16.4)

To switch between active versions, change `mcVer=1.?` in `gradle.properties` file.

If running on IDE, to ensure IDE pickup the changed versions, you will need to run a gradle command again to allow gradle to update all the libs. (In IntellJ you will also need to do a gradle sync again if it didn't start it automatically.)
>Note: There may be a `java.nio.file.FileSystemException` thrown on running the command after switching versions. To fix it, either restart your IDE (as your IDE is locking up a file) or use tools like LockHunter to unlock the linked file. (Often a lib file under `common\build\lib` or `forge\build\lib` or `fabric\build\lib`). If anyone knows how to solve this issue please comment to this issue: https://gitlab.com/jeseibel/minecraft-lod-mod/-/issues/233
 
## Compiling

**Using GUI**
1. Download the zip of the project and extract it
2. Download the core from https://gitlab.com/jeseibel/distant-horizons-core and extract into a folder called `core`
3. Open a command line in the project folder
4. Run the command: `./gradlew assemble`
5. Then run command: `./gradlew mergeJars`
6. The compiled jar file will be in the folder `Merged`

**If in terminal:**
1. `git clone -b stable --recurse-submodules https://gitlab.com/jeseibel/minecraft-lod-mod.git`
2. `cd minecraft-lod-mod`
3. `./gradlew assemble`
4. `./gradlew mergeJars`
5. The compiled jar file will be in the folder `Merged`
> ### **WARNING:** Due to a bug at the moment, the merged jar does not contain the core.
> To add the core follow the steps below
> 1. Go into `core/build/libs` and extract the jar with the smallest name
> 2. In that extracted folder, copy the folder called `com`
> 3. Go into the `Merged` folder and extract the jar in that
> 4. Paste in the folder which you copied from step 2 into the newly extracted jar
> 5. Zip all the files that were extracted and make sure the file extension is .jar

>Note: You can add the arg: `-PmcVer=1.?` to tell gradle to build a selected MC version instead of having to manually modify the `gradle.properties` file.


## Other commands

`./gradlew --refresh-dependencies` to refresh local dependencies.

`./gradlew clean` to reset everything (this does not affect your code) and then start the process again.


## Note to self

The Minecraft source code is NOT added to your workspace in an editable way. Minecraft is treated like a normal Library. Sources are there for documentation and research purposes only.

Source code uses Mojang mappings & [Parchment](https://parchmentmc.org/) mappings.

To generate the source code run `./gradlew genSources`\
If your IDE fails to auto-detect the sources JAR when browsing Minecraft classes manually select the JAR file ending with -sources.jar when prompted by your IDE

In IntelliJ it's at the top where it says choose sources when browsing Minecraft classes

## Useful commands

Build only Fabric: `./gradlew fabric:assemble` or `./gradlew fabric:build`\
Build only Forge: `./gradlew fabric:assemble` or `./gradlew forge:build`\
Run the Fabric client (for debugging): `./gradlew fabric:runClient`\
Run the Forge client (for debugging): `./gradlew forge:runClient`

## Open Source Acknowledgements

XZ for Java (data compression)\
https://tukaani.org/xz/java.html

DHJarMerger (To merge multiple mod versions into one jar)\
https://github.com/Ran-helo/DHJarMerger
