package com.monicahq.android

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.preferencesDataStore
import com.monicahq.android.auth.AuthStore
import com.monicahq.android.auth.KeystoreTokenCipher
import com.monicahq.android.auth.SessionManager
import com.monicahq.android.auth.SignInManager
import com.monicahq.android.auth.TokenEndpointClient
import com.monicahq.android.instance.InstanceChecker
import com.monicahq.android.instance.InstanceRepository
import com.monicahq.android.network.HttpClients
import com.monicahq.android.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Context.settings by preferencesDataStore(name = "settings")

/** Excluded from backups: the session never leaves the phone. */
private val Context.auth by preferencesDataStore(name = "auth")

/**
 * Creates the app's long-lived objects once. A plain container is enough while there are only a
 * few of them; Hilt comes when several screens share several dependencies.
 */
class MonicaApplication : Application() {
    /** Work that outlives any screen, such as reading the session at start. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val httpClient by lazy { HttpClients.create() }
    private val tokenClient by lazy { TokenEndpointClient(httpClient) }
    private val authStore by lazy { AuthStore(auth, KeystoreTokenCipher()) }

    val instanceRepository: InstanceRepository by lazy { InstanceRepository(settings) }

    /** Only debug builds may talk to an instance over plain http. */
    val instanceChecker: InstanceChecker by lazy {
        InstanceChecker(httpClient, appVersion = BuildConfig.VERSION_NAME, allowHttp = BuildConfig.DEBUG)
    }

    val sessionManager: SessionManager by lazy { SessionManager(authStore, tokenClient, httpClient) }

    val vaultRepository: VaultRepository by lazy { VaultRepository(sessionManager) }

    val signInManager: SignInManager by lazy {
        SignInManager(authStore, instanceChecker, tokenClient, sessionManager, deviceName())
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { sessionManager.load() }
    }

    /** The name the person gave the phone, so the web settings can tell their phones apart. */
    private fun deviceName(): String = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
}
