# Android - Circuit App Template
An Android App template that is preconfigured with ⚡️ Circuit UDF architecture.

> [!CAUTION]
> Since [`1.0.0`](https://github.com/hossain-khan/android-compose-app-template/releases/tag/1.0.0) release this this template has extended it's feature set
> and does not adhere to templating realm any more. If you plan to use latest version, you may have to spend extra time deleting features that you don't want.
> Browse the 🏷️ [1.0](https://github.com/hossain-khan/android-compose-app-template/tree/1.0.0) tagged release to read original instructions.

## What do you get in this template? 📜
* ✔️ [Circuit](https://github.com/slackhq/circuit) library setup for the app
* ✔️ [Metro](https://zacsweers.github.io/metro/) Dependency Injection for all Circuit Screens & Presenter combo
* ✔️ [OkHttp](https://square.github.io/okhttp/) & [Retrofit](https://square.github.io/retrofit/) networking with [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) and sample API implementation
* ✔️ GitHub Actions for CI and automated release builds
* ✔️ Automated APK/AAB builds with keystore signing (see [RELEASE.md](RELEASE.md))
* ✔️ [Google font](https://github.com/hossain-khan/android-compose-app-template/blob/main/app/src/main/java/app/example/ui/theme/Type.kt#L9-L14) for choosing different app font.
* ✔️ `BuildConfig` turned on with example of reading config from `local.properties` file.
* ✔️ [Kotlin formatter](https://github.com/jeremymailen/kotlinter-gradle) plugin for code formatting and linting
* ✔️ [Work Manager](https://developer.android.com/develop/background-work/background-tasks/persistent) for scheduling background tasks
* ✔️ [Dev Container](.devcontainer) for a ready-to-use Android development environment in VS Code or GitHub Codespaces

> [!WARNING]
> _This template is only for Android app setup. If you are looking for a multi-platform supported template,_
> _look at the official [Circuit](https://github.com/slackhq/circuit) example apps included in the project repository._

### Post-process after cloning 🧑‍🏭

1. Checkout the cloned repo
2. Navigate to repo directory in your terminal

You have **two options** for customizing this template:

<details>
<summary>Option 1: Automated Customization (Recommended)</summary>

#### Option 1: Automated Customization (Recommended) 🤖
Run the setup script to automatically handle most of the configuration:

**Script Usage:**
```bash
./setup-project.sh <package-name> <AppName> [flags]
```

**Parameters:**
- `<package-name>` - Your app's package name in reverse domain notation (e.g., `com.mycompany.appname`)
- `<AppName>` - Your app's class name in **PascalCase** (e.g., `TodoApp`, `NewsApp`, `MyPhotos`)
  - Used to rename `CircuitApp` → `{AppName}App`
  - Becomes your main Application class name
  - Sets app display name in `strings.xml`
  - Used in git commit messages

**Examples:**
```bash
# Basic usage - keeps examples and WorkManager by default
./setup-project.sh com.mycompany.appname MyAppName

# Remove WorkManager if you don't need background tasks
./setup-project.sh com.mycompany.appname MyAppName --remove-workmanager

# Remove example screens and data layer for a clean starting point
./setup-project.sh com.mycompany.appname MyAppName --remove-examples

# Keep the script for debugging (useful during development)
./setup-project.sh com.mycompany.appname MyAppName --keep-script
```

**What the script does automatically:**
- Renames package from `app.example` to your preferred package name
- Preserves subdirectory structure (`ui/theme/`, `di/`, `circuit/`, `work/`, `data/`)
- Updates app name and package ID in XML and Gradle files
- Renames `CircuitApp` to `YourAppNameApp`
- Keeps WorkManager files by default (use `--remove-workmanager` to exclude)
- Keeps example screens by default (use `--remove-examples` to exclude)
- Creates a fresh git repository with descriptive initial commit
- Removes template-specific files

</details>

<details>
<summary>Option 2: Manual Customization 🔧</summary>

#### Option 2: Manual Customization 🔧
If you prefer manual control, complete these tasks:

* [ ] Rename the package from **`app.example`** to your preferred app package name.
* [ ] Update directory structure based on package name update
* [ ] Update app name and package id in XML and Gradle
* [ ] Rename `CircuitApp***` to preferred file names
* [ ] Remove `Example***` files that were added to showcase example usage of app and Circuit.
* [ ] Remove WorkManager and Worker example files if you are not using them.

</details>

<details>
<summary>Additional Manual Steps (Both Options) 📝</summary>

#### Additional Manual Steps (Both Options) 📝
These still need to be done manually after using the script:

* [ ] Update `.editorconfig` based on your project preference
* [ ] Update your app theme colors (_use [Theme Builder](https://material-foundation.github.io/material-theme-builder/)_)
* [ ] Generate your app icon (_use [Icon Kitchen](https://icon.kitchen/)_)
* [ ] Update/remove repository license
* [ ] Configure [renovate](https://github.com/apps/renovate) for dependency management or remove [`renovate.json`](https://github.com/hossain-khan/android-compose-app-template/blob/main/renovate.json) file
* [ ] Choose [Google font](https://github.com/hossain-khan/android-compose-app-template/blob/main/app/src/main/java/app/example/ui/theme/Type.kt#L16-L30) for your app, or remove it.
* [ ] Verify Android Gradle Plugin (AGP) version compatibility with your development environment in `gradle/libs.versions.toml`
* [ ] **(Optional)** Set up production keystore for release builds - see [RELEASE.md](RELEASE.md) for automated APK signing

</details>


## Dev Container 🐳

This template includes a [Dev Container](.devcontainer) configuration for a ready-to-use Android development environment.

<details>

### Features
- **Base Image**: Java 21 (Bookworm)
- **Android SDK**: Automatically installed via post-create script (API 36, Build Tools 36.0.0)
- **VS Code Extensions**: Kotlin, Gradle, Java, and GitHub Copilot support
- **ADB Access**: Configured with `--privileged` mode to allow connecting physical Android devices

### Usage

1. Open the project in VS Code
2. When prompted, click **"Reopen in Container"** (or run `Dev Containers: Reopen in Container` from the Command Palette)
3. Wait for the container to build and the post-create script to complete

You can also open this project directly in [GitHub Codespaces](https://github.com/features/codespaces).

### Available Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Format Kotlin code
./gradlew formatKotlin

# Lint Kotlin code
./gradlew lintKotlin
```

</details>

## Demo 📹
Here is a demo of the template app containing screens shown in the 📖 [circuit tutorial](https://slackhq.github.io/circuit/tutorial/) documentation.

The demo showcases the basic Circuit architecture pattern with screen navigation and state management.

https://github.com/user-attachments/assets/56d6f28b-5b46-4aac-a30e-80116986589e


### Templated Apps
Here are some apps that has been created using the template.

| 📱 App | Repo URL | 
| ------ | ------- |
| <img alt="google-play" src="https://github.com/user-attachments/assets/18725aa7-ea0b-4d6d-962a-e0358703041c" height="14"> Weather Alert | https://github.com/hossain-khan/android-weather-alert |
| <img alt="google-play" src="https://github.com/user-attachments/assets/18725aa7-ea0b-4d6d-962a-e0358703041c" height="14"> Remote Notify | https://github.com/hossain-khan/android-remote-notify |
| <img alt="google-play" src="https://github.com/user-attachments/assets/18725aa7-ea0b-4d6d-962a-e0358703041c" height="14"> TRMNL Display | https://github.com/usetrmnl/trmnl-android |
| <img alt="google-play" src="https://github.com/user-attachments/assets/18725aa7-ea0b-4d6d-962a-e0358703041c" height="14"> TRMNL Buddy | https://github.com/hossain-khan/trmnl-android-buddy | 
| <img alt="google-play" src="https://github.com/user-attachments/assets/18725aa7-ea0b-4d6d-962a-e0358703041c" height="14"> Math Pup Tutor | https://github.com/hossain-khan/kids-math-tutor | 


## 📓 Additional References

<details>
    <summary>Metro Usage</summary>


## Metro Dependency Injection 🔧

This template uses [Metro](https://zacsweers.github.io/metro/latest/) (v0.10.4) - a modern, multiplatform Kotlin dependency injection framework. 

> **What is Dependency Injection?** DI is a design pattern that provides objects (dependencies) to a class rather than having the class create them itself. This makes code more testable, maintainable, and modular.

Metro combines the best features of:
- **Dagger**: Lean, efficient generated code with compile-time validation
- **kotlin-inject**: Simple, Kotlin-first API design
- **Anvil**: Powerful aggregation and contribution system

### Key Metro Features Used

The template demonstrates several Metro patterns:

- **[Dependency Graphs](https://zacsweers.github.io/metro/latest/dependency-graphs/)**: `AppGraph` is the root DI component scoped to the application lifecycle
- **[Constructor Injection](https://zacsweers.github.io/metro/latest/injection-types/#constructor-injection)**: Activities and other classes use constructor-based DI
- **[Aggregation](https://zacsweers.github.io/metro/latest/aggregation/)**: `@ContributesTo` automatically contributes bindings to the graph without explicit wiring
- **[Multibindings](https://zacsweers.github.io/metro/latest/bindings/#multibindings)**: Activity and Worker factories use map multibindings for flexible injection
- **[Assisted Injection](https://zacsweers.github.io/metro/latest/injection-types/#assisted-injection)**: Workers mix runtime parameters with injected dependencies
- **[Scopes](https://zacsweers.github.io/metro/latest/scopes/)**: `@SingleIn(AppScope::class)` ensures singleton instances

### Metro 0.10.x Modern Patterns

**Since Metro 0.10.0**, the `contributesAsInject` feature is enabled by default, making `@Inject` implicit on:
- `@ContributesBinding`
- `@ContributesIntoMap`
- `@ContributesIntoSet`

This means you no longer need to explicitly annotate contributing classes with `@Inject` - it's automatic! ✨

### Metro Implementation Examples

Below are simplified examples from the template. See the actual implementation files for complete details.

```kotlin
// AppGraph - Root dependency graph
@DependencyGraph(scope = AppScope::class)
@SingleIn(AppScope::class)
interface AppGraph {
    val circuit: Circuit
    val workManager: WorkManager
    // ... other graph accessors
    
    @DependencyGraph.Factory
    interface Factory {
        fun create(@ApplicationContext @Provides context: Context): AppGraph
    }
}

// Activity with constructor injection (no @Inject needed!)
@ActivityKey(MainActivity::class)
@ContributesIntoMap(AppScope::class, binding = binding<Activity>())
class MainActivity(
    private val circuit: Circuit,
) : ComponentActivity() {
    // ... activity implementation
}

// Worker with assisted injection (requires additional annotations for multibinding)
@AssistedInject
class SampleWorker(
    context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    // ... worker implementation
    
    // Note: Requires @WorkerKey and @AssistedFactory annotations
    // See app/src/main/java/app/example/work/SampleWorker.kt for complete example
}
```

For complete Metro documentation and advanced features, see the [official Metro documentation](https://zacsweers.github.io/metro/latest/).

</details>
