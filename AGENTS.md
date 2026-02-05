# AGENTS.md

Guidelines and commands for agentic coding agents working on the sing-box-for-android project.

## Build Commands

```bash
# Build
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew assemble           # Build all variants

# Quality
./gradlew spotlessCheck      # Check formatting
./gradlew spotlessApply      # Apply formatting
./gradlew detekt             # Run static analysis

# Dependencies
./gradlew app:dependencies
```

## Testing

```bash
./gradlew test               # Run all tests
./gradlew testDebugUnitTest  # Debug unit tests
./gradlew connectedAndroidTest # Instrumented tests
./gradlew test --tests "com.example.TestClass" # Single test
```

## Code Style

### Kotlin Conventions
- **Indentation**: 4 spaces (Spotless + Ktlint)
- **Line length**: 120 chars (Detekt), 140 (EditorConfig)
- **Trailing commas**: Required
- **Encoding**: UTF-8, LF line endings, final newlines required

### Naming
- **Classes**: PascalCase (`ProfileManager`)
- **Functions/Properties**: camelCase (`resolveDomain`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- **Packages**: lowercase with dots (`io.nekohasekai.sfa.database`)

### Imports
- Standard library → Android framework → Third-party → Project
- No unused imports (Detekt)
- Avoid wildcard imports (`*`)

### Type Safety
- Use nullable types (`String?`) when appropriate
- Prefer `?.` and `?:` over `!!`
- Use `val` over `var`, immutable by default
- Sealed classes for restricted hierarchies
- Data classes over regular classes

### Architecture
- **MVVM**: ViewModels with Compose UI
- **Repository Pattern**: Centralize data access
- **Hilt**: `@Inject`, `@Module`, `@Provides`
- **Coroutines**: `suspend` functions, `withContext(Dispatchers.IO)`
- **Flow**: Prefer over LiveData
- **Room**: KSP compiler, DAO patterns

### Error Handling
- Wrap network/IO in try-catch
- Use `android.util.Log` with levels:
  - `Log.d(TAG, "message")`
  - `Log.i(TAG, "✅ $result")`
  - `Log.w(TAG, "⚠️ $message")`
  - `Log.e(TAG, "❌ $message", exception)`
- Throw exceptions for unrecoverable errors

### Compose
- State: `remember`, `mutableStateOf`, `ViewModel`
- Optimize with `derivedStateOf`
- Use Material3 design system
- Add `@Preview` annotations
- Chain modifiers properly

## Project Structure

```
app/                    # Main application
io.nekohasekai.sfa.database  # Room entities/DAOs
io.nekohasekai.sfa.compose   # Compose UI
io.nekohasekai.sfa.utils     # Utilities
io.nekohasekai.sfa.bg        # Background services
io.nekohasekai.sfa.ktx       # Kotlin extensions
```

## Development Workflow

1. `./gradlew spotlessApply` - Format code
2. `./gradlew detekt` - Run analysis
3. `./gradlew assembleDebug` - Build
4. Test manually

## Key Libraries

- **Compose BOM**: Version management
- **Material3**: Design system
- **Room**: Database with KSP (`@Entity`, `@DAO`, `@Database`)
- **WorkManager**: Background tasks
- **Kotlinx Serialization**: JSON (`@Serializable`)
- **Shizuku**: System integration (API 23+)
- **Xposed**: VPN detection bypass

## Quality Gates

- Build must succeed: `./gradlew assembleDebug`
- Formatting must pass: `./gradlew spotlessCheck`
- No critical Detekt violations
- APK installs without crashes

## Getting Started

1. `./gradlew assembleDebug` - Verify build works
2. Study existing ViewModel, Compose, and Room patterns
3. Follow project conventions
4. Run quality gates before committing

This project uses comprehensive linting and formatting. Always run quality checks before submitting changes.