package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal enum class SheetTutorial(val key: String, val title: StringResource, val steps: List<StringResource>) {
    Upload("sheet-tutorial-upload-v1", Res.string.sheet_tutorial_upload, listOf(
        Res.string.sheet_tutorial_upload_kind, Res.string.sheet_tutorial_upload_instrument, Res.string.sheet_tutorial_upload_file,
    )),
    Editor("sheet-tutorial-editor-v1", Res.string.sheet_tutorial_editor, listOf(
        Res.string.sheet_tutorial_editor_cell, Res.string.sheet_tutorial_editor_input, Res.string.sheet_tutorial_editor_save,
    )),
    Review("sheet-tutorial-review-v1", Res.string.sheet_tutorial_review, listOf(
        Res.string.sheet_tutorial_review_open, Res.string.sheet_tutorial_review_edit, Res.string.sheet_tutorial_review_next,
    )),
}

@Composable
internal fun SheetTutorials(vararg tutorials: SheetTutorial) {
    val store = koinInject<KeyValueStore>()
    val available = tutorials.toList()
    var active by rememberSaveable { mutableStateOf<String?>(null) }
    var step by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(available, active) {
        if (available.none { it.key == active }) {
            step = 0
            active = available.firstOrNull { store.getString(it.key) != "seen" }?.key
        }
    }

    Column(verticalArrangement = Arrangement.Top) {
        available.forEach { tutorial ->
            TextButton(onClick = { step = 0; active = tutorial.key }) {
                Text(stringResource(Res.string.sheet_tutorial_replay, stringResource(tutorial.title)))
            }
        }
    }

    val tutorial = available.firstOrNull { it.key == active } ?: return
    val finish = {
        store.putString(tutorial.key, "seen")
        active = null
    }
    EolssuDialog(
        title = stringResource(Res.string.sheet_tutorial_step, stringResource(tutorial.title), step + 1, tutorial.steps.size),
        message = stringResource(tutorial.steps[step]),
        onDismissRequest = finish,
    ) {
        val last = step == tutorial.steps.lastIndex
        EolssuButton(
            stringResource(if (last) Res.string.sheet_tutorial_done else Res.string.sheet_tutorial_next),
            onClick = { if (last) finish() else step++ },
        )
        EolssuButton(stringResource(Res.string.sheet_tutorial_skip), finish, style = EolssuButtonStyle.Text)
    }
}
