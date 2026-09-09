package com.dangerfield.drop2048.libraries.core

import com.dangerfield.drop2048.buildinfo.Drop2048BuildConfig

object SupabaseInfo {
    val projectId: String
        get() = Drop2048BuildConfig.SUPABASE_PROJECT_ID

    val url: String
        get() = Drop2048BuildConfig.SUPABASE_URL

    val anonKey: String
        get() = Drop2048BuildConfig.SUPABASE_ANON_KEY
}
