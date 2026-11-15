package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_conversion_banner_dismiss
import eolssu.shared.generated.resources.sheet_conversion_banner_title
import org.jetbrains.compose.resources.stringResource

/** Completion notices stay in a separate top lane until opened or dismissed. */
@Composable
fun SheetCompletionBannerHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) { data ->
        val colors = EolssuTheme.colors
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.TopCenter) {
            Surface(
                modifier = Modifier.widthIn(max = CompactContentMaxWidth).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = colors.playSoft, contentColor = colors.text,
                border = BorderStroke(1.dp, colors.play), shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(EolssuIcons.Check, contentDescription = null, tint = colors.play)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.sheet_conversion_banner_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(data.visuals.message, style = MaterialTheme.typography.bodyLarge)
                    }
                    data.visuals.actionLabel?.let { label ->
                        FilledTonalButton(onClick = data::performAction) { Text(label) }
                    }
                    IconButton(onClick = data::dismiss) {
                        Icon(EolssuIcons.Close, stringResource(Res.string.sheet_conversion_banner_dismiss))
                    }
                }
            }
        }
    }
}
