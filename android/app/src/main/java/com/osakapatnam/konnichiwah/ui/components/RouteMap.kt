package com.osakapatnam.konnichiwah.ui.components

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

// OpenFreeMap's free OpenStreetMap style: no key, no billing. The style
// carries its own attribution, which the map shows in its corner.
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

// A line on the map: a ride in its line's colour, or a walk (dashed).
data class MapLine(val points: List<Pair<Double, Double>>, val color: Color, val walk: Boolean)

// A dot on the map: a station, a place, or where the traveller is.
data class MapPin(val lat: Double, val lng: Double, val color: Color)

// A MapLibre map showing lines and pins, zoomed to fit them. MapLibre's map is
// an Android View, so we host it in AndroidView and pass it the screen's
// lifecycle (it needs onStart, onStop and so on to manage its resources).
@Composable
fun RouteMap(lines: List<MapLine>, pins: List<MapPin>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(MAP_STYLE)) { loaded ->
                loaded.addSource(GeoJsonSource(LINES))
                loaded.addSource(GeoJsonSource(PINS))
                loaded.addLayer(
                    LineLayer(WALK_LAYER, LINES)
                        .withFilter(Expression.eq(Expression.get("walk"), Expression.literal(true)))
                        .withProperties(
                            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineDasharray(arrayOf(1f, 1.5f)),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        ),
                )
                loaded.addLayer(
                    LineLayer(RIDE_LAYER, LINES)
                        .withFilter(Expression.eq(Expression.get("walk"), Expression.literal(false)))
                        .withProperties(
                            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                            PropertyFactory.lineWidth(6f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                )
                loaded.addLayer(
                    CircleLayer(PIN_LAYER, PINS).withProperties(
                        PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleStrokeColor("#ffffff"),
                        PropertyFactory.circleStrokeWidth(2.5f),
                    ),
                )
                style = loaded
            }
        }
    }

    // Draw (or redraw) whenever the lines or pins change, once the style is in.
    LaunchedEffect(style, lines, pins) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(LINES)?.setGeoJson(linesGeoJson(lines))
        s.getSourceAs<GeoJsonSource>(PINS)?.setGeoJson(pinsGeoJson(pins))

        val points = lines.flatMap { it.points } + pins.map { it.lat to it.lng }
        when {
            points.size >= 2 -> {
                val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.first, it.second) }).build()
                map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 600)
            }
            points.size == 1 -> map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].first, points[0].second), 15.0))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val LINES = "route-lines"
private const val PINS = "route-pins"
private const val WALK_LAYER = "route-walks"
private const val RIDE_LAYER = "route-rides"
private const val PIN_LAYER = "route-pins-layer"

private fun hex(color: Color): String = "#%06x".format(color.toArgb() and 0xffffff)

// GeoJSON wants [lng, lat], the other way round from everything else.
private fun position(lat: Double, lng: Double): JsonArray = buildJsonArray {
    add(JsonPrimitive(lng))
    add(JsonPrimitive(lat))
}

private fun linesGeoJson(lines: List<MapLine>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        lines.filter { it.points.size >= 2 }.forEach { line ->
            add(
                buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") {
                        put("color", hex(line.color))
                        put("walk", line.walk)
                    }
                    putJsonObject("geometry") {
                        put("type", "LineString")
                        put("coordinates", JsonArray(line.points.map { position(it.first, it.second) }))
                    }
                },
            )
        }
    }
}.toString()

private fun pinsGeoJson(pins: List<MapPin>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        pins.forEach { pin ->
            add(
                buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("color", hex(pin.color)) }
                    putJsonObject("geometry") {
                        put("type", "Point")
                        put("coordinates", position(pin.lat, pin.lng))
                    }
                },
            )
        }
    }
}.toString()
