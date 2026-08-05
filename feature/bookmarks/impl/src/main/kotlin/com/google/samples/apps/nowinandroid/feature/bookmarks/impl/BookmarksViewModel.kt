/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.samples.apps.nowinandroid.feature.bookmarks.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.domain.BulkRemoveBookmarksUseCase
import com.google.samples.apps.nowinandroid.core.domain.RestoreBookmarksUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateBookmarkNoteUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceBookmarkUseCase
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState.Loading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val userDataRepository: UserDataRepository,
    userNewsResourceRepository: UserNewsResourceRepository,
    private val updateNewsResourceBookmarkUseCase: UpdateNewsResourceBookmarkUseCase,
    private val bulkRemoveBookmarksUseCase: BulkRemoveBookmarksUseCase,
    private val restoreBookmarksUseCase: RestoreBookmarksUseCase,
    private val updateBookmarkNoteUseCase: UpdateBookmarkNoteUseCase,
) : ViewModel() {

    var shouldDisplayUndoBookmark by mutableStateOf(false)
    private var lastRemovedBookmarks: Map<String, String?> = emptyMap()

    var isSelectionMode: Boolean
        get() = savedStateHandle.get<Boolean>(IS_SELECTION_MODE_KEY) ?: false
        private set(value) = savedStateHandle.set(IS_SELECTION_MODE_KEY, value)

    var selectedResourceIds: Set<String>
        get() = savedStateHandle.get<List<String>>(SELECTED_RESOURCE_IDS_KEY)?.toSet() ?: emptySet()
        private set(value) = savedStateHandle.set(SELECTED_RESOURCE_IDS_KEY, value.toList())

    var noteToEdit: Pair<String, String>?
        get() {
            val id = savedStateHandle.get<String>(NOTE_TO_EDIT_ID_KEY)
            val note = savedStateHandle.get<String>(NOTE_TO_EDIT_NOTE_KEY)
            return if (id != null && note != null) id to note else null
        }
        private set(value) {
            savedStateHandle.set(NOTE_TO_EDIT_ID_KEY, value?.first)
            savedStateHandle.set(NOTE_TO_EDIT_NOTE_KEY, value?.second)
        }

    val feedUiState: StateFlow<NewsFeedUiState> =
        userNewsResourceRepository.observeAllBookmarked()
            .map<List<UserNewsResource>, NewsFeedUiState>(NewsFeedUiState::Success)
            .onStart { emit(Loading) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Loading,
            )

    fun removeFromSavedResources(newsResourceId: String) {
        viewModelScope.launch {
            val userData = userDataRepository.userData.firstOrNull()
            val note = userData?.bookmarkNotes?.get(newsResourceId)
            lastRemovedBookmarks = mapOf(newsResourceId to note)
            shouldDisplayUndoBookmark = true
            updateNewsResourceBookmarkUseCase(newsResourceId, false)
        }
    }

    fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean) {
        viewModelScope.launch {
            userDataRepository.setNewsResourceViewed(newsResourceId, viewed)
        }
    }

    fun undoBookmarkRemoval() {
        viewModelScope.launch {
            if (lastRemovedBookmarks.isNotEmpty()) {
                restoreBookmarksUseCase(lastRemovedBookmarks)
            }
        }
        clearUndoState()
    }

    fun clearUndoState() {
        shouldDisplayUndoBookmark = false
        lastRemovedBookmarks = emptyMap()
    }

    fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        if (!isSelectionMode) {
            selectedResourceIds = emptySet()
        }
    }

    fun toggleResourceSelection(newsResourceId: String) {
        selectedResourceIds = if (newsResourceId in selectedResourceIds) {
            selectedResourceIds - newsResourceId
        } else {
            selectedResourceIds + newsResourceId
        }
    }

    fun selectAll(ids: List<String>) {
        selectedResourceIds = ids.toSet()
    }

    fun bulkRemove() {
        viewModelScope.launch {
            val userData = userDataRepository.userData.firstOrNull()
            lastRemovedBookmarks = selectedResourceIds.associateWith { id ->
                userData?.bookmarkNotes?.get(id)
            }
            bulkRemoveBookmarksUseCase(selectedResourceIds.toList())
            shouldDisplayUndoBookmark = true
            toggleSelectionMode()
        }
    }

    fun editNote(newsResourceId: String, currentNote: String) {
        noteToEdit = newsResourceId to currentNote
    }

    fun dismissNoteEdit() {
        noteToEdit = null
    }

    fun saveNote(newsResourceId: String, note: String) {
        viewModelScope.launch {
            updateBookmarkNoteUseCase.saveNote(newsResourceId, note)
            dismissNoteEdit()
        }
    }

    fun deleteNote(newsResourceId: String) {
        viewModelScope.launch {
            updateBookmarkNoteUseCase.deleteNote(newsResourceId)
            dismissNoteEdit()
        }
    }
}

private const val IS_SELECTION_MODE_KEY = "isSelectionMode"
private const val SELECTED_RESOURCE_IDS_KEY = "selectedResourceIds"
private const val NOTE_TO_EDIT_ID_KEY = "noteToEditId"
private const val NOTE_TO_EDIT_NOTE_KEY = "noteToEditNote"
