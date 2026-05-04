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

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val selfieRepository: SelfieRepository
) : ViewModel() {

    val selfies: StateFlow<List<Selfie>> = selfieRepository.getAllSelfies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSelfie(selfie: Selfie) {
        viewModelScope.launch {
            selfieRepository.deleteSelfie(selfie)
        }
    }
}