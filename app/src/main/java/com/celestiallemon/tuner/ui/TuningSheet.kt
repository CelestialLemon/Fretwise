package com.celestiallemon.tuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiallemon.tuner.Instrument
import com.celestiallemon.tuner.Instruments
import com.celestiallemon.tuner.Tuning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningSheet(
    instrument: Instrument,
    tuning: Tuning,
    a4: Double,
    onSelect: (Instrument, Tuning) -> Unit,
    onA4Change: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var browsing by remember { mutableStateOf(instrument) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
        contentColor = Palette.Bone,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in Instruments.All) {
                    val selected = option == browsing
                    Text(
                        option.name,
                        color = if (selected) Palette.Background else Palette.Bone,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Palette.Brass else Color.Transparent)
                            .border(1.dp, if (selected) Palette.Brass else Palette.Outline, CircleShape)
                            .clickable(role = Role.Tab) {
                                browsing = option
                                if (option.isChromatic) onSelect(option, option.tunings.first())
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(browsing.tunings, key = { it.id }) { option ->
                    val current = browsing == instrument && option == tuning
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(browsing, option) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.name,
                                color = if (current) Palette.Brass else Palette.Bone,
                                fontFamily = Display,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                            )
                            if (option.strings.isNotEmpty()) {
                                Text(
                                    option.strings.joinToString("  ") { it.letter },
                                    color = Palette.Muted,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                        if (current) Icon(Icons.Rounded.Check, contentDescription = "Current tuning", tint = Palette.Brass)
                    }
                }
            }
            ReferencePitch(a4, onA4Change)
        }
    }
}

@Composable
private fun ReferencePitch(a4: Double, onChange: (Double) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.SurfaceHigh)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Reference pitch", color = Palette.Bone, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                if (a4 == 440.0) "Concert pitch" else "Tap the number to reset to 440",
                color = Palette.Muted,
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = { onChange(a4 - 1) }) {
            Icon(Icons.Rounded.Remove, contentDescription = "Lower reference pitch", tint = Palette.Bone)
        }
        Text(
            "A4 = ${formatHz(a4)} Hz",
            color = if (a4 == 440.0) Palette.Bone else Palette.Brass,
            fontFamily = Display,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Reset to 440 Hz") { onChange(440.0) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
        IconButton(onClick = { onChange(a4 + 1) }) {
            Icon(Icons.Rounded.Add, contentDescription = "Raise reference pitch", tint = Palette.Bone)
        }
        Spacer(Modifier.width(2.dp))
    }
}
