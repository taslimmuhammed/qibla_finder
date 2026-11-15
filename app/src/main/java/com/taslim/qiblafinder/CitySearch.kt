package com.taslim.qiblafinder

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.taslim.qiblafinder.ui.theme.QiblaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A place the user picked by hand instead of sharing their location. */
data class CityResult(
    val name: String,
    val region: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double
) {
    /** "London, United Kingdom", the same shape reverse geocoding produces. */
    val placeName: String get() = listOfNotNull(name, country).distinct().joinToString(", ")

    val detail: String get() = listOfNotNull(region, country).distinct().joinToString(", ")
}

/** Works offline, so there is always something to pick even without a connection. */
private val POPULAR_CITIES = listOf(
    CityResult("Makkah", "Makkah Province", "Saudi Arabia", "SA", 21.4225, 39.8262),
    CityResult("Madinah", "Madinah Province", "Saudi Arabia", "SA", 24.4672, 39.6111),
    CityResult("London", "England", "United Kingdom", "GB", 51.5072, -0.1276),
    CityResult("Istanbul", null, "Türkiye", "TR", 41.0082, 28.9784),
    CityResult("Cairo", null, "Egypt", "EG", 30.0444, 31.2357),
    CityResult("Dubai", null, "United Arab Emirates", "AE", 25.2048, 55.2708),
    CityResult("Karachi", "Sindh", "Pakistan", "PK", 24.8607, 67.0011),
    CityResult("Dhaka", null, "Bangladesh", "BD", 23.8103, 90.4125),
    CityResult("Jakarta", null, "Indonesia", "ID", -6.2088, 106.8456),
    CityResult("Kuala Lumpur", null, "Malaysia", "MY", 3.1390, 101.6869),
    CityResult("New York", "New York", "United States", "US", 40.7128, -74.0060),
    CityResult("Toronto", "Ontario", "Canada", "CA", 43.6532, -79.3832),
)

private fun Address.toCity(): CityResult? {
    if (!hasLatitude() || !hasLongitude()) return null
    val name = locality ?: subAdminArea ?: featureName ?: adminArea ?: return null
    return CityResult(
        name = name,
        region = adminArea?.takeIf { it != name },
        country = countryName,
        countryCode = countryCode?.uppercase(),
        latitude = latitude,
        longitude = longitude
    )
}

/** Forward-geocodes [query]. Returns null when the device has no geocoder at all. */
suspend fun searchCities(context: Context, query: String): List<CityResult>? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(query, 8, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    continuation.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    continuation.resumeWithException(IOException(errorMessage))
                }
            })
        }
    } else {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, 8) ?: emptyList()
        }
    }
    return addresses.mapNotNull { it.toCity() }.distinctBy { it.placeName + it.region }
}

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val cities: List<CityResult>) : SearchState
    data object Failed : SearchState
    data object Unavailable : SearchState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySearchSheet(
    onPick: (CityResult) -> Unit,
    onDismiss: () -> Unit,
    onUseCurrentLocation: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = QiblaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    fun close(then: () -> Unit) {
        keyboard?.hide()
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        state = SearchState.Loading
        delay(350)
        state = runCatching { searchCities(context, trimmed) }.fold(
            onSuccess = { it?.let(SearchState::Results) ?: SearchState.Unavailable },
            onFailure = { SearchState.Failed }
        )
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        contentColor = colors.text
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("Choose your city", style = MaterialTheme.typography.headlineLarge, color = colors.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "We'll use it for the Qibla and prayer times. You can switch to your live location anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search for a city", color = colors.textFaint) },
                    leadingIcon = {
                        Icon(MihrabIcons.Search, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable(role = Role.Button, onClickLabel = "Clear search") { query = "" }
                            ) {
                                Icon(MihrabIcons.Close, contentDescription = "Clear search", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.gold.copy(alpha = 0.6f),
                        unfocusedBorderColor = colors.border,
                        cursorColor = colors.gold,
                        focusedContainerColor = colors.chip,
                        unfocusedContainerColor = colors.chip,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.heightIn(max = 460.dp)
            ) {
                if (onUseCurrentLocation != null && query.isBlank()) {
                    item {
                        PlaceRow(
                            icon = MihrabIcons.Crosshair,
                            title = "Use my current location",
                            subtitle = "Stays accurate when you travel",
                            onClick = { close(onUseCurrentLocation) }
                        )
                    }
                }
                when (val current = state) {
                    SearchState.Idle -> {
                        item { ListCaption("Popular cities") }
                        items(POPULAR_CITIES, key = { it.placeName }) { city ->
                            PlaceRow(MihrabIcons.Pin, city.name, city.detail) { close { onPick(city) } }
                        }
                    }
                    SearchState.Loading -> item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        ) {
                            CircularProgressIndicator(color = colors.gold, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                    is SearchState.Results -> if (current.cities.isEmpty()) {
                        item { SearchMessage("No places match “${query.trim()}”. Check the spelling, or try a nearby larger city.") }
                    } else {
                        items(current.cities, key = { it.placeName + it.region + it.latitude }) { city ->
                            PlaceRow(MihrabIcons.Pin, city.name, city.detail) { close { onPick(city) } }
                        }
                    }
                    SearchState.Failed -> item {
                        SearchMessage("Search needs an internet connection. Check you're online and try again, or clear the search to pick a popular city.")
                    }
                    SearchState.Unavailable -> item {
                        SearchMessage("City search isn't available on this phone. Clear the search to pick a popular city, or use your current location.")
                    }
                }
            }
        }
    }
}

@Composable
private fun ListCaption(text: String) {
    Eyebrow(
        text = text,
        color = QiblaTheme.colors.textFaint,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun SearchMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = QiblaTheme.colors.textMuted,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
    )
}

@Composable
private fun PlaceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    val colors = QiblaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
