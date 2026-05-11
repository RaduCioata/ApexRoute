package com.apexroute.presentation.scenic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexroute.data.repository.RouteRepositoryImpl
import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.usecase.GenerateScenicRouteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScenicRouteViewModel : ViewModel() {

    private val repository = RouteRepositoryImpl()
    private val generateScenic = GenerateScenicRouteUseCase(repository)

    private val _uiState = MutableStateFlow(ScenicRouteUiState())
    val uiState: StateFlow<ScenicRouteUiState> = _uiState.asStateFlow()

    fun setPointA(point: GeoPoint, label: String = "") {
        _uiState.update { it.copy(pointA = point, pointALabel = label) }
    }

    fun setPointB(point: GeoPoint, label: String = "") {
        _uiState.update { it.copy(pointB = point, pointBLabel = label) }
    }

    fun generateRoute() {
        val state = _uiState.value
        val a = state.pointA
        val b = state.pointB

        if (a == null || b == null) {
            _uiState.update { it.copy(error = "Please set both starting point and destination.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, routes = emptyList(), loadingMessage = "Fetching road network…") }
            try {
                val result = generateScenic(a, b)
                result.fold(
                    onSuccess = { routes -> _uiState.update { it.copy(isLoading = false, routes = routes, selectedRouteIndex = 0) } },
                    onFailure = { err -> _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error") } }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error") }
            }
        }
    }

    fun selectRoute(index: Int) {
        val maxIndex = _uiState.value.routes.size - 1
        if (index in 0..maxIndex) {
            _uiState.update { it.copy(selectedRouteIndex = index) }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearRoute() { _uiState.update { it.copy(routes = emptyList(), selectedRouteIndex = 0) } }
}
