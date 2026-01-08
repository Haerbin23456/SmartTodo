# Refactoring Plan: Clean Code & Architecture

## Phase 22: Package Structure Reorganization
1.  Create new package directories:
    *   `ui/components`: For reusable UI widgets.
    *   `ui/home`: For main screens (TaskList, Inbox, Stream).
    *   `logic`: For business logic helpers (`TaskProcessor`, `DeepSeekHelper`).
2.  Move files:
    *   Move `DeepSeekHelper.kt`, `TaskProcessor.kt`, `AppManagement.kt` -> `logic/`
    *   Keep `MainActivity.kt` clean as the entry point.

## Phase 23: UI Component Extraction
1.  **Extract from MainActivity.kt**:
    *   `SmartTaskCard` -> `ui/components/TaskCards.kt`
    *   `InboxCard` -> `ui/components/TaskCards.kt` (or separate file)
    *   `RawStreamList` -> `ui/components/RawStreamList.kt`
    *   `ManualInputDialog` -> `ui/components/Dialogs.kt`
2.  **Refactor MainActivity.kt**:
    *   Replace inline composables with imports.
    *   Ensure navigation logic remains clear.

## Phase 24: Code Quality Polish
1.  **Import Cleanup**: Fix imports after moving files.
2.  **RawStreamList Unification**: Ensure the "Crash Fix" version of `RawStreamList` logic (simple Column) is used consistently in the extracted component to prevent future crashes in nested scrolls.
