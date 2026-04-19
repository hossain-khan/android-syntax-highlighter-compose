# GitHub Actions Release Setup Guide

This guide walks you through setting up automated release builds and signing for your Android app template.

> [!IMPORTANT]  
> **Note:** The release workflow will work without secrets configured by using the debug keystore as a fallback. However, for production releases, you **must** configure the secrets to use a proper production keystore.



## Prerequisites

- [ ] You have admin access to the GitHub repository
- [ ] You have `keytool` installed (comes with JDK)
- [ ] You have access to create and manage GitHub repository secrets

## Step 1: Create Production Release Keystore

### 1.1 Generate the Keystore

Run this command to create a new production keystore:

```bash
keytool -genkey -v \
  -keystore app-release.keystore \
  -alias app-release-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storetype PKCS12
```

> [!CAUTION]  
> **Important Notes:**
> - You'll be prompted for a keystore password - **use a strong, unique password**
> - You'll be prompted for a key password - **use the SAME password as the keystore password**
> - Fill in the certificate information (name, organization, etc.)
> - The keystore will be valid for ~27 years (10,000 days)

### 1.2 Secure the Keystore

- [ ] Save the keystore file in a secure location (NOT in the repository)
- [ ] Create a secure backup of the keystore file
- [ ] Document the passwords in a secure password manager
- [ ] **NEVER** commit the keystore file to version control

### 1.3 Record Your Keystore Information

Keep this information secure:

- **Keystore Password**: `[YOUR_PASSWORD_HERE]`
- **Key Alias**: `app-release-key` (or whatever you used)
- **Keystore Location**: `[PATH_TO_YOUR_KEYSTORE]`

## Step 2: Convert Keystore to Base64

The GitHub Actions workflow needs the keystore as a base64-encoded string.

### 2.1 Encode the Keystore

**On macOS:**
```bash
base64 -i app-release.keystore | pbcopy
```
The base64 string is now in your clipboard.

**On Linux:**
```bash
base64 -i app-release.keystore > keystore-base64.txt
cat keystore-base64.txt
```
Copy the output (the entire base64 string).

**On Windows (PowerShell):**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app-release.keystore")) | Set-Clipboard
```

### 2.2 Verify the Encoding

Test that the encoding worked:

**On macOS/Linux:**
```bash
echo "[PASTE_YOUR_BASE64_HERE]" | base64 -d > test-keystore.keystore
keytool -list -keystore test-keystore.keystore
rm test-keystore.keystore
```

If it lists the keystore contents, the encoding is correct.

## Step 3: Configure GitHub Repository Secrets

### 3.1 Navigate to Repository Settings

1. Go to: `https://github.com/[YOUR-USERNAME]/[YOUR-REPO]/settings/secrets/actions`
2. Click "New repository secret"

### 3.2 Add Required Secrets

Add these three secrets (one at a time):

#### Secret 1: KEYSTORE_BASE64
- **Name**: `KEYSTORE_BASE64`
- **Value**: [Paste the entire base64 string from Step 2.1]
- Click "Add secret"

#### Secret 2: KEYSTORE_PASSWORD
- **Name**: `KEYSTORE_PASSWORD`
- **Value**: [Your keystore password]
- Click "Add secret"

#### Secret 3: KEY_ALIAS
- **Name**: `KEY_ALIAS`
- **Value**: `app-release-key` (or whatever alias you used)
- Click "Add secret"

### 3.3 Verify Secrets Are Set

- [ ] Confirm all three secrets appear in the repository secrets list
- [ ] Verify the secret names match exactly (case-sensitive)

## Step 4: Test the Keystore Configuration

### 4.1 Test APK Signing Workflow

1. Go to: `https://github.com/[YOUR-USERNAME]/[YOUR-REPO]/actions/workflows/test-keystore-apk-signing.yml`
2. Click "Run workflow"
3. Select the `main` branch
4. Click "Run workflow" button

**Expected Result:** APK should be successfully built and signed

### 4.2 Verify APK Creation

- [ ] Check the workflow run completed successfully
- [ ] Verify "✅ Android release build succeeded with production keystore" appears in logs
- [ ] Verify "✅ Release APK generated successfully" appears in logs
- [ ] Download the "keystore-test-results" artifact (optional)

## Step 5: Test Automated Release Build

### 5.1 Trigger Release Workflow Manually

1. Go to: `https://github.com/[YOUR-USERNAME]/[YOUR-REPO]/actions/workflows/android-release.yml`
2. Click "Run workflow"
3. Select the `main` branch
4. Click "Run workflow" button

### 5.2 Verify Release Build

- [ ] Check the workflow run completed successfully
- [ ] Verify APK was created in the artifacts
- [ ] Download the APK artifact named "app-apk"
- [ ] Verify the APK file name includes the version number

### 5.3 Install and Test the APK (Optional)

- [ ] Transfer the APK to an Android device
- [ ] Install the APK (you may need to allow "Install from unknown sources")
- [ ] Verify the app launches and functions correctly

## Step 6: Test GitHub Release Integration

### 6.1 Create a Test Release

1. Go to: `https://github.com/[YOUR-USERNAME]/[YOUR-REPO]/releases/new`
2. Create a new tag (e.g., `v1.0.0-test`)
3. Add a release title and description
4. Click "Publish release"

### 6.2 Verify APK Attachment

- [ ] Wait for the "Android Release Build" workflow to complete
- [ ] Refresh the release page
- [ ] Verify the APK and AAB files are attached to the release
- [ ] Verify the file names match the version

### 6.3 Clean Up Test Release (Optional)

- [ ] Delete the test release if desired
- [ ] Delete the test tag: `git push --delete origin v1.0.0-test`

## Ongoing Usage

### Automatic Builds

The `android-release.yml` workflow automatically runs on:

- **Every push to main branch**: Creates a snapshot build (artifact available for 30 days)
- **Manual trigger**: Run the workflow manually from the Actions tab
- **GitHub release**: Publishes APK and AAB and attaches them to the release

### Creating Production Releases

1. Update version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2  // Increment
   versionName = "1.1.0"  // Update
   ```
2. Commit and push to main branch
3. Create a GitHub release with tag matching the version (e.g., `v1.1.0`)
4. The workflow automatically builds and attaches the signed APK and AAB

### Troubleshooting

If builds fail:

1. Run the `test-keystore-apk-signing.yml` workflow for diagnostics
2. Review the workflow logs for error messages
3. Verify all three secrets are set correctly
4. Check that the keystore alias matches the `KEY_ALIAS` secret
5. Ensure the keystore and key passwords are the same

## Security Reminders

- [ ] Never commit keystore files to version control
- [ ] Keep keystore passwords in a secure password manager
- [ ] Maintain secure backups of the keystore file
- [ ] Rotate the keystore only if compromised (requires user updates)
- [ ] Limit repository access to trusted collaborators
- [ ] Regularly audit who has access to repository secrets

## Completion

- [ ] All steps completed successfully
- [ ] Release workflows tested and working
- [ ] Keystore securely backed up
- [ ] Team members informed of the new release process

---

## Additional Resources

- [Android App Signing Documentation](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [GitHub Encrypted Secrets](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions)

## Need Help?

- Review workflow logs in the Actions tab
- Check `keystore/README.md` for additional documentation
- Run diagnostic workflows for troubleshooting
- Ensure all secrets are set correctly with exact names
