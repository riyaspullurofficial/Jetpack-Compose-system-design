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

package com.google.samples.apps.nowinandroid.core.domain

import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.common.result.DomainResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * A use case that captures the current state of bookmarks and their notes for a list of IDs.
 * Used to create an "Undo" memento before a bulk or single removal operation.
 */
class GetBookmarkMementoUseCase @Inject constructor(
    private val userDataRepository: UserDataRepository,
) {
    suspend operator fun invoke(newsResourceIds: Set<String>): Map<String, String?> {
        val userDataResult = userDataRepository.userData.first { it is DomainResult.Success }
        if (userDataResult is DomainResult.Success) {
            val userData = userDataResult.data
            return newsResourceIds.associateWith { id ->
                userData.bookmarkNotes[id]
            }
        }
        return emptyMap()
    }
}
