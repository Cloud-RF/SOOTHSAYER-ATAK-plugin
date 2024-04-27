package com.atakmap.android.soothsayer.models.response

class TemplatesResponse : ArrayList<TemplatesResponseItem>()

data class TemplatesResponseItem(
    val id: Int,
    val name: String
)