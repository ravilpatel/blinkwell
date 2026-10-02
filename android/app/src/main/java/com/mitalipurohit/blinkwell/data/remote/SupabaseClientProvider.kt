package com.mitalipurohit.blinkwell.data.remote

import com.mitalipurohit.blinkwell.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {

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
        return try {
            val session = auth.currentSessionOrNull()
            if (session != null) {
                session.user?.id
            } else {
                auth.signInAnonymously()
                auth.currentUserOrNull()?.id
            }
        } catch (e: Exception) {
            null
        }
    }
}
