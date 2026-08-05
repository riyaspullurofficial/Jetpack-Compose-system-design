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

import androidx.lifecycle.SavedStateHandle
import com.google.samples.apps.nowinandroid.core.common.result.ActionResult
import com.google.samples.apps.nowinandroid.core.common.result.toDomainError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A helper class to manage the state of the bookmark note dialog in ViewModels.
 */
class BookmarkNoteViewModelState(
    private val savedStateHandle: SavedStateHandle,
    private val onSave: suspend (String, String) -> Unit,
    private val onDelete: suspend (String) -> Unit,
    private val onToggleBookmark: suspend (String, Boolean) -> Unit,
) {
    private val _actionResult = MutableStateFlow<ActionResult<Unit>>(ActionResult.Idle)
    val actionResult: StateFlow<ActionResult<Unit>> = _actionResult.asStateFlow()

    val noteToEdit: StateFlow<Pair<String, String>?> =
        savedStateHandle.getStateFlow(NOTE_TO_EDIT_KEY, null)

    fun onEditNote(newsResourceId: String, currentNote: String) {
        savedStateHandle[NOTE_TO_EDIT_KEY] = newsResourceId to currentNote
    }

    fun onToggleBookmark(
        scope: CoroutineScope,
        newsResourceId: String,
        isBookmarked: Boolean,
    ) {
        scope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                onToggleBookmark(newsResourceId, isBookmarked)
                if (isBookmarked) {
                    onEditNote(newsResourceId, "")
                }
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
    }

    fun onSaveNote(
        scope: CoroutineScope,
        newsResourceId: String,
        note: String,
    ) {
        scope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                onSave(newsResourceId, note)
                dismissNoteEdit()
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
    }

    fun onDeleteNote(
        scope: CoroutineScope,
        newsResourceId: String,
    ) {
        scope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                onDelete(newsResourceId)
                dismissNoteEdit()
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
    }

    fun consumeActionResult() {
        _actionResult.value = ActionResult.Idle
    }

    fun dismissNoteEdit() {
        savedStateHandle[NOTE_TO_EDIT_KEY] = null
    }

    companion object {
        private const val NOTE_TO_EDIT_KEY = "noteToEdit"
    }
}
