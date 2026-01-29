package com.yourown.ai.presentation.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourown.ai.data.remote.xai.XAIVoiceClient
import kotlin.math.sin

/**
 * Voice control panel - microphone button and waveform
 */
@Composable
fun VoiceControlPanel(
    isRecording: Boolean,
    isPlaying: Boolean,
    audioLevel: Float,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStopPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Waveform indicator
            AudioWaveform(
                audioLevel = audioLevel,
                isActive = isRecording || isPlaying,
                isRecording = isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
            
            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop TTS button (only show when playing)
                if (isPlaying) {
                    FloatingActionButton(
                        onClick = onStopPlayback,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(24.dp))
                    }
                }
                
                // Main microphone button
                FloatingActionButton(
                    onClick = {
                        if (isRecording) onStopRecording() else onStartRecording()
                    },
                    containerColor = if (isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (isRecording) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop recording" else "Start recording",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            
            // Status text
            Text(
                text = when {
                    isRecording -> "Listening..."
                    isPlaying -> "Speaking..."
                    else -> "Tap to speak"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Audio waveform visualization
 */
@Composable
fun AudioWaveform(
    audioLevel: Float,
    isActive: Boolean,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    // Animate phase for wave movement
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    // Animate amplitude
    val targetAmplitude = if (isActive) (audioLevel.coerceIn(0.1f, 1f)) else 0.1f
    val amplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(100),
        label = "amplitude"
    )
    
    val color = when {
        isRecording -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        val path = Path()
        val points = 100
        
        for (i in 0..points) {
            val x = (i.toFloat() / points) * width
            
            // Create sine wave with multiple frequencies
            val wave1 = sin((i.toFloat() / points * 4 + phase / 60) * Math.PI * 2).toFloat()
            val wave2 = sin((i.toFloat() / points * 2 + phase / 40) * Math.PI * 2).toFloat()
            val wave3 = sin((i.toFloat() / points * 6 + phase / 80) * Math.PI * 2).toFloat()
            
            val y = centerY + (wave1 * 0.5f + wave2 * 0.3f + wave3 * 0.2f) * amplitude * centerY * 0.8f
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Voice selector dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelector(
    selectedVoice: XAIVoiceClient.Voice,
    onVoiceSelect: (XAIVoiceClient.Voice) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedVoice.displayName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Voice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            XAIVoiceClient.Voice.entries.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = voice.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = voice.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onVoiceSelect(voice)
                        expanded = false
                    },
                    leadingIcon = {
                        if (voice == selectedVoice) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Message bubble for voice chat
 */
@Composable
fun VoiceMessageBubble(
    message: VoiceMessage,
    onViewLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Message header with logs button (for AI messages)
                if (!isUser && message.requestLogs != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onViewLogs,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = "View Request Logs",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
    }
}

/**
 * Connection status indicator
 */
@Composable
fun ConnectionStatus(
    isConnected: Boolean,
    isConnecting: Boolean,
    sessionId: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isConnected -> MaterialTheme.colorScheme.primary
                        isConnecting -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }
                )
        )
        
        Text(
            text = when {
                isConnected -> "Connected"
                isConnecting -> "Connecting..."
                else -> "Disconnected"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (sessionId != null) {
            Text(
                text = "• ${sessionId.take(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Transcript preview (shows interim transcript while recording)
 */
@Composable
fun TranscriptPreview(
    transcript: String,
    modifier: Modifier = Modifier
) {
    if (transcript.isNotEmpty()) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
