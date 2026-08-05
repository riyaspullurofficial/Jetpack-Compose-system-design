/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.nowinandroid.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun BookmarkNoteDialog(
    noteToEdit: Pair<String, String>?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    noteToEdit?.let { (id, note) ->
        NoteEditDialog(
            initialNote = note,
            onDismiss = onDismiss,
            onSave = { onSave(id, it) },
            onDelete = { onDelete(id) },
        )
    }
}

@Composable
fun NoteEditDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit = {},
) {
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialNote.isBlank()) {
                    stringResource(R.string.core_ui_add_note)
                } else {
                    stringResource(R.string.core_ui_edit_note)
                },
            )
        },
        text = {
            Column {
                TextField(
                    value = note,
                    onValueChange = { if (it.length <= 280) note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.core_ui_note_label)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 5,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${note.length}/280",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp),
                    color = if (note.length > 280) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(note) },
            ) {
                Text(stringResource(R.string.core_ui_save))
            }
        },
        dismissButton = {
            Row {
                if (initialNote.isNotBlank()) {
                    TextButton(
                        onClick = onDelete,
                    ) {
                        Text(
                            text = stringResource(R.string.core_ui_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.core_ui_cancel))
                }
            }
        },
    )
}
