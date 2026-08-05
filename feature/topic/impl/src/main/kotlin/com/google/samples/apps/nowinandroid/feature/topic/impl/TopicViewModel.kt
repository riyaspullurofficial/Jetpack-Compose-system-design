/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.samples.apps.nowinandroid.feature.topic.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.common.result.DomainResult
import com.google.samples.apps.nowinandroid.core.data.repository.NewsResourceQuery
import com.google.samples.apps.nowinandroid.core.data.repository.TopicsRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.domain.UpdateBookmarkNoteUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceBookmarkUseCase
import com.google.samples.apps.nowinandroid.core.domain.UpdateNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.model.data.FollowableTopic
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.BookmarkNoteViewModelState
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = TopicViewModel.Factory::class)
class TopicViewModel @AssistedInject constructor(
    savedStateHandle: SavedStateHandle,
    private val userDataRepository: UserDataRepository,
    topicsRepository: TopicsRepository,
    userNewsResourceRepository: UserNewsResourceRepository,
    private val updateNewsResourceBookmarkUseCase: UpdateNewsResourceBookmarkUseCase,
    private val updateNewsResourceViewedUseCase: UpdateNewsResourceViewedUseCase,
    private val updateBookmarkNoteUseCase: UpdateBookmarkNoteUseCase,
    @Assisted val topicId: String,
) : ViewModel() {

    private val bookmarkNoteViewModelState = BookmarkNoteViewModelState(
        savedStateHandle = savedStateHandle,
        onSave = updateBookmarkNoteUseCase::saveNote,
        onDelete = updateBookmarkNoteUseCase::deleteNote,
        onToggleBookmark = updateNewsResourceBookmarkUseCase::invoke,
    )

    val topicUiState: StateFlow<TopicUiState> = topicUiState(
        topicId = topicId,
        userDataRepository = userDataRepository,
        topicsRepository = topicsRepository,
    )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TopicUiState.Loading,
        )

    val newsUiState: StateFlow<NewsFeedUiState> = newsUiState(
        topicId = topicId,
        userDataRepository = userDataRepository,
        userNewsResourceRepository = userNewsResourceRepository,
    )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NewsFeedUiState.Loading,
        )

    fun followTopicToggle(followed: Boolean) {
        viewModelScope.launch {
            userDataRepository.setTopicIdFollowed(topicId, followed)
        }
    }

    val noteToEdit get() = bookmarkNoteViewModelState.noteToEdit

    fun onEditNote(newsResourceId: String, currentNote: String) {
        bookmarkNoteViewModelState.onEditNote(newsResourceId, currentNote)
    }

    fun bookmarkNews(newsResourceId: String, bookmarked: Boolean) {
        bookmarkNoteViewModelState.onToggleBookmark(viewModelScope, newsResourceId, bookmarked)
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

    @AssistedFactory
    interface Factory {
        fun create(
            topicId: String,
        ): TopicViewModel
    }
}

private fun topicUiState(
    topicId: String,
    userDataRepository: UserDataRepository,
    topicsRepository: TopicsRepository,
): Flow<TopicUiState> {
    return combine(
        userDataRepository.userData,
        topicsRepository.getTopic(id = topicId),
        ::Pair,
    )
        .map { (userDataResult, topicResult) ->
            if (userDataResult is DomainResult.Success && topicResult is DomainResult.Success) {
                TopicUiState.Success(
                    followableTopic = FollowableTopic(
                        topic = topicResult.data,
                        isFollowed = topicId in userDataResult.data.followedTopics,
                    ),
                )
            } else if (userDataResult is DomainResult.Error || topicResult is DomainResult.Error) {
                TopicUiState.Error
            } else {
                TopicUiState.Loading
            }
        }
}

private fun newsUiState(
    topicId: String,
    userNewsResourceRepository: UserNewsResourceRepository,
    userDataRepository: UserDataRepository,
): Flow<NewsFeedUiState> {
    return combine(
        userNewsResourceRepository.observeAll(NewsResourceQuery(filterTopicIds = setOf(topicId))),
        userDataRepository.userData,
        ::Pair,
    )
        .map { (newsResourcesResult, userDataResult) ->
            if (newsResourcesResult is DomainResult.Success && userDataResult is DomainResult.Success) {
                NewsFeedUiState.Success(newsResourcesResult.data)
            } else if (newsResourcesResult is DomainResult.Error || userDataResult is DomainResult.Error) {
                NewsFeedUiState.Error
            } else {
                NewsFeedUiState.Loading
            }
        }
}

sealed interface TopicUiState {
    data class Success(val followableTopic: FollowableTopic) : TopicUiState
    data object Error : TopicUiState
    data object Loading : TopicUiState
}
