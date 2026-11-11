package com.idealplayer.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idealplayer.app.core.audio.AudioChannelGroup
import com.idealplayer.app.core.audio.AudioExtractionState
import com.idealplayer.app.core.audio.AudioSourceClassifier
import com.idealplayer.app.core.audio.ExternalAudioState
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.model.Channel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalAudioBottomSheet(
    state: ExternalAudioState,
    onSelectChannel: (Channel) -> Unit,
    onStopAudio: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSyncOffsetChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181A20),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = "External Audio",
                        tint = IdealPlayerColors.Primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "مصدر الصوت الخارجي (SONIX)",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.isActive) {
                    FilledTonalButton(
                        onClick = onStopAudio,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                            contentColor = Color(0xFFEF5350)
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("العودة للصوت الأصلي", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active State & Controls Card
            if (state.isActive && state.selectedChannel != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF262A34)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.selectedChannel.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusColor = when (state.extractionState) {
                                        AudioExtractionState.PLAYING -> Color(0xFF4CAF50)
                                        AudioExtractionState.STREAM_OPENING,
                                        AudioExtractionState.DECODING_PCM,
                                        AudioExtractionState.OUTPUT_PRODUCING -> Color(0xFFFFB300)
                                        else -> Color(0xFFE53935)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = state.extractionState.displayNameAr,
                                        color = statusColor,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (!state.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.errorMessage,
                                color = Color(0xFFEF5350),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Delay / Sync Offset Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مزامنة الصوت: ${if (state.syncOffsetMs > 0) "+${state.syncOffsetMs}ms" else "${state.syncOffsetMs}ms"}",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                            Row {
                                OutlinedButton(
                                    onClick = { onSyncOffsetChange(state.syncOffsetMs - 250L) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("-250ms", fontSize = 11.sp, color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = { onSyncOffsetChange(0L) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("0", fontSize = 11.sp, color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = { onSyncOffsetChange(state.syncOffsetMs + 250L) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("+250ms", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Volume Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeDown,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Slider(
                                value = state.volume,
                                onValueChange = onVolumeChange,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = IdealPlayerColors.Primary,
                                    activeTrackColor = IdealPlayerColors.Primary,
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Channel Filter Chips
            var selectedFilter by remember { mutableStateOf<AudioChannelGroup?>(null) }
            var searchQuery by remember { mutableStateOf("") }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث في قنوات الصوت (beIN / ALWAN)...", fontSize = 13.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IdealPlayerColors.Primary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("الكل (${state.availableAudioSources.size})") }
                )
                FilterChip(
                    selected = selectedFilter == AudioChannelGroup.BEIN_SPORTS,
                    onClick = { selectedFilter = AudioChannelGroup.BEIN_SPORTS },
                    label = { Text("beIN SPORTS") }
                )
                FilterChip(
                    selected = selectedFilter == AudioChannelGroup.ALWAN,
                    onClick = { selectedFilter = AudioChannelGroup.ALWAN },
                    label = { Text("ALWAN") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Candidate Channels List
            val filteredChannels = remember(state.availableAudioSources, selectedFilter, searchQuery) {
                state.availableAudioSources.filter { ch ->
                    val matchesGroup = when (selectedFilter) {
                        null -> true
                        AudioChannelGroup.BEIN_SPORTS -> AudioSourceClassifier.classify(ch.name, ch.groupTitle) == AudioChannelGroup.BEIN_SPORTS
                        AudioChannelGroup.ALWAN -> AudioSourceClassifier.classify(ch.name, ch.groupTitle) == AudioChannelGroup.ALWAN
                        else -> true
                    }
                    val matchesSearch = searchQuery.isBlank() || ch.name.contains(searchQuery, ignoreCase = true)
                    matchesGroup && matchesSearch
                }
            }

            if (filteredChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.availableAudioSources.isEmpty())
                            "لم يتم العثور على قنوات beIN أو ALWAN في قائمة الاشتراك الحالية"
                        else
                            "لا توجد نتائج تطابق البحث",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { channel ->
                        val isSelected = state.selectedChannel?.id == channel.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectChannel(channel) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) IdealPlayerColors.Primary.copy(alpha = 0.2f) else Color(0xFF222630)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.VolumeUp else Icons.Filled.Radio,
                                        contentDescription = null,
                                        tint = if (isSelected) IdealPlayerColors.Primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = channel.name,
                                            color = if (isSelected) IdealPlayerColors.Primary else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (channel.groupTitle.isNotBlank()) {
                                            Text(
                                                text = channel.groupTitle,
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = IdealPlayerColors.Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
