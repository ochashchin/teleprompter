package com.example.kotlinmultiplatform.features.newtask

import com.example.kotlinmultiplatform.core.BaseViewModel
import com.example.kotlinmultiplatform.core.UiEvent
import com.example.kotlinmultiplatform.core.UiIntent
import com.example.kotlinmultiplatform.core.UiState
import kotlinx.coroutines.launch

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable state for the new-task / edit-task screen.
 *
 * [topicText] and [scriptText] are the canonical text values.
 * The composable's [TextFieldState] objects are the visual layer — they sync
 * their content into the VM via [NewTaskIntent.TopicChanged] /
 * [NewTaskIntent.ScriptChanged] on every keystroke, and the VM pushes back
 * via [NewTaskEvent.PrefillFields] / [NewTaskEvent.ClearFields] events only
 * when a bulk rewrite is needed (edit mode load, prefill from preview-back,
 * draft restore, discard).  This avoids fighting Compose's text engine.
 */
data class NewTaskState(
    val topicText:      String  = "",
    val scriptText:     String  = "",
    val editingTaskId:  Int?    = null,
    val isPreviewMode:  Boolean = false,
    val showSaveDialog: Boolean = false,
    val draftTitle:     String  = "",
    val isBusy:         Boolean = false,
) : UiState {

    val hasContent: Boolean
        get() = topicText.isNotBlank() || scriptText.isNotBlank()

    /** The title to persist: typed topic, falling back to the localised draft string. */
    val effectiveTitle: String
        get() = topicText.trim().ifBlank { draftTitle }
}

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface NewTaskEvent : UiEvent {

    // ── Field control events ─────────────────────────────────────────────────
    //
    // These tell the composable to rewrite its TextFieldState objects in bulk.
    // Normal keystroke changes flow the other way (Intent → VM).

    /** Overwrite both fields with the supplied content. */
    data class PrefillFields(val topic: String, val script: String) : NewTaskEvent

    /** Clear both fields to empty strings. */
    data object ClearFields : NewTaskEvent

    // ── Navigation events ────────────────────────────────────────────────────

    /**
     * Navigate forward to the Detail/preview screen for this task.
     * [task] is the full Task object so Destination.Detail can carry it
     * during the Step 3 migration (Step 4 removes the Task from Destination).
     */
    data class NavigateToDetail(
        val taskId:      Int,
        val taskTitle:   String,
        val taskScript:  String,
        val shapeOrdinal: Int,
        val isPreview:   Boolean,
    ) : NewTaskEvent

    /** Pop back to TaskList (save or discard completed). */
    data object NavigateBack : NewTaskEvent

    /** Dismiss keyboard (composable acts on this immediately). */
    data object DismissKeyboard : NewTaskEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface NewTaskIntent : UiIntent {

    /**
     * Open the screen.
     *
     * @param editingTaskId   null = create mode; non-null = edit existing task.
     * @param isPreviewReturn true when navigating back from Detail preview
     *                        (fields already populated — skip draft restore).
     * @param draftTitle      Localised fallback title string from the composable.
     */
    data class Init(
        val editingTaskId:   Int?    = null,
        val isPreviewReturn: Boolean = false,
        val draftTitle:      String  = "",
    ) : NewTaskIntent

    // Text field sync — fired on every keystroke
    data class TopicChanged(val text: String)  : NewTaskIntent
    data class ScriptChanged(val text: String) : NewTaskIntent

    data object NextClicked      : NewTaskIntent
    data object BackPressed      : NewTaskIntent

    // Save-dialog responses
    data object SaveConfirmed    : NewTaskIntent
    data object DiscardConfirmed : NewTaskIntent
    data object DialogDismissed  : NewTaskIntent

    /**
     * Called when returning from the Detail/preview screen via its Back button.
     * The Detail screen passes back the current title + script so the fields
     * can be restored exactly as the user left them.
     */
    data class ReturnFromPreview(val title: String, val script: String) : NewTaskIntent
}

// ── Repository interface ──────────────────────────────────────────────────────

/** Persistence contract for new-task creation and draft management. */
interface NewTaskRepository {
    /** All per-task Settings helpers and nextId live here. */
    fun nextId(): Int
    fun consumeNextId(): Int          // reads then increments the counter
    fun saveTask(title: String, desc: String, shapeOrdinal: Int): SavedTask
    fun updateTask(id: Int, title: String, desc: String)
    fun loadTask(id: Int): SavedTask?
    fun saveDraft(topic: String, script: String)
    fun loadDraft(): DraftData?
    fun clearDraft()
    fun ensureSeeded()                // idempotent first-run seed
}

data class SavedTask(
    val id:           Int,
    val title:        String,
    val description:  String,
    val shapeOrdinal: Int,
)

data class DraftData(
    val topic:  String,
    val script: String,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class NewTaskViewModel(
    private val repository: NewTaskRepository,
) : BaseViewModel<NewTaskState, NewTaskEvent>(
    initialState = NewTaskState(),
) {
    override fun onIntent(intent: UiIntent) {
        when (intent) {
            is NewTaskIntent.Init            -> init(intent)
            is NewTaskIntent.TopicChanged    -> updateState { it.copy(topicText  = intent.text) }
            is NewTaskIntent.ScriptChanged   -> updateState { it.copy(scriptText = intent.text) }
            is NewTaskIntent.NextClicked     -> onNext()
            is NewTaskIntent.BackPressed     -> onBack()
            is NewTaskIntent.SaveConfirmed   -> onSaveConfirmed()
            is NewTaskIntent.DiscardConfirmed -> onDiscardConfirmed()
            is NewTaskIntent.DialogDismissed -> updateState { it.copy(showSaveDialog = false) }
            is NewTaskIntent.ReturnFromPreview -> onReturnFromPreview(intent.title, intent.script)
            else -> Unit
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun init(intent: NewTaskIntent.Init) {
        repository.ensureSeeded()

        updateState {
            it.copy(
                draftTitle    = intent.draftTitle,
                editingTaskId = intent.editingTaskId,
                isPreviewMode = false,
            )
        }

        when {
            intent.isPreviewReturn -> {
                // Back from preview: fields already populated by ReturnFromPreview intent,
                // just mark mode — no field event needed.
                updateState { it.copy(isPreviewMode = true) }
            }
            intent.editingTaskId != null -> {
                val task = repository.loadTask(intent.editingTaskId)
                if (task != null) {
                    updateState {
                        it.copy(topicText = task.title, scriptText = task.description)
                    }
                    emitEvent(NewTaskEvent.PrefillFields(task.title, task.description))
                }
            }
            else -> {
                // Create mode: restore draft if one exists
                val draft = repository.loadDraft()
                if (draft != null) {
                    updateState { it.copy(topicText = draft.topic, scriptText = draft.script) }
                    emitEvent(NewTaskEvent.PrefillFields(draft.topic, draft.script))
                } else {
                    updateState { it.copy(topicText = "", scriptText = "") }
                    emitEvent(NewTaskEvent.ClearFields)
                }
            }
        }
    }

    // ── ReturnFromPreview ─────────────────────────────────────────────────────

    private fun onReturnFromPreview(title: String, script: String) {
        updateState {
            it.copy(
                topicText     = title,
                scriptText    = script,
                isPreviewMode = true,
            )
        }
        emitEvent(NewTaskEvent.PrefillFields(title, script))
    }

    // ── Next ──────────────────────────────────────────────────────────────────

    private fun onNext() {
        val state = currentState
        if (state.scriptText.trim().isEmpty()) return   // guard: composable also checks

        updateState { it.copy(isBusy = true) }

        val title  = state.effectiveTitle
        val script = state.scriptText.trim()
        val editId = state.editingTaskId

        val saved: SavedTask = if (editId != null) {
            val existing = repository.loadTask(editId)
            val changed  = existing == null ||
                    title != existing.title ||
                    script != existing.description
            if (changed) repository.updateTask(editId, title, script)
            repository.loadTask(editId)
                ?: SavedTask(editId, title, script, 0)
        } else {
            val task = repository.saveTask(title, script, shapeOrdinal = randomShapeOrdinal())
            task
        }

        repository.clearDraft()

        updateState {
            it.copy(
                editingTaskId = saved.id,
                isPreviewMode = false,
                isBusy        = false,
            )
        }

        emitEvent(
            NewTaskEvent.NavigateToDetail(
                taskId       = saved.id,
                taskTitle    = saved.title,
                taskScript   = saved.description,
                shapeOrdinal = saved.shapeOrdinal,
                isPreview    = true,
            )
        )
    }

    // ── Back ──────────────────────────────────────────────────────────────────

    private fun onBack() {
        emitEvent(NewTaskEvent.DismissKeyboard)

        val state = currentState

        when {
            state.isPreviewMode -> {
                // Returning to TaskList after having previewed — commit silently
                viewModelScope.launch { commitAndReturn() }
            }
            !state.hasContent -> {
                clearAndReturn()
            }
            state.editingTaskId != null -> {
                val existing  = repository.loadTask(state.editingTaskId)
                val unchanged = existing != null &&
                        state.effectiveTitle == existing.title &&
                        state.scriptText.trim() == existing.description
                if (unchanged) clearAndReturn()
                else updateState { it.copy(showSaveDialog = true) }
            }
            else -> {
                updateState { it.copy(showSaveDialog = true) }
            }
        }
    }

    private fun onSaveConfirmed() {
        updateState { it.copy(showSaveDialog = false) }
        viewModelScope.launch { commitAndReturn() }
    }

    private fun onDiscardConfirmed() {
        updateState { it.copy(showSaveDialog = false) }
        clearAndReturn()
    }

    private fun commitAndReturn() {
        val state = currentState
        if (state.hasContent) {
            if (state.editingTaskId != null) {
                repository.updateTask(
                    state.editingTaskId,
                    state.effectiveTitle,
                    state.scriptText.trim(),
                )
            } else {
                repository.saveTask(
                    state.effectiveTitle,
                    state.scriptText.trim(),
                    shapeOrdinal = randomShapeOrdinal(),
                )
            }
        }
        repository.clearDraft()
        resetState()
        emitEvent(NewTaskEvent.ClearFields)
        emitEvent(NewTaskEvent.NavigateBack)
    }

    private fun clearAndReturn() {
        repository.clearDraft()
        resetState()
        emitEvent(NewTaskEvent.ClearFields)
        emitEvent(NewTaskEvent.NavigateBack)
    }

    private fun resetState() {
        updateState {
            NewTaskState(draftTitle = it.draftTitle)
        }
    }

    // ── Draft auto-save ───────────────────────────────────────────────────────
    //
    // Called from a LaunchedEffect in the composable on text changes, and from
    // the platform layer on app-backgrounded lifecycle events.

    fun saveDraft() {
        val state = currentState
        if (state.hasContent && state.editingTaskId == null) {
            repository.saveDraft(state.topicText, state.scriptText)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun randomShapeOrdinal(): Int =
        (0 until LEADING_SHAPE_COUNT).random()

    companion object {
        // Total entries in LeadingShapeType — keep in sync with the enum.
        // Using a constant avoids importing the @ExperimentalMaterial3ExpressiveApi
        // annotated enum into this commonMain ViewModel.
        private const val LEADING_SHAPE_COUNT = 30
    }
}
