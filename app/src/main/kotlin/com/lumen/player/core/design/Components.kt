package com.lumen.player.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class PillStyle { FILLED, OUTLINED, EMBER }

// open eye: the songs are in the Library; crossed eye in the accent colour: they are kept out of it
@Composable
fun HideToggle(hidden: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.accent
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (hidden) accent.copy(alpha = 0.14f) else Color.Transparent)
            .then(if (hidden) Modifier else Modifier.border(BorderStroke(1.dp, LumenColors.line), CircleShape))
            .clickable(onClick = onClick)
            .testTag("hide_toggle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            if (hidden) "Show songs in Library" else "Keep songs out of Library",
            tint = if (hidden) accent else LumenColors.paper,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.FILLED,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val accent = LocalAccent.current.accent
    val (background, content) = when (style) {
        PillStyle.FILLED -> LumenColors.paper to LumenColors.ink
        PillStyle.OUTLINED -> Color.Transparent to LumenColors.paper
        PillStyle.EMBER -> accent.copy(alpha = 0.14f) to accent
    }
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (enabled) background else background.copy(alpha = background.alpha * 0.4f))
            .then(if (style == PillStyle.OUTLINED) Modifier.border(BorderStroke(1.dp, LumenColors.line), CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = if (enabled) content else content.copy(alpha = 0.4f)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = LumenType.body, color = color)
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LumenType.displayLarge, color = LumenColors.paper, modifier = modifier.padding(horizontal = Space.side))
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, overline: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.side, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null) Text(overline.uppercase(), style = LumenType.overline, color = LumenColors.paper3)
            Text(title, style = LumenType.title, color = LumenColors.paper)
        }
        if (action != null) {
            Text(
                action,
                style = LumenType.label,
                color = LumenColors.paper2,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(8.dp),
            )
        }
    }
}

@Composable
fun SegmentedTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.side),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .testTag("tab_$label")
                    .clip(CircleShape)
                    .background(if (active) LumenColors.ink3 else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, style = LumenType.label, color = if (active) LumenColors.paper else LumenColors.paper3)
            }
        }
    }
}

@Composable
fun EmptyState(serifLine: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(serifLine, style = LumenType.display, color = LumenColors.paper, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = LumenType.body, color = LumenColors.paper2, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            PillButton(action, onAction, modifier = Modifier.testTag("empty_action"))
        }
    }
}
