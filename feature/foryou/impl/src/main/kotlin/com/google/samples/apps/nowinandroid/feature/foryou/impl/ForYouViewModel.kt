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

package com.google.samples.apps.nowinandroid.feature.foryou.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent.Param
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsHelper
import com.google.samples.apps.nowinandroid.core.data.repository.NewsResourceQuery
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.data.util.SyncManager
import com.google.samples.apps.nowinandroid.core.common.result.ActionResult
import com.google.samples.apps.nowinandroid.core.common.result.DomainResult
import com.google.samples.apps.nowinandroid.core.common.result.toDomainError
import com.google.samples.apps.nowinandroid.core.domain.GetFollowableTopicsUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateBookmarkNoteUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceBookmarkUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.notifications.DEEP_LINK_NEWS_RESOURCE_ID_KEY
import com.google.samples.apps.nowinandroid.core.ui.BookmarkNoteViewModelState
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.inject.Inject

@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    syncManager: SyncManager,
    private val analyticsHelper: AnalyticsHelper,
    private val userDataRepository: UserDataRepository,
    userNewsResourceRepository: UserNewsResourceRepository,
    getFollowableTopics: GetFollowableTopicsUseCase,
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

    private val shouldShowOnboarding: Flow<Boolean> =
        userDataRepository.userData.map { result ->
            if (result is DomainResult.Success) {
                !result.data.shouldHideOnboarding
            } else {
                false
            }
        }

    val deepLinkedNewsResource = savedStateHandle.getStateFlow<String?>(
        key = DEEP_LINK_NEWS_RESOURCE_ID_KEY,
        null,
    )
        .flatMapLatest { newsResourceId ->
            if (newsResourceId == null) {
                flowOf(DomainResult.Success(emptyList()))
            } else {
                userNewsResourceRepository.observeAll(
                    NewsResourceQuery(
                        filterNewsIds = setOf(newsResourceId),
                    ),
                )
            }
        }
        .map { result ->
            if (result is DomainResult.Success) {
                result.data.firstOrNull()
            } else {
                null
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val isSyncing = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val feedState: StateFlow<NewsFeedUiState> =
        userNewsResourceRepository.observeAllForFollowedTopics()
            .map<DomainResult<List<UserNewsResource>>, NewsFeedUiState> { result ->
                when (result) {
                    is DomainResult.Success -> NewsFeedUiState.Success(result.data)
                    is DomainResult.Error -> NewsFeedUiState.Error(result.error)
                    DomainResult.Loading -> NewsFeedUiState.Loading
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NewsFeedUiState.Loading,
            )

    val onboardingUiState: StateFlow<OnboardingUiState> =
        combine(
            shouldShowOnboarding,
            getFollowableTopics(),
        ) { shouldShowOnboarding, topicsResult ->
            if (shouldShowOnboarding) {
                when (topicsResult) {
                    is DomainResult.Success -> OnboardingUiState.Shown(topics = topicsResult.data)
                    is DomainResult.Error -> OnboardingUiState.LoadFailed
                    DomainResult.Loading -> OnboardingUiState.Loading
                }
            } else {
                OnboardingUiState.NotShown
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = OnboardingUiState.Loading,
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

    val noteToEdit get() = bookmarkNoteViewModelState.noteToEdit

    fun updateTopicSelection(topicId: String, isChecked: Boolean) {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                userDataRepository.setTopicIdFollowed(topicId, isChecked)
                _actionResult.value = ActionResult.Success(Unit)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.toDomainError())
            }
        }
    }

    fun onEditNote(newsResourceId: String, currentNote: String) {
        bookmarkNoteViewModelState.onEditNote(newsResourceId, currentNote)
    }

    fun updateNewsResourceSaved(newsResourceId: String, isChecked: Boolean) {
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

    fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean) {
        viewModelScope.launch {
            updateNewsResourceViewedUseCase(newsResourceId, viewed)
        }
    }

    fun onDeepLinkOpened(newsResourceId: String) {
        if (newsResourceId == deepLinkedNewsResource.value?.id) {
            savedStateHandle[DEEP_LINK_NEWS_RESOURCE_ID_KEY] = null
        }
        analyticsHelper.logNewsDeepLinkOpen(newsResourceId = newsResourceId)
        viewModelScope.launch {
            updateNewsResourceViewedUseCase(
                newsResourceId = newsResourceId,
                viewed = true,
            )
        }
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Loading
            try {
                userDataRepository.setShouldHideOnboarding(shouldHideOnboarding = true)
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

private fun AnalyticsHelper.logNewsDeepLinkOpen(newsResourceId: String) =
    logEvent(
        AnalyticsEvent(
            type = "news_deep_link_opened",
            extras = listOf(
                Param(
                    key = DEEP_LINK_NEWS_RESOURCE_ID_KEY,
                    value = newsResourceId,
                ),
            ),
        ),
    )
