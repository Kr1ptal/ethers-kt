package io.ethers.providers

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp-based implementation of [HttpTransport].
 */
class OkHttpTransport(
    url: String,
    headers: Map<String, String>,
    private val client: OkHttpClient,
) : HttpTransport {

    private val httpUrl = url.toHttpUrl()
    private val requestHeaders = Headers.Builder().apply {
        headers.forEach { (k, v) -> add(k, v) }
    }.build()

    override fun execute(body: ByteArray, callback: (HttpResult) -> Unit) {
        val requestBody = body.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(httpUrl)
            .headers(requestHeaders)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(HttpResult.Failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = if (it.isSuccessful) {
                        HttpResult.Success(it.body.byteStream())
                    } else {
                        HttpResult.HttpError(it.code, it.message, it.body.byteStream())
                    }
                    callback(result)
                }
            }
        })
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
