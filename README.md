# koupper-cli

Terminal client for the Koupper (Octopus) engine.

## Install / upgrade

Prefer the [standalone installer](https://github.com/koupper-jvm/koupper/releases/latest) (CLI + engine + `octopus-api` → mavenLocal together):

```bash
curl -L -o install-standalone.kts https://github.com/koupper-jvm/koupper/releases/latest/download/install-standalone.kts
kotlinc -script install-standalone.kts -- --force
```

Docs: https://koupper.com/ · Getting started: https://koupper.com/getting-started.html

## Use from Gradle

```gradle
repositories { mavenLocal(); mavenCentral() }
dependencies { implementation("com.koupper:octopus-api:7.2.1") }
```

## Common commands

```bash
koupper -v
koupper doctor
koupper run script.kts
koupper module <name>
koupper provider list
```

## Contributing

Branch from **`develop`**, PR into **`develop`**. See [koupper/CONTRIBUTING.md](https://github.com/koupper-jvm/koupper/blob/develop/CONTRIBUTING.md).
