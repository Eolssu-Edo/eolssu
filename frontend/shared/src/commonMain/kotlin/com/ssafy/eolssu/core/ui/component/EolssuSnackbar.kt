package com.ssafy.eolssu.core.ui.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme

/** 스낵바 바탕: 검정 80% 불투명(S15P31E102-803). */
private val SnackbarContainer = Color.Black.copy(alpha = 0.8f)
private val SnackbarShape = RoundedCornerShape(12.dp)

/** 앱 공통 스낵바 자리. Material 기본 모양 대신 [EolssuSnackbar]를 그린다. */
@Composable
fun EolssuSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> EolssuSnackbar(data) }
}

/**
 * 가운데에 글자 길이만큼의 폭으로 뜨는 검정 반투명 둥근 사각형 스낵바.
 * 문장마다 줄을 나눠([snackbarLines]) 13sp 흰 글씨로 가운데 정렬한다.
 * 버튼(`actionLabel`)이 있으면 문구는 왼쪽 정렬, 버튼은 오른쪽에 대표색 글자로 둔다.
 */
@Composable
fun EolssuSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val action = data.visuals.actionLabel
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = CompactContentMaxWidth)
                .clip(SnackbarShape)
                .background(SnackbarContainer)
                // 동작 버튼은 누르는 영역이 48dp라 위아래 여백을 4dp로 줄이고, 글은 6dp를 더해 여백 10dp를 유지한다(S15P31E102-971).
                .padding(start = 16.dp, end = if (action != null) 6.dp else 16.dp).padding(vertical = if (action != null) 4.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                snackbarLines(data.visuals.message),
                modifier = Modifier.weight(1f, fill = false).padding(vertical = if (action != null) 6.dp else 0.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = Color.White,
                textAlign = if (action != null) TextAlign.Start else TextAlign.Center,
            )
            if (action != null) {
                Text(
                    action,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = UserActionLog.click(QaTarget.EOLSSU_SNACKBAR_01, data::performAction))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = EolssuTheme.colors.primary,
                )
            }
        }
    }
}

private val SentenceEnd = Regex("([.!?])\\s+")
private val MiddleDot = Regex("\\s+·\\s+")

/** 스낵바 문구를 문장마다 한 줄로 나눈다. 마침표 등 뒤의 공백과 ` · `로 이은 문장 사이를 줄바꿈으로 바꾼다. */
internal fun snackbarLines(message: String): String =
    message.trim().replace(SentenceEnd, "$1\n").replace(MiddleDot, "\n")
