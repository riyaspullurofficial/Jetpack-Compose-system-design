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

/**
 * A sealed class that represents the result of a one-off user action (e.g., clicking a button).
 * This is used to track the lifecycle of a single operation.
 */
sealed interface ActionResult<out T> {
    data class Success<T>(val data: T) : ActionResult<T>
    data class Error(val error: DomainError) : ActionResult<Nothing>
    data object Loading : ActionResult<Nothing>
    data object Idle : ActionResult<Nothing>
}
