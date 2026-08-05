# Now in Android

A fully functional Android app built with Kotlin and Jetpack Compose. Users can browse Android development news, follow topics, receive notifications for new content, and bookmark articles.

## Architecture

The app follows [official architecture guidance](https://developer.android.com/topic/architecture) and is described in detail in the [architecture learning journey](docs/ArchitectureLearningJourney.md). Modularization strategy is covered in the [modularization learning journey](docs/ModularizationLearningJourney.md).

## Modules

The main Android app lives in `app/`. Feature modules live in `feature/` and core/shared modules in `core/`.

## Build & Run

The app has two product flavors (`demo`, `prod`) and two build types (`debug`, `release`).

The `demo` flavor uses static local data — use this for development. The `prod` flavor makes real network calls to a backend server that is not publicly available.

```bash
# Build
./gradlew assembleDemoDebug

# Fix formatting
./gradlew spotlessApply
```

## Testing

The project uses dependency injection with Hilt. Most data layer components are defined as interfaces with concrete implementations swapped for test doubles in tests.

**No mocking libraries are used.** Test doubles implement the same interfaces as production code and provide test-only hooks for state manipulation. `ViewModel` tests use these test repositories via constructor injection.

```bash
# Run local tests
./gradlew testDemoDebugUnitTest

# Run a single test class
./gradlew testDemoDebugUnitTest --tests "com.example.MyTestClass"

# Run instrumented tests
./gradlew connectedDemoDebugAndroidTest
```

> **Note:** Do not run `./gradlew test` or `./gradlew connectedAndroidTest` — those execute tests against all build variants, which will fail. Only the `demoDebug` variant has tests.

---

# Technical Assessment

You have **80 minutes** to complete the task below. You are free to use any LLM tools available to you.

You are evaluated on:
- **Functional completeness** — does it work as described?
- **Architecture fit** — does your code follow the patterns already in the codebase?
- **Code quality** — is it clean, testable, and maintainable?
- **Testing** — did you write meaningful tests?

You are **free to choose your own technical approach**. The requirements below describe *what* the feature should do, not *how* to build it.

---

## Enhanced Bookmark Management — Notes & Multi-Select

The Bookmarks screen currently supports only basic save/unsave of articles. Enhance it with two capabilities: personal notes on bookmarked articles, and a multi-select mode for bulk management.

### What We Want

**Bookmark notes:**
- When a user bookmarks an article (from any screen), show an optional text field where they can type a personal note (max 280 characters).
- Users should be able to skip adding a note — bookmarking without a note is still valid.
- On the Bookmarks screen, articles that have a note should display the note text below the article title.
- Users should be able to edit or delete an existing note by tapping on it.
- Notes must persist across app restarts.
- When an article is unbookmarked, its associated note must be automatically deleted.

**Multi-select mode:**
- A way to enter "selection mode" on the Bookmarks screen (e.g., long-press on an article, or an "Edit" button).
- In selection mode:
  - Each article shows a checkbox.
  - Users can tap to select/deselect individual articles.
  - A "Select All" option is available.
  - A counter shows how many items are selected (e.g., "3 selected").
  - A "Remove" action removes all selected bookmarks at once.
- After bulk removal, show a single undo snackbar that restores ALL removed bookmarks — including their notes — in one action.
- A way to exit selection mode without taking action (e.g., a "Cancel" button or back press).
- Selection mode should be exited automatically after a bulk action is performed.

**How they interact:**
- Bulk-removing bookmarks must also clean up all associated notes. Undo must restore both the bookmarks and their notes together.
- In selection mode, tapping an article should toggle its checkbox — not open its note for editing. Note editing should only be available outside of selection mode.
- The single-item unbookmark behavior that already exists should continue to work as-is outside of selection mode.

### What We Don't Want

- Do NOT support rich text, images, or formatting in notes — plain text only.
- Do NOT add notes to non-bookmarked articles.
- Do NOT show notes on screens other than the Bookmarks screen.
- Do NOT add multi-select to any other screen — only the Bookmarks screen.
- Do NOT add additional bulk actions beyond remove (no bulk "share", "export", or "move to folder").
