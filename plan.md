# 🎯 
```markdown
# ROLE & MINDSET

You are an expert Android/Kotlin developer with deep knowledge in:
- **Android Architecture:** MVVM, Repository Pattern, Dependency Injection (Hilt/Dagger)
- **Kotlin:** Coroutines, Flow, Sealed Classes, Data Classes, Extension Functions
- **Jetpack Compose:** State Management, Recomposition, Navigation
- **Gradle:** Build Configuration, Multi-module Projects, Flavors
- **V2Ray/Proxy Protocols:** VLESS, VMess, Shadowsocks, Trojan, sing-box config format
- **JSON Processing:** org.json.JSONObject, kotlinx.serialization
- **Go/JNI:** Understanding native library integration (Libbox)

# PROJECT CONTEXT

Project: sing-box-for-android (fork with Force Resolve feature)
Build System: Gradle 9.1.0, Kotlin 2.1.0
Architecture: Multi-module, Hilt DI, Jetpack Compose
Main Feature: Force Resolve - converts domain names to IP addresses in proxy configs

# TASK OVERVIEW

Fix Force Resolve feature bugs and complete implementation.

## COMPLETED WORK
- ✅ JSON formatting fix (toString(4) for pretty-print)
- ✅ Data Layer modules (core/data)
- ✅ Native JNI mock (core/libbox)
- ✅ Shared UI components (shared/ui)
- ✅ Subscription module (feature/subscription)
- ✅ Config Editor module (feature/config)
- ✅ Settings module (feature/settings)

## REMAINING TASKS

### HIGH PRIORITY
1. Skip Types Expansion
2. DNS Resolve Error Handling
3. Edit Profile Screen - Force Resolve toggle
4. Comprehensive Debugging

### MEDIUM PRIORITY
5. V2Ray Server Connection Test (NekoBox-style URL test)

# RULES

## Code Quality
- Follow Kotlin style guide (4-space indent, camelCase)
- Use meaningful variable/function names
- Add KDoc comments for public APIs
- Prefer immutability (val over var)
- Use scope functions appropriately (let, apply, also, run)

## Safety
- Always use null-safe operators (?., !!, ?:)
- Wrap network/IO in try-catch blocks
- Use Dispatchers.IO for blocking operations
- Handle edge cases (empty lists, null values)

## Git Commits
After EACH TODO item completion, write a SHORT commit message:
- Format: `[Module] Brief description`
- Example: `[ForceResolve] Add skip types for dns/reject/loopback`
- Max 50 characters
- Present tense

## Logging
- Use android.util.Log consistently
- Log levels: d=debug, i=info, w=warning, e=error
- Include context: Log.d("ForceResolve", "✅ $domain -> $ip")
- Use emojis for visual distinction: ✅ success, ⚠️ warning, ❌ error

# TODO LIST

## PHASE 1: Force Resolve Bug Fixes

### 1.1 Skip Types Expansion
**File:** `NewProfileViewModel.kt` (~line 345)
**File:** `UpdateProfileWork.kt` (~line 128)

**Current:**
```kotlin
if (type == "selector" || type == "urltest" || type == "direct" || type == "block") {
    continue
}
```

**Fix:**
```kotlin
val skipTypes = setOf(
    "selector", "urltest", "direct", "block",
    "dns", "reject", "blackhole", "loopback"
)
if (type in skipTypes) {
    continue
}
```

**Commit:** `[ForceResolve] Expand skip types for better config support`

---

### 1.2 DNS Resolve Logging
**File:** `NewProfileViewModel.kt` (~line 372-380)
**File:** `UpdateProfileWork.kt` (~line 155-163)

**Current:**
```kotlin
private fun resolveDomain(domain: String): String? {
    return try {
        val addresses = java.net.InetAddress.getAllByName(domain)
        addresses.firstOrNull()?.hostAddress
    } catch (e: Exception) {
        android.util.Log.e("ForceResolve", "Failed to resolve $domain", e)
        null
    }
}
```

**Fix:**
```kotlin
private fun resolveDomain(domain: String): String? {
    return try {
        val addresses = java.net.InetAddress.getAllByName(domain)
        val ip = addresses.firstOrNull()?.hostAddress
        if (ip != null) {
            android.util.Log.i(TAG, "✅ $domain -> $ip")
        } else {
            android.util.Log.w(TAG, "⚠️ No IP found for $domain")
        }
        ip
    } catch (e: Exception) {
        android.util.Log.e(TAG, "❌ DNS lookup failed: $domain (${e.message})")
        null
    }
}
```

**Commit:** `[ForceResolve] Improve DNS resolve logging with emojis`

---

## PHASE 2: Edit Profile Screen

### 2.1 Add Force Resolve Toggle to Edit Screen
**File:** `feature/profile/EditProfileScreen.kt` (or similar)

**Requirements:**
- Add Force Resolve Switch (like in NewProfileScreen.kt)
- Position: After Auto Update settings
- State binding: viewModel.forceResolve
- OnChange: viewModel.updateForceResolve(enabled)

**Reference:**
```kotlin
// From NewProfileScreen.kt
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(
        text = stringResource(R.string.profile_force_resolve),
        style = MaterialTheme.typography.bodyLarge,
    )
    Switch(
        checked = uiState.forceResolve,
        onCheckedChange = viewModel::updateForceResolve,
    )
}
```

**Commit:** `[EditProfile] Add Force Resolve toggle UI`

---

### 2.2 Edit Profile ViewModel Logic
**File:** `EditProfileViewModel.kt`

**Add:**
1. forceResolve field to UiState
2. updateForceResolve(Boolean) function
3. Apply Force Resolve in updateRemoteProfile() when user presses "Update Now"

**Example:**
```kotlin
data class EditProfileUiState(
    val forceResolve: Boolean = false,
    // ... other fields
)

fun updateForceResolve(enabled: Boolean) {
    _uiState.update { it.copy(forceResolve = enabled) }
}

suspend fun updateRemoteProfile() {
    val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
    val finalContent = if (_uiState.value.forceResolve) {
        resolveDomainToIP(content)
    } else {
        content
    }
    // Save finalContent...
}
```

**Commit:** `[EditProfile] Add Force Resolve logic to ViewModel`

---

## PHASE 3: Comprehensive Debugging

### 3.1 Build & Compile Check
```bash
./gradlew clean
./gradlew compileDebugKotlin 2>&1 | tee compile.log
```

**Check for:**
- Unresolved reference
- Type mismatch
- Missing imports
- Hilt binding errors

**Commit:** `[Build] Fix compilation errors in [module]`

---

### 3.2 Module-by-Module Validation

**For each module (core/data, feature/subscription, etc):**
1. Check imports
2. Verify Hilt annotations (@Module, @Provides, @HiltViewModel)
3. Validate Room entities/DAOs
4. Test Compose previews

**Commit format:** `[ModuleName] Fix [specific issue]`

---

### 3.3 Final Integration Test
```bash
./gradlew assembleDebug
```

**Success criteria:** BUILD SUCCESSFUL, 0 errors

**Commit:** `[Release] Final integration fixes complete`

---

## PHASE 4: V2Ray Connection Test (Future)

### 4.1 Research NekoBox Implementation
- [ ] Analyze speedtest.go
- [ ] Find Libbox API for outbound testing
- [ ] Design Kotlin wrapper

**Commit:** `[Research] NekoBox URL test analysis complete`

---

### 4.2 Implement ServerTester
**File:** `core/network/ServerTester.kt` (new)

```kotlin
suspend fun testServer(outbound: Outbound): TestResult {
    return withContext(Dispatchers.IO) {
        try {
            // 1. Create temp proxy connection
            // 2. HTTP GET to https://www.gstatic.com/generate_204
            // 3. Check 204 response
            TestResult.Success
        } catch (e: Exception) {
            TestResult.Failed(e.message)
        }
    }
}
```

**Commit:** `[ServerTest] Implement basic server connectivity test`

---

# EXECUTION PLAN

## STEP 1: Scan Project Structure
```bash
cd /path/to/sing-box-for-android
find . -name "NewProfileViewModel.kt"
find . -name "UpdateProfileWork.kt"
find . -name "EditProfile*.kt"
```

## STEP 2: Apply Fixes (Phase 1)
- Open NewProfileViewModel.kt
- Apply skip types fix
- Apply DNS logging fix
- Save
- Repeat for UpdateProfileWork.kt
- **Commit after each file**

## STEP 3: Edit Profile (Phase 2)
- Locate EditProfileScreen.kt and EditProfileViewModel.kt
- Add Force Resolve UI
- Add Force Resolve logic
- **Commit after each component**

## STEP 4: Debug (Phase 3)
- Run compile
- Fix errors one by one
- **Commit after each fix**

## STEP 5: Verify
```bash
./gradlew assembleDebug
# Expected: BUILD SUCCESSFUL
```

# OUTPUT FORMAT

For each TODO:

```
✅ TODO: [Description]
FILE: path/to/File.kt
LINES: 123-145

CHANGES:
- Added skip types set
- Improved logging

COMMIT: [ForceResolve] Add skip types for dns/reject/loopback

STATUS: ✅ Complete / ⏳ In Progress / ❌ Blocked
```

# SUCCESS CRITERIA

- [ ] All Phase 1 fixes applied
- [ ] Edit Profile has Force Resolve toggle
- [ ] Build succeeds with 0 errors
- [ ] Force Resolve works end-to-end (create + edit + update)
- [ ] Clean commit history with descriptive messages

# BEGIN EXECUTION

Start with PHASE 1, TODO 1.1 - Skip Types Expansion.
Work systematically through each TODO.
Commit after EVERY completed task.
Report progress after each commit.

GO!
```

