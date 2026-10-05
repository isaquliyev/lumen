package com.lumen.player.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun <T> SortChip(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(LumenColors.ink3)
                .clickable { open = true }
                .testTag("sort_chip")
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(selected), style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = LumenColors.paper2, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = LumenColors.ink2) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = LumenType.body, color = LumenColors.paper) },
                    trailingIcon = { if (option == selected) Icon(Icons.Rounded.Check, null, tint = LocalAccent.current.accent) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag("sort_${label(option)}"),
                )
            }
        }
    }
}

@Composable
fun PlayFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(LumenColors.paper)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, "Play", tint = LumenColors.ink, modifier = Modifier.size(30.dp))
    }
}

// A-Z fast-scroll rail: tap or drag to jump to the first item with that letter
@Composable
fun AlphabetRail(letters: List<Char>, onLetter: (Char) -> Unit, modifier: Modifier = Modifier) {
    if (letters.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    var current by remember { mutableStateOf<Char?>(null) }
    fun pick(y: Float, height: Int) {
        val index = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != current) {
            current = letter
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            onLetter(letter)
        }
    }
    Column(
        modifier
            .fillMaxHeight()
            .width(24.dp)
            .testTag("alphabet_rail")
            .pointerInput(letters) {
                detectTapGestures { pick(it.y, size.height) }
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(onDragEnd = { current = null }) { change, _ -> pick(change.position.y, size.height) }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    letter.toString(),
                    style = LumenType.overline,
                    color = if (letter == current) LocalAccent.current.accent else LumenColors.paper3,
                )
            }
        }
    }
}

@Composable
fun SpacerW(width: Int) = Spacer(Modifier.width(width.dp))
