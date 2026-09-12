package com.example.selfiememory.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selfiememory.data.repository.SelfieRepository
import com.example.selfiememory.domain.model.Selfie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.selfiememory.service.MemoryExporter
import com.example.selfiememory.data.repository.SettingsRepository
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val selfieRepository: SelfieRepository,
    private val exporter: MemoryExporter,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val query=MutableStateFlow("");private val favorites=MutableStateFlow(false);private val trash=MutableStateFlow(false)
    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting
    val settings=settingsRepository.settings.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),com.example.selfiememory.domain.model.Settings())
    val selfies: StateFlow<List<Selfie>> = combine(selfieRepository.getAllSelfies(),selfieRepository.getTrashed(),query,favorites,trash){active,deleted,q,f,t->(if(t)deleted else active).filter{(!f||it.favorite)&&(q.isBlank()||it.note.contains(q,true)||it.tags.contains(q,true))}}
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun search(v:String){query.value=v};fun favorites(v:Boolean){favorites.value=v}
    fun trash(v:Boolean){trash.value=v};fun restore(id:Int){viewModelScope.launch{selfieRepository.restoreSelfie(id)}}
    fun monthlyVideo(done: (Uri?, String?) -> Unit) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                runCatching { exporter.monthlyVideo(selfies.value) }
                    .onSuccess { done(it, null) }
                    .onFailure { done(null, it.message ?: "Video konnte nicht erstellt werden") }
            } finally {
                _exporting.value = false
            }
        }
    }

    fun deleteSelfie(selfie: Selfie) {
        viewModelScope.launch {
            selfieRepository.deleteSelfie(selfie)
        }
    }
}
