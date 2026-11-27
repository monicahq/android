package com.monicahq.android.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Builds the app's HTTP client. Every client the app uses derives from [create]. */
object HttpClients {
    /**
     * Redirects are never followed: a redirect could carry a request, and its credentials, to
     * another server. A `3xx` answer is shown as "your instance moved" instead. There is no logging
     * interceptor in any build, so tokens never reach the logs.
     */
    fun create(): OkHttpClient =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
}

/** A finished exchange: the status code and the whole body, already read. */
class HttpAnswer(
    val code: Int,
    val body: String,
)

/**
 * Runs the call on OkHttp's threads and reads the whole body there, so no network work ever runs
 * on the caller's thread (Android refuses network on the main thread). Cancelling the coroutine,
 * or the client's dispatcher, cancels the call.
 */
suspend fun Call.fetch(): HttpAnswer =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    try {
                        val answer = response.use { HttpAnswer(it.code, it.body.string()) }
                        continuation.resume(answer)
                    } catch (e: IOException) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    continuation.resumeWithException(e)
                }
            },
        )
    }
