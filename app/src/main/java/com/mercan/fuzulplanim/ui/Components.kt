package com.mercan.fuzulplanim.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mercan.fuzulplanim.util.tl0

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier.fillMaxWidth()
    Card(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    accent: Color = Blue,
    note: String? = null,
    modifier: Modifier = Modifier
) {
    AppCard(modifier) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 4.dp))
        if (!note.isNullOrBlank()) {
            Text(note, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Ink, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Pill(text: String, color: Color = Teal) {
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = .1f)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
fun EmptyState(title: String, body: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    AppCard {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(body, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        if (actionText != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 14.dp)) { Text(actionText) }
        }
    }
}

@Composable
fun MiniLineChart(values: List<Double>, modifier: Modifier = Modifier, color: Color = Teal) {
    if (values.size < 2) return
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 1.0
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier.height(110.dp).fillMaxWidth()) {
        val pts = values.mapIndexed { i, v ->
            val x = size.width * i / (values.size - 1).toFloat()
            val y = size.height - ((v - min) / span).toFloat() * size.height * .82f - size.height * .09f
            x to y
        }
        val path = Path()
        path.moveTo(pts.first().first, pts.first().second)
        for (i in 1 until pts.size) path.lineTo(pts[i].first, pts[i].second)
        drawPath(path, color = color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        pts.forEach { drawCircle(color, radius = 5f, center = androidx.compose.ui.geometry.Offset(it.first, it.second)) }
    }
}

@Composable
fun ExpenseBars(items: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val max = items.maxOfOrNull { kotlin.math.abs(it.second) }?.takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (name, value) ->
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(name, fontSize = 12.sp, color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tl0(value), fontSize = 12.sp, color = Muted)
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(7.dp).background(Border, RoundedCornerShape(999.dp))) {
                    Box(Modifier.fillMaxWidth((kotlin.math.abs(value) / max).toFloat().coerceIn(0f, 1f)).height(7.dp).background(Blue, RoundedCornerShape(999.dp)))
                }
            }
        }
    }
}

@Composable
fun FormField(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text
        )
    )
}
