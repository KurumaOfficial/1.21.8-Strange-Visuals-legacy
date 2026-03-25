# Strange Visuals NEW

Current branch after the update and refactor pass.

## Requirements

- JDK 21
- Windows PowerShell or any shell that can run `gradlew`

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```

## Notes

- The project targets Java 21 through Gradle toolchains.
- `mod_version` is managed from `gradle.properties`.
- `fabric.mod.json` uses the Gradle-expanded `${version}` placeholder.
