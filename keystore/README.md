## Android Signing Keystore

This directory contains keystores for signing Android builds.

### Debug Keystore

For local development and CI builds without production keystore configured, a debug keystore (`debug.keystore`) is used with standard Android debug credentials:
- Store Password: `android`
- Key Alias: `androiddebugkey`
- Key Password: `android`

**Note:** The release workflow automatically falls back to this debug keystore when production secrets are not configured, allowing the template to build successfully out of the box.

See https://developer.android.com/studio/publish/app-signing#debug-mode

### Release Keystore (Production)

**For production releases**, you should configure a production keystore following the setup guide in [RELEASE.md](../RELEASE.md).

Once configured:
- Release builds are automatically signed with your production keystore
- APK and AAB files are built on every push to main branch
- When a GitHub release is created, signed APK/AAB are automatically attached

For example:

<img width="600" alt="Screenshot 2025-11-23 at 7 45 24 PM" src="https://github.com/user-attachments/assets/e79d887e-4a56-486c-869e-4b4a42e3adaa" />



### Workflows

Two GitHub Actions workflows are available for testing and building releases:

1. **[android-release.yml](https://github.com/hossain-khan/android-compose-app-template/blob/main/.github/workflows/android-release.yml)**: Builds release APK on main branch pushes and GitHub releases
2. **[test-keystore-apk-signing.yml](https://github.com/hossain-khan/android-compose-app-template/blob/main/.github/workflows/test-keystore-apk-signing.yml)**: Validates keystore configuration and APK signing

See [RELEASE.md](../RELEASE.md) for details on how to setup the workflows with secrets.
