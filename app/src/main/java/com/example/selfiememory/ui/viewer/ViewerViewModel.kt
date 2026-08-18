package com.example.selfiememory.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selfiememory.data.repository.SelfieRepository
import com.example.selfiememory.domain.model.Selfie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val selfieRepository: SelfieRepository
) : ViewModel() {

    private val _selfieId = MutableStateFlow(0)
    val selfies: StateFlow<List<Selfie>> = selfieRepository.getAllSelfies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selfie: StateFlow<Selfie?> = combine(_selfieId, selfies) { id, list ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setSelfieId(id: Int) {
        _selfieId.value = id
    }

    fun deleteSelfie(selfie: Selfie) {
        viewModelScope.launch {
            selfieRepository.deleteSelfie(selfie)
        }
    }
}
