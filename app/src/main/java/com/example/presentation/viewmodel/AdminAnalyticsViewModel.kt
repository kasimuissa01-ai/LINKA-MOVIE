package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.analytics.AdminAnalyticsSummary
import com.example.data.analytics.AppAnalyticsManager
import com.example.data.analytics.DateFilterRange
import com.example.data.analytics.MovieAnalyticsItem
import com.example.data.analytics.MovieSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AnalyticsUiState {
    object Loading : AnalyticsUiState()
    data class Success(val summary: AdminAnalyticsSummary) : AnalyticsUiState()
    object Empty : AnalyticsUiState()
    data class Error(val message: String, val isTableMissing: Boolean = false) : AnalyticsUiState()
}

class AdminAnalyticsViewModel(
    private val analyticsManager: AppAnalyticsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalyticsUiState>(AnalyticsUiState.Loading)
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val _selectedRange = MutableStateFlow(DateFilterRange.LAST_30_DAYS)
    val selectedRange: StateFlow<DateFilterRange> = _selectedRange.asStateFlow()

    private val _selectedSortOption = MutableStateFlow(MovieSortOption.WATCH_TIME)
    val selectedSortOption: StateFlow<MovieSortOption> = _selectedSortOption.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMovieDrilldown = MutableStateFlow<MovieAnalyticsItem?>(null)
    val selectedMovieDrilldown: StateFlow<MovieAnalyticsItem?> = _selectedMovieDrilldown.asStateFlow()

    init {
        loadAnalytics()
    }

    fun setDateFilter(range: DateFilterRange) {
        if (_selectedRange.value != range) {
            _selectedRange.value = range
            loadAnalytics()
        }
    }

    fun setSortOption(option: MovieSortOption) {
        _selectedSortOption.value = option
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectMovieForDrilldown(item: MovieAnalyticsItem?) {
        _selectedMovieDrilldown.value = item
    }

    fun refresh() {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.value = AnalyticsUiState.Loading

            val range = _selectedRange.value
            val result = analyticsManager.fetchAdminAnalyticsSummary(range.days)

            result.fold(
                onSuccess = { summary ->
                    if (summary.totalInstallations == 0L && summary.totalPlays == 0L && summary.movieStats.isEmpty()) {
                        _uiState.value = AnalyticsUiState.Empty
                    } else {
                        _uiState.value = AnalyticsUiState.Success(summary)
                    }
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "Unknown error"
                    val isTableMissing = errorMsg.contains("PGRST205") ||
                            errorMsg.contains("Could not find the function") ||
                            errorMsg.contains("schema cache") ||
                            errorMsg.contains("PGRST202")

                    _uiState.value = AnalyticsUiState.Error(
                        message = if (isTableMissing) {
                            "Supabase analytics tables or RPC function 'get_admin_analytics_summary' are not created yet. Please execute the provided SQL migration in your Supabase SQL Editor."
                        } else {
                            "Failed to load analytics: $errorMsg"
                        },
                        isTableMissing = isTableMissing
                    )
                }
            )
        }
    }
}
