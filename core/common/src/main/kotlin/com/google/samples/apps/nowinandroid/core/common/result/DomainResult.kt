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

package com.google.samples.apps.nowinandroid.core.common.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A sealed class that represents the result of a domain operation, including its loading state.
 * This is specifically tailored for Domain Layer use cases to abstract data source results.
 */
sealed class DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>()
    data class Error(val exception: Throwable? = null, val message: String? = null) : DomainResult<Nothing>()
    data object Loading : DomainResult<Nothing>()
}

/**
 * Extension function to convert a [Flow] of [T] into a [Flow] of [DomainResult] of [T].
 */
fun <T> Flow<T>.asDomainResult(): Flow<DomainResult<T>> = map<T, DomainResult<T>> { DomainResult.Success(it) }
    .onStart { emit(DomainResult.Loading) }
    .catch { emit(DomainResult.Error(it)) }
