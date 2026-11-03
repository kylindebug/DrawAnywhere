package com.shezik.drawanywhere.view.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.shezik.drawanywhere.R

/** All secondary controls are reachable from the single settings button. */
@Composable
internal fun SimpleSettings(buttons: Map<String, ToolbarButton>) {
    var section by remember { mutableStateOf("tool_controls") }
    Row {
        for (id in listOf("undo", "redo")) RenderButton(buttons.getValue(id), Alignment.TopCenter)
    }
    Row {
        for (id in listOf("tool_controls", "color_picker", "settings")) {
            val button = buttons.getValue(id)
            TextButton(onClick = { section = id }) { Icon(button.icon, button.contentDescription) }
        }
    }
    HorizontalDivider()
    val selected = buttons.getValue(section)
    Text(selected.contentDescription, style = MaterialTheme.typography.titleSmall)
    if (section == "color_picker" && !selected.isEnabled) {
        Text(stringResource(R.string.tool_controls))
    } else selected.popupPages.forEach { it() }
}
