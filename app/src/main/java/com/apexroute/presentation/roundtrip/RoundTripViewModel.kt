package com.apexroute.presentation.roundtrip

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexroute.data.repository.RouteRepositoryImpl
import com.apexroute.data.repository.RecentRoutesRepository
import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.usecase.GenerateRoundTripUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Round Trip Generator flow.
 * Shared between RoundTripSetupScreen and RouteResultScreen.
 */
class RoundTripViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RouteRepositoryImpl()
    private val recentRoutesRepo = RecentRoutesRepository(application)
    private val generateRoundTrip = GenerateRoundTripUseCase(repository)

    private val _uiState = MutableStateFlow(RoundTripUiState())
    val uiState: StateFlow<RoundTripUiState> = _uiState.asStateFlow()

    // Default: Cluj-Napoca center
    private val defaultStart = GeoPoint(46.7712, 23.6236)

    init {
        _uiState.update { it.copy(startPoint = defaultStart) }
    }

    fun setDuration(minutes: Int) {
        _uiState.update { it.copy(durationMinutes = minutes) }
    }

    fun setStartPoint(point: GeoPoint) {
        _uiState.update { it.copy(startPoint = point) }
    }

    fun generateRoute() {
        val state = _uiState.value
        val start = state.startPoint ?: defaultStart

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    route = null,
                    loadingMessage = "Fetching road network…"
                )
            }

            try {
                _uiState.update { it.copy(loadingMessage = "Building road graph…") }

                val result = generateRoundTrip(start, state.durationMinutes)

                result.fold(
                    onSuccess = { route ->
                        recentRoutesRepo.saveRoute(route)
                        _uiState.update {
                            it.copy(isLoading = false, route = route, error = null)
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "An unexpected error occurred"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearRoute() {
        _uiState.update { it.copy(route = null) }
    }
}
