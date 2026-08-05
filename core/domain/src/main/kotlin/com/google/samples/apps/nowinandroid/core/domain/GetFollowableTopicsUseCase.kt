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

package com.google.samples.apps.nowinandroid.core.domain

import com.google.samples.apps.nowinandroid.core.data.repository.TopicsRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.domain.TopicSortField.NAME
import com.google.samples.apps.nowinandroid.core.domain.TopicSortField.NONE
import com.google.samples.apps.nowinandroid.core.model.data.FollowableTopic
import com.google.samples.apps.nowinandroid.core.common.result.DomainResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * A use case which obtains a list of topics with their followed state.
 */
class GetFollowableTopicsUseCase @Inject constructor(
    private val topicsRepository: TopicsRepository,
    private val userDataRepository: UserDataRepository,
) {
    /**
     * Returns a list of topics with their associated followed state.
     *
     * @param sortBy - the field used to sort the topics. Default NONE = no sorting.
     */
    operator fun invoke(sortBy: TopicSortField = NONE): Flow<DomainResult<List<FollowableTopic>>> = combine(
        userDataRepository.userData,
        topicsRepository.getTopics(),
    ) { userDataResult, topicsResult ->
        if (userDataResult is DomainResult.Success && topicsResult is DomainResult.Success) {
            val userData = userDataResult.data
            val followedTopics = topicsResult.data.map { topic ->
                FollowableTopic(
                    topic = topic,
                    isFollowed = topic.id in userData.followedTopics,
                )
            }
            DomainResult.Success(
                when (sortBy) {
                    NAME -> followedTopics.sortedBy { it.topic.name }
                    else -> followedTopics
                },
            )
        } else if (userDataResult is DomainResult.Error) {
            DomainResult.Error(userDataResult.exception, userDataResult.message)
        } else if (topicsResult is DomainResult.Error) {
            DomainResult.Error(topicsResult.exception, topicsResult.message)
        } else {
            DomainResult.Loading
        }
    }
}

enum class TopicSortField {
    NONE,
    NAME,
}
