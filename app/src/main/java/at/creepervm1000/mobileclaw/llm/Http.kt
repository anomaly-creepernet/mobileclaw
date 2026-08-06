package at.creepervm1000.mobileclaw.llm

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Trims trailing slashes so callers can safely append "/v1/...". */
    fun normalizeBase(url: String): String = url.trim().trimEnd('/')
}
