package com.trendyol.android.devtools.mock_interceptor

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.trendyol.android.devtools.mock_interceptor.internal.RequestQueue
import com.trendyol.android.devtools.mock_interceptor.internal.WebServer
import com.trendyol.android.devtools.mock_interceptor.internal.ext.readString
import com.trendyol.android.devtools.mock_interceptor.internal.ext.safeParse
import com.trendyol.android.devtools.mock_interceptor.internal.ext.toHeaders
import com.trendyol.android.devtools.mock_interceptor.internal.model.Carrier
import com.trendyol.android.devtools.mock_interceptor.internal.model.RequestData
import com.trendyol.android.devtools.mock_interceptor.internal.model.ResponseCarrier
import com.trendyol.android.devtools.mock_interceptor.internal.model.ResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit

class MockInterceptor(context: Context) : Interceptor {

    private val supervisorJob = SupervisorJob()

    private val interceptorScope = CoroutineScope(supervisorJob + Dispatchers.IO)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val webServer = WebServer(context, interceptorScope)

    private val requestQueue = RequestQueue()

    init {
        collectSocket()
        collectRequestQueue()
    }

    private fun collectSocket() = interceptorScope.launch {
        val responseCarrierAdapter = moshi.adapter(ResponseCarrier::class.java)
        webServer.incomingFlow.collect { json ->
            responseCarrierAdapter.safeParse(json)?.let { responseCarrier ->
                requestQueue.resume(responseCarrier)
            }
        }
    }

    private fun collectRequestQueue() = interceptorScope.launch {
        val carrierAdapter = moshi.adapter(Carrier::class.java)
        requestQueue.getQueueChannel().receiveAsFlow().collect { carrier ->
            webServer.ongoingFlow.emit(carrierAdapter.toJson(carrier))
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (webServer.hasConnection().not()) {
            return chain.proceed(chain.request())
        }

        val request = chain
            .withConnectTimeout(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
            .withReadTimeout(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
            .withWriteTimeout(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
            .request()

        val response = chain.proceed(request)

        val carrier = requestQueue.add(
            requestData = RequestData(
                url = request.url.toString(),
                method = request.method,
                headers = request.headers.toMultimap(),
                body = request.body.readString(),
            ),
            responseData = ResponseData(
                code = response.code,
                headers = response.headers.toMultimap(),
                body = response.body.readString(),
            ),
        )

        val responseData = runBlocking { requestQueue.waitFor(carrier.id) }

        return Response.Builder()
            .request(request)
            .message(DEFAULT_RESPONSE_MESSAGE)
            .code(responseData.code)
            .protocol(Protocol.HTTP_2)
            .body(createResponseBody(responseData))
            .headers(responseData.headers.toHeaders())
            .build()
    }

    private fun createResponseBody(responseData: ResponseData): ResponseBody {
        return moshi.adapter(Any::class.java)
            .toJson(responseData.body)
            .toByteArray()
            .toResponseBody(CONTENT_TYPE_JSON.toMediaTypeOrNull())
    }

    companion object {
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val DEFAULT_RESPONSE_MESSAGE = ""
        private const val REQUEST_TIMEOUT = 50000
    }
}
