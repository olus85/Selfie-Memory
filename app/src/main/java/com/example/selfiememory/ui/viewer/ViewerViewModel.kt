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
import android.net.Uri
import com.example.selfiememory.service.MemoryExporter

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val selfieRepository: SelfieRepository,
    private val exporter: MemoryExporter
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

    fun setFavorite(selfie: Selfie) { viewModelScope.launch { selfieRepository.setFavorite(selfie.id, !selfie.favorite) } }
    fun saveJournal(selfie: Selfie, note: String, tags: String) { viewModelScope.launch { selfieRepository.updateJournal(selfie.id, note, tags) } }
    fun createComparison(a: Selfie, b: Selfie, done: (Uri?) -> Unit) { viewModelScope.launch { done(runCatching { exporter.collage(a,b) }.getOrNull()) } }
}
