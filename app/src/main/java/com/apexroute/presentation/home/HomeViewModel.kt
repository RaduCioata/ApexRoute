package com.apexroute.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.apexroute.data.repository.RecentRoutesRepository
import com.apexroute.domain.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecentRoutesRepository(application)
    private val _recentRoutes = MutableStateFlow<List<Route>>(emptyList())
    val recentRoutes: StateFlow<List<Route>> = _recentRoutes.asStateFlow()

    fun loadRecentRoutes() {
        _recentRoutes.value = repository.getRecentRoutes()
    }
}
