package com.tutpro.baresip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lab operator directory (mirrors the lab server's 1000-1005 extensions). */
data class YmaxExtension(val name: String, val ext: String)

val YMAX_EXTENSIONS = listOf(
    YmaxExtension("اپراتور ۱", "1000"),
    YmaxExtension("اپراتور ۲", "1001"),
    YmaxExtension("اپراتور ۳", "1002"),
    YmaxExtension("اپراتور ۴", "1003"),
    YmaxExtension("اپراتور ۵", "1004"),
    YmaxExtension("اپراتور ۶", "1005")
)

/** Numeric in-app dialpad — no system keyboard for the dial box. */
@Composable
fun YmaxDialpad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                rowKeys.forEach { key ->
                    FilledTonalButton(
                        onClick = { onDigit(key) },
                        modifier = Modifier.size(width = 68.dp, height = 52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = key, fontSize = 22.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(modifier = Modifier.size(width = 68.dp, height = 52.dp))
            FilledTonalButton(
                onClick = onBackspace,
                modifier = Modifier.size(width = 68.dp, height = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "⌫", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.size(width = 68.dp, height = 52.dp))
        }
    }
}

/** Icon button with a Persian label under it. */
@Composable
fun YmaxLabeledIcon(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondary,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        }
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Network extension directory with one-tap call. */
@Composable
fun YmaxExtensionDirectory(onCall: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = "داخلی‌های شبکه",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            YMAX_EXTENSIONS.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.name, fontSize = 15.sp)
                        Text(
                            text = "داخلی ${item.ext}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onCall(item.ext) }) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "تماس با ${item.name}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
