package com.mitalipurohit.blinkwell.data.remote

import com.mitalipurohit.blinkwell.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime

import android.util.Log

object SupabaseClientProvider {

    private const val TAG = "SupabaseClientProvider"

    fun isConfigured(): Boolean {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        return url.isNotBlank() && 
               !url.contains("placeholder") && 
               key.isNotBlank() && 
               !key.contains("placeholder")
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    val auth: Auth
        get() = client.auth

    val postgrest: Postgrest
        get() = client.postgrest

    suspend fun ensureAnonymousAuth(): String? {
        if (!isConfigured()) {
            Log.w(TAG, "Supabase is not configured! SUPABASE_URL is currently '${BuildConfig.SUPABASE_URL}'. Please provide valid credentials in local.properties.")
            return null
        }
        return try {
            val session = auth.currentSessionOrNull()
            if (session != null) {
                val uid = session.user?.id
                Log.d(TAG, "Existing Supabase session active for user: $uid")
                uid
            } else {
                Log.d(TAG, "Attempting anonymous sign in...")
                auth.signInAnonymously()
                val uid = auth.currentUserOrNull()?.id
                Log.i(TAG, "Successfully authenticated anonymously with UID: $uid")
                uid
            }
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed. Please check if 'Allow Anonymous Sign-ins' is enabled in your Supabase Auth settings. Error: ${e.message}", e)
            null
        }
    }
}
