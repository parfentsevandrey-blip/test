package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 10 to 20: the Roman potter's wheel, undated in the sources and placed at 14 with the accession of Tiberius. */
val db203Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.DEVICE,
        section = "Clay",
        title = "The potter's wheel of the Roman provinces, a turntable that let a potter throw clay",
        originalTitle = "Potter's wheel",
        author = "Roman potters",
        year = 14,
        summary = "A potter's wheel turned the clay on an axle so that the potter could throw and shape a pot, and the sources do not date its first use in the empire.",
        details = "The sources do not date this device; 14 is the year of the anchor, the accession of Tiberius, not of the making. Wheel-made pottery turns up across the provinces, but no Roman text gives a date for the wheel's introduction, so the year is a placed one only.",
        rare = true,
    ),

)
