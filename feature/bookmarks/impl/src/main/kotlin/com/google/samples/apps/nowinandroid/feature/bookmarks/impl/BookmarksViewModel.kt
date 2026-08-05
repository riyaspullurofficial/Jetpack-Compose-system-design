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
import com.google.samples.apps.nowinandroid.core.common.result.ActionResult
import com.google.samples.apps.nowinandroid.core.common.result.DomainResult
import com.google.samples.apps.nowinandroid.core.common.result.toDomainError
import com.google.samples.apps.nowinandroid.core.domain.BulkRemoveBookmarksUseCase
import com.google.samples.apps.nowinandroid.core.domain.GetBookmarkMementoUseCase
import com.google.samples.apps.nowinandroid.core.domain.RestoreBookmarksUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateBookmarkNoteUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceBookmarkUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.BookmarkNoteViewModelState
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState.Loading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    userNewsResourceRepository: UserNewsResourceRepository,
    private val updateNewsResourceBookmarkUseCase: UpdateNewsResourceBookmarkUseCase,
    private val bulkRemoveBookmarksUseCase: BulkRemoveBookmarksUseCase,
    private val restoreBookmarksUseCase: RestoreBookmarksUseCase,
    private val getBookmarkMementoUseCase: GetBookmarkMementoUseCase,
    private val updateNewsResourceViewedUseCase: UpdateNewsResourceViewedUseCase,
    private val updateBookmarkNoteUseCase: UpdateBookmarkNoteUseCase,
) : ViewModel() {

    private val bookmarkNoteViewModelState = BookmarkNoteViewModelState(
        savedStateHandle = savedStateHandle,
        onSave = updateBookmarkNoteUseCase::saveNote,
        onDelete = updateBookmarkNoteUseCase::deleteNote,
        onToggleBookmark = updateNewsResourceBookmarkUseCase::invoke,
    )

    var shouldDisplayUndoBookmark by mutableStateOf(false)
    private var lastRemovedBookmarks: Map<String, String?> = emptyMap()

    val isSelectionMode: StateFlow<Boolean> = savedStateHandle.getStateFlow(IS_SELECTION_MODE_KEY, false)

    val selectedResourceIds: StateFlow<Set<String>> = savedStateHandle.getStateFlow(SELECTED_RESOURCE_IDS_KEY, emptySet())

    val noteToEdit = bookmarkNoteViewModelState.noteToEdit

    val feedUiState: StateFlow<NewsFeedUiState> =
        userNewsResourceRepository.observeAllBookmarked()
            .map<DomainResult<List<UserNewsResource>>, NewsFeedUiState> { result ->
                when (result) {
                    is DomainResult.Success -> NewsFeedUiState.Success(result.data)
                    is DomainResult.Error -> NewsFeedUiState.Error(result.error)
                    DomainResult.Loading -> NewsFeedUiState.Loading
                }
            }
            .onStart { emit(Loading) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Loading,
            )

    private val _actionResult = MutableStateFlow<ActionResult<Unit>>(ActionResult.Idle)
    val actionResult: StateFlow<ActionResult<Unit>> = _actionResult.asStateFlow()

    fun removeFromSavedResources(newsResourceId: String) {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                lastRemovedBookmarks = getBookmarkMementoUseCase(setOf(newsResourceId))
                shouldDisplayUndoBookmark = true
                updateNewsResourceBookmarkUseCase(newsResourceId, false)
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
    }

    fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean) {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                updateNewsResourceViewedUseCase(newsResourceId, viewed)
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
    }

    fun undoBookmarkRemoval() {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                if (lastRemovedBookmarks.isNotEmpty()) {
                    restoreBookmarksUseCase(lastRemovedBookmarks)
                }
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
        clearUndoState()
    }

    fun clearUndoState() {
        shouldDisplayUndoBookmark = false
        lastRemovedBookmarks = emptyMap()
    }

    fun toggleSelectionMode() {
        val currentMode = isSelectionMode.value
        savedStateHandle[IS_SELECTION_MODE_KEY] = !currentMode
        if (currentMode) {
            savedStateHandle[SELECTED_RESOURCE_IDS_KEY] = emptySet<String>()
        }
    }

    fun toggleResourceSelection(newsResourceId: String) {
        val currentIds = selectedResourceIds.value
        savedStateHandle[SELECTED_RESOURCE_IDS_KEY] = if (newsResourceId in currentIds) {
            currentIds - newsResourceId
        } else {
            currentIds + newsResourceId
        }
    }

    fun selectAll(ids: List<String>) {
        savedStateHandle[SELECTED_RESOURCE_IDS_KEY] = ids.toSet()
    }

    fun bulkRemove() {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                val currentSelected = selectedResourceIds.value
                lastRemovedBookmarks = getBookmarkMementoUseCase(currentSelected)
                bulkRemoveBookmarksUseCase(currentSelected.toList())
                shouldDisplayUndoBookmark = true
                toggleSelectionMode()
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
    }

    fun consumeActionResult() {
        _actionResult.value = ActionResult.Idle
    }

    fun editNote(newsResourceId: String, currentNote: String) {
        bookmarkNoteViewModelState.onEditNote(newsResourceId, currentNote)
    }

    fun dismissNoteEdit() {
        bookmarkNoteViewModelState.dismissNoteEdit()
    }

    fun saveNote(newsResourceId: String, note: String) {
        bookmarkNoteViewModelState.onSaveNote(viewModelScope, newsResourceId, note)
    }

    fun deleteNote(newsResourceId: String) {
        bookmarkNoteViewModelState.onDeleteNote(viewModelScope, newsResourceId)
    }
}

private const val IS_SELECTION_MODE_KEY = "isSelectionMode"
private const val SELECTED_RESOURCE_IDS_KEY = "selectedResourceIds"
