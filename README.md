# SyntaxHighlight

An Android app built with:
- ⚡️ [Circuit](https://github.com/slackhq/circuit) for UI architecture
- 🏗️ [Metro](https://zacsweers.github.io/metro/) for Dependency Injection
- 🎨 [Jetpack Compose](https://developer.android.com/jetpack/compose) for UI
- 📱 Material Design 3

## Getting Started

1. Open the project in Android Studio
2. Update your app theme colors using [Theme Builder](https://material-foundation.github.io/material-theme-builder/)
3. Generate your app icon using [Icon Kitchen](https://icon.kitchen/)
4. Build and run!

## Architecture

This app follows the Circuit UDF (Unidirectional Data Flow) architecture with Metro for dependency injection.

## GitHub Actions

This project includes automated workflows:
- **CI builds** on pull requests and main branch
- **Android Lint** checks for code quality
- **Release builds** - Currently uses debug keystore (see builds are signed but not for production)

Note: For production releases with proper signing, you'll need to:
1. Generate a production keystore
2. Configure GitHub secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS)
3. The workflows will automatically use production keystore once secrets are set

