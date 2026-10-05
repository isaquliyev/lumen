package com.lumen.player.feature.shell

import androidx.lifecycle.ViewModel
import com.lumen.player.core.AppEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShellViewModel @Inject constructor(val events: AppEvents) : ViewModel()
