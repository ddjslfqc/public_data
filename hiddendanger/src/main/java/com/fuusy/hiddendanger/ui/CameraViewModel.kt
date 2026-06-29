package com.fuusy.hiddendanger.ui

import androidx.lifecycle.MutableLiveData
import com.fuusy.common.base.BaseViewModel

class CameraViewModel : BaseViewModel() {
    
    val capturedImages = MutableLiveData<List<String>>()
    val isFlashEnabled = MutableLiveData<Boolean>(false)
    val isFrontCamera = MutableLiveData<Boolean>(false)
    
    fun updateCapturedImages(images: List<String>) {
        capturedImages.value = images
    }
    
    fun toggleFlash() {
        isFlashEnabled.value = !(isFlashEnabled.value ?: false)
    }
    
    fun switchCamera() {
        isFrontCamera.value = !(isFrontCamera.value ?: false)
    }
}