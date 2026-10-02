package com.example.athan

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.athan.ui.theme.AthanTheme
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.time.Instant

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mediaPlayer: MediaPlayer? = null

    var currentLatitude by mutableStateOf(36.2642)
    var currentLongitude by mutableStateOf(2.2283)
    var locationName by mutableStateOf("خميس مليانة")

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                fetchLastLocation()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkAndRequestLocationPermission()

        setContent {
            AthanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B1220)
                ) {
                    PrayerScreen(
                        latitude = currentLatitude,
                        longitude = currentLongitude,
                        cityName = locationName,
                        onPlayAthan = { playAthanSound() }
                    )
                }
            }
        }
    }

    private fun playAthanSound() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.adhan)
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLastLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLastLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    currentLatitude = it.latitude
                    currentLongitude = it.longitude
                    locationName = "موقعي الحالي (GPS)"
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

@Composable
fun PrayerScreen(
    latitude: Double,
    longitude: Double,
    cityName: String,
    onPlayAthan: () -> Unit
) {
    val coordinates = Coordinates(latitude = latitude, longitude = longitude)

    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var lastPlayedMinute by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()

            val today = currentTime.toLocalDate()
            val dateComponents = DateComponents(today.year, today.monthValue, today.dayOfMonth)
            val parameters = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters.copy(madhab = Madhab.SHAFI)
            val prayerTimes = PrayerTimes(coordinates, dateComponents, parameters)

            val prayerList = listOf(
                instantToLocalTime(prayerTimes.fajr),
                instantToLocalTime(prayerTimes.dhuhr),
                instantToLocalTime(prayerTimes.asr),
                instantToLocalTime(prayerTimes.maghrib),
                instantToLocalTime(prayerTimes.isha)
            )

            val currentLocalTime = currentTime.toLocalTime().withSecond(0).withNano(0)
            if (currentTime.minute != lastPlayedMinute) {
                for (prayerTime in prayerList) {
                    val pTimeClean = prayerTime.withSecond(0).withNano(0)
                    if (currentLocalTime == pTimeClean) {
                        onPlayAthan()
                        lastPlayedMinute = currentTime.minute
                        break
                    }
                }
            }

            delay(1000)
        }
    }

    val today = currentTime.toLocalDate()
    val dateComponents = DateComponents(today.year, today.monthValue, today.dayOfMonth)
    val parameters = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters.copy(madhab = Madhab.SHAFI)
    val prayerTimes = PrayerTimes(coordinates, dateComponents, parameters)

    val fajr = instantToLocalTime(prayerTimes.fajr)
    val sunrise = instantToLocalTime(prayerTimes.sunrise)
    val dhuhr = instantToLocalTime(prayerTimes.dhuhr)
    val asr = instantToLocalTime(prayerTimes.asr)
    val maghrib = instantToLocalTime(prayerTimes.maghrib)
    val isha = instantToLocalTime(prayerTimes.isha)

    val prayers = listOf(
        "الفجر" to fajr,
        "الشروق" to sunrise,
        "الظهر" to dhuhr,
        "العصر" to asr,
        "المغرب" to maghrib,
        "العشاء" to isha
    )

    val nextPrayer = prayers
        .map { prayer ->
            var prayerDateTime = LocalDateTime.of(today, prayer.second)
            if (!prayerDateTime.isAfter(currentTime)) {
                prayerDateTime = prayerDateTime.plusDays(1)
            }
            Triple(prayer.first, prayer.second, prayerDateTime)
        }
        .minByOrNull { it.third }!!

    val remainingSeconds = ChronoUnit.SECONDS.between(currentTime, nextPrayer.third)
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val countdown = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = "🕌 مواقيت الصلاة",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = cityName,
            color = Color(0xFF94A3B8),
            fontSize = 17.sp
        )

        Spacer(modifier = Modifier.height(25.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF172033))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "الصلاة القادمة",
                    color = Color(0xFF94A3B8),
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = nextPrayer.first,
                    color = Color(0xFF38BDF8),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = formatTime(nextPrayer.second),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "متبقي $countdown",
                    color = Color(0xFFCBD5E1),
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        prayers.forEach { prayer ->
            PrayerTime(
                prayerName = prayer.first,
                prayerTime = formatTime(prayer.second)
            )
        }
    }
}

fun instantToLocalTime(instant: Instant): LocalTime {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalTime.of(localDateTime.hour, localDateTime.minute)
}

fun formatTime(time: LocalTime): String {
    return String.format("%02d:%02d", time.hour, time.minute)
}

@Composable
fun PrayerTime(prayerName: String, prayerTime: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172033))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = prayerName,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = prayerTime,
                color = Color(0xFF38BDF8),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}