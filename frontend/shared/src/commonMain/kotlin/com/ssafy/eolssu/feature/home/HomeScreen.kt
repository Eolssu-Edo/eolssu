package com.ssafy.eolssu.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.home.component.HeroScroll
import com.ssafy.eolssu.feature.home.component.HomeIcons
import com.ssafy.eolssu.feature.home.component.InstrumentSheet
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_brand
import eolssu.shared.generated.resources.home_change_instrument
import eolssu.shared.generated.resources.home_current_instrument
import eolssu.shared.generated.resources.home_feature_fingering
import eolssu.shared.generated.resources.home_feature_learn
import eolssu.shared.generated.resources.home_feature_reading
import eolssu.shared.generated.resources.home_feature_song_select
import eolssu.shared.generated.resources.home_hero_body
import eolssu.shared.generated.resources.home_hero_title
import eolssu.shared.generated.resources.home_section_practice
import eolssu.shared.generated.resources.home_start_evaluation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** 홈의 연습·학습 카드. 연결된 화면이 생기기 전까지는 모두 '준비 중' 안내를 띄운다. */
enum class HomeFeature(val label: StringResource, val icon: ImageVector) {
    SongSelect(Res.string.home_feature_song_select, HomeIcons.Flute),
    Fingering(Res.string.home_feature_fingering, HomeIcons.Hand),
    Reading(Res.string.home_feature_reading, HomeIcons.Grid),
    Learn(Res.string.home_feature_learn, HomeIcons.Book),
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onOpenInstrumentSheet: () -> Unit,
    onDismissInstrumentSheet: () -> Unit,
    onSelectInstrument: (Instrument) -> Unit,
    onStartEvaluation: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit,
) {
    val brand = stringResource(Res.string.home_brand)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = EolssuTheme.colors.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 30.dp, bottom = 24.dp),
            ) {
                Text(brand, style = EolssuTheme.serif.brand)
                InstrumentChip(state.selectedInstrument, onOpenInstrumentSheet)
                HeroScroll(
                    title = stringResource(Res.string.home_hero_title),
                    body = stringResource(Res.string.home_hero_body),
                    actionLabel = stringResource(Res.string.home_start_evaluation),
                    brand = brand,
                    onAction = onStartEvaluation,
                    modifier = Modifier.padding(top = 30.dp, bottom = 18.dp),
                )
                Text(
                    stringResource(Res.string.home_section_practice),
                    style = MaterialTheme.typography.labelLarge,
                    color = EolssuTheme.colors.textMuted,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                )
                FeatureGrid(onFeatureClick)
            }
        }
    }
    if (state.isInstrumentSheetOpen) {
        InstrumentSheet(
            instruments = state.instruments,
            selected = state.selectedInstrument,
            onSelect = onSelectInstrument,
            onDismiss = onDismissInstrumentSheet,
        )
    }
}

@Composable
private fun InstrumentChip(instrument: Instrument, onClick: () -> Unit) {
    val name = stringResource(instrument.label)
    val description = stringResource(Res.string.home_change_instrument, name)
    Row(
        Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.home_current_instrument),
            style = MaterialTheme.typography.bodySmall,
            color = EolssuTheme.colors.textMuted,
        )
        OutlinedButton(
            onClick = onClick,
            shape = CircleShape,
            border = BorderStroke(1.dp, EolssuTheme.colors.line),
            modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = description },
        ) {
            Icon(HomeIcons.Flute, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
            Icon(HomeIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(12.dp), tint = EolssuTheme.colors.textMuted)
        }
    }
}

@Composable
private fun FeatureGrid(onFeatureClick: (HomeFeature) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeFeature.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { feature ->
                    FeatureCard(feature, onClick = { onFeatureClick(feature) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: HomeFeature, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 104.dp),
        shape = MaterialTheme.shapes.medium,
        color = EolssuTheme.colors.background,
        border = BorderStroke(1.dp, EolssuTheme.colors.line),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(34.dp).background(EolssuTheme.colors.surface, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(feature.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(stringResource(feature.label), style = MaterialTheme.typography.titleSmall)
        }
    }
}
