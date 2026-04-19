#!/bin/bash
#
# Android Compose Circuit App Template Customizer
# Adapted for Circuit + Metro DI architecture
# Compatible with bash 3.2+ (standard on macOS)
#
# Usage: bash setup-project.sh com.mycompany.appname AppName [--remove-workmanager] [--keep-script]
#
# Parameters:
#   com.mycompany.appname  - Your app's package name (reverse domain notation)
#   AppName                - Your app's class name in PascalCase (used for CircuitApp → AppNameApp)
#                            Also used as display name in strings.xml and git commit messages
#
# Examples:
#   bash setup-project.sh com.mycompany.todoapp TodoApp
#   bash setup-project.sh dev.hossain.gphotos MyPhotos --remove-workmanager
#   bash setup-project.sh com.example.allapp AllApp --remove-workmanager --keep-script
#
# Features:
#   - Flexible flag positioning (flags can come before or after positional arguments)
#   - Preserves subdirectory structure (ui/theme, di, circuit, work, data directories)
#   - Handles package renaming and file/class renaming automatically
#   - Keeps WorkManager components by default (use --remove-* to exclude)
#   - Creates fresh git repository with initial commit
#
# AppName Usage:
#   The AppName parameter is used in multiple places and must be in PascalCase:
#   - Renames CircuitApp.kt → {AppName}App.kt (e.g., TodoApp.kt, NewsApp.kt)
#   - Updates class references: CircuitApp → {AppName}App in all .kt, .xml, .kts files
#   - Sets app display name in strings.xml by splitting PascalCase: WeatherForecast → "Weather Forecast"
#   - Sets project name in settings.gradle.kts by splitting PascalCase: PhotoGallery → "Photo Gallery"
#   - Used in git commit message as project identifier
#   - Becomes the main Application class name for your project
#
# Licensed under the Apache License, Version 2.0 (the "License");
#

# Compatible with bash 3.2+ (default on macOS)
# No minimum version check needed - script uses bash 3.2+ features

# exit when any command fails
set -e

# Error handling function
cleanup_on_error() {
    echo ""
    echo "❌ Script failed at line $1"
    echo "🔍 The setup script has been preserved for debugging"
    echo "💡 You can re-run the script after fixing any issues"
    echo "📝 Check the error message above for details"
    exit 1
}

# Set up error trap
trap 'cleanup_on_error $LINENO' ERR

# Parse arguments using while loop with shift for proper flag handling
REMOVE_EXAMPLES=false
REMOVE_WORKMANAGER=false
KEEP_SCRIPT=false
POSITIONAL_ARGS=()

# Process all arguments, collecting positional args and setting flags
while [[ $# -gt 0 ]]; do
  case $1 in
    --remove-examples)
      REMOVE_EXAMPLES=true
      shift
      ;;
    --remove-workmanager)
      REMOVE_WORKMANAGER=true
      shift
      ;;
    --keep-script)
      KEEP_SCRIPT=true
      shift
      ;;
    -*)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
    *)
      # Collect positional arguments
      POSITIONAL_ARGS+=("$1")
      shift
      ;;
  esac
done

# Check if we have enough positional arguments
if [[ ${#POSITIONAL_ARGS[@]} -lt 2 ]]; then
   echo "Usage: bash setup-project.sh com.mycompany.appname AppName [--remove-workmanager] [--keep-script]" >&2
   echo ""
   echo "Parameters:"
   echo "  com.mycompany.appname  Your app's package name (reverse domain notation)"
   echo "  AppName                Your app's class name in PascalCase (e.g., TodoApp, NewsApp)"
   echo "                         Used for: CircuitApp→AppNameApp, app display name, main class"
   echo ""
   echo "Examples:"
   echo "  bash setup-project.sh com.mycompany.todoapp TodoApp"
   echo "  bash setup-project.sh com.mycompany.taskapp TaskApp --remove-workmanager"
   echo "  bash setup-project.sh com.mycompany.debugapp DebugApp --keep-script"
   echo "  bash setup-project.sh com.mycompany.allapp AllApp --remove-workmanager --keep-script"
   echo ""
   echo "Options:"
   echo "  --remove-workmanager Remove WorkManager related files (kept by default)"
   echo "  --keep-script       Keep this setup script (useful for debugging)"
   echo ""
   echo "Note: Flags can be positioned anywhere in the command line"
   echo "      Directory structure (ui/theme, di, circuit, work, data) is preserved"
   echo "      WorkManager is kept by default - use --remove-workmanager to exclude"
   exit 2
fi

PACKAGE="${POSITIONAL_ARGS[0]}"
APPNAME="${POSITIONAL_ARGS[1]}"
SUBDIR=${PACKAGE//.//} # Replaces . with /

# Create display name by splitting PascalCase into separate words
# e.g., "WeatherForecast" becomes "Weather Forecast"
DISPLAY_NAME=$(echo "$APPNAME" | sed 's/\([A-Z]\)/ \1/g' | sed 's/^ //')

echo "🚀 Starting Android Circuit App Template customization..."
echo "📦 New package: $PACKAGE"
echo "📱 App name: $APPNAME"
echo "📱 Display name: $DISPLAY_NAME"
KEEP_WORKMANAGER=$([ "$REMOVE_WORKMANAGER" = false ] && echo "true" || echo "false")
echo "⚙️  Keep WorkManager: $KEEP_WORKMANAGER"
echo "📜 Keep script: $KEEP_SCRIPT"
echo ""

# Step 1: Move package structure from app.example to new package
echo "📁 Step 1: Restructuring package directories..."
for n in $(find . -type d \( -path '*/src/androidTest' -or -path '*/src/main' -or -path '*/src/test' \) )
do
  if [ -d "$n/java/app/example" ]; then
    echo "Creating $n/java/$SUBDIR"
    mkdir -p $n/java/$SUBDIR
    echo "Moving directory structure from $n/java/app/example to $n/java/$SUBDIR"
    # Use cp to preserve directory structure, then remove source
    if [ "$(find $n/java/app/example -type f | wc -l)" -gt 0 ]; then
      # Copy all contents preserving directory structure
      cp -r $n/java/app/example/* $n/java/$SUBDIR/ 2>/dev/null || true
      # Also handle the case where there are hidden files
      cp -r $n/java/app/example/.[^.]* $n/java/$SUBDIR/ 2>/dev/null || true
      echo "Successfully copied $(find $n/java/$SUBDIR -type f | wc -l | tr -d ' ') files"
    else
      echo "No files found in $n/java/app/example to move."
    fi
    echo "Removing old $n/java/app"
    rm -rf $n/java/app
  fi
done

# Step 2: Update package declarations and imports
echo "📝 Step 2: Updating package declarations and imports..."
find ./ -type f -name "*.kt" -exec sed -i.bak "s/package app\.example/package $PACKAGE/g" {} \;
find ./ -type f -name "*.kt" -exec sed -i.bak "s/import app\.example/import $PACKAGE/g" {} \;

# Step 3: Update Gradle files and XML
echo "🔧 Step 3: Updating Gradle and XML files..."
find ./ -type f -name "*.kts" -exec sed -i.bak "s/app\.example/$PACKAGE/g" {} \;
find ./ -type f -name "*.xml" -exec sed -i.bak "s/app\.example/$PACKAGE/g" {} \;

# Step 4: Rename CircuitApp to {AppName}App
echo "⚡ Step 4: Renaming CircuitApp to ${APPNAME}App..."
find ./ -type f -name "*.kt" -exec sed -i.bak "s/CircuitApp/${APPNAME}App/g" {} \;
find ./ -type f -name "*.xml" -exec sed -i.bak "s/CircuitApp/${APPNAME}App/g" {} \;
find ./ -type f -name "*.kts" -exec sed -i.bak "s/CircuitApp/${APPNAME}App/g" {} \;

# Rename CircuitApp.kt file
if [ -f "app/src/main/java/$SUBDIR/CircuitApp.kt" ]; then
    mv "app/src/main/java/$SUBDIR/CircuitApp.kt" "app/src/main/java/$SUBDIR/${APPNAME}App.kt"
    echo "Renamed CircuitApp.kt to ${APPNAME}App.kt"
fi

# Step 5: Update app name in strings.xml and settings.gradle.kts
echo "🏷️  Step 5: Updating app name in resource and configuration files..."
find ./ -name "strings.xml" -exec sed -i.bak "s/<string name=\"app_name\">.*<\/string>/<string name=\"app_name\">$DISPLAY_NAME<\/string>/g" {} \;
find ./ -name "settings.gradle.kts" -exec sed -i.bak "s/rootProject\.name = \".*\"/rootProject.name = \"$DISPLAY_NAME\"/g" {} \;

# Step 6: Handle Example files
if [ "$REMOVE_EXAMPLES" = true ]; then
    echo "🗑️  Step 6: Removing Example* files..."
    find ./ -name "Example*.kt" -type f -delete
    find ./ -name "*Example*.kt" -type f -delete
    echo "Example files removed"

    # Remove circuit overlay directory (AppInfoOverlay.kt references example-only icons)
    echo "🗑️  Removing circuit/overlay directory..."
    OVERLAY_DIR=$(find ./ -type d -name "overlay" -path "*/circuit/overlay" 2>/dev/null | head -1)
    [ -n "$OVERLAY_DIR" ] && rm -rf "$OVERLAY_DIR" && echo "Removed: $OVERLAY_DIR"

    # Remove example-only data layer files (model, network, repository)
    echo "🗑️  Removing example data layer (model, network, repository)..."
    for DATA_SUBDIR in model network repository; do
        DIR=$(find ./ -type d -name "$DATA_SUBDIR" -path "*/data/$DATA_SUBDIR" 2>/dev/null | head -1)
        [ -n "$DIR" ] && rm -rf "$DIR" && echo "Removed: $DIR"
    done
    # Remove top-level data directory if it becomes empty
    DATA_DIR=$(find ./ -type d -name "data" -empty 2>/dev/null | head -1)
    [ -n "$DATA_DIR" ] && rm -rf "$DATA_DIR" && echo "Removed empty data dir: $DATA_DIR"

    # Remove example-only vector drawable icons
    echo "🗑️  Removing example-only vector drawable icons..."
    rm -f ./app/src/main/res/drawable/baseline_info_24.xml
    rm -f ./app/src/main/res/drawable/baseline_person_24.xml
    rm -f ./app/src/main/res/drawable/add_24dp.xml
    rm -f ./app/src/main/res/drawable/arrow_back_24dp.xml
    rm -f ./app/src/main/res/drawable/delete_24dp.xml
    rm -f ./app/src/main/res/drawable/edit_24dp.xml
    rm -f ./app/src/main/res/drawable/email_24dp.xml
    rm -f ./app/src/main/res/drawable/send_24dp.xml
    echo "Example-only drawables removed"

    # Generate a minimal HomeScreen.kt as the new root navigation destination.
    # This replaces InboxScreen (removed with Example* files) so MainActivity compiles.
    echo "📝 Generating HomeScreen.kt as starter root screen..."
    CIRCUIT_DIR=$(find ./ -type d -name "circuit" -path "*/main/*" 2>/dev/null | head -1)
    if [ -n "$CIRCUIT_DIR" ]; then
        # Derive the base app package from MainActivity.kt (most reliable source)
        BASE_PACKAGE=$(find ./ -name "MainActivity.kt" -type f 2>/dev/null | head -1 | xargs grep "^package " 2>/dev/null | sed 's/^package //' | tr -d '[:space:]')
        CIRCUIT_PACKAGE="${BASE_PACKAGE}.circuit"
        cat > "$CIRCUIT_DIR/HomeScreen.kt" << HOMESCREEN_EOF
package ${CIRCUIT_PACKAGE}

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import kotlinx.parcelize.Parcelize

/**
 * Home screen - the app's starting point.
 *
 * Replace this with your own initial screen.
 * See https://slackhq.github.io/circuit/ for Circuit architecture guidance.
 */
@Parcelize
data object HomeScreen : Screen {
    data object State : CircuitUiState
}

@CircuitInject(HomeScreen::class, AppScope::class)
class HomePresenter
    constructor() : Presenter<HomeScreen.State> {
        @Composable
        override fun present(): HomeScreen.State = HomeScreen.State
    }

@CircuitInject(HomeScreen::class, AppScope::class)
@Composable
fun HomeContent(
    @Suppress("UNUSED_PARAMETER") state: HomeScreen.State,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Welcome! Replace this screen with your own.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
HOMESCREEN_EOF
        echo "Created: $CIRCUIT_DIR/HomeScreen.kt"
    fi

    # Update MainActivity.kt to use HomeScreen instead of InboxScreen
    echo "🔧 Updating MainActivity.kt to use HomeScreen..."
    find ./ -name "MainActivity.kt" -type f | while read -r file; do
        if [ -f "$file" ] && grep -q "InboxScreen" "$file"; then
            # Replace the InboxScreen import line with HomeScreen import
            sed -i.bak "s|import ${BASE_PACKAGE}\.circuit\.InboxScreen|import ${BASE_PACKAGE}.circuit.HomeScreen|" "$file"
            # Replace any wildcard circuit import that would have covered InboxScreen
            sed -i.bak "s|import app\.example\.circuit\.InboxScreen|import ${BASE_PACKAGE}.circuit.HomeScreen|" "$file"
            # Replace the root screen in the nav stack
            sed -i.bak 's/root = InboxScreen/root = HomeScreen/' "$file"
            echo "Updated MainActivity.kt to use HomeScreen"
        fi
    done
else
    echo "📚 Step 6: Keeping Example* files for reference..."
fi

# Step 7: Handle WorkManager files
if [ "$REMOVE_WORKMANAGER" = true ]; then
    echo "🗑️  Step 7: Removing WorkManager files and references..."
    
    # Remove WorkManager files
    find ./ -path "*/work/*" -name "*.kt" -type f -delete
    find ./ -name "*Worker*.kt" -type f -delete
    find ./ -name "WorkerKey.kt" -type f -delete
    find ./ -name "AppWorkerFactory.kt" -type f -delete
    # Remove work directory if empty
    find ./ -name "work" -type d -exec rm -rf {} + 2>/dev/null || true
    
    # Clean up AppGraph.kt references
    echo "Cleaning WorkManager references from AppGraph.kt..."
    find ./ -name "AppGraph.kt" -type f | while read -r file; do
        if [ -f "$file" ]; then
            # Remove WorkManager import
            sed -i.bak '/import androidx\.work\.WorkManager/d' "$file"
            # Remove workManager and workerFactory properties
            sed -i.bak '/val workManager: WorkManager/d' "$file"
            sed -i.bak '/val workerFactory: AppWorkerFactory/d' "$file"
            # Remove providesWorkManager function (multi-line removal using sed)
            sed -i.bak '/^[[:space:]]*@Provides$/,/^[[:space:]]*): WorkManager = WorkManager\.getInstance(context)$/d' "$file"
        fi
    done
    
    # Clean up Application class (CircuitApp.kt) using Python for complex multi-line patterns
    echo "Cleaning WorkManager references from Application class..."
    find ./ -name "*App.kt" -type f | while read -r file; do
        if [ -f "$file" ] && grep -q "WorkManager\|Configuration\.Provider" "$file"; then
            # Create temporary Python script for complex cleanup
            cat > /tmp/cleanup_app.py << 'EOF'
import sys
import re

def clean_workmanager_from_app(content):
    # Remove all WorkManager-related imports
    content = re.sub(r'import androidx\.work\..*\n', '', content)
    content = re.sub(r'import .*\.work\..*\n', '', content)
    
    # Remove Configuration.Provider from class declaration more carefully
    # Handle case where it's the only interface: "Application(), Configuration.Provider"
    content = re.sub(r'Application\(\),\s*Configuration\.Provider\s*{', 'Application() {', content)
    # Handle case where it's with other interfaces: ", Configuration.Provider"
    content = re.sub(r',\s*Configuration\.Provider', '', content)
    # Handle case where it's the first interface: "Configuration.Provider, "
    content = re.sub(r'Configuration\.Provider,\s*', '', content)
    
    # Remove workManagerConfiguration property (multi-line, more precise)
    content = re.sub(r'\s*override val workManagerConfiguration:.*?\.build\(\)\s*\n', '', content, flags=re.DOTALL)
    
    # Remove scheduleBackgroundWork method call from onCreate
    content = re.sub(r'\s*scheduleBackgroundWork\(\)\s*\n', '\n', content)
    
    # Remove the entire scheduleBackgroundWork method (improved pattern)
    # This pattern matches the comment, method signature, and body until the closing brace
    content = re.sub(r'\s*/\*\*\s*\*\s*Schedules a background work.*?\*/\s*private fun scheduleBackgroundWork\(\).*?appGraph\.workManager\.enqueue\(workRequest\)\s*\n\s*\}', '', content, flags=re.DOTALL)
    
    # Also handle cases where the method might not have the exact comment format
    content = re.sub(r'\s*private fun scheduleBackgroundWork\(\).*?appGraph\.workManager\.enqueue\(workRequest\)\s*\n\s*\}', '', content, flags=re.DOTALL)
    
    # Clean up formatting issues - fix the line that has both appGraph() function and override
    content = re.sub(r'(\}\s*)(override fun onCreate)', r'\1\n\n    \2', content)
    content = re.sub(r'(fun appGraph\(\): AppGraph = appGraph)\s*(override fun onCreate)', r'\1\n\n    \2', content)
    
    # Clean up extra whitespace and format properly  
    content = re.sub(r'\n\s*\n\s*\n+', '\n\n', content)  # Remove multiple newlines
    content = re.sub(r'\{\s*\n\s*\}', '{\n    }', content)  # Fix empty braces
    
    # Ensure proper formatting around class declaration
    content = re.sub(r'(class\s+\w+\s*:\s*Application\(\)\s*)\{\s*\n\s*\n', r'\1{\n', content)
    
    return content

if __name__ == "__main__":
    file_path = sys.argv[1]
    with open(file_path, 'r') as f:
        content = f.read()
    
    cleaned_content = clean_workmanager_from_app(content)
    
    with open(file_path, 'w') as f:
        f.write(cleaned_content)
EOF
            
            python3 /tmp/cleanup_app.py "$file"
            rm -f /tmp/cleanup_app.py
        fi
    done
    
    # Clean up build.gradle.kts
    echo "Removing WorkManager dependency from build.gradle.kts..."
    find ./ -name "build.gradle.kts" -type f | while read -r file; do
        if [ -f "$file" ]; then
            sed -i.bak '/implementation(libs\.androidx\.work)/d' "$file"
        fi
    done
    
    # Clean up AndroidManifest.xml WorkManager provider configuration
    echo "Removing WorkManager configuration from AndroidManifest.xml..."
    find ./ -name "AndroidManifest.xml" -type f | while read -r file; do
        if [ -f "$file" ]; then
            # Use Python for more reliable multi-line XML cleanup
            cat > /tmp/cleanup_manifest.py << 'EOF'
import sys
import re

def clean_workmanager_from_manifest(content):
    # Remove WorkManager provider configuration including comments
    # This pattern matches from the WorkManager comment to the closing </provider> tag
    content = re.sub(r'\s*<!--[^>]*WorkManager[^>]*-->[^<]*<provider[^>]*>\s*<!--[^>]*-->\s*<meta-data[^>]*tools:node="remove"\s*/>\s*</provider>', '', content, flags=re.DOTALL)
    
    # Also handle case where there might be variations in whitespace/formatting
    content = re.sub(r'\s*<!--[^>]*WorkManager.*?</provider>', '', content, flags=re.DOTALL)
    
    return content

if __name__ == "__main__":
    file_path = sys.argv[1]
    with open(file_path, 'r') as f:
        content = f.read()
    
    cleaned_content = clean_workmanager_from_manifest(content)
    
    with open(file_path, 'w') as f:
        f.write(cleaned_content)
EOF
            python3 /tmp/cleanup_manifest.py "$file"
            rm -f /tmp/cleanup_manifest.py
        fi
    done
    
    
    echo "WorkManager files and references completely removed"
else
    echo "⚙️  Step 7: Keeping WorkManager files..."
fi

# Post-cleanup: Handle AppGraph.kt context parameter when both examples and WorkManager are removed
# When both are removed, the @ApplicationContext @Provides context: Context in AppGraph.Factory
# becomes unused (no @Provides function or contributed binding requires it), causing a Metro warning
# that is treated as an error due to -Werror. Remove the unused context parameter in that case.
if [ "$REMOVE_WORKMANAGER" = true ] && [ "$REMOVE_EXAMPLES" = true ]; then
    echo "🔧 Post-cleanup: Removing unused context parameter from AppGraph.Factory..."
    find ./ -name "AppGraph.kt" -type f | while read -r file; do
        if [ -f "$file" ] && grep -q "ApplicationContext" "$file"; then
            cat > /tmp/cleanup_appgraph_context.py << 'EOF'
import sys
import re

def clean_unused_context_from_appgraph(content):
    # Normalize line endings to LF for consistent processing
    content = content.replace('\r\n', '\n').replace('\r', '\n')

    # Remove @ApplicationContext and Context imports (no longer needed).
    # Match the full import line regardless of what comes before the package segment.
    content = re.sub(r'^import\s+\S+\.di\.ApplicationContext[ \t]*\n', '', content, flags=re.MULTILINE)
    content = re.sub(r'^import\s+android\.content\.Context[ \t]*\n', '', content, flags=re.MULTILINE)

    # Remove the @ApplicationContext @Provides context: Context parameter from Factory.create().
    # The parameter may span multiple lines (annotation on one line, param on the next).
    # This parameter becomes unused when both WorkManager and examples are removed.
    content = re.sub(
        r'(fun create\()\s*@ApplicationContext\s+@Provides\s+context:\s+Context,?\s*(\))',
        r'\1\2',
        content,
        flags=re.DOTALL
    )

    # Clean up consecutive blank lines left behind
    content = re.sub(r'\n{3,}', '\n\n', content)

    return content

if __name__ == "__main__":
    file_path = sys.argv[1]
    with open(file_path, 'r') as f:
        content = f.read()

    cleaned_content = clean_unused_context_from_appgraph(content)

    with open(file_path, 'w') as f:
        f.write(cleaned_content)
EOF
            python3 /tmp/cleanup_appgraph_context.py "$file"
            rm -f /tmp/cleanup_appgraph_context.py
        fi
    done

    # Update Application class to call create() without context argument
    find ./ -name "*App.kt" -type f | while read -r file; do
        if [ -f "$file" ] && grep -q "\.create(this)" "$file"; then
            sed -i.bak 's/\.create(this)/.create()/g' "$file"
        fi
    done

    echo "AppGraph.kt context parameter cleanup complete"
fi

# Step 8: Clean up backup files
echo "🧹 Step 8: Cleaning up backup files..."
find . -name "*.bak" -type f -delete

# Step 9: Format Kotlin code
echo "✨ Step 9: Formatting Kotlin code..."
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    echo "Running ./gradlew formatKotlin to fix code formatting..."
    if ./gradlew formatKotlin --no-daemon --console=plain; then
        echo "✅ Code formatting completed successfully"
    else
        echo "⚠️  Warning: formatKotlin failed or gradle is not available"
        echo "   Please run './gradlew formatKotlin' manually after setup completes"
        echo "   This will fix any Kotlin code formatting issues"
    fi
else
    echo "⚠️  Warning: gradlew not found, skipping automatic formatting"
    echo "   Please run './gradlew formatKotlin' manually after setup completes"
fi

# Step 10: Update README and remove template-specific files
echo "📄 Step 10: Cleaning up template files..."
if [ -f "README.md" ]; then
    # Create a minimal README for the new project
    cat > README.md << EOF
# $APPNAME

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

EOF
fi

# Remove template-specific files
echo "🗑️  Removing template-specific files..."
rm -rf .google/ 2>/dev/null || true
# Remove only template-specific GitHub Actions workflows, keep CI and release workflows
rm -f .github/workflows/test-setup-script.yml 2>/dev/null || true
rm -f CONTRIBUTING.md 2>/dev/null || true
rm -f LICENSE 2>/dev/null || true
rm -f RELEASE.md 2>/dev/null || true
# Keep keystore/ directory - it contains debug.keystore needed for release builds
# Remove only the README inside keystore/ as it's template-specific
rm -f keystore/README.md 2>/dev/null || true

# Remove renovate config (user can add back if needed)
if [ -f "renovate.json" ]; then
    echo "📋 Found renovate.json - you may want to review and configure this for your project"
fi

# Step 11: Handle git repository initialization
echo "📦 Step 11: Checking git repository status..."

# Check if git is installed
if ! command -v git &> /dev/null; then
    echo "⚠️  Warning: git is not installed or not available in PATH."
    echo "   You can initialize git manually later with: git init"
else
    # Check if git is already initialized
    if [ -d ".git" ]; then
        echo "ℹ️  Git repository already exists - preserving existing repository"
        echo "   You can review changes with: git status"
        echo "   Remember to commit your customization changes when ready"
    else
        echo "🔄 Creating new git repository for fresh start..."
        
        # Initialize git repository and handle errors
        if ! git init; then
            echo "❌ Error: Failed to initialize git repository."
            exit 1
        fi

        if ! git add .; then
            echo "❌ Error: Failed to stage files for commit."
            exit 1
        fi

        if ! git commit -m "Initial commit: $APPNAME

- Customized from android-compose-app-template
- Package: $PACKAGE  
- Circuit + Metro architecture
- WorkManager kept: $([ "$REMOVE_WORKMANAGER" = false ] && echo "true" || echo "false")"; then
            echo "❌ Error: Failed to create initial commit."
            exit 1
        fi
        
        echo "✅ New git repository initialized with initial commit"
    fi
fi

echo ""
echo "✅ Customization complete!"
echo ""
echo "🎉 Your $APPNAME is ready!"
echo "📦 Package: $PACKAGE"
echo "📁 Main class: ${APPNAME}App"
echo ""
echo "📋 Next steps:"
echo "   1. Open the project in Android Studio"
echo "   2. Update app theme colors"
echo "   3. Generate your app icon"
echo "   4. Review and update .editorconfig"
if [ "$REMOVE_WORKMANAGER" = true ]; then
    echo "   5. Add WorkManager back if you need background tasks"
fi
echo ""

# Handle script cleanup
if [ "$KEEP_SCRIPT" = false ]; then
    echo "ℹ️ Removing setup scripts..."
    rm -f setup-project.sh
    rm -f setup-android-env.sh
    echo "🚀 Happy coding!"
else
    echo "📜 Setup script kept for debugging/re-running if needed"
    # Always clean up Android environment setup script (it's not needed after project setup)
    if [ -f "setup-android-env.sh" ]; then
        echo "ℹ️ Removing Android environment setup script (no longer needed)..."
        rm -f setup-android-env.sh
    fi
    echo "🚀 Happy coding!"
    echo ""
    echo "💡 Tip: You can safely delete the setup-project.sh when you no longer need it"
fi
