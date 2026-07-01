package com.zor.monitor.models

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class Record(
    val id: String = UUID.randomUUID().toString(),
    val date: String = "",                  // dd.MM.yy (для UI)
    val time: String = "",
    val direction: String = "",
    val point: String = "",
    val type: String = "",
    @SerializedName("freq_video") val freqVideo: String = "",
    @SerializedName("freq_control") val freqControl: String = "",
    val status: String = "АКТИВЕН",        // "ПОДАВЛЕН", "АКТИВЕН", "ДЕТОНАЦИЯ"
    val exported: Boolean = false,
    val voiceText: String = "",
    val isoDate: String = ""                // yyyy-MM-dd (внутренний формат)
)
