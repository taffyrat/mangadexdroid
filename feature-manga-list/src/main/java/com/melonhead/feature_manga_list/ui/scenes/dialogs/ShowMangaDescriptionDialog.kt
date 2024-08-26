package com.melonhead.feature_manga_list.ui.scenes.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.melonhead.lib_core.extensions.Previews
import com.melonhead.data_shared.models.ui.UIManga

@Composable
internal fun ShowMangaDescriptionDialog(
    uiManga: UIManga?,
    onDismissed: () -> Unit,
) {
    val description = uiManga?.description
    if (description != null) {
        AlertDialog(
            onDismissRequest = { onDismissed() },
            title = {
                Text(text = uiManga.title)
            },
            text = {
                Text(text = description)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissed()
                }) {
                    Text("Close")
                }
            }
        )
    }
}

@Preview
@Composable
private fun MarkChapterReadPreview() {
    com.melonhead.lib_core.theme.MangadexFollowerTheme {
        ShowMangaDescriptionDialog(
            Previews.previewUIManga(),
            onDismissed = { })
    }
}