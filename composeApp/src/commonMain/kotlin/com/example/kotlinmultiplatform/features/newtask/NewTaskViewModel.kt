package com.example.kotlinmultiplatform.features.newtask

import com.example.kotlinmultiplatform.core.BaseViewModel
import com.example.kotlinmultiplatform.core.UiEvent
import com.example.kotlinmultiplatform.core.UiIntent
import com.example.kotlinmultiplatform.core.UiState
import kotlinx.coroutines.launch

// ── State ─────────────────────────────────────────────────────────────────────

data class NewTaskState(
    val topicText:       String  = "",
    val scriptText:      String  = "",
    val editingTaskId:   Int?    = null,
    val isPreviewMode:   Boolean = false,
    val showSaveDialog:  Boolean = false,
    val draftTitle:      String  = "",
    val isBusy:          Boolean = false,
    /** True when style-only changes have been made (no text edit needed to trigger dialog). */
    val hasStyleChanges: Boolean = false,
) : UiState {

    val hasContent: Boolean
        get() = topicText.isNotBlank() || scriptText.isNotBlank()

    val effectiveTitle: String
        get() = topicText.trim().ifBlank { draftTitle }
}

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface NewTaskEvent : UiEvent {
    data class PrefillFields(val topic: String, val script: String) : NewTaskEvent
    data object ClearFields : NewTaskEvent
    data class NavigateToDetail(
        val taskId:       Int,
        val taskTitle:    String,
        val taskScript:   String,
        val shapeOrdinal: Int,
        val isPreview:    Boolean,
    ) : NewTaskEvent
    data object NavigateBack    : NewTaskEvent
    /** Emitted after Save-confirmed back: carries the id of the task that was saved (or null if nothing was saved). */
    data class NavigateBackAfterSave(val savedTaskId: Int?) : NewTaskEvent
    data object DismissKeyboard : NewTaskEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface NewTaskIntent : UiIntent {
    data class Init(
        val editingTaskId:   Int?    = null,
        val isPreviewReturn: Boolean = false,
        val draftTitle:      String  = "",
    ) : NewTaskIntent
    data class TopicChanged(val text: String)  : NewTaskIntent
    data class ScriptChanged(val text: String) : NewTaskIntent
    data object NextClicked      : NewTaskIntent
    data object BackPressed      : NewTaskIntent
    data object StyleChanged     : NewTaskIntent
    data object StyleSaved       : NewTaskIntent
    data object SaveConfirmed    : NewTaskIntent
    data object DiscardConfirmed : NewTaskIntent
    data object DialogDismissed  : NewTaskIntent
    data class ReturnFromPreview(val title: String, val script: String) : NewTaskIntent
}

// ── Repository interface ──────────────────────────────────────────────────────

interface NewTaskRepository {
    fun nextId(): Int
    fun consumeNextId(): Int
    fun saveTask(title: String, desc: String, shapeOrdinal: Int): SavedTask
    fun updateTask(id: Int, title: String, desc: String)
    fun loadTask(id: Int): SavedTask?
    fun saveDraft(topic: String, script: String)
    fun loadDraft(): DraftData?
    fun clearDraft()
    fun ensureSeeded()
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
            is NewTaskIntent.Init             -> init(intent)
            is NewTaskIntent.TopicChanged     -> updateState { it.copy(topicText  = intent.text) }
            is NewTaskIntent.ScriptChanged    -> updateState { it.copy(scriptText = intent.text) }
            is NewTaskIntent.NextClicked      -> onNext()
            is NewTaskIntent.BackPressed      -> onBack()
            is NewTaskIntent.StyleChanged     -> updateState { it.copy(hasStyleChanges = true) }
            is NewTaskIntent.StyleSaved       -> updateState { it.copy(hasStyleChanges = false) }
            is NewTaskIntent.SaveConfirmed    -> onSaveConfirmed()
            is NewTaskIntent.DiscardConfirmed -> onDiscardConfirmed()
            is NewTaskIntent.DialogDismissed  -> updateState { it.copy(showSaveDialog = false) }
            is NewTaskIntent.ReturnFromPreview -> onReturnFromPreview(intent.title, intent.script)
            else -> Unit
        }
    }

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
                updateState { it.copy(isPreviewMode = true) }
            }
            intent.editingTaskId != null -> {
                val task = repository.loadTask(intent.editingTaskId)
                if (task != null) {
                    updateState { it.copy(topicText = task.title, scriptText = task.description) }
                    emitEvent(NewTaskEvent.PrefillFields(task.title, task.description))
                }
            }
            else -> {
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

    private fun onReturnFromPreview(title: String, script: String) {
        updateState { it.copy(topicText = title, scriptText = script, isPreviewMode = true) }
        emitEvent(NewTaskEvent.PrefillFields(title, script))
    }

    private fun onNext() {
        val state = currentState
        if (state.scriptText.trim().isEmpty()) return

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
            repository.loadTask(editId) ?: SavedTask(editId, title, script, 0)
        } else {
            repository.saveTask(title, script, shapeOrdinal = randomShapeOrdinal())
        }

        repository.clearDraft()
        updateState { it.copy(editingTaskId = saved.id, isPreviewMode = false, isBusy = false) }
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

    private fun onBack() {
        emitEvent(NewTaskEvent.DismissKeyboard)
        val state = currentState
        when {
            state.isPreviewMode -> {
                viewModelScope.launch { commitAndReturn() }
            }
            state.hasStyleChanges && !state.hasContent -> {
                updateState { it.copy(showSaveDialog = true) }
            }
            !state.hasContent -> {
                clearAndReturn()
            }
            state.editingTaskId != null -> {
                val existing  = repository.loadTask(state.editingTaskId)
                val unchanged = existing != null &&
                        state.effectiveTitle == existing.title &&
                        state.scriptText.trim() == existing.description
                if (unchanged && !state.hasStyleChanges) clearAndReturn()
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
        var savedTaskId: Int? = state.editingTaskId
        if (state.hasContent) {
            if (state.editingTaskId != null) {
                repository.updateTask(
                    state.editingTaskId,
                    state.effectiveTitle,
                    state.scriptText.trim(),
                )
            } else {
                val saved = repository.saveTask(
                    state.effectiveTitle,
                    state.scriptText.trim(),
                    shapeOrdinal = randomShapeOrdinal(),
                )
                savedTaskId = saved.id
            }
        }
        repository.clearDraft()
        resetState()
        // Emit NavigateBackAfterSave FIRST so AppRoot migrates style spans before
        // ClearFields fires — ClearFields resets scriptFieldState to length 0 which
        // triggers onTextChanged(0) in ScriptTextField, wiping all spans from memory.
        // Migration must read the non-empty _spans, so it must happen first.
        emitEvent(NewTaskEvent.NavigateBackAfterSave(savedTaskId))
        emitEvent(NewTaskEvent.ClearFields)
    }

    private fun clearAndReturn() {
        repository.clearDraft()
        resetState()
        emitEvent(NewTaskEvent.ClearFields)
        emitEvent(NewTaskEvent.NavigateBack)
    }

    private fun resetState() {
        updateState { NewTaskState(draftTitle = it.draftTitle, hasStyleChanges = false) }
    }

    fun saveDraft() {
        val state = currentState
        if (state.hasContent && state.editingTaskId == null) {
            repository.saveDraft(state.topicText, state.scriptText)
        }
    }

    private fun randomShapeOrdinal(): Int = (0 until LEADING_SHAPE_COUNT).random()

    companion object {
        private const val LEADING_SHAPE_COUNT = 30
    }
}