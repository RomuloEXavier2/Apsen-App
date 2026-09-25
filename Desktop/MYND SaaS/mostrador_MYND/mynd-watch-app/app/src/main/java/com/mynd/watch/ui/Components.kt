package com.mynd.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HudCard(borderColor: Color = CyanDark, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = borderColor.copy(alpha = 0.3f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 8.dp.toPx())
                )
            }
            .background(BgCard, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
    ) { content() }
}

@Composable
internal fun CyanButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = CyanDark, contentColor = Cyan)
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
internal fun OutlineAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanDark),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> HudDropdown(label: String, selected: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: selected.toString()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel, onValueChange = {}, readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan, unfocusedBorderColor = CyanDark,
                focusedTextColor = TxtPri, unfocusedTextColor = TxtPri,
                focusedLabelColor = TxtSec, unfocusedLabelColor = TxtSec
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = BgCard) {
            options.forEach { (opt, lbl) ->
                DropdownMenuItem(text = { Text(lbl, color = TxtPri, fontSize = 13.sp) },
                    onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
internal fun HudTextField(value: String, onChange: (String) -> Unit, label: String, isPassword: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan, unfocusedBorderColor = CyanDark,
            focusedLabelColor = Cyan, unfocusedLabelColor = TxtSec,
            cursorColor = Cyan,
            focusedTextColor = TxtPri, unfocusedTextColor = TxtPri,
            focusedContainerColor = BgField, unfocusedContainerColor = BgField
        ),
        shape = RoundedCornerShape(8.dp)
    )
}
