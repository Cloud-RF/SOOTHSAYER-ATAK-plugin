package com.atakmap.android.soothsayer.models.linksmodel

import com.atakmap.coremap.maps.coords.GeoPoint

data class LinkDataModel(
    val markerId: String,
    var linkRequest: LinkRequest,
    val links: ArrayList<Link>,
    var linkResponse: LinkResponse?
)

data class Link(val linkId: String, val startPoint: GeoPoint, val endPoint: GeoPoint)