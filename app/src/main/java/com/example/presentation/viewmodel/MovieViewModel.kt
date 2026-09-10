package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.MovieRepository
import com.example.domain.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MovieUiState(
    val featuredMovies: List<Movie> = emptyList(),
    val allMovies: List<Movie> = emptyList(),
    val popularMovies: List<Movie> = emptyList(),
    val sciFiMovies: List<Movie> = emptyList(),
    val actionMovies: List<Movie> = emptyList(),
    val dramaMovies: List<Movie> = emptyList(),
    val selectedGenre: String? = null,
    val selectedYear: Int? = null,
    val searchQuery: String = "",
    val searchResults: List<Movie> = emptyList(),
    val isLoading: Boolean = false
)

class MovieViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear = _selectedYear.asStateFlow()

    val uiState: StateFlow<MovieUiState> = combine(
        repository.getAllMovies(),
        repository.getFeaturedMovies(),
        _searchQuery,
        _selectedGenre,
        _selectedYear
    ) { allMovies, featured, query, genre, year ->
        val filteredForSearch = allMovies.filter { movie ->
            val matchesQuery = query.isBlank() ||
                    movie.title.contains(query, ignoreCase = true) ||
                    movie.description.contains(query, ignoreCase = true) ||
                    movie.genres.any { it.contains(query, ignoreCase = true) }
            val matchesGenre = genre == null || movie.genres.any { it.equals(genre, ignoreCase = true) }
            val matchesYear = year == null || movie.releaseYear == year
            matchesQuery && matchesGenre && matchesYear
        }

        MovieUiState(
            featuredMovies = featured.ifEmpty { allMovies.take(3) },
            allMovies = allMovies,
            popularMovies = allMovies.sortedByDescending { it.rating },
            sciFiMovies = allMovies.filter { it.genres.any { g -> g.contains("Sci-Fi", ignoreCase = true) } },
            actionMovies = allMovies.filter { it.genres.any { g -> g.contains("Action", ignoreCase = true) } },
            dramaMovies = allMovies.filter { it.genres.any { g -> g.contains("Adventure", ignoreCase = true) || g.contains("Drama", ignoreCase = true) || g.contains("Nature", ignoreCase = true) } },
            selectedGenre = genre,
            selectedYear = year,
            searchQuery = query,
            searchResults = filteredForSearch,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MovieUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onGenreSelected(genre: String?) {
        _selectedGenre.value = if (_selectedGenre.value == genre) null else genre
    }

    fun onYearSelected(year: Int?) {
        _selectedYear.value = if (_selectedYear.value == year) null else year
    }
}
