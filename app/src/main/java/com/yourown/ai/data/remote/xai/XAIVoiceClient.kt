package com.yourown.ai.data.remote.xai

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.Base64

/**
 * xAI Grok Voice Agent API Client
 * WebSocket endpoint: wss://api.x.ai/v1/realtime
 * 
 * Supported voices: Ara (default), Rex, Sal, Eve, Leo
 * Audio format: PCM16 (16-bit linear PCM)
 */
class XAIVoiceClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "XAIVoiceClient"
        private const val WEBSOCKET_URL = "wss://api.x.ai/v1/realtime"
    }
    
    private var currentWebSocket: WebSocket? = null
    
    /**
     * Voice options for Grok Voice Agent
     */
    enum class Voice(val id: String, val displayName: String, val description: String) {
        ARA("human_Ara", "Ara", "Female • Warm, friendly"),
        REX("human_Rex", "Rex", "Male • Confident, clear"),
        SAL("human_Sal", "Sal", "Neutral • Smooth, balanced"),
        EVE("human_Eve", "Eve", "Female • Energetic, upbeat"),
        LEO("human_Leo", "Leo", "Male • Authoritative, strong")
    }
    
    /**
     * Voice session state
     */
    sealed class VoiceEvent {
        data class Connected(val sessionId: String) : VoiceEvent()
        data class AudioReceived(val audioData: ByteArray) : VoiceEvent() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as AudioReceived
                return audioData.contentEquals(other.audioData)
            }
            
            override fun hashCode(): Int = audioData.contentHashCode()
        }
        data class TranscriptReceived(val text: String, val isFinal: Boolean) : VoiceEvent()
        data class ResponseStarted(val responseId: String) : VoiceEvent()
        data class ResponseCompleted(val responseId: String) : VoiceEvent()
        data class Error(val message: String, val exception: Throwable? = null) : VoiceEvent()
        data object Disconnected : VoiceEvent()
    }
    
    /**
     * Connect to Voice Agent and start session
     */
    fun connect(
        apiKey: String,
        model: String = "grok-2-latest",
        voice: Voice = Voice.ARA,
        systemPrompt: String? = null
    ): Flow<VoiceEvent> = callbackFlow {
        try {
            val request = Request.Builder()
                .url(WEBSOCKET_URL)
                .header("Authorization", "Bearer $apiKey")
                .build()
            
            currentWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")
                    
                    // Send session.update message to configure session
                    val sessionConfig = buildMap {
                        put("type", "session.update")
                        put("session", buildMap {
                            put("modalities", listOf("text", "audio"))
                            put("voice", voice.id)
                            if (systemPrompt != null) {
                                put("instructions", systemPrompt)
                            }
                            put("input_audio_format", "pcm16")
                            put("output_audio_format", "pcm16")
                            put("input_audio_transcription", buildMap {
                                put("model", "whisper-1")
                            })
                            put("turn_detection", null) // Disable automatic turn detection, manual commit
                        })
                    }
                    
                    val json = gson.toJson(sessionConfig)
                    webSocket.send(json)
                    Log.i(TAG, "Sent session.update with voice: ${voice.id} (${voice.displayName})")
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        Log.d(TAG, "Received message: ${text.take(200)}")
                        val event = gson.fromJson(text, Map::class.java)
                        val type = event["type"] as? String
                        
                        when (type) {
                            "session.created", "session.updated" -> {
                                val session = event["session"] as? Map<*, *>
                                val sessionId = session?.get("id") as? String ?: "unknown"
                                trySend(VoiceEvent.Connected(sessionId))
                                Log.i(TAG, "Session ready: $sessionId (event: $type)")
                            }
                            
                            "conversation.created" -> {
                                // Conversation created, ready to start
                                val conversation = event["conversation"] as? Map<*, *>
                                val convId = conversation?.get("id") as? String ?: "unknown"
                                Log.d(TAG, "Conversation created: $convId")
                            }
                            
                            "ping" -> {
                                // Keepalive ping from server, just log it
                                Log.v(TAG, "Received ping")
                            }
                            
                            "conversation.item.input_audio_transcription.completed" -> {
                                val transcript = event["transcript"] as? String ?: ""
                                trySend(VoiceEvent.TranscriptReceived(transcript, true))
                                Log.d(TAG, "User transcript completed: $transcript")
                            }
                            
                            "response.output_audio.delta" -> {
                                val delta = event["delta"] as? String
                                if (!delta.isNullOrEmpty()) {
                                    val audioBytes = Base64.getDecoder().decode(delta)
                                    trySend(VoiceEvent.AudioReceived(audioBytes))
                                }
                            }
                            
                            "response.output_audio_transcript.delta" -> {
                                // Interim transcript of AI response (streaming)
                                val delta = event["delta"] as? String
                                if (!delta.isNullOrEmpty()) {
                                    Log.v(TAG, "AI transcript delta: $delta")
                                }
                            }
                            
                            "response.output_audio_transcript.done" -> {
                                // Final transcript of AI response
                                val transcript = event["transcript"] as? String ?: ""
                                trySend(VoiceEvent.TranscriptReceived(transcript, false))
                                Log.d(TAG, "AI transcript completed: $transcript")
                            }
                            
                            "response.created" -> {
                                val response = event["response"] as? Map<*, *>
                                val responseId = response?.get("id") as? String ?: "unknown"
                                trySend(VoiceEvent.ResponseStarted(responseId))
                                Log.d(TAG, "Response started: $responseId")
                            }
                            
                            "response.done" -> {
                                val response = event["response"] as? Map<*, *>
                                val responseId = response?.get("id") as? String ?: "unknown"
                                trySend(VoiceEvent.ResponseCompleted(responseId))
                                Log.d(TAG, "Response completed: $responseId")
                            }
                            
                            "error" -> {
                                val error = event["error"] as? Map<*, *>
                                val message = error?.get("message") as? String ?: "Unknown error"
                                trySend(VoiceEvent.Error(message))
                                Log.e(TAG, "Voice API error: $message")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing message", e)
                        trySend(VoiceEvent.Error("Failed to process message: ${e.message}", e))
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    Log.d(TAG, "Received binary message: ${bytes.size} bytes")
                    // Binary messages might be audio data
                    trySend(VoiceEvent.AudioReceived(bytes.toByteArray()))
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${response?.code}", t)
                    trySend(VoiceEvent.Error("Connection failed: ${t.message}", t))
                    close(t)
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code - $reason")
                    webSocket.close(1000, null)
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code - $reason")
                    trySend(VoiceEvent.Disconnected)
                    close()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Voice API", e)
            trySend(VoiceEvent.Error("Failed to connect: ${e.message}", e))
            close(e)
        }
        
        awaitClose {
            Log.d(TAG, "Closing voice session")
            disconnect()
        }
    }
    
    /**
     * Disconnect and close WebSocket
     */
    fun disconnect() {
        currentWebSocket?.let { ws ->
            try {
                ws.close(1000, "Session ended")
                Log.d(TAG, "WebSocket closed manually")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing WebSocket", e)
            }
        }
        currentWebSocket = null
    }
    
    /**
     * Send audio data to Voice Agent
     * @param audioData PCM16 audio bytes
     */
    fun sendAudio(audioData: ByteArray) {
        try {
            val webSocket = currentWebSocket ?: run {
                Log.w(TAG, "WebSocket not connected")
                return
            }
            
            val base64Audio = Base64.getEncoder().encodeToString(audioData)
            val message = buildMap {
                put("type", "input_audio_buffer.append")
                put("audio", base64Audio)
            }
            
            val json = gson.toJson(message)
            webSocket.send(json)
            Log.d(TAG, "Sent audio data: ${audioData.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio", e)
        }
    }
    
    /**
     * Commit audio buffer and trigger response
     */
    fun commitAudio() {
        try {
            val webSocket = currentWebSocket ?: run {
                Log.w(TAG, "WebSocket not connected")
                return
            }
            
            val message = mapOf("type" to "input_audio_buffer.commit")
            val json = gson.toJson(message)
            webSocket.send(json)
            Log.d(TAG, "Committed audio buffer")
        } catch (e: Exception) {
            Log.e(TAG, "Error committing audio", e)
        }
    }
    
    /**
     * Send text message (for debugging or text-only mode)
     */
    fun sendText(text: String) {
        try {
            val webSocket = currentWebSocket ?: run {
                Log.w(TAG, "WebSocket not connected")
                return
            }
            
            val message = buildMap {
                put("type", "conversation.item.create")
                put("item", buildMap {
                    put("type", "message")
                    put("role", "user")
                    put("content", listOf(
                        mapOf("type" to "input_text", "text" to text)
                    ))
                })
            }
            
            val json = gson.toJson(message)
            webSocket.send(json)
            
            // Request response
            val responseMessage = mapOf("type" to "response.create")
            webSocket.send(gson.toJson(responseMessage))
            
            Log.d(TAG, "Sent text message: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text", e)
        }
    }
    
    /**
     * Cancel current response
     */
    fun cancelResponse() {
        try {
            val webSocket = currentWebSocket ?: run {
                Log.w(TAG, "WebSocket not connected")
                return
            }
            
            val message = mapOf("type" to "response.cancel")
            val json = gson.toJson(message)
            webSocket.send(json)
            Log.d(TAG, "Cancelled response")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling response", e)
        }
    }
}
