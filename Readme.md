# Distant Horizons

This mod adds a Level Of Detail (LOD) system to Minecraft.\
This implementation renders simplified chunks outside the normal render distance\
allowing for an increased view distance without harming performance.

Or in other words: this mod lets you see farther without turning your game into a slide show.\
If you want to see a quick demo, check out a video covering the mod here:

[![Minecraft Level Of Detail (LOD) mod - Alpha 1.4](https://i.ytimg.com/vi_webp/H2tnvEVbO1c/mqdefault.webp)](https://www.youtube.com/watch?v=H2tnvEVbO1c)

MC version: 1.17.1\
Fabric version: 0.39.2+1.17\
Cloth config version: 5.0.38\
ModMenu version: 2.0.14

Notes:\
This version has been confirmed to work in Intellij and Fabric Minecraft.\
(Retail running fabric version 0.39.2+1.17)


## source code installation

See the Fabric Documentation online for more detailed instructions:\
https://fabricmc.net/wiki/tutorial:setup

### Prerequisites

* A Java Development Kit (JDK) for Java 16 (recommended) or newer. Visit https://adoptium.net/releases.html for installers.
* Any Java IDE, for example Intellij IDEA and Eclipse. You may also use any other code editors, such as Visual Studio Code.

**If using IntelliJ:**
1. In the IDEA main menu, select 'Import Project' (or File → Open… if you already have a project open).
2. Select the project's build.gradle file to import the project.
3. After Gradle is done setting up, close (File → Close Project) and re-open the project to fix run configurations not displaying correctly.
4. (If the run configurations still don't show up, try reimporting the Gradle project from the Gradle tab in IDEA.)

**If using Ecplise:**
If you are using Eclipse and you would like to have the IDE run configs you can run gradlew eclipse. The project can then be imported as a normal (non-gradle) Eclipse project into your workspace using the 'File' - 'Import…' menu, then 'General' → 'Existing Projects into Workspace'.


## Compiling

1. open a command line in the project folder
2. run the command: `./gradlew build`
3. the compiled jar file will be in the folder `build\libs`


## Other commands

`./gradlew --refresh-dependencies` to refresh local dependencies.

`./gradlew clean` to reset everything (this does not affect your code) and then start the process again.


## Note to self

The Minecraft source code is NOT added to your workspace in an editable way. Minecraft is treated like a normal Library. Sources are there for documentation and research purposes only.

Source code uses Mojang mappings.

The source code can be 'created' with the `./eclipse` command and can be found in the following path:\
`minecraft-lod-mod\build\fg_cache\mcp\ VERSION \joined\ RANDOM_STRING \patch\output.jar`
