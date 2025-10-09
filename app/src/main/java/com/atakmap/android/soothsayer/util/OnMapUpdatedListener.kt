package com.atakmap.android.soothsayer.util

import com.atakmap.android.maps.Polyline

fun interface OnMapUpdatedListener {
    fun onMapViewAddItem(line: Polyline)
}