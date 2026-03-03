# WebView SSO Authentication Review

**Reviewer:** Roxy (AI Agent)  
**Date:** 2026-03-03  
**Scope:** Browser-based SSO authentication via WebView for Authentik forward-auth

---

## Summary

The WebView-based SSO implementation has several issues ranging from critical race conditions to medium-severity security concerns. The core flow works but has edge cases that will cause auth failures and poor UX.

---

## Issues Found

### 🔴 CRITICAL: Race Condition in Auto-Finish Detection

**Location:** `WebViewAuthActivity.kt` lines 186-200

**Problem:** The auto-finish logic fires as soon as the WebView returns to the ComfyUI host, but doesn't verify that auth actually succeeded or that cookies were set.

```kotlin
override fun onPageFinished(view: WebView?, pageUrl: String?) {
    isLoading = false
    currentUrl = pageUrl ?: ""
    authAppearsComplete = isOnTargetHost(pageUrl, host)
    CookieManager.getInstance().flush()
    // Auto-finish if we're back on the ComfyUI host
    if (authAppearsComplete) {
        collectAndReturn()  // ← Fires immediately!
    }
}
```

**Scenario:** 
1. User navigates to `https://comfy.example.com`
2. Traefik redirects to Authentik (`https://auth.example.com/...`)
3. Authentik redirects back to ComfyUI with a temporary redirect (302)
4. `onPageFinished` fires on the ComfyUI host BEFORE the final page loads
5. Activity closes with incomplete/no cookies

**Root Cause:** `isOnTargetHost()` only checks the hostname, not whether cookies exist or the page content indicates success.

**Fix:**
```kotlin
override fun onPageFinished(view: WebView?, pageUrl: String?) {
    isLoading = false
    currentUrl = pageUrl ?: ""
    authAppearsComplete = isOnTargetHost(pageUrl, host)
    CookieManager.getInstance().flush()
    
    // Auto-finish only if we have cookies AND we're on target host
    if (authAppearsComplete) {
        val cookies = WebViewAuthActivity.extractCookies(url)
        if (cookies.isNotEmpty() && cookies.contains("authentik_session")) {
            // Add a small delay to ensure all cookies are flushed
            view?.postDelayed({ collectAndReturn() }, 300)
        }
    }
}
```

**Alternative (more robust):** Check for a specific success indicator like the ComfyUI API responding with 200:
```kotlin
if (authAppearsComplete) {
    val cookies = WebViewAuthActivity.extractCookies(url)
    if (cookies.isNotEmpty()) {
        // Verify cookies work by testing the API
        scope.launch(Dispatchers.IO) {
            val testResult = testAuthWithCookies(url, cookies)
            if (testResult) {
                withContext(Dispatchers.Main) { collectAndReturn() }
            }
        }
    }
}
```

---

### 🔴 CRITICAL: No 401 Detection for Session Expiry

**Location:** `AuthInterceptor.kt`, `ConnectionManager.kt`

**Problem:** Once connected, if the Authentik session expires, requests start returning 401/403. The app doesn't detect this and doesn't trigger re-authentication.

**Current Flow:**
1. User authenticates via WebView, cookies stored
2. App connects successfully
3. ... time passes, Authentik session expires (default 24h-7d) ...
4. Next request returns 401
5. `ConnectionManager` sees a network error, not an auth error
6. User sees "Connection failed" instead of being prompted to re-login

**Root Cause:** `AuthInterceptor` only adds headers on requests, it doesn't inspect responses. There's no OkHttp `Authenticator` configured.

**Fix - Add an Authenticator to OkHttp:**
```kotlin
// In ComfyUIClient.kt
class SessionExpiredAuthenticator(
    private val onSessionExpired: () -> Unit
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Only trigger for cookie-based auth (browser auth type)
        if (response.code == 401 || response.code == 403) {
            // Check if this is our first attempt (avoid infinite loop)
            if (response.request.header("X-Auth-Retry") == null) {
                onSessionExpired()
            }
        }
        return null  // Don't retry automatically, let the UI handle re-auth
    }
}

// Configure client with authenticator
private val httpClient = SelfSignedCertHelper.configureToAcceptSelfSigned(
    OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(SessionExpiredAuthenticator { 
            // Signal that re-auth is needed
            connectionState.value = ConnectionState.AuthExpired
        })
        // ...
).build()
```

**Fix - ConnectionManager should detect auth failures:**
```kotlin
// In ConnectionManager.kt - testConnection callback
client.testConnection { success, errorMessage, _, failureType ->
    if (!success && failureType == ConnectionFailure.AUTHENTICATION) {
        // For browser auth, this means session expired - trigger re-auth
        if (server.authType == AuthType.BROWSER) {
            _webSocketState.value = WebSocketState.Failed(
                reason = "Session expired",
                failureType = ConnectionFailure.AUTHENTICATION
            )
            // Prompt user to re-authenticate
            showAuthExpiredDialog()
        }
    }
}
```

---

### 🟡 MEDIUM: Global Cookie Clear on Login

**Location:** `WebViewAuthActivity.kt` line 91

**Problem:** `removeAllCookies(null)` clears ALL cookies from the system CookieManager, not just cookies for the target domain.

```kotlin
CookieManager.getInstance().apply {
    setAcceptCookie(true)
    removeAllCookies(null)  // ← Nukes everything!
    flush()
}
```

**Impact:**
- Breaks other WebViews in the app if any exist
- Could affect other apps sharing the WebView cookie store (rare on modern Android)
- User loses any "remember me" state from previous logins to other services

**Fix:** Only clear cookies for the specific domain:
```kotlin
CookieManager.getInstance().apply {
    setAcceptCookie(true)
    // Clear only cookies for our target domain and common auth domains
    val targetUri = Uri.parse(url)
    val targetDomain = targetUri.host ?: ""
    
    // Get current cookies and selectively remove (CookieManager doesn't support per-domain clear)
    // Alternative: Accept that old cookies might exist and let the server handle it
    // Most auth providers will overwrite with fresh cookies anyway
}
```

**Better Fix:** Don't clear cookies at all. Let the auth provider set fresh cookies that overwrite stale ones. Only clear if auth explicitly fails:
```kotlin
// Remove the removeAllCookies call entirely
// Auth providers will set new cookies on successful login
// If login fails, user can tap "Clear cookies" button manually
```

---

### 🟡 MEDIUM: Insecure Fallback for EncryptedSharedPreferences

**Location:** `CredentialStorage.kt` lines 30-40

**Problem:** If EncryptedSharedPreferences creation fails, the code falls back to regular (unencrypted) SharedPreferences:

```kotlin
} catch (e: Exception) {
    DebugLogger.e(TAG, "Failed to create encrypted prefs, using fallback: ${e.message}")
    // Fallback to regular SharedPreferences if encryption fails
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
```

**Impact:** On devices where encryption fails (old Android, corrupted keystore), session cookies are stored in plaintext. Malicious apps with root access can read them.

**Fix:** Fail loudly instead of silently downgrading:
```kotlin
} catch (e: Exception) {
    DebugLogger.e(TAG, "Failed to create encrypted prefs: ${e.message}")
    // Don't fall back to insecure storage - throw and let the UI handle it
    throw SecurityException("Cannot create secure credential storage", e)
}
```

**Alternative:** Store a flag indicating insecure mode and warn the user:
```kotlin
} catch (e: Exception) {
    DebugLogger.e(TAG, "SECURITY WARNING: Using unencrypted credential storage")
    _isSecureStorage.value = false  // Observable by UI to show warning
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
```

---

### 🟡 MEDIUM: Cookie Leakage to Third-Party Auth Domains

**Location:** `WebViewAuthActivity.kt` `extractCookies()` method

**Problem:** The code only extracts cookies for the target URL, which is correct. However, during the auth flow, the WebView visits multiple domains (Authentik, possibly OAuth providers like Google/GitHub). Those cookies are stored in the system CookieManager and persist.

**Impact:** If another part of the app creates a WebView to a different domain that happens to share an auth provider, those cookies might be sent, leaking session state.

**Fix:** Clear third-party cookies after successful auth:
```kotlin
fun collectAndReturn() {
    val cookies = WebViewAuthActivity.extractCookies(url)
    
    // Clear non-target cookies to prevent leakage
    // Keep only cookies for the ComfyUI domain
    CookieManager.getInstance().apply {
        // Unfortunately CookieManager doesn't support per-domain clear
        // Best we can do is disable third-party cookies for future WebViews
        setAcceptThirdPartyCookies(webViewRef, false)
    }
    
    onDone(cookies)
}
```

---

### 🟡 MEDIUM: Protocol Detection Guesses Wrong

**Location:** `ServerDialog.kt` lines 353-356

**Problem:** The protocol is guessed based solely on port number:

```kotlin
val proto = if (portNum == 443) "https" else "http"
val serverUrl = "$proto://$trimmedHostname:$portNum"
```

**Impact:** 
- HTTPS on non-443 ports (common with reverse proxies): incorrectly uses HTTP
- Server on port 8443 with HTTPS: uses HTTP, fails
- HTTP on port 443 (weird but possible): uses HTTPS, fails

**Fix:** Use the same protocol detection as `ComfyUIClient.testConnection()`:
```kotlin
// Try HTTPS first, fall back to HTTP
suspend fun detectProtocol(hostname: String, port: Int): String {
    return try {
        // Quick HEAD request to test HTTPS
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://$hostname:$port/")
            .head()
            .build()
        client.newCall(request).execute().use { 
            if (it.isSuccessful || it.code == 401 || it.code == 403) "https" else "http"
        }
    } catch (e: Exception) {
        "http"  // HTTPS failed, try HTTP
    }
}
```

**Alternative:** Store the detected protocol in the Server model after first successful connection.

---

### 🟢 LOW: No WebSocket Cookie Injection

**Location:** `ComfyUIClient.kt` WebSocket client

**Problem:** The WebSocket client uses the same `authInterceptor`, but WebSocket connections might not respect the interceptor in all cases. The cookie should be added to the WebSocket handshake headers explicitly.

**Current code doesn't show WebSocket URL construction, but typically:**
```kotlin
// WebSocket URL should include cookies in the initial request
val wsRequest = Request.Builder()
    .url("wss://$hostname:$port/ws?clientId=$clientId")
    .header("Cookie", cookieHeader)  // Explicit cookie header
    .build()
```

**Verify:** Check if `webSocketClient.newWebSocket(request, listener)` respects the interceptor. If yes, this is a non-issue.

---

### 🟢 LOW: No Cookie Expiry Tracking

**Location:** `CredentialStorage.kt`

**Problem:** Cookies are stored as raw strings without parsing expiry times. The app has no way to know if cookies are expired without hitting the server.

**Impact:** App attempts connections with expired cookies, sees failures, then re-auths. Could provide better UX by checking expiry proactively.

**Fix (optional improvement):**
```kotlin
data class StoredCookies(
    val cookies: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val estimatedExpiry: Long? = null  // Parse from Set-Cookie if available
)

fun areCookiesLikelyExpired(stored: StoredCookies): Boolean {
    val age = System.currentTimeMillis() - stored.capturedAt
    // Authentik default session is 24 hours
    return age > TimeUnit.HOURS.toMillis(20)  // Check 4 hours early
}
```

---

## Architectural Concerns

### 1. No Refresh Token Support

Authentik and most OAuth providers support refresh tokens for silent re-authentication. The current implementation requires full user interaction for every re-auth.

**Recommendation:** If Authentik is configured with OAuth client credentials for the app, implement silent refresh using the refresh token. This would prevent the "session expired, please log in again" interruption.

### 2. Cookie vs. Token-Based Auth

Storing raw cookies is fragile (they contain multiple values, expire, change format). Consider:
- Extracting just the session token from cookies
- Using Authentik's API to exchange cookies for a longer-lived API token
- Implementing OAuth2 PKCE flow instead of cookie capture

### 3. Single Point of Failure

`CredentialStorage` is the only place credentials are stored. If it gets corrupted:
- User can't connect
- No recovery path

Consider adding a "Clear credentials and re-auth" button in settings.

---

## Recommended Priority

1. **Fix race condition in auto-finish** (CRITICAL) - Users report auth failing randomly
2. **Add 401 detection and re-auth flow** (CRITICAL) - Silent failures after session expiry
3. **Fix global cookie clear** (MEDIUM) - Annoying side effects
4. **Fix insecure storage fallback** (MEDIUM) - Security concern
5. **Improve protocol detection** (MEDIUM) - HTTPS on non-443 ports broken

---

## Files Changed

| File | Changes Needed |
|------|----------------|
| `WebViewAuthActivity.kt` | Fix auto-finish race, add cookie verification |
| `AuthInterceptor.kt` | Add 401 response handling (or use Authenticator) |
| `ConnectionManager.kt` | Handle auth expiry state, trigger re-auth flow |
| `CredentialStorage.kt` | Remove insecure fallback, add expiry tracking |
| `ServerDialog.kt` | Fix protocol detection |
| `ComfyUIClient.kt` | Add Authenticator for 401 handling |

---

## Testing Checklist

- [ ] Login with valid credentials → connects successfully
- [ ] Login with invalid credentials → shows error, doesn't crash
- [ ] Wait for session to expire (~24h) → re-auth prompted automatically
- [ ] Cancel WebView mid-auth → returns to login screen gracefully
- [ ] Rapid navigation during auth → no premature auto-finish
- [ ] Poor network during auth → timeout with clear error
- [ ] App killed during auth → no corrupted state on relaunch
- [ ] Multiple servers with different auth types → credentials isolated correctly
