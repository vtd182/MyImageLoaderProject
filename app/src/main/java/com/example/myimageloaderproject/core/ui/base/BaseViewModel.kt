package com.example.myimageloaderproject.core.ui.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseViewModel<State : Any, Intent : Any>(
    initialState: State
) : ViewModel() {
    
    protected val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<State> = _uiState.asStateFlow()
    
    abstract fun handleIntent(intent: Intent)
    
    protected fun updateState(update: State.() -> State) {
        _uiState.value = _uiState.value.update()
    }
}
