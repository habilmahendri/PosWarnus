# POS Foundation, Auth & Navigation Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the app's foundation — DI, Supabase client wiring, session-persisted email/password auth, and the Splash → Login → Dashboard navigation shell — as a working, testable vertical slice that every later PRD feature builds on.

**Architecture:** Hilt for DI; MVVM (Compose `ViewModel` + `StateFlow`) for each screen; a thin `AuthGateway` interface isolates the real `supabase-kt` Auth client behind a fakeable boundary so `AuthRepository`/`ViewModel` logic is unit-testable without hitting the network; a dedicated `SessionStore` (Jetpack DataStore Preferences) persists the serialized session across process death, independent of whatever internal persistence `supabase-kt` ships with, so this app controls exactly when a session is considered valid.

**Tech Stack:** Kotlin, Jetpack Compose + Navigation Compose, Hilt (+ KSP), `io.github.jan-tennert.supabase` (`auth-kt`, `postgrest-kt` via BOM) with the Ktor OkHttp engine, `androidx.datastore:datastore-preferences`, `kotlinx-coroutines-test`, JUnit4.

**Spec:** [docs/PRD-pos.md](../../PRD-pos.md) — this plan implements PRD sections 4.1 (Splashscreen) and 4.2 (Login), plus the project-wide foundation (DI, Supabase client, navigation shell) that sections 4.3–4.11 depend on.

**Scope note:** The PRD covers 11 features including offline-first sync, a configurable modifier/variant catalog, tax, cash payment, Bluetooth printing, and transaction history. That is too much for one implementation plan to stay reviewable. This plan is **Phase 1 of a phased rollout** — it delivers a complete, demoable Splash → Login → Dashboard(placeholder) → Logout flow. Each later PRD feature area gets its own plan once this one lands:
- Phase 2: Local cache (Room) + Catalog/Dashboard (PRD 4.3)
- Phase 3: Menu Detail Dialog + Cart (PRD 4.4, 4.5)
- Phase 4: Tax config + Payment + Success (PRD 4.6–4.8)
- Phase 5: Offline sync engine (PRD 4.9) — note: some of this (local-first transaction write, idempotent sync) is foundational enough it may need to land before Phase 4 ships to production; revisit sequencing once Phase 2/3 are built.
- Phase 6: Bluetooth printing (PRD 4.10)
- Phase 7: Transaction history (PRD 4.11)

## Global Constraints

- Package/namespace: `com.example.poswarnus` (PRD §1, existing project).
- `minSdk 24`, `compileSdk`/`targetSdk 36` (existing `app/build.gradle.kts`).
- Login is email + password against Supabase Auth; no self-registration UI in this app (PRD 4.2, Non-Goals).
- Session persists across app restarts until explicit logout or invalid/expired token (PRD 4.2 acceptance criteria).
- Splash always shows for a fixed duration then navigates to the Login destination unconditionally; it performs no network/session check itself (PRD 4.1). The Login destination is what decides, on entry, whether to show the form or skip straight to Dashboard.
- Splash fixed duration: 1500ms (PRD specifies "~1–2s"; this plan pins the exact value since the spec left it a range).
- Supabase project URL and anon/publishable key are never hardcoded in source; they are read from Gradle (`local.properties` → `BuildConfig` fields), consistent with this repo's existing secret-handling via `local.properties` (already gitignored for `sdk.dir`).
- `AndroidManifest.xml` must declare the `INTERNET` permission (required by the Supabase/Ktor client).

## Review Focus

- Blank/empty email or password submitted via the login form — show inline validation error, make zero network calls.
- Supabase rejects the credentials (wrong password) or the request fails (network error) — inline error shown, loading state cleared, no navigation.
- App relaunched with a still-valid persisted session — Login destination skips the form and navigates straight to Dashboard.
- App relaunched with an expired/invalid persisted session (or none at all) — Login destination renders the form normally, no crash or redirect loop.
- Login button double-tapped before the first request resolves — only one login call fires; the button is disabled while loading.

---

### Task 1: Project foundation — Hilt, dependencies, manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts` (root, to register the Hilt/KSP plugins)
- Create: `app/src/main/java/com/example/poswarnus/PosWarNusApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `PosWarNusApplication` (Hilt entry point, referenced by `AndroidManifest.xml`'s `android:name`) — later tasks' `@HiltAndroidApp`-dependent code (Hilt modules, `@AndroidEntryPoint` activities) rely on this existing.

- [ ] **Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add under `[versions]`: `hilt`, `ksp` (pin to the Kotlin version's matching KSP release), `supabaseBom`, `ktorClientOkhttp`, `kotlinxSerializationJson`, `navigationCompose`, `hiltNavigationCompose`, `datastorePreferences`, `kotlinxCoroutinesTest`. Add under `[libraries]`: `hilt-android`, `hilt-compiler`, `supabase-bom` (platform), `supabase-auth`, `supabase-postgrest`, `ktor-client-okhttp`, `kotlinx-serialization-json`, `androidx-navigation-compose`, `androidx-hilt-navigation-compose`, `androidx-datastore-preferences`, `kotlinx-coroutines-test`. Add under `[plugins]`: `hilt-android`, `kotlin-ksp`, `kotlin-serialization`.

- [ ] **Step 2: Apply plugins and dependencies**

In root `build.gradle.kts`, add `alias(libs.plugins.hilt.android) apply false`, `alias(libs.plugins.kotlin.ksp) apply false`, `alias(libs.plugins.kotlin.serialization) apply false` to the existing `plugins {}` block. In `app/build.gradle.kts`, apply `libs.plugins.hilt.android`, `libs.plugins.kotlin.ksp`, `libs.plugins.kotlin.serialization`; add `implementation(platform(libs.supabase.bom))`, `implementation(libs.supabase.auth)`, `implementation(libs.supabase.postgrest)`, `implementation(libs.ktor.client.okhttp)`, `implementation(libs.kotlinx.serialization.json)`, `implementation(libs.androidx.navigation.compose)`, `implementation(libs.androidx.hilt.navigation.compose)`, `implementation(libs.androidx.datastore.preferences)`, `implementation(libs.hilt.android)`, `ksp(libs.hilt.compiler)`; add `testImplementation(libs.kotlinx.coroutines.test)`. Add `buildFeatures { buildConfig = true }` inside the `android {}` block (needed for Task 2's `BuildConfig` fields).

- [ ] **Step 3: Create `PosWarNusApplication`**

```kotlin
package com.example.poswarnus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PosWarNusApplication : Application()
```

- [ ] **Step 4: Wire the manifest**

In `app/src/main/AndroidManifest.xml`, add `<uses-permission android:name="android.permission.INTERNET" />` above the `<application>` tag, and set `android:name=".PosWarNusApplication"` on the `<application>` tag.

- [ ] **Step 5: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (no source beyond `PosWarNusApplication` and the existing template exists yet, so this only proves the new plugins/dependencies resolve and KSP/Hilt annotation processing runs cleanly).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts build.gradle.kts \
  app/src/main/java/com/example/poswarnus/PosWarNusApplication.kt \
  app/src/main/AndroidManifest.xml
git commit -m "feat: add Hilt, Supabase, DataStore and Navigation Compose dependencies"
```

---

### Task 2: Supabase configuration & client DI module

**Files:**
- Modify: `app/build.gradle.kts` (BuildConfig fields)
- Create: `app/src/main/java/com/example/poswarnus/data/remote/SupabaseConfig.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/SupabaseModule.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/remote/SupabaseConfigTest.kt`

**Interfaces:**
- Consumes: nothing beyond Task 1's build setup.
- Produces: `SupabaseConfig(url: String, anonKey: String)` with `fun SupabaseConfig.requireValid(): SupabaseConfig` (throws `IllegalStateException` if either field is blank) — Task 2's own DI module uses it; no later task needs to construct it directly (they inject `SupabaseClient` instead). `SupabaseModule` provides a `@Singleton SupabaseClient` with `Auth` and `Postgrest` installed — Task 4's `SupabaseAuthGateway` consumes this `SupabaseClient`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.poswarnus.data.remote

import org.junit.Assert.assertThrows
import org.junit.Test

class SupabaseConfigTest {
    @Test
    fun `requireValid returns the same config when url and anonKey are non-blank`() {
        val config = SupabaseConfig(url = "https://example.supabase.co", anonKey = "key123")
        assertEquals(config, config.requireValid())
    }

    @Test
    fun `requireValid throws when url is blank`() {
        assertThrows(IllegalStateException::class.java) {
            SupabaseConfig(url = "", anonKey = "key123").requireValid()
        }
    }

    @Test
    fun `requireValid throws when anonKey is blank`() {
        assertThrows(IllegalStateException::class.java) {
            SupabaseConfig(url = "https://example.supabase.co", anonKey = "").requireValid()
        }
    }
}
```

(add `import org.junit.Assert.assertEquals`)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.remote.SupabaseConfigTest"`
Expected: FAIL — `SupabaseConfig` / `requireValid` unresolved.

- [ ] **Step 3: Implement `SupabaseConfig`**

```kotlin
package com.example.poswarnus.data.remote

data class SupabaseConfig(val url: String, val anonKey: String)

fun SupabaseConfig.requireValid(): SupabaseConfig {
    check(url.isNotBlank()) { "Supabase URL is missing. Set SUPABASE_URL in local.properties." }
    check(anonKey.isNotBlank()) { "Supabase anon key is missing. Set SUPABASE_ANON_KEY in local.properties." }
    return this
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.remote.SupabaseConfigTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Read Supabase credentials into `BuildConfig`**

In `app/build.gradle.kts`, inside `android { defaultConfig { ... } }`, read `SUPABASE_URL` and `SUPABASE_ANON_KEY` from the project's `local.properties` (via a `java.util.Properties` load, the same pattern Android Studio already uses for `sdk.dir`) and emit them with `buildConfigField("String", "SUPABASE_URL", "\"$value\"")` / `buildConfigField("String", "SUPABASE_ANON_KEY", "\"$value\"")`, defaulting to empty strings when the properties are absent so a fresh checkout still builds (it will fail fast at runtime via `requireValid()` instead).

- [ ] **Step 6: Implement `SupabaseModule`**

```kotlin
package com.example.poswarnus.di

import com.example.poswarnus.BuildConfig
import com.example.poswarnus.data.remote.SupabaseConfig
import com.example.poswarnus.data.remote.requireValid
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val config = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY).requireValid()
        return createSupabaseClient(config.url, config.anonKey) {
            install(Auth)
            install(Postgrest)
        }
    }
}
```

- [ ] **Step 7: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts \
  app/src/main/java/com/example/poswarnus/data/remote/SupabaseConfig.kt \
  app/src/main/java/com/example/poswarnus/di/SupabaseModule.kt \
  app/src/test/java/com/example/poswarnus/data/remote/SupabaseConfigTest.kt
git commit -m "feat: add Supabase client DI module with config validation"
```

---

### Task 3: Session persistence (`SessionStore`)

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/auth/SessionStore.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/DataStoreModule.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/auth/DataStoreSessionStoreTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `interface SessionStore { suspend fun save(sessionJson: String?); suspend fun load(): String? }` and `class DataStoreSessionStore(private val dataStore: DataStore<Preferences>) : SessionStore` — Task 4's `AuthRepositoryImpl` consumes `SessionStore` (injected via `DataStoreModule`'s `@Singleton SessionStore` binding).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.poswarnus.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSessionStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): DataStoreSessionStore {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("session_test.preferences_pb") }
        )
        return DataStoreSessionStore(dataStore)
    }

    @Test
    fun `load returns null when nothing has been saved`() = runTest {
        assertNull(newStore().load())
    }

    @Test
    fun `save then load returns the same session json`() = runTest {
        val store = newStore()
        store.save("""{"access_token":"abc"}""")
        assertEquals("""{"access_token":"abc"}""", store.load())
    }

    @Test
    fun `saving null clears the stored session`() = runTest {
        val store = newStore()
        store.save("""{"access_token":"abc"}""")
        store.save(null)
        assertNull(store.load())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.auth.DataStoreSessionStoreTest"`
Expected: FAIL — `DataStoreSessionStore` unresolved.

- [ ] **Step 3: Implement `SessionStore` and `DataStoreSessionStore`**

```kotlin
package com.example.poswarnus.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

interface SessionStore {
    suspend fun save(sessionJson: String?)
    suspend fun load(): String?
}

class DataStoreSessionStore(private val dataStore: DataStore<Preferences>) : SessionStore {
    private val key = stringPreferencesKey("session_json")

    override suspend fun save(sessionJson: String?) {
        dataStore.edit { prefs ->
            if (sessionJson == null) prefs.remove(key) else prefs[key] = sessionJson
        }
    }

    override suspend fun load(): String? = dataStore.data.first()[key]
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.auth.DataStoreSessionStoreTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Wire the real Android `DataStore` via Hilt**

```kotlin
package com.example.poswarnus.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.poswarnus.data.auth.DataStoreSessionStore
import com.example.poswarnus.data.auth.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "session")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        DataStoreSessionStore(context.sessionDataStore)
}
```

- [ ] **Step 6: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/auth/SessionStore.kt \
  app/src/main/java/com/example/poswarnus/di/DataStoreModule.kt \
  app/src/test/java/com/example/poswarnus/data/auth/DataStoreSessionStoreTest.kt
git commit -m "feat: add DataStore-backed session persistence"
```

---

### Task 4: `AuthGateway` + `AuthRepository`

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/auth/AuthGateway.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/auth/SupabaseAuthGateway.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/auth/AuthRepository.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/AuthModule.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/auth/AuthRepositoryTest.kt`

**Interfaces:**
- Consumes: `SessionStore` (Task 3).
- Produces: `interface AuthGateway { suspend fun signIn(email: String, password: String): String; suspend fun signOut() }` (`signIn` returns the serialized session JSON on success, throws on failure — exact exception type is whatever `supabase-kt` throws; `AuthRepositoryImpl` only needs "throws on failure"). `interface AuthRepository { suspend fun login(email: String, password: String): AuthResult; suspend fun logout(); suspend fun isLoggedIn(): Boolean }` with `sealed interface AuthResult { data object Success : AuthResult; data class Failure(val message: String) : AuthResult }` — Task 5's `LoginViewModel` consumes `AuthRepository` and `AuthResult`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.poswarnus.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAuthGateway(
    private val signInResult: Result<String> = Result.success("""{"access_token":"abc"}"""),
) : AuthGateway {
    var signInCallCount = 0
        private set
    var signOutCalled = false
        private set

    override suspend fun signIn(email: String, password: String): String {
        signInCallCount++
        return signInResult.getOrThrow()
    }

    override suspend fun signOut() {
        signOutCalled = true
    }
}

private class InMemorySessionStore(initial: String? = null) : SessionStore {
    var current: String? = initial
    override suspend fun save(sessionJson: String?) { current = sessionJson }
    override suspend fun load(): String? = current
}

class AuthRepositoryTest {
    @Test
    fun `login success persists the session and returns Success`() = runTest {
        val sessionStore = InMemorySessionStore()
        val repository = AuthRepositoryImpl(FakeAuthGateway(), sessionStore)

        val result = repository.login("cashier@warnus.test", "correct-password")

        assertEquals(AuthResult.Success, result)
        assertEquals("""{"access_token":"abc"}""", sessionStore.current)
    }

    @Test
    fun `login failure does not persist a session and returns Failure`() = runTest {
        val sessionStore = InMemorySessionStore()
        val gateway = FakeAuthGateway(signInResult = Result.failure(RuntimeException("Invalid login credentials")))
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        val result = repository.login("cashier@warnus.test", "wrong-password")

        assertTrue(result is AuthResult.Failure)
        assertEquals(null, sessionStore.current)
    }

    @Test
    fun `logout clears the persisted session`() = runTest {
        val sessionStore = InMemorySessionStore(initial = """{"access_token":"abc"}""")
        val gateway = FakeAuthGateway()
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        repository.logout()

        assertTrue(gateway.signOutCalled)
        assertEquals(null, sessionStore.current)
    }

    @Test
    fun `isLoggedIn reflects whether a session is persisted`() = runTest {
        val repository = AuthRepositoryImpl(FakeAuthGateway(), InMemorySessionStore(initial = null))
        assertFalse(repository.isLoggedIn())

        val repositoryWithSession = AuthRepositoryImpl(
            FakeAuthGateway(),
            InMemorySessionStore(initial = """{"access_token":"abc"}"""),
        )
        assertTrue(repositoryWithSession.isLoggedIn())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.auth.AuthRepositoryTest"`
Expected: FAIL — `AuthGateway`, `AuthRepositoryImpl`, `AuthResult` unresolved.

- [ ] **Step 3: Implement `AuthGateway` and `AuthResult`/`AuthRepository`**

```kotlin
package com.example.poswarnus.data.auth

interface AuthGateway {
    suspend fun signIn(email: String, password: String): String
    suspend fun signOut()
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String) : AuthResult
}

interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun logout()
    suspend fun isLoggedIn(): Boolean
}

class AuthRepositoryImpl(
    private val gateway: AuthGateway,
    private val sessionStore: SessionStore,
) : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult =
        try {
            val sessionJson = gateway.signIn(email, password)
            sessionStore.save(sessionJson)
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Login failed")
        }

    override suspend fun logout() {
        gateway.signOut()
        sessionStore.save(null)
    }

    override suspend fun isLoggedIn(): Boolean = sessionStore.load() != null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.auth.AuthRepositoryTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Implement the real `SupabaseAuthGateway`**

```kotlin
package com.example.poswarnus.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.Json

class SupabaseAuthGateway(private val client: SupabaseClient) : AuthGateway {
    override suspend fun signIn(email: String, password: String): String {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val session = requireNotNull(client.auth.currentSessionOrNull()) {
            "Supabase reported a successful sign-in but returned no session"
        }
        return Json.encodeToString(session)
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }
}
```

One line on an open choice: if the installed `auth-kt` version's `UserSession` is not directly `@Serializable`-friendly via plain `Json.encodeToString(session)`, use the SDK's own session-to-string helper if one is exposed (check `io.github.jan.supabase.auth.auth` package docs for the installed version); the stored string only needs to be an opaque blob `SessionStore` round-trips, not a format other code parses.

- [ ] **Step 6: Wire `AuthModule`**

```kotlin
package com.example.poswarnus.di

import com.example.poswarnus.data.auth.AuthGateway
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthRepositoryImpl
import com.example.poswarnus.data.auth.SessionStore
import com.example.poswarnus.data.auth.SupabaseAuthGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideAuthGateway(client: SupabaseClient): AuthGateway = SupabaseAuthGateway(client)

    @Provides
    @Singleton
    fun provideAuthRepository(gateway: AuthGateway, sessionStore: SessionStore): AuthRepository =
        AuthRepositoryImpl(gateway, sessionStore)
}
```

- [ ] **Step 7: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/auth/ app/src/main/java/com/example/poswarnus/di/AuthModule.kt \
  app/src/test/java/com/example/poswarnus/data/auth/AuthRepositoryTest.kt
git commit -m "feat: add AuthGateway/AuthRepository with Supabase-backed implementation"
```

---

### Task 5: `LoginViewModel`

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/login/LoginViewModel.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/login/LoginViewModelTest.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `AuthResult` (Task 4).
- Produces: `data class LoginUiState(val email: String = "", val password: String = "", val isLoading: Boolean = false, val errorMessage: String? = null)`, `sealed interface LoginEvent { data object NavigateToDashboard : LoginEvent }`, and `class LoginViewModel(private val authRepository: AuthRepository) : ViewModel()` with `val uiState: StateFlow<LoginUiState>`, `val events: SharedFlow<LoginEvent>`, `fun onEmailChanged(value: String)`, `fun onPasswordChanged(value: String)`, `fun onLoginClicked()` — Task 6's `LoginScreen` consumes all of these.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.example.poswarnus.ui.login

import app.cash.turbine.test
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeAuthRepository(
    var loginResult: AuthResult = AuthResult.Success,
    var isLoggedInResult: Boolean = false,
) : AuthRepository {
    var loginCallCount = 0
        private set

    override suspend fun login(email: String, password: String): AuthResult {
        loginCallCount++
        return loginResult
    }

    override suspend fun logout() {}
    override suspend fun isLoggedIn(): Boolean = isLoggedInResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `blank email or password shows validation error without calling repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)

        viewModel.onPasswordChanged("something")
        viewModel.onLoginClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.loginCallCount)
        assertEquals("Email and password are required", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `successful login emits NavigateToDashboard and clears loading`() = runTest {
        val repository = FakeAuthRepository(loginResult = AuthResult.Success)
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("correct-password")

        viewModel.events.test {
            viewModel.onLoginClicked()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoginEvent.NavigateToDashboard, awaitItem())
        }
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed login shows error message and clears loading`() = runTest {
        val repository = FakeAuthRepository(loginResult = AuthResult.Failure("Invalid login credentials"))
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("wrong-password")

        viewModel.onLoginClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Invalid login credentials", viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `double-tapping login while loading only triggers one repository call`() = runTest {
        val repository = FakeAuthRepository(loginResult = AuthResult.Success)
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("correct-password")

        viewModel.onLoginClicked()
        viewModel.onLoginClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.loginCallCount)
    }

    @Test
    fun `init checks isLoggedIn and emits NavigateToDashboard immediately when already authenticated`() = runTest {
        val repository = FakeAuthRepository(isLoggedInResult = true)
        val viewModel = LoginViewModel(repository)

        viewModel.events.test {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoginEvent.NavigateToDashboard, awaitItem())
        }
    }

    @Test
    fun `init does not emit NavigateToDashboard when no session is persisted`() = runTest {
        val repository = FakeAuthRepository(isLoggedInResult = false)
        val viewModel = LoginViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
```

Add `testImplementation("app.cash.turbine:turbine:<latest stable>")` to `app/build.gradle.kts` (and a matching `turbine` entry in `gradle/libs.versions.toml`) for the `events.test { }` Flow-testing helper used above — fold this dependency addition into this task's Step 1 commit rather than Task 1, since this is the first test that needs it.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.login.LoginViewModelTest"`
Expected: FAIL — `LoginViewModel`, `LoginUiState`, `LoginEvent` unresolved.

- [ ] **Step 3: Implement `LoginViewModel`**

```kotlin
package com.example.poswarnus.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface LoginEvent {
    data object NavigateToDashboard : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) _events.emit(LoginEvent.NavigateToDashboard)
        }
    }

    fun onEmailChanged(value: String) { _uiState.value = _uiState.value.copy(email = value, errorMessage = null) }
    fun onPasswordChanged(value: String) { _uiState.value = _uiState.value.copy(password = value, errorMessage = null) }

    fun onLoginClicked() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Email and password are required")
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authRepository.login(state.email, state.password)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(LoginEvent.NavigateToDashboard)
                }
                is AuthResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.login.LoginViewModelTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml \
  app/src/main/java/com/example/poswarnus/ui/login/LoginViewModel.kt \
  app/src/test/java/com/example/poswarnus/ui/login/LoginViewModelTest.kt
git commit -m "feat: add LoginViewModel with validation, error handling and session short-circuit"
```

---

### Task 6: `LoginScreen` composable

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/login/LoginScreen.kt`
- Test: `app/src/androidTest/java/com/example/poswarnus/ui/login/LoginScreenTest.kt`

**Interfaces:**
- Consumes: `LoginViewModel`, `LoginUiState`, `LoginEvent` (Task 5).
- Produces: `@Composable fun LoginScreen(onNavigateToDashboard: () -> Unit, viewModel: LoginViewModel = hiltViewModel())` — Task 7's nav graph consumes this as the Login destination's content.

- [ ] **Step 1: Write the failing Compose UI test**

```kotlin
package com.example.poswarnus.ui.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickingLoginWithEmptyFieldsShowsValidationError() {
        // Uses a real LoginViewModel backed by a repository whose isLoggedIn() is false and
        // login() is never expected to be called for this case, so any AuthRepository stub works.
        composeTestRule.setContent {
            LoginScreen(onNavigateToDashboard = {}, viewModel = LoginViewModel(NeverCalledAuthRepository()))
        }

        composeTestRule.onNodeWithText("Login").performClick()

        composeTestRule.onNodeWithText("Email and password are required").assertExists()
    }

    @Test
    fun enteringCredentialsAndLoggingInNavigatesOnSuccess() {
        var navigated = false
        composeTestRule.setContent {
            LoginScreen(
                onNavigateToDashboard = { navigated = true },
                viewModel = LoginViewModel(AlwaysSucceedsAuthRepository()),
            )
        }

        composeTestRule.onNodeWithText("Email").performTextInput("cashier@warnus.test")
        composeTestRule.onNodeWithText("Password").performTextInput("correct-password")
        composeTestRule.onNodeWithText("Login").performClick()
        composeTestRule.waitForIdle()

        assert(navigated)
    }
}
```

Define `NeverCalledAuthRepository` and `AlwaysSucceedsAuthRepository` as small private test-only `AuthRepository` implementations in the same file (mirroring the fakes already written in Task 5's unit test, but local to this androidTest source set since unit-test and androidTest source sets don't share classes by default).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest --tests "com.example.poswarnus.ui.login.LoginScreenTest"`
Expected: FAIL — `LoginScreen` unresolved. (Requires a connected device/emulator; if none is available, skip straight to Step 3 and verify manually per Task 8.)

- [ ] **Step 3: Implement `LoginScreen`**

```kotlin
package com.example.poswarnus.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is LoginEvent.NavigateToDashboard) onNavigateToDashboard()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        OutlinedTextField(value = state.email, onValueChange = viewModel::onEmailChanged, label = { Text("Email") })
        OutlinedTextField(value = state.password, onValueChange = viewModel::onPasswordChanged, label = { Text("Password") })
        state.errorMessage?.let { Text(it) }
        Button(onClick = viewModel::onLoginClicked, enabled = !state.isLoading) {
            if (state.isLoading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text("Login")
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest --tests "com.example.poswarnus.ui.login.LoginScreenTest"`
Expected: PASS (2 tests) on a connected device/emulator; otherwise defer to Task 8's manual verification.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/login/LoginScreen.kt \
  app/src/androidTest/java/com/example/poswarnus/ui/login/LoginScreenTest.kt
git commit -m "feat: add LoginScreen composable"
```

---

### Task 7: `SplashViewModel`, `SplashScreen`, `DashboardScreen` placeholder, and nav graph

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/splash/SplashViewModel.kt`
- Create: `app/src/main/java/com/example/poswarnus/ui/splash/SplashScreen.kt`
- Create: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt`
- Create: `app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt`
- Modify: `app/src/main/java/com/example/poswarnus/MainActivity.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/splash/SplashViewModelTest.kt`

**Interfaces:**
- Consumes: `LoginScreen` (Task 6), `AuthRepository` (Task 4, injected into `DashboardScreen`'s own small inline ViewModel for logout).
- Produces: `object PosDestinations { const val SPLASH = "splash"; const val LOGIN = "login"; const val DASHBOARD = "dashboard" }` and `@Composable fun PosNavGraph(navController: NavHostController = rememberNavController())` — this is the sole composable `MainActivity` calls; no later task needs anything else from this task's files except these route constants (Phase 2+ add their own destinations to this same graph).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.poswarnus.ui.splash

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashViewModelTest {
    @Test
    fun `emits NavigateToLogin once the delay provider completes`() = runTest {
        var delayInvoked = false
        val viewModel = SplashViewModel(delayProvider = { delayInvoked = true })

        var emitted: SplashEvent? = null
        val job = kotlinx.coroutines.launch { emitted = viewModel.events.first() }
        viewModel.start()
        job.join()

        assertEquals(true, delayInvoked)
        assertEquals(SplashEvent.NavigateToLogin, emitted)
    }
}
```

(add `import kotlinx.coroutines.flow.first`, `import kotlinx.coroutines.launch`)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.splash.SplashViewModelTest"`
Expected: FAIL — `SplashViewModel`, `SplashEvent` unresolved.

- [ ] **Step 3: Implement `SplashViewModel`**

```kotlin
package com.example.poswarnus.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashEvent {
    data object NavigateToLogin : SplashEvent
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val delayProvider: suspend () -> Unit = { delay(1500) },
) : ViewModel() {
    private val _events = MutableSharedFlow<SplashEvent>()
    val events: SharedFlow<SplashEvent> = _events.asSharedFlow()

    fun start() {
        viewModelScope.launch {
            delayProvider()
            _events.emit(SplashEvent.NavigateToLogin)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.splash.SplashViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Implement `SplashScreen`**

```kotlin
package com.example.poswarnus.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.start()
        viewModel.events.collect { event ->
            if (event is SplashEvent.NavigateToLogin) onNavigateToLogin()
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("POS WarNus")
    }
}
```

- [ ] **Step 6: Implement the `DashboardScreen` placeholder**

```kotlin
package com.example.poswarnus.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}

@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Dashboard (catalog coming in Phase 2)")
        Button(onClick = { viewModel.logout(onLoggedOut) }) { Text("Logout") }
    }
}
```

- [ ] **Step 7: Implement `PosNavGraph` and wire `MainActivity`**

```kotlin
package com.example.poswarnus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.poswarnus.ui.dashboard.DashboardScreen
import com.example.poswarnus.ui.login.LoginScreen
import com.example.poswarnus.ui.splash.SplashScreen

object PosDestinations {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
}

@Composable
fun PosNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = PosDestinations.SPLASH) {
        composable(PosDestinations.SPLASH) {
            SplashScreen(onNavigateToLogin = {
                navController.navigate(PosDestinations.LOGIN) {
                    popUpTo(PosDestinations.SPLASH) { inclusive = true }
                }
            })
        }
        composable(PosDestinations.LOGIN) {
            LoginScreen(onNavigateToDashboard = {
                navController.navigate(PosDestinations.DASHBOARD) {
                    popUpTo(PosDestinations.LOGIN) { inclusive = true }
                }
            })
        }
        composable(PosDestinations.DASHBOARD) {
            DashboardScreen(onLoggedOut = {
                navController.navigate(PosDestinations.LOGIN) {
                    popUpTo(PosDestinations.DASHBOARD) { inclusive = true }
                }
            })
        }
    }
}
```

Modify `MainActivity.kt`: annotate the class `@AndroidEntryPoint`, and replace its `setContent` body with `PosWarNusTheme { PosNavGraph() }` (keep the existing theme wrapper already generated by the template).

- [ ] **Step 8: Verify the project builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/splash/ app/src/main/java/com/example/poswarnus/ui/dashboard/ \
  app/src/main/java/com/example/poswarnus/ui/navigation/ app/src/main/java/com/example/poswarnus/MainActivity.kt \
  app/src/test/java/com/example/poswarnus/ui/splash/SplashViewModelTest.kt
git commit -m "feat: wire Splash -> Login -> Dashboard navigation graph"
```

---

### Task 8: Manual end-to-end verification

**Files:** none (verification only).

- [ ] **Step 1: Set local Supabase credentials**

Add `SUPABASE_URL=...` and `SUPABASE_ANON_KEY=...` to `local.properties` (pointing at a real or test Supabase project with at least one email/password user created via the Supabase dashboard).

- [ ] **Step 2: Install and run on a device/emulator**

Run: `./gradlew installDebug`, then launch the app.
Expected: Splash shows for ~1.5s, then the Login form appears.

- [ ] **Step 3: Verify the five Review Focus behaviors manually**

Expected, in order: (1) tapping Login with both fields empty shows "Email and password are required" and makes no network call (check via a network inspector or just that no error toast about a request appears); (2) entering a wrong password shows an inline error from Supabase and stays on Login; (3) entering valid credentials navigates to Dashboard; (4) force-stopping and relaunching the app skips Splash's destination straight past Login into Dashboard (session persisted); (5) tapping Logout returns to Login, and force-stopping/relaunching now shows the Login form again (session cleared).

- [ ] **Step 4: Record the result**

No commit for this task — if all five checks pass, Phase 1 is done; if any fails, fix forward in a new commit on top of the relevant task before moving to Phase 2.
