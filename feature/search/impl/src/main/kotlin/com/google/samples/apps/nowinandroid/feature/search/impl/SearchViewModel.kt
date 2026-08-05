/*
 * Copyright 2023 The Android Open Source Project
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

package com.google.samples.apps.nowinandroid.feature.search.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent.Param
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsHelper
import com.google.samples.apps.nowinandroid.core.data.repository.RecentSearchRepository
import com.google.samples.apps.nowinandroid.core.data.repository.SearchContentsRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.common.result.ActionResult
import com.google.samples.apps.nowinandroid.core.common.result.DomainResult
import com.google.samples.apps.nowinandroid.core.common.result.toDomainError
import com.google.samples.apps.nowinandroid.core.domain.GetRecentSearchQueriesUseCase
import com.google.samples.apps.nowinandroid.core.domain.GetSearchContentsUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateBookmarkNoteUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceBookmarkUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.model.data.UserSearchResult
import com.google.samples.apps.nowinandroid.core.ui.BookmarkNoteViewModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    getSearchContentsUseCase: GetSearchContentsUseCase,
    recentSearchQueriesUseCase: GetRecentSearchQueriesUseCase,
    private val searchContentsRepository: SearchContentsRepository,
    private val recentSearchRepository: RecentSearchRepository,
    private val userDataRepository: UserDataRepository,
    private val savedStateHandle: SavedStateHandle,
    private val analyticsHelper: AnalyticsHelper,
    private val updateNewsResourceBookmarkUseCase: UpdateNewsResourceBookmarkUseCase,
    private val updateNewsResourceViewedUseCase: UpdateNewsResourceViewedUseCase,
    private val updateBookmarkNoteUseCase: UpdateBookmarkNoteUseCase,
) : ViewModel() {

    private val bookmarkNoteViewModelState = BookmarkNoteViewModelState(
        savedStateHandle = savedStateHandle,
        onSave = updateBookmarkNoteUseCase::saveNote,
        onDelete = updateBookmarkNoteUseCase::deleteNote,
        onToggleBookmark = updateNewsResourceBookmarkUseCase::invoke,
    )

    val searchQuery = savedStateHandle.getStateFlow(key = SEARCH_QUERY, initialValue = "")

    val searchResultUiState: StateFlow<SearchResultUiState> =
        searchContentsRepository.getSearchContentsCount()
            .flatMapLatest { totalCountResult ->
                when (totalCountResult) {
                    is DomainResult.Success -> {
                        val totalCount = totalCountResult.data
                        if (totalCount < SEARCH_MIN_FTS_ENTITY_COUNT) {
                            flowOf(SearchResultUiState.SearchNotReady)
                        } else {
                            searchQuery.flatMapLatest { query ->
                                if (query.trim().length < SEARCH_QUERY_MIN_LENGTH) {
                                    flowOf(SearchResultUiState.EmptyQuery)
                                } else {
                                    getSearchContentsUseCase(query)
                                        .map<DomainResult<UserSearchResult>, SearchResultUiState> { result ->
                                            when (result) {
                                                is DomainResult.Success -> SearchResultUiState.Success(
                                                    topics = result.data.topics,
                                                    newsResources = result.data.newsResources,
                                                )

                                                is DomainResult.Error -> SearchResultUiState.LoadFailed(result.error)
                                                DomainResult.Loading -> SearchResultUiState.Loading
                                            }
                                        }
                                        .catch { emit(SearchResultUiState.LoadFailed()) }
                                }
                            }
                        }
                    }

                    is DomainResult.Error -> flowOf(SearchResultUiState.LoadFailed(totalCountResult.error))
                    DomainResult.Loading -> flowOf(SearchResultUiState.Loading)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SearchResultUiState.Loading,
            )

    val recentSearchQueriesUiState: StateFlow<RecentSearchQueriesUiState> =
        recentSearchQueriesUseCase()
            .map { result ->
                when (result) {
                    is DomainResult.Success -> RecentSearchQueriesUiState.Success(result.data)
                    is DomainResult.Error -> RecentSearchQueriesUiState.LoadFailed(result.error)
                    DomainResult.Loading -> RecentSearchQueriesUiState.Loading
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RecentSearchQueriesUiState.Loading,
            )

    private val _actionResult = MutableStateFlow<ActionResult<Unit>>(ActionResult.Idle)
    val actionResult: StateFlow<ActionResult<Unit>> = combine(
        _actionResult,
        bookmarkNoteViewModelState.actionResult,
    ) { local, common ->
        if (local is ActionResult.Loading || common is ActionResult.Loading) ActionResult.Loading
        else if (local is ActionResult.Error) local
        else if (common is ActionResult.Error) common
        else if (local is ActionResult.Success<*> || common is ActionResult.Success<*>) ActionResult.Success(Unit)
        else ActionResult.Idle
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActionResult.Idle,
    )

    fun onSearchQueryChanged(query: String) {
        savedStateHandle[SEARCH_QUERY] = query
    }

    /**
     * Called when the search action is explicitly triggered by the user. For example, when the
     * search icon is tapped in the IME or when the enter key is pressed in the search text field.
     *
     * The search results are displayed on the fly as the user types, but to explicitly save the
     * search query in the search text field, defining this method.
     */
    fun onSearchTriggered(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                recentSearchRepository.insertOrReplaceRecentSearch(searchQuery = query)
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
        analyticsHelper.logEventSearchTriggered(query = query)
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                recentSearchRepository.clearRecentSearches()
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
    }

    val noteToEdit get() = bookmarkNoteViewModelState.noteToEdit

    fun onEditNote(newsResourceId: String, currentNote: String) {
        bookmarkNoteViewModelState.onEditNote(newsResourceId, currentNote)
    }

    fun setNewsResourceBookmarked(newsResourceId: String, isChecked: Boolean) {
        bookmarkNoteViewModelState.onToggleBookmark(viewModelScope, newsResourceId, isChecked)
    }

    fun saveNote(newsResourceId: String, note: String) {
        bookmarkNoteViewModelState.onSaveNote(viewModelScope, newsResourceId, note)
    }

    fun deleteNote(newsResourceId: String) {
        bookmarkNoteViewModelState.onDeleteNote(viewModelScope, newsResourceId)
    }

    fun dismissNoteEdit() {
        bookmarkNoteViewModelState.dismissNoteEdit()
    }

    fun followTopic(followedTopicId: String, followed: Boolean) {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                userDataRepository.setTopicIdFollowed(followedTopicId, followed)
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

    fun consumeActionResult() {
        _actionResult.value = ActionResult.Idle
        bookmarkNoteViewModelState.consumeActionResult()
    }
}

private fun AnalyticsHelper.logEventSearchTriggered(query: String) =
    logEvent(
        event = AnalyticsEvent(
            type = SEARCH_QUERY,
            extras = listOf(element = Param(key = SEARCH_QUERY, value = query)),
        ),
    )

/** Minimum length where search query is considered as [SearchResultUiState.EmptyQuery] */
private const val SEARCH_QUERY_MIN_LENGTH = 2

/** Minimum number of the fts table's entity count where it's considered as search is not ready */
private const val SEARCH_MIN_FTS_ENTITY_COUNT = 1
private const val SEARCH_QUERY = "searchQuery"
