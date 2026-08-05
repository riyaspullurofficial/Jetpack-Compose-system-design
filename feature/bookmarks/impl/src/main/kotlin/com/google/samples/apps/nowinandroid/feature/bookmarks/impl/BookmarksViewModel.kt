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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserNewsResourceRepository
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
    private val userDataRepository: UserDataRepository,
    userNewsResourceRepository: UserNewsResourceRepository,
) : ViewModel() {

    var shouldDisplayUndoBookmark by mutableStateOf(false)
    private var lastRemovedBookmarks: Map<String, String?> = emptyMap()

    var isSelectionMode by mutableStateOf(false)
        private set

    var selectedResourceIds by mutableStateOf(setOf<String>())
        private set

    var noteToEdit by mutableStateOf<Pair<String, String>?>(null)
        private set

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
            userDataRepository.setNewsResourceBookmarked(newsResourceId, false)
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
                userDataRepository.restoreBookmarks(lastRemovedBookmarks)
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
            userDataRepository.setNewsResourcesBookmarked(selectedResourceIds.toList(), false)
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
            userDataRepository.setBookmarkNote(newsResourceId, note)
            dismissNoteEdit()
        }
    }

    fun deleteNote(newsResourceId: String) {
        viewModelScope.launch {
            userDataRepository.deleteBookmarkNote(newsResourceId)
            dismissNoteEdit()
        }
    }
}
