package io.ethers.providers

import io.channels.core.Channel
import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.json.JsonElement
import io.ethers.core.success
import io.ethers.logger.dbg
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.inf
import io.ethers.logger.trc
import io.ethers.logger.wrn
import io.ethers.providers.types.BatchRpcRequest
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.jctools.queues.MpscUnboundedXaddArrayQueue
import org.jctools.queues.SpscUnboundedArrayQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * [JsonRpcClient] implementation via WS transport. Supports single, batch, and subscription requests.
 *
 * Reconnection happens automatically when WS connection is in dropped / fail state. All unfinished requests for which
 * the response was not received are automatically resubmitted when new connection is established.
 */
class WsClient(
    url: String,
    private val client: OkHttpClient,
    headers: Map<String, String> = emptyMap(),
    private val resubscribeOnReconnect: Boolean = true,
) : JsonRpcClient {
    @JvmOverloads
    constructor(url: String, config: RpcClientConfig = RpcClientConfig()) : this(
        url,
        config.client!!,
        config.requestHeaders,
        config.resubscribeOnReconnect,
    )

    private val LOG = getLogger()

    // these are modified by a single thread
    private val inFlightRequests = HashMap<Long, CompletableRequest<*>>()
    private val inFlightBatchRequests = HashMap<Long, Pair<CompletableBatchRequest, HashMap<Long, Int>>>()
    private val inFlightSubscriptionRequests = HashMap<Long, CompletableSubscriptionRequest<*>>()
    private val requestIdToSubscription = HashMap<Long, Subscription<*>>()
    private val serverIdToSubscription = HashMap<String, Subscription<*>>()

    // use lock instead of "synchronized" to avoid thread pinning if using virtual thread for processor
    private val eventLock = reentrantLock()
    private val newEventCondition = eventLock.newCondition()
    private val connectionOpenedCondition = eventLock.newCondition()
    private val connectionClosedCondition = eventLock.newCondition()

    // queues chosen based on https://vmlens.com/articles/scale/scalability_queue/
    private val messageQueue = SpscUnboundedArrayQueue<String>(512)
    private val requestQueue = MpscUnboundedXaddArrayQueue<CompletableRequest<*>>(512)
    private val batchRequestQueue = MpscUnboundedXaddArrayQueue<CompletableBatchRequest>(256)
    private val subscriptionQueue = MpscUnboundedXaddArrayQueue<CompletableSubscriptionRequest<*>>(128)
    private val unsubscribeQueue = MpscUnboundedXaddArrayQueue<Long>(128)

    private val reconnect = atomic(false)
    private val stopping = atomic(false)

    init {
        val requestHeaders = Headers.Builder().apply { headers.forEach { (key, value) -> add(key, value) } }.build()
        val wsRequest = Request.Builder().url(url).headers(requestHeaders).build()
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                LOG.inf { "WebSocket connection opened" }

                eventLock.withLock { connectionOpenedCondition.signalAll() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                messageQueue.add(text)

                eventLock.withLock { newEventCondition.signalAll() }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // will trigger "onFailure" callback
                throw Exception("Binary messages are not supported")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                LOG.dbg { "WebSocket connection closing: $code $reason. Closing our side as well." }

                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                LOG.dbg { "WebSocket connection closed: $code $reason" }

                eventLock.withLock { connectionClosedCondition.signalAll() }

                requestReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                eventLock.withLock { connectionClosedCondition.signalAll() }

                if (stopping.value) {
                    LOG.dbg(t) { "WebSocket failure ignored because we are stopping" }
                    return
                }

                LOG.err(t) { "WebSocket failure" }
                requestReconnect()
            }

            private fun requestReconnect() = eventLock.withLock {
                reconnect.value = true

                // wake up the processor thread, in case it's waiting for new events
                newEventCondition.signalAll()
            }
        }

        val processorThread = AsyncExecutor.maybeVirtualThread {
            LOG.inf { "Starting WebSocket processor thread and connecting to websocket" }

            var websocket = eventLock.withLock {
                val ws = client.newWebSocket(wsRequest, wsListener)
                connectionOpenedCondition.await(
                    client.connectTimeoutMillis.toLong(),
                    TimeUnit.MILLISECONDS,
                )
                ws
            }

            var requestId = 1L
            var msg: String?
            var request: CompletableRequest<*>?
            var batchRequest: CompletableBatchRequest?
            var subscriptionRequest: CompletableSubscriptionRequest<*>?
            var unsubscribeRequestId: Long?

            var lastTimeoutCheck = TimeSource.Monotonic.markNow()

            while (!stopping.value) {
                try {
                    // first, process all messages in the queue
                    while (messageQueue.poll().also { msg = it } != null) {

                        // messages are terminated by a new line. Remove it when logging to get nicer output
                        LOG.trc { "Processing message: ${msg?.removeSuffix("\n")}" }

                        try {
                            handleMessage(msg!!)
                        } catch (e: Exception) {
                            LOG.err(e) { "Error processing message, skipping: $msg" }
                        }
                    }

                    // second, check if we need to reconnect, initiating re-subscription for existing subs
                    if (reconnect.value) {
                        var reconnectSuccessful = false

                        while (!reconnectSuccessful && !stopping.value) {
                            LOG.dbg { "Trying to reconnect WebSocket" }

                            // close the old websocket, just in case. Do this while holding a lock, so we don't start
                            // awaiting on the condition after the ws listener already notified it, which would lead
                            // to a deadlock
                            eventLock.withLock {
                                if (websocket.close(1000, "Close")) {
                                    connectionClosedCondition.await()
                                }
                            }

                            // Clear the flag in each iteration since it might be set again while reconnecting.
                            // Has to be done after closing in case connection is still open, since it will trigger
                            // a new reconnection attempt
                            reconnect.value = false

                            // and wait explicitly for the new connection to be opened, the delay acting as a back-off
                            reconnectSuccessful = eventLock.withLock {
                                // sends the connection request asynchronously, so the lock won't be held very long
                                websocket = client.newWebSocket(wsRequest, wsListener)

                                connectionOpenedCondition.await(
                                    client.connectTimeoutMillis.toLong(),
                                    TimeUnit.MILLISECONDS,
                                )
                            }

                            if (!reconnectSuccessful) {
                                handleTimeouts(client.readTimeoutMillis.toLong().milliseconds)

                                Thread.sleep(2000L)
                            }
                        }

                        if (stopping.value) {
                            break
                        }

                        // re-queue all requests
                        if (inFlightRequests.isNotEmpty()) {
                            val iter = inFlightRequests.iterator()
                            while (iter.hasNext()) {
                                val value = iter.next().value
                                LOG.dbg { "Re-queued in-flight request: $value" }

                                requestQueue.add(value)
                                iter.remove()
                            }
                        }

                        // re-queue all batch requests
                        if (inFlightBatchRequests.isNotEmpty()) {
                            val iter = inFlightBatchRequests.iterator()
                            while (iter.hasNext()) {
                                val (batchRequest, _) = iter.next().value
                                LOG.dbg { "Re-queued in-flight batch request: $batchRequest" }

                                batchRequestQueue.add(batchRequest)
                                iter.remove()
                            }
                        }

                        // re-queue all subscription requests
                        if (inFlightSubscriptionRequests.isNotEmpty()) {
                            val iter = inFlightSubscriptionRequests.iterator()
                            while (iter.hasNext()) {
                                val value = iter.next().value
                                LOG.dbg { "Re-queued in-flight subscription request: $value" }

                                subscriptionQueue.add(value)
                                iter.remove()
                            }
                        }

                        // handle existing streams: either resubscribe or close them
                        if (resubscribeOnReconnect) {
                            // send resubscribe requests for all existing streams
                            for ((id, sub) in requestIdToSubscription) {
                                LOG.dbg { "Resubscribing stream with ID: $id" }
                                websocket.send(buildJsonRpcRequest("eth_subscribe", id, sub.params))
                            }
                        } else {
                            // close all streams so consumers can handle resubscription explicitly
                            for ((id, sub) in requestIdToSubscription) {
                                LOG.dbg { "Closing stream on reconnect: $id" }
                                sub.stream.close()
                            }

                            requestIdToSubscription.clear()
                            serverIdToSubscription.clear()
                        }
                    }

                    // third, process all single and batch requests in the queue
                    while (requestQueue.poll().also { request = it } != null) {
                        val id = requestId++
                        val req = buildJsonRpcRequest(request!!.method, id, request.params)

                        LOG.trc { "Processing request: $req" }
                        inFlightRequests[id] = request
                        websocket.send(req)
                    }

                    while (batchRequestQueue.poll().also { batchRequest = it } != null) {
                        var batchId = -1L
                        val idToIndex = HashMap<Long, Int>(batchRequest!!.request.requests.size, 1.0F)

                        val sb = StringBuilder()
                        sb.append('[')
                        for (i in batchRequest.request.requests.indices) {
                            if (i > 0) sb.append(',')
                            val reqItem = batchRequest.request.requests[i]
                            val id = requestId++
                            if (batchId == -1L) batchId = id
                            idToIndex[id] = i
                            sb.append(buildJsonRpcRequest(reqItem.method, id, reqItem.params))
                        }
                        sb.append(']')

                        val req = sb.toString()

                        LOG.trc { "Processing batch request: $req" }
                        inFlightBatchRequests[batchId] = Pair(batchRequest, idToIndex)
                        websocket.send(req)
                        batchRequest.request.markAsSent()
                    }

                    // fourth, process all subscription requests in the queue
                    while (subscriptionQueue.poll().also { subscriptionRequest = it } != null) {
                        val id = requestId++
                        val req = buildJsonRpcRequest("eth_subscribe", id, subscriptionRequest!!.params)

                        LOG.trc { "Processing subscription request: $req" }
                        inFlightSubscriptionRequests[id] = subscriptionRequest
                        websocket.send(req)
                    }

                    // fifth, process all unsubscribe requests in the queue
                    while (unsubscribeQueue.poll().also { unsubscribeRequestId = it } != null) {
                        val sub = requestIdToSubscription.remove(unsubscribeRequestId!!) ?: continue

                        LOG.trc { "Unsubscribing from stream: ${sub.serverId}" }
                        serverIdToSubscription.remove(sub.serverId)

                        val id = requestId++
                        val req = buildJsonRpcRequest("eth_unsubscribe", id, arrayOf(sub.serverId))

                        LOG.trc { "Processing unsubscribe request: $req" }
                        websocket.send(req)
                    }

                    // check and handle timed-out requests every 1000ms
                    if (lastTimeoutCheck.elapsedNow() > 1000.milliseconds) {
                        handleTimeouts(client.readTimeoutMillis.toLong().milliseconds)
                        lastTimeoutCheck = TimeSource.Monotonic.markNow()
                    }

                    eventLock.withLock {
                        // do a quick check if any new events arrived while processing requests, while holding the lock
                        // to prevent race conditions, so we don't wait unnecessarily. We wait for max 1 second, so we
                        // still process timeouts in a timely manner, even if there are no new events.
                        if (messageQueue.isEmpty &&
                            requestQueue.isEmpty &&
                            batchRequestQueue.isEmpty &&
                            subscriptionQueue.isEmpty &&
                            unsubscribeQueue.isEmpty
                        ) {
                            newEventCondition.await(1, TimeUnit.SECONDS)
                        }
                    }
                } catch (e: Exception) {
                    LOG.err(e) { "Exception when processing events, reconnecting WebSocket" }

                    reconnect.value = true
                }
            }

            // close the websocket, expire all remaining requests, and unsubscribe from all subscriptions
            websocket.close(1000, "Close")
            handleTimeouts(Duration.ZERO)
            for ((_, subscription) in requestIdToSubscription) {
                subscription.stream.close()
            }

            requestIdToSubscription.clear()
            serverIdToSubscription.clear()
        }

        processorThread.name = "WsClient-Processor-${processorThread.id}"
        processorThread.start()
    }

    private fun handleTimeouts(timeout: Duration) {
        removeTimedOutRequests(inFlightRequests, timeout)
        removeTimedOutRequests(inFlightSubscriptionRequests, timeout)
        removeTimedOutBatchRequests(timeout)
    }

    private fun removeTimedOutBatchRequests(timeout: Duration) {
        // skip expiration if timeout is not set or requests are empty
        if (timeout.inWholeMilliseconds < 0 || inFlightBatchRequests.isEmpty()) {
            return
        }

        val iter = inFlightBatchRequests.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val (batchRequest, _) = entry.value
            if (batchRequest.expireIfTimedOut(timeout)) {
                LOG.wrn { "Batch request timed out: ID ${entry.key}" }
                iter.remove()
            }
        }
    }

    private fun <T : ExpiringRequest> removeTimedOutRequests(requests: MutableMap<Long, T>, timeout: Duration) {
        // skip expiration if timeout is not set or requests are empty
        if (timeout.inWholeMilliseconds < 0 || requests.isEmpty()) {
            return
        }

        val iter = requests.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.expireIfTimedOut(timeout)) {
                LOG.wrn { "Request timed out: ID ${entry.key}" }
                iter.remove()
            }
        }
    }

    private fun handleMessage(text: String) {
        val element = Kotlinx.DEFAULT.parseToJsonElement(text)

        if (element is JsonArray) {
            handleBatchResponse(text, element)
            return
        }

        val obj = element.jsonObject
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: -1L
        val method = obj["method"]?.jsonPrimitive?.content
        val resultEl = obj["result"]
        val paramsEl = obj["params"]
        val errorEl = obj["error"]
        val error = if (errorEl != null && errorEl !is JsonNull) RpcError.fromJsonObject(errorEl.jsonObject) else null

        // DO NOT CHANGE ORDER OF THESE OPERATIONS
        when {
            method != null && paramsEl != null -> handleNotification(paramsEl.jsonObject)

            id != -1L && error != null -> handleResponse(id, null, error)

            id != -1L && resultEl != null -> handleResponse(id, resultEl, null)

            else -> {
                val invalid = RpcError(
                    RpcError.CODE_INVALID_RESPONSE,
                    "Invalid response",
                    JsonElement(text),
                )

                if (id != -1L) {
                    handleResponse(id, null, invalid)
                } else {
                    throw Exception("Invalid response: $text")
                }
            }
        }
    }

    private fun handleBatchResponse(text: String, array: JsonArray) {
        var batch: CompletableBatchRequest? = null
        var requestIndexPerId: HashMap<Long, Int>? = null

        for (element in array) {
            val obj = element.jsonObject
            val responseId = obj["id"]?.jsonPrimitive?.longOrNull ?: -1L

            if (batch == null && responseId != -1L) {
                val data = getBatchFromRequestId(responseId)
                if (data != null) {
                    batch = data.first
                    requestIndexPerId = data.second
                }
            }

            val errorEl = obj["error"]
            val resultEl = obj["result"]
            val error = if (errorEl != null && errorEl !is JsonNull) RpcError.fromJsonObject(errorEl.jsonObject) else null

            if (batch == null || requestIndexPerId == null || responseId == -1L) {
                throw Exception("Invalid response, no matching batch found for ID $responseId: $text")
            }

            val responseIndex = requestIndexPerId[responseId]!!

            val response = when {
                resultEl == null && error == null -> HttpClient.ERROR_INVALID_RESPONSE
                resultEl != null -> success(batch.request.requests[responseIndex].resultDecoder(resultEl))
                else -> failure(error!!)
            }

            batch.request.responses[responseIndex].complete(response)
        }

        batch?.future?.complete(true)
    }

    private fun getBatchFromRequestId(requestId: Long): Pair<CompletableBatchRequest, HashMap<Long, Int>>? {
        // Handle if the batch returned responses in the same order as they were sent
        val directMatch = inFlightBatchRequests.remove(requestId)
        if (directMatch != null) {
            return directMatch
        }

        // Find a batch by looking through all in-flight batches for one containing this ID
        for ((batchId, data) in inFlightBatchRequests) {
            if (data.second.containsKey(requestId)) {
                return inFlightBatchRequests.remove(batchId)
            }
        }

        return null
    }

    private fun handleResponse(id: Long, resultElement: KJsonElement?, error: RpcError?) {
        val request = inFlightRequests.remove(id)
        if (request != null) {
            handleRequestResponse(id, request, resultElement, error)
            return
        }

        val subscriptionRequest = inFlightSubscriptionRequests.remove(id)
        if (subscriptionRequest != null) {
            handleSubscriptionResponse(id, subscriptionRequest, resultElement, error)
            return
        }

        val resubscribed = requestIdToSubscription[id]
        if (resubscribed != null) {
            handleResubscriptionResponse(id, resubscribed, resultElement, error)
        }
    }

    private fun <T> handleRequestResponse(
        id: Long,
        request: CompletableRequest<T>,
        resultElement: KJsonElement?,
        error: RpcError?,
    ) {
        val result = if (error == null && resultElement != null) request.resultDecoder(resultElement) else null

        val response = when {
            result == null && error == null -> HttpClient.ERROR_INVALID_RESPONSE
            result != null -> success<T>(result)
            else -> failure(error!!)
        }

        LOG.trc { "Handled response for request $id: $response" }

        request.future.complete(response)
    }

    private fun <T : Any> handleSubscriptionResponse(
        id: Long,
        request: CompletableSubscriptionRequest<T>,
        resultElement: KJsonElement?,
        error: RpcError?,
    ) {
        if (error != null) {
            request.future.complete(failure(error))
        } else {
            val subscription = Subscription(
                serverId = resultElement!!.jsonPrimitive.content,
                params = request.params,
                resultDecoder = request.resultDecoder,
                stream = QueueChannel.spscUnbounded {
                    // requestId is constant even across re-subscriptions. Just queue for processor thread.
                    unsubscribeQueue.add(id)
                    eventLock.withLock {
                        newEventCondition.signalAll()
                    }
                },
            )

            requestIdToSubscription[id] = subscription
            serverIdToSubscription[subscription.serverId] = subscription

            request.future.complete(success(subscription.stream))
        }

        LOG.trc { "Handled response for subscription request $id" }
    }

    private fun <T : Any> handleResubscriptionResponse(
        id: Long,
        subscription: Subscription<T>,
        resultElement: KJsonElement?,
        error: RpcError?,
    ) {
        if (error != null) {
            // will cause re-subscription to be attempted again
            throw Exception("Error re-subscribing to stream: ${subscription.serverId}, error: $error")
        } else {
            val newServerId = resultElement!!.jsonPrimitive.content
            // remove old serverId
            serverIdToSubscription.remove(subscription.serverId)

            // update serverId with new value
            subscription.serverId = newServerId
            serverIdToSubscription[subscription.serverId] = subscription
        }

        LOG.trc { "Handled response for re-subscription request $id" }
    }

    private fun handleNotification(paramsObj: kotlinx.serialization.json.JsonObject) {
        val subscriptionId = paramsObj["subscription"]?.jsonPrimitive?.content ?: return
        val resultEl = paramsObj["result"] ?: return
        val subscription = serverIdToSubscription[subscriptionId] ?: return
        subscription.handleNotification(resultEl)
    }

    /**
     * Signal to close WS connection.
     */
    override fun close() {
        LOG.inf { "Requesting to close WebSocket" }

        stopping.value = true

        // wake up the event loop thread so it can exit
        eventLock.withLock { newEventCondition.signalAll() }

        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    override fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean> {
        if (batch.isEmpty) {
            batch.markAsSent()
            return CompletableFuture.completedFuture(true)
        }

        val request = CompletableBatchRequest(batch, CompletableFuture())

        batchRequestQueue.add(request)
        eventLock.withLock { newEventCondition.signalAll() }
        return request.future
    }

    override fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): CompletableFuture<Result<T, RpcError>> {
        val request = CompletableRequest(
            method,
            params,
            resultDecoder,
            CompletableFuture(),
        )

        requestQueue.add(request)
        eventLock.withLock { newEventCondition.signalAll() }
        return request.future
    }

    override fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): CompletableFuture<Result<ChannelReceiver<T>, RpcError>> {
        val request = CompletableSubscriptionRequest(
            params,
            resultDecoder,
            CompletableFuture(),
        )

        subscriptionQueue.add(request)
        eventLock.withLock { newEventCondition.signalAll() }
        return request.future
    }

    private abstract class ExpiringRequest {
        private val initiated = TimeSource.Monotonic.markNow()

        fun expireIfTimedOut(duration: Duration): Boolean {
            if (initiated.elapsedNow() > duration) {
                expireRequest()
                return true
            }

            return false
        }

        abstract fun expireRequest()
    }

    private class CompletableRequest<T>(
        val method: String,
        val params: Array<*>,
        val resultDecoder: (KJsonElement) -> T,
        val future: CompletableFuture<Result<T, RpcError>>,
    ) : ExpiringRequest() {
        override fun expireRequest() {
            future.complete(HttpClient.ERROR_CALL_TIMEOUT)
        }
    }

    private data class CompletableBatchRequest(
        val request: BatchRpcRequest,
        val future: CompletableFuture<Boolean>,
    ) : ExpiringRequest() {
        override fun expireRequest() {
            for (i in request.responses.indices) {
                val response = request.responses[i]
                response.complete(HttpClient.ERROR_CALL_TIMEOUT)
            }

            future.complete(false)
        }
    }

    private class CompletableSubscriptionRequest<T : Any>(
        val params: Array<*>,
        val resultDecoder: (KJsonElement) -> T,
        val future: CompletableFuture<Result<ChannelReceiver<T>, RpcError>>,
    ) : ExpiringRequest() {
        override fun expireRequest() {
            future.complete(HttpClient.ERROR_CALL_TIMEOUT)
        }
    }

    private class Subscription<T : Any>(
        var serverId: String,
        val params: Array<*>,
        val resultDecoder: (KJsonElement) -> T,
        val stream: Channel<T>,
    ) {
        fun handleNotification(event: KJsonElement) {
            stream.offer(resultDecoder(event))
        }
    }
}
