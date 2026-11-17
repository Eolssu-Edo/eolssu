package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import com.ssafy.eolssu.feature.song.edit.accompanimentName

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.offset
import eolssu.shared.generated.resources.sheetedit_redo
import eolssu.shared.generated.resources.sheet_adapt_tab
import eolssu.shared.generated.resources.sheetedit_undo
import eolssu.shared.generated.resources.sheetedit_panel_collapse
import eolssu.shared.generated.resources.sheetedit_panel_expand
import eolssu.shared.generated.resources.sheetedit_panel_hint
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import eolssu.shared.generated.resources.sheetedit_register_jungcheong_short
import eolssu.shared.generated.resources.sheetedit_register_baetak_short
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.core.ui.score.ComposedGlyph
import com.ssafy.eolssu.core.ui.score.ScoreSigimsaeGlyph
import com.ssafy.eolssu.core.ui.score.scoreSigimsaeImage
import com.ssafy.eolssu.feature.song.edit.BaseYuls
import com.ssafy.eolssu.feature.song.edit.EditTab
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.Sigimsae
import com.ssafy.eolssu.feature.song.edit.sigimsaeConflicts
import com.ssafy.eolssu.feature.song.edit.YulRegister
import com.ssafy.eolssu.feature.song.edit.isAvailablePitch
import com.ssafy.eolssu.feature.song.edit.isPitch
import com.ssafy.eolssu.feature.song.edit.pitchKey
import com.ssafy.eolssu.feature.song.edit.pitchNote
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_cell_add_column
import eolssu.shared.generated.resources.sheetedit_yul_octave_caption
import eolssu.shared.generated.resources.sheetedit_yul_pitch_caption
import eolssu.shared.generated.resources.sheetedit_cell_delete_column
import eolssu.shared.generated.resources.sheetedit_cell_jangdan_change
import eolssu.shared.generated.resources.sheetedit_cell_jangdan_desc
import eolssu.shared.generated.resources.sheetedit_cell_merge
import eolssu.shared.generated.resources.sheetedit_cell_split
import eolssu.shared.generated.resources.sheetedit_erase
import eolssu.shared.generated.resources.sheetedit_key_hold
import eolssu.shared.generated.resources.sheetedit_key_input
import eolssu.shared.generated.resources.sheetedit_key_rest
import eolssu.shared.generated.resources.sheetedit_key_unavailable
import eolssu.shared.generated.resources.sheetedit_register_cheong
import eolssu.shared.generated.resources.sheetedit_register_base
import eolssu.shared.generated.resources.sheetedit_register_tak
import eolssu.shared.generated.resources.sheetedit_sig_chuseong
import eolssu.shared.generated.resources.sheetedit_sig_conflict_description
import eolssu.shared.generated.resources.sheetedit_sig_conflict_hint
import eolssu.shared.generated.resources.sheetedit_sig_jeonseong
import eolssu.shared.generated.resources.sheetedit_sig_limit
import eolssu.shared.generated.resources.sheetedit_sig_need_pitch
import eolssu.shared.generated.resources.sheetedit_sig_none
import eolssu.shared.generated.resources.sheetedit_sig_remove
import eolssu.shared.generated.resources.sheetedit_sig_toeseong
import eolssu.shared.generated.resources.sheetedit_sig_yoseong
import eolssu.shared.generated.resources.sheetedit_tab_cell
import eolssu.shared.generated.resources.sheetedit_tab_sigimsae
import eolssu.shared.generated.resources.sheetedit_tab_yul
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** 편집 입력판이 부르는 동작. */
class EditInputActions(
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onSelectTab: (EditTab) -> Unit,
    val onPut: (ScoreNote) -> Unit,
    val onPutYul: (Yul) -> Unit,
    val onSelectRegister: (YulRegister) -> Unit,
    val onErase: () -> Unit,
    val onToggleSigimsae: (Sigimsae) -> Unit,
    val onRemoveSigimsae: (Int) -> Unit,
    val onSplit: () -> Unit,
    val onMerge: () -> Unit,
    val onAddColumn: () -> Unit,
    val onDeleteColumn: () -> Unit,
    val onChangeJangdan: () -> Unit,
    val onSetPanelExpanded: (Boolean) -> Unit = {},
)

/**
 * EDIT-02 아래 입력판: 실행 취소·다시 실행과 실제 저장 상태, 탭 율명·기호 / 시김새 / 정간·장단.
 *
 * @param register 다음 율명에 붙일 음역 단계.
 * @param availablePitches 서버에 등록된 (율명, 옥타브). null이면 모두 누를 수 있고 서버 저장 때 확인한다.
 * @param docked 태블릿 가로에서 악보 오른쪽 칸에 세울 때 true. 네 모서리를 둥글게 하고 12율 자판을 6개씩 두 줄로 놓아 가로로 밀지 않게 한다.
 */
@Composable
internal fun EditInputPanel(
    draft: SheetDraft,
    selected: Int,
    selectedSub: Int?,
    tab: EditTab,
    register: YulRegister,
    availablePitches: Set<Pair<String, Int>>?,
    canUndo: Boolean,
    canRedo: Boolean,
    actions: EditInputActions,
    modifier: Modifier = Modifier,
    saveFeedback: @Composable () -> Unit = {},
    adaptationContent: @Composable () -> Unit = {},
    docked: Boolean = false,
    expanded: Boolean = true,
) {
    val colors = EolssuTheme.colors
    val shape = if (docked) RoundedCornerShape(20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val tabsScroll = rememberScrollState()
    LaunchedEffect(tab) { if (tab == EditTab.Adaptation) tabsScroll.animateScrollTo(0) }
    Column(
        modifier
            .fillMaxWidth()
            // 회색 바탕 위 테두리 없는 흰 자판(설정 시트와 같은 묶음 모양, S15P31E102-768).
            .background(colors.groupedBackground, shape)
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 버튼은 48dp 터치 영역 가운데에 40dp로 그려진다. 보이는 왼쪽 끝을 아래 탭과 맞추려고 줄을 4dp 당긴다.
        Row(Modifier.fillMaxWidth().height(48.dp).offset(x = (-4).dp), verticalAlignment = Alignment.CenterVertically) {
            SquareIconButton(SheetEditIcons.Undo, stringResource(Res.string.sheetedit_undo), actions.onUndo, enabled = canUndo)
            SquareIconButton(SheetEditIcons.Redo, stringResource(Res.string.sheetedit_redo), actions.onRedo, enabled = canRedo)
            Box(Modifier.weight(1f).offset(x = 4.dp), contentAlignment = Alignment.CenterEnd) {
                // 접혀 있으면 펼침 버튼 옆에서 아래 입력판으로 직접 고칠 수 있음을 알린다(S15P31E102-1042). 글자를 눌러도 펼친다.
                if (expanded) saveFeedback()
                else Text(stringResource(Res.string.sheetedit_panel_hint),
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { actions.onSetPanelExpanded(true) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = colors.textSub, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            InputPanelToggle(expanded, onToggle = { actions.onSetPanelExpanded(!expanded) }, Modifier.offset(x = 8.dp))
        }
        // 접으면 실행 취소 줄 한 줄만 남겨 짧은 휴대폰에서도 정간보를 넓게 본다(S15P31E102-972).
        if (!expanded) return@Column
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(Modifier.horizontalScroll(tabsScroll)) {
                SegmentTabs(
                    labels = listOf(stringResource(Res.string.sheet_adapt_tab), stringResource(Res.string.sheetedit_tab_yul), stringResource(Res.string.sheetedit_tab_sigimsae), stringResource(Res.string.sheetedit_tab_cell)),
                    selected = tab.ordinal,
                    onSelect = { actions.onSelectTab(EditTab.entries[it]) },
                    fill = false,
                )
            }
        }
        when (tab) {
            EditTab.Adaptation -> adaptationContent()
            EditTab.Yul -> YulKeys(draft, selected, selectedSub, register, availablePitches, actions, wrapKeys = docked)
            EditTab.Sigimsae -> SigimsaeKeys(draft, selected, draft.sigimsaeSub(selected, selectedSub), actions)
            EditTab.Cell -> CellTools(draft, selected, actions)
        }
    }
}

/** 입력판 접기·펼치기(누르는 칸 48dp). 펼쳐 있으면 아래 화살표, 접혀 있으면 위 화살표를 보인다. */
@Composable
internal fun InputPanelToggle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    SquareIconButton(
        EolssuIcons.ChevronDown,
        stringResource(if (expanded) Res.string.sheetedit_panel_collapse else Res.string.sheetedit_panel_expand),
        onToggle,
        modifier.rotate(if (expanded) 0f else 180f),
    )
}

/** 처음부터 있던 네 시김새는 앱 문구로, 나머지는 공통 렌더러에 등록된 이름 그대로 적는다. */
@Composable
internal fun sigimsaeLabel(value: Sigimsae): String = legacySigimsaeName(value)?.let { stringResource(it) } ?: value.name

private fun legacySigimsaeName(value: Sigimsae): StringResource? = when (value.name) {
    Sigimsae.Yoseong.name -> Res.string.sheetedit_sig_yoseong
    Sigimsae.Toeseong.name -> Res.string.sheetedit_sig_toeseong
    Sigimsae.Chuseong.name -> Res.string.sheetedit_sig_chuseong
    Sigimsae.Jeonseong.name -> Res.string.sheetedit_sig_jeonseong
    else -> null
}

/**
 * 율명·기호 탭: 다섯 음역 직접 선택, 12율 가로 스크롤 자판, 지속(ㅡ)·쉼(△)·지우기.
 * 자판 글자는 지금 음역 단계를 붙인 모양(청성이면 汰)으로 바뀌고, 서버에 등록되지 않은 음은 흐리게 막는다.
 * 기본음을 처음 선택하고, 어느 음역이든 한 번 눌러 직접 고른다.
 */
@Composable
private fun YulKeys(
    draft: SheetDraft,
    selected: Int,
    selectedSub: Int?,
    register: YulRegister,
    available: Set<Pair<String, Int>>?,
    actions: EditInputActions,
    wrapKeys: Boolean = false,
) {
    val colors = EolssuTheme.colors
    // 소박을 골랐으면 그 소박의 음만, 아니면 정간의 모든 음을 자판에 표시한다.
    val notes = selectedSub?.let { listOfNotNull(draft.cells[selected].getOrNull(it)) } ?: draft.cells[selected]
    val current = notes.mapNotNull { it.pitchKey() }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 윗줄은 옥타브(음역), 아랫줄은 음(율명)을 고른다는 것을 줄 위에 적는다(S15P31E102-1042).
        RowCaption(stringResource(Res.string.sheetedit_yul_octave_caption))
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YulRegister.entries.forEach { option ->
                RegisterButton(
                    register = option,
                    name = registerShortName(option),
                    on = register == option,
                    onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_01, { actions.onSelectRegister(option) }),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val yulKey: @Composable (base: Yul) -> Unit = { base ->
            YulKey(
                note = draft.pitchNote(base, register.octave),
                enabled = isAvailablePitch(available, base, register.octave),
                selected = (base to register.octave) in current,
                onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_02, { actions.onPutYul(base) }),
            )
        }
        RowCaption(stringResource(Res.string.sheetedit_yul_pitch_caption))
        if (wrapKeys) {
            // 오른쪽 칸 안쪽 너비(약 372dp)에 52dp 자판 6개(342dp)가 들어가므로 두 줄로 모두 보인다.
            BaseYuls.chunked(YulKeysPerWrappedRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { base -> key(base.name) { yulKey(base) } }
                }
            }
        } else {
            val listState = rememberLazyListState()
            Box {
                LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(BaseYuls, key = { it.name }) { base -> yulKey(base) }
                }
                ScrollFades(listState)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbolKey(ScoreNote.Hold, stringResource(Res.string.sheetedit_key_hold), ScoreNote.Hold in notes) { actions.onPut(ScoreNote.Hold) }
            SymbolKey(ScoreNote.Rest, stringResource(Res.string.sheetedit_key_rest), ScoreNote.Rest in notes) { actions.onPut(ScoreNote.Rest) }
            Box(Modifier.weight(1f))
            val eraseLabel = stringResource(Res.string.sheetedit_erase)
            Surface(
                onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_03, actions.onErase),
                modifier = Modifier.width(64.dp).height(48.dp).semantics { contentDescription = eraseLabel },
                shape = RoundedCornerShape(10.dp),
                color = colors.primary,
                contentColor = colors.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(SheetEditIcons.Erase, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** 오른쪽 칸에 세운 입력판에서 12율 자판 한 줄에 놓는 수. */
private const val YulKeysPerWrappedRow = 6

/** 버튼 아래에 적는 다섯 음역 이름. */
@Composable
private fun registerShortName(register: YulRegister): String = stringResource(
    when (register) {
        YulRegister.BaeTak -> Res.string.sheetedit_register_baetak_short
        YulRegister.Tak -> Res.string.sheetedit_register_tak
        YulRegister.Base -> Res.string.sheetedit_register_base
        YulRegister.Cheong -> Res.string.sheetedit_register_cheong
        YulRegister.JungCheong -> Res.string.sheetedit_register_jungcheong_short
    },
)

/**
 * 음역 버튼: 변과 한글 이름을 각각 가운데에 놓는다. 기본의 변 공간은 비워 두되 한글 기준선을 유지한다.
 * 기존 정간보 렌더러와 같은 부수(彳·亻·氵·氵氵)를 사용하고 선택은 연두 바탕·테두리·라디오 상태로 구별한다.
 */
@Composable
private fun RegisterButton(register: YulRegister, name: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val radical = when (register) {
        YulRegister.BaeTak -> "彳"
        YulRegister.Tak -> "亻"
        YulRegister.Base -> ""
        YulRegister.Cheong -> "氵"
        YulRegister.JungCheong -> "氵氵"
    }
    Surface(
        onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_04, onClick),
        modifier = modifier.heightIn(min = 52.dp).semantics {
            role = Role.RadioButton
            selected = on
            contentDescription = name
        },
        shape = RoundedCornerShape(10.dp),
        color = if (on) colors.primary else colors.surface,
        contentColor = if (on) colors.onPrimary else colors.text,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                if (register == YulRegister.BaeTak) {
                    // 彳은 글꼴에서 전각 폭의 가운데를 쓰지만 亻·氵는 왼쪽 절반만 쓴다.
                    Text(radical, style = EolssuTheme.serif.yul.copy(fontSize = 19.sp, lineHeight = 19.sp))
                } else if (radical.isNotEmpty()) {
                    // 왼쪽 절반 부수는 렌더러와 같은 폭으로 줄여야 한글 중심과 맞는다.
                    ComposedGlyph(radical, "", 19.sp, 19.sp, overlap = 0.45f)
                }
            }
            Text(name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = if (on) colors.onPrimary else colors.textMuted, maxLines = 1)
        }
    }
}

/** 율명 자판 한 칸(52dp): 지금 음역의 정간보 글자와 한글 이름. 고른 정간에 이미 있는 음이면 먹색 테두리다. */
@Composable
private fun YulKey(note: ScoreNote, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val source = note as? ScoreNote.SourcePitch
    val hangul = source?.hangul ?: (note as? ScoreNote.Pitch)?.yul?.hangul.orEmpty()
    val fullName = source?.fullName ?: (note as? ScoreNote.Pitch)?.yul?.fullName.orEmpty()
    val description = if (enabled) stringResource(Res.string.sheetedit_key_input, fullName) else stringResource(Res.string.sheetedit_key_unavailable, fullName)
    // 낼 수 없는 음도 흐리게만 두고 누를 수 있게 한다. 누르면 악보는 그대로 두고 이유를 알린다(S15P31E102-1080).
    Surface(
        onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_05, onClick),
        modifier = Modifier.width(52.dp).height(58.dp).alpha(if (enabled) 1f else 0.35f).semantics {
            contentDescription = description
            this.selected = selected
        },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.accentTint else colors.surface,
        contentColor = colors.text,
        border = if (selected) BorderStroke(1.5.dp, colors.accent) else null,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 4.dp)) {
            Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                // 한글 대체가 필요한 표기는 작게, 보조 폰트의 세 율명은 공통 렌더러에서 한자로 그린다.
                if (source != null && source.symbol == source.hangul) {
                    Text(hangul, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                } else {
                    ScoreNoteGlyph(note, 19.sp)
                }
            }
            Text(hangul, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = colors.textMuted, maxLines = 1)
        }
    }
}

/** 지속(ㅡ)·쉼(△) 자판. 음역과 상관없이 같은 기호를 적는다. */
@Composable
private fun SymbolKey(note: ScoreNote, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val description = stringResource(Res.string.sheetedit_key_input, label)
    Surface(
        onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_06, onClick),
        modifier = Modifier.width(52.dp).height(48.dp).semantics { contentDescription = description; this.selected = selected },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.accentTint else colors.surface,
        contentColor = colors.text,
        border = if (selected) BorderStroke(1.5.dp, colors.accent) else null,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(note.symbol, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 19.sp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = colors.textMuted)
        }
    }
}

/** 가로로 밀 수 있는 목록 양 끝을 입력판 바탕색으로 흐리게 해 더 있다는 것을 알린다. */
@Composable
private fun BoxScope.ScrollFades(state: LazyListState) {
    val surface = EolssuTheme.colors.groupedBackground
    // matchParentSize: 흐림 상자가 목록 높이를 키우지 않고 목록 크기에 맞춘다.
    Box(Modifier.matchParentSize()) {
        if (state.canScrollBackward) Box(
            Modifier.align(Alignment.CenterStart).width(20.dp).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(surface, Color.Transparent))),
        )
        if (state.canScrollForward) Box(
            Modifier.align(Alignment.CenterEnd).width(28.dp).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, surface))),
        )
    }
}

/**
 * 흰 바탕 테두리 도구 버튼(44dp · 13sp 600). [picture]를 주면 이름 왼쪽에 바뀌는 모양 그림을 둔다(정간·장단 탭, S15P31E102-1042).
 */
@Composable
private fun ToolButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    picture: CellPicture? = null,
) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_07, onClick),
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp).alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        contentColor = colors.text,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            if (picture != null) {
                val ink = colors.text
                val mark = colors.primary
                Canvas(Modifier.size(width = 40.dp, height = 22.dp)) { drawCellPicture(picture, ink, mark) }
            }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/** 율명·기호 탭에서 줄마다 무엇을 고르는지 적는 작은 제목. */
@Composable
private fun RowCaption(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
        color = EolssuTheme.colors.textSub, modifier = Modifier.padding(start = 2.dp))
}

/** 정간·장단 탭 도구 버튼의 그림 종류. */
private enum class CellPicture { Split, Merge, AddColumn, DeleteColumn }

/**
 * 도구를 누르면 바뀌는 모양을 작게 그린다. 나누기·합치기는 `정간 → 바뀐 정간`, 각 추가는 지금 각 왼쪽의 연두 새 각,
 * 각 삭제는 X 친 각이다. 정간보처럼 각은 세로로 길고 오른쪽부터 읽는다.
 */
private fun DrawScope.drawCellPicture(picture: CellPicture, ink: Color, mark: Color) {
    val line = 1.5.dp.toPx()
    val stroke = Stroke(width = line)
    val corner = CornerRadius(2.dp.toPx())
    val h = size.height
    fun cell(x: Float, w: Float, split: Boolean, fill: Color? = null) {
        if (fill != null) drawRoundRect(fill, Offset(x, 1f), Size(w, h - 2f), corner)
        drawRoundRect(ink, Offset(x, 1f), Size(w, h - 2f), corner, style = stroke)
        if (split) drawLine(ink, Offset(x, h / 2), Offset(x + w, h / 2), line)
    }
    fun arrow(from: Float, to: Float) {
        val y = h / 2
        val head = 3.dp.toPx()
        drawLine(ink, Offset(from, y), Offset(to, y), line)
        drawLine(ink, Offset(to - head, y - head), Offset(to, y), line)
        drawLine(ink, Offset(to - head, y + head), Offset(to, y), line)
    }
    val box = 10.dp.toPx()
    val pad = 4.dp.toPx()
    when (picture) {
        CellPicture.Split -> { cell(0f, box, split = false); arrow(box + pad, size.width - box - pad); cell(size.width - box, box, split = true) }
        CellPicture.Merge -> { cell(0f, box, split = true); arrow(box + pad, size.width - box - pad); cell(size.width - box, box, split = false) }
        CellPicture.AddColumn -> {
            val w = 11.dp.toPx()
            val gap = 6.dp.toPx()
            val left = (size.width - 2 * w - gap) / 2
            cell(left, w, split = false, fill = mark)
            val plus = 3.dp.toPx()
            drawLine(ink, Offset(left + w / 2 - plus, h / 2), Offset(left + w / 2 + plus, h / 2), line)
            drawLine(ink, Offset(left + w / 2, h / 2 - plus), Offset(left + w / 2, h / 2 + plus), line)
            cell(left + w + gap, w, split = true)
        }
        CellPicture.DeleteColumn -> {
            val w = 12.dp.toPx()
            val left = (size.width - w) / 2
            val out = 3.dp.toPx()
            cell(left, w, split = true)
            drawLine(ink, Offset(left - out, 2f), Offset(left + w + out, h - 2f), line)
            drawLine(ink, Offset(left + w + out, 2f), Offset(left - out, h - 2f), line)
        }
    }
}

/**
 * 시김새 탭: 고른 정간에 붙인 시김새 알약(붙인 순서, ×를 누르면 뗌)과 등록된 시김새 전체의 가로 스크롤 목록(누르면 붙이거나 뗌).
 * 나눈 정간은 고른 소박([sub])에 붙인다. 율명이 아닌 소박이나 [SheetDraft.MAX_SIGIMSAE]개를 채운 소박에는 더 붙이지 않는다.
 * 붙은 것과 함께 쓸 수 없는 시김새는 흐리게 보인다. 누르면 겹치는 것을 빼고 붙이므로 다 찬 소박에서도 누를 수 있다.
 */
@Composable
private fun SigimsaeKeys(draft: SheetDraft, selected: Int, sub: Int, actions: EditInputActions) {
    val colors = EolssuTheme.colors
    val added = draft.sigimsaeAt(selected, sub)
    val hasPitch = draft.cells[selected].getOrNull(sub)?.isPitch() == true
    val full = added.size >= SheetDraft.MAX_SIGIMSAE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 36.dp).background(colors.surface, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (added.isEmpty()) {
                Text(
                    stringResource(if (hasPitch) Res.string.sheetedit_sig_none else Res.string.sheetedit_sig_need_pitch),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
            } else {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    added.forEachIndexed { position, value -> AddedSigimsae(value) { actions.onRemoveSigimsae(position) } }
                }
            }
        }
        val listState = rememberLazyListState()
        Box {
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 4.dp)) {
                itemsIndexed(Sigimsae.entries, key = { _, value -> value.name }) { _, value ->
                    val order = added.indexOfFirst { it.name == value.name }
                    // 함께 쓸 수 없는 것은 흐리게 두되 누를 수 있다. 누르면 겹치는 것을 빼고 붙이며 무엇을 뺐는지 알린다.
                    // 흐린 율명을 눌러도 이유를 알리는 것과 같은 방식이다(S15P31E102-1080·1097).
                    val conflicting = order < 0 && sigimsaeConflicts(added, value).isNotEmpty()
                    SigimsaeCard(value, order, conflicting,
                        enabled = hasPitch && (order >= 0 || !full || conflicting)) { actions.onToggleSigimsae(value) }
                }
            }
            ScrollFades(listState)
        }
        if (full) HintText(stringResource(Res.string.sheetedit_sig_limit, SheetDraft.MAX_SIGIMSAE))
        if (hasPitch && added.isNotEmpty() && Sigimsae.entries.any { sigimsaeConflicts(added, it).isNotEmpty() }) {
            HintText(stringResource(Res.string.sheetedit_sig_conflict_hint))
        }
    }
}

/** 붙인 시김새 한 개(기호 · 이름 · ×). 붙인 순서대로 놓이고, 누르면 그 시김새만 뗀다. */
@Composable
private fun AddedSigimsae(value: Sigimsae, onRemove: () -> Unit) {
    val colors = EolssuTheme.colors
    val name = sigimsaeLabel(value)
    val description = stringResource(Res.string.sheetedit_sig_remove, name)
    Surface(
        onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_08, onRemove),
        modifier = Modifier.heightIn(min = 32.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = colors.accentTint,
        contentColor = colors.text,
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // 그림이 없으면 이름이 바로 옆에 있으니 대신 적는 글자를 두 번 쓰지 않는다.
            if (scoreSigimsaeImage(value.scoreName) != null) ScoreSigimsaeGlyph(value.scoreName, 12.sp)
            Text(name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text("×", style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp), color = colors.textMuted)
        }
    }
}

/**
 * 시김새 목록 한 칸(76 × 66dp): 기호와 이름만 가운데 정렬한다. 붙어 있으면 먹색 테두리와 순서 번호.
 * [conflicting]이면 붙은 시김새와 함께 쓸 수 없어 흐리게 보이고, 읽어 주는 설명에 '누르면 바꿔요'를 더한다.
 */
@Composable
private fun SigimsaeCard(value: Sigimsae, order: Int, conflicting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val on = order >= 0
    val conflictDescription = stringResource(Res.string.sheetedit_sig_conflict_description)
    Surface(
        onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_09, onClick),
        enabled = enabled,
        modifier = Modifier.width(76.dp).height(66.dp).alpha(if (!enabled) 0.4f else if (conflicting) 0.55f else 1f).semantics {
            selected = on
            if (conflicting) stateDescription = conflictDescription
        },
        shape = RoundedCornerShape(10.dp),
        color = if (on) colors.accentTint else colors.surface,
        contentColor = colors.text,
        border = if (on) BorderStroke(1.5.dp, colors.accent) else null,
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                SigimsaeTile(value)
                Text(
                    sigimsaeLabel(value),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (on) Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp).background(colors.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${order + 1}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = colors.surface)
            }
        }
    }
}

/** 시김새 기호 견본(26dp 점선 상자). 공통 렌더러의 기호 그림을 쓰고, 그림이 없는 시김새는 이름을 적는다. */
@Composable
private fun SigimsaeTile(value: Sigimsae) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .size(26.dp)
            .drawBehind {
                drawRoundRect(
                    colors.textDisabled,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (scoreSigimsaeImage(value.scoreName) != null) {
            ScoreSigimsaeGlyph(value.scoreName, 13.sp)
        } else {
            // 그림이 없는 시김새는 이름을 상자 안에 들어가게 작게 한 줄로 적는다.
            Text(
                value.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp, letterSpacing = 0.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** 정간 나누기·합치기, 각 추가·삭제, 장단 정보와 '장단 바꾸기'. */
@Composable
private fun CellTools(draft: SheetDraft, selected: Int, actions: EditInputActions) {
    val colors = EolssuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 바꿀 수 없는 정간에서는 눌러도 아무 일이 없으므로 끈다(S15P31E102-1096).
            ToolButton(stringResource(Res.string.sheetedit_cell_split), actions.onSplit, Modifier.weight(1f),
                enabled = draft.canSplit(selected), picture = CellPicture.Split)
            ToolButton(stringResource(Res.string.sheetedit_cell_merge), actions.onMerge, Modifier.weight(1f),
                enabled = draft.canMerge(selected), picture = CellPicture.Merge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton(stringResource(Res.string.sheetedit_cell_add_column), actions.onAddColumn, Modifier.weight(1f),
                picture = CellPicture.AddColumn)
            ToolButton(stringResource(Res.string.sheetedit_cell_delete_column), actions.onDeleteColumn, Modifier.weight(1f),
                picture = CellPicture.DeleteColumn)
        }
        // 빠르기 구간이 걸친 각을 지우려 할 때만 막힌 이유를 알린다. 늘 띄워 두지 않는다(S15P31E102-1138).
        // 장단 고르기는 다른 도구와 같은 높이의 한 줄로 둔다. 줄을 누르면 장단 선택 창이 열린다(S15P31E102-1042).
        val jangdanChange = stringResource(Res.string.sheetedit_cell_jangdan_change)
        Surface(
            onClick = UserActionLog.click(QaTarget.EDIT_INPUT_PANEL_10, actions.onChangeJangdan),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = jangdanChange },
            shape = RoundedCornerShape(10.dp),
            color = colors.surface,
            contentColor = colors.text,
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(jangdanLabel(draft.accompanimentName), maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                Text(draft.jangdan?.let { stringResource(Res.string.sheetedit_cell_jangdan_desc, it.beats, it.subdivision, draft.bpm) }.orEmpty(),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textSub)
            }
        }
    }
}
