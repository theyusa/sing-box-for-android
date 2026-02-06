# AGENTS.md

Guidelines and commands for agentic coding agents working on the sing-box-for-android project.

## Build Commands

```bash
# Build variants (play=api23+, other=api23+, otherLegacy=api21+)
./gradlew clean
./gradlew assembleDebug        # Builds all debug variants
./gradlew assembleRelease      # Builds all release variants
./gradlew assemblePlayDebug    # Play Store variant (API 23+)
./gradlew assembleOtherDebug   # GitHub variant (API 23+)
./gradlew assembleOtherLegacyDebug  # Legacy variant (API 21+)
./gradlew assemble             # Build all variants

# Quality
./gradlew spotlessCheck        # Check formatting (Ktlint 1.7.1)
./gradlew spotlessApply        # Apply formatting automatically
./gradlew detekt               # Run static analysis (Detekt 1.23.8)
./gradlew kspPlayDebugKotlin   # Run KSP annotation processing
./gradlew kspOtherDebugKotlin

# Dependencies
./gradlew app:dependencies
./gradlew :app:dependencies --configuration playDebugCompileClasspath
```

## Testing

```bash
./gradlew test                    # Run all unit tests
./gradlew testDebugUnitTest       # Debug variant unit tests
./gradlew testPlayDebugUnitTest   # Play variant unit tests
./gradlew testOtherDebugUnitTest  # Other variant unit tests
./gradlew connectedAndroidTest     # Instrumented tests on device
./gradlew test --tests "io.nekohasekai.sfa.database.ProfileDaoTest"  # Single test class
./gradlew test --tests "*shouldReturnProfileById"  # Specific test method
```

## Code Style

### Kotlin Conventions
- **Indentation**: 4 spaces (Spotless + Ktlint)
- **Line length**: 120 chars (Detekt), 140 (EditorConfig)
- **Trailing commas**: Required on call sites and declarations
- **Encoding**: UTF-8, LF line endings, final newlines required
- **No wildcard imports**: `import java.util.*` (except `java.util.*` excluded)

### Naming
- **Classes**: PascalCase (`ProfileManager`, `GroupsViewModel`)
- **Functions/Properties**: camelCase (`resolveDomain`, `isLoading`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- **Packages**: lowercase with dots (`io.nekohasekai.sfa.database`)
- **Composables**: PascalCase (`ProxyGroupCard`, `ServerEditDialog`)
- **Enums**: PascalCase with UPPER_SNAKE_CASE entries (`ServerSelectionMode.SELECT`)

### Imports
- Order: Standard library → Android framework → Third-party → Project
- Detekt's NoWildcardImport rule enforced
- No unused imports (auto-removed by Spotless)

### Type Safety
- Use nullable types (`String?`) when appropriate
- Prefer `?.` and `?:` over `!!` (detekt enforces this)
- Use `val` over `var`, immutable by default
- Sealed classes for restricted hierarchies (`ServerEditEvent`, `GroupsEvent`)
- Data classes over regular classes for state models

### Architecture Patterns

**MVVM with BaseViewModel**:
```kotlin
class MyViewModel : BaseViewModel<MyUiState, MyEvent>() {
    override fun createInitialState() = MyUiState()
    
    // Use updateState, never access _uiState directly
    fun doSomething() {
        updateState { copy(isLoading = true) }
    }
}
```

**State Management**:
- Use `updateState { copy(...) }` for state updates (provided by BaseViewModel)
- Access current state via `currentState` property
- Collect UI state with `by viewModel.uiState.collectAsState()`
- Emit events via `sendEvent(MyEvent.Action)` for one-time events
- Use `sendGlobalEvent(UiEvent.Error("message"))` for global UI events

**Compose UI**:
- State: `remember`, `mutableStateOf`, ViewModel
- Optimize recompositions with `derivedStateOf`, `remember` for callbacks
- Use Material3 design system components
- Add `@Preview` annotations for composable functions
- Chain modifiers properly: `Modifier.fillMaxWidth().padding(16.dp)`

**Database (Room)**:
- Use KSP compiler (`@Entity`, `@DAO`, `@Database`)
- Schema location: `app/schemas`
- DAO functions return `Flow<T>` for reactive data
- Use `@Transaction` for complex operations

**Coroutines**:
- `suspend` functions for IO operations
- `withContext(Dispatchers.IO)` for blocking calls
- `viewModelScope.launch` in ViewModels
- Avoid `GlobalScope` - use appropriate scopes

### Error Handling
- Wrap network/IO in try-catch with `runCatching` or try-catch blocks
- Use `android.util.Log` with levels:
  - `Log.d(TAG, "message")` - Debug info
  - `Log.i(TAG, "✅ $result")` - Success messages
  - `Log.w(TAG, "⚠️ $message")` - Warnings
  - `Log.e(TAG, "❌ $message", exception)` - Errors with stacktrace
- In ViewModels, use `sendError(exception)` which calls `sendErrorMessage`
- For unrecoverable errors, throw exceptions

### Material Icons
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp  // Correct
// NOT: import androidx.compose.material.icons.automirrored.filled.TouchApp

// Usage:
Icon(imageVector = Icons.Default.TouchApp, ...)
```

## Project Structure

```
app/
├── src/main/
│   ├── java/io/nekohasekai/sfa/
│   │   ├── compose/          # Compose UI components
│   │   │   ├── base/          # BaseViewModel, ScreenEvent, etc.
│   │   │   └── screen/        # Screen-specific ViewModels/Composables
│   │   ├── database/          # Room entities, DAOs, ProfileManager
│   │   ├── utils/             # Utilities, helpers
│   │   ├── bg/                # Background services
│   │   ├── ktx/               # Kotlin extensions
│   │   └── test/              # Testing utilities (not unit tests)
│   ├── minApi23/              # API 23+ specific code
│   ├── minApi21/              # API 21+ specific code
│   └── github/                # GitHub-specific code
└── libs/                      # Native libraries (libbox.aar)
```

## Development Workflow

1. `./gradlew spotlessApply` - Format code automatically
2. `./gradlew detekt` - Run static analysis
3. `./gradlew assembleOtherDebug` - Build (or your target variant)
4. Test manually on device/emulator
5. `./gradlew spotlessCheck && ./gradlew detekt` - Verify before commit

## Key Libraries

- **Compose BOM**: Version management for Compose libraries
- **Material3**: Design system (`androidx.compose.material3`)
- **Room**: Database with KSP (`@Entity`, `@DAO`, `@Database`)
- **WorkManager**: Background tasks
- **Kotlinx Serialization**: JSON (`@Serializable`, `Json.decodeFromString`)
- **Shizuku**: System integration (API 23+)
- **libsu**: ROOT package query
- **Xposed**: VPN detection bypass (compile-only)

## Quality Gates

- Build must succeed: `./gradlew assembleOtherDebug`
- Formatting must pass: `./gradlew spotlessCheck`
- No critical Detekt violations
- APK installs without crashes

## Build Flavors

| Flavor | Min SDK | Use Case |
|--------|---------|----------|
| play | 23 | Play Store with in-app updates, MLKit barcode scanning |
| other | 23 | GitHub release with Shizuku support |
| otherLegacy | 21 | GitHub release for older Android versions |

## Getting Started

1. `./gradlew assembleOtherDebug` - Verify build works
2. Study `BaseViewModel`, `GroupsViewModel`, and `ProxyGroupCard` patterns
3. Follow project naming and formatting conventions
4. Run quality gates before committing changes
5. Always test on your target flavor (usually `other` for development)

## Common Issues

**Icon import errors**: Use `Icons.Default.IconName` not `Icons.AutoMirrored.IconName`

**ViewModel state updates**: Always use `updateState { copy(...) }` never `_uiState.value = ...`

**KSP compilation failures**: Room schema changes may require `app/schemas` cleanup
