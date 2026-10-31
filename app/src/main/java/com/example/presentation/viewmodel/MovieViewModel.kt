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
    val trendingMovies: List<Movie> = emptyList(),
    val popularMovies: List<Movie> = emptyList(),
    val swahiliMovies: List<Movie> = emptyList(),
    val zombieMovies: List<Movie> = emptyList(),
    val romanceMovies: List<Movie> = emptyList(),
    val actionMovies: List<Movie> = emptyList(),
    val horrorMovies: List<Movie> = emptyList(),
    val comedyMovies: List<Movie> = emptyList(),
    val animationMovies: List<Movie> = emptyList(),
    val sciFiMovies: List<Movie> = emptyList(),
    val dramaMovies: List<Movie> = emptyList(),
    val selectedGenre: String? = null,
    val selectedCountry: String? = null,
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

    private val _selectedCountry = MutableStateFlow<String?>(null)
    val selectedCountry = _selectedCountry.asStateFlow()

    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear = _selectedYear.asStateFlow()

    private val _isRefreshing = MutableStateFlow(true)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.syncCatalogFromSupabase()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private data class MovieFilters(
        val query: String = "",
        val genre: String? = null,
        val country: String? = null,
        val year: Int? = null
    )

    private val _filtersFlow = combine(
        _searchQuery,
        _selectedGenre,
        _selectedCountry,
        _selectedYear
    ) { query, genre, country, year ->
        MovieFilters(query, genre, country, year)
    }

    private val _catalogFlow = combine(
        repository.getAllMovies(),
        repository.getFeaturedMovies(),
        _filtersFlow
    ) { rawAllMovies, rawFeatured, filters ->
        val query = filters.query
        val genre = filters.genre
        val country = filters.country
        val year = filters.year
        val allMovies = rawAllMovies.map { movie ->
            if (movie.episodes.isNotEmpty()) {
                movie.copy(episodes = movie.episodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })))
            } else movie
        }
        val featured = rawFeatured.map { movie ->
            if (movie.episodes.isNotEmpty()) {
                movie.copy(episodes = movie.episodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })))
            } else movie
        }

        val filteredForSearch = allMovies.filter { movie ->
            val matchesQuery = query.isBlank() ||
                    movie.title.contains(query, ignoreCase = true) ||
                    movie.description.contains(query, ignoreCase = true) ||
                    movie.genres.any { it.contains(query, ignoreCase = true) }
            val matchesGenre = when {
                genre == null || genre.equals("All", ignoreCase = true) -> true
                genre.equals("Zilizotafsiriwa", ignoreCase = true) -> movie.isSwahiliTranslated
                genre.equals("Trending", ignoreCase = true) -> movie.isTrending
                else -> movie.genres.any { it.contains(genre, ignoreCase = true) }
            }
            val matchesCountry = country == null || country.equals("All", ignoreCase = true) ||
                    movie.country.equals(country, ignoreCase = true)
            val matchesYear = year == null || movie.releaseYear == year
            matchesQuery && matchesGenre && matchesCountry && matchesYear
        }

        // Admin-curated Trending Shelf: Only movies marked as isTrending
        val explicitlyTrending = allMovies.filter { it.isTrending }
        val trending = if (explicitlyTrending.isNotEmpty()) {
            explicitlyTrending
        } else {
            // Graceful fallback to top-rated / featured if admin hasn't toggled specific movies yet
            (featured + allMovies.sortedByDescending { it.rating }).distinctBy { it.id }.take(5)
        }

        // Dedicated Swahili Translated Movies
        val swahiliTranslated = allMovies.filter { it.isSwahiliTranslated }

        // Zombies Movies
        val zombies = allMovies.filter { m ->
            m.genres.any { it.contains("Zombie", ignoreCase = true) }
        }

        // Strict deduplication across genre shelves:
        val usedInGenres = mutableSetOf<String>()

        fun takeUniqueForGenre(predicate: (Movie) -> Boolean): List<Movie> {
            val matched = allMovies.filter { movie ->
                movie.id !in usedInGenres && predicate(movie)
            }
            matched.forEach { usedInGenres.add(it.id) }
            return matched
        }

        val romance = takeUniqueForGenre { m ->
            m.genres.any { it.contains("Romance", ignoreCase = true) || it.contains("Love", ignoreCase = true) }
        }
        val action = takeUniqueForGenre { m ->
            m.genres.any { it.contains("Action", ignoreCase = true) }
        }
        val horror = takeUniqueForGenre { m ->
            m.genres.any { it.contains("Horror", ignoreCase = true) || it.contains("Thriller", ignoreCase = true) || it.contains("Suspense", ignoreCase = true) }
        }
        val comedy = takeUniqueForGenre { m ->
            m.genres.any { it.contains("Comed", ignoreCase = true) || it.contains("Funny", ignoreCase = true) }
        }
        val animation = takeUniqueForGenre { m ->
            m.genres.any { it.contains("Animat", ignoreCase = true) || it.contains("Anime", ignoreCase = true) || it.contains("Cartoon", ignoreCase = true) }
        }
        val sciFi = takeUniqueForGenre { m ->
            m.genres.any { it.contains("Sci-Fi", ignoreCase = true) || it.contains("Science", ignoreCase = true) || it.contains("Cyber", ignoreCase = true) }
        }
        val dramaMatches = takeUniqueForGenre { m ->
            m.genres.any { it.contains("Drama", ignoreCase = true) || it.contains("Adventure", ignoreCase = true) || it.contains("Epic", ignoreCase = true) || it.contains("Nature", ignoreCase = true) }
        }
        val remainingUnassigned = allMovies.filter { it.id !in usedInGenres }
        val drama = dramaMatches + remainingUnassigned

        fun sortShelf(list: List<Movie>): List<Movie> = list.sortedWith(
            compareByDescending<Movie> { it.isTrending }
                .thenByDescending { it.releaseYear }
                .thenByDescending { it.uploadDate }
                .thenByDescending { it.rating }
        )

        MovieUiState(
            featuredMovies = featured.ifEmpty { allMovies.take(3) },
            allMovies = allMovies,
            trendingMovies = sortShelf(trending),
            popularMovies = sortShelf(trending),
            swahiliMovies = sortShelf(swahiliTranslated),
            zombieMovies = sortShelf(zombies),
            romanceMovies = sortShelf(romance),
            actionMovies = sortShelf(action),
            horrorMovies = sortShelf(horror),
            comedyMovies = sortShelf(comedy),
            animationMovies = sortShelf(animation),
            sciFiMovies = sortShelf(sciFi),
            dramaMovies = sortShelf(drama),
            selectedGenre = genre,
            selectedCountry = country,
            selectedYear = year,
            searchQuery = query,
            searchResults = sortShelf(filteredForSearch),
            isLoading = false
        )
    }

    val uiState: StateFlow<MovieUiState> = combine(
        _catalogFlow,
        _isRefreshing
    ) { state, refreshing ->
        state.copy(isLoading = refreshing && state.allMovies.isEmpty())
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

    fun onCountrySelected(country: String?) {
        _selectedCountry.value = if (_selectedCountry.value == country) null else country
    }

    fun onYearSelected(year: Int?) {
        _selectedYear.value = if (_selectedYear.value == year) null else year
    }
}
