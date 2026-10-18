package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1068: a brass astrolabe made at Toledo by Ibrahim ibn Sa'id as-Sahli, dated by a named month of AH 460. */
val db98Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.DEVICE,
        section = "Astronomical instruments",
        title = "The Toledo astrolabe of Ibrahim ibn Sa'id as-Sahli, dated 1068 by a named month",
        originalTitle = "Toledo astrolabe, AH 460",
        author = "Ibrahim ibn Sa'id as-Sahli",
        year = 1068,
        summary = "A brass astrolabe made at Toledo by Ibrahim ibn Sa'id as-Sahli is dated 1068, which is AH 460, by the month of Shawwal that the maker named.",
        details = "The instrument is now at the Museum of the History of Science in Oxford. Exact dates of this kind seldom appear on astrolabes, so the year rests on the named month of Shawwal and is less certain than an exact day would make it. Shawwal of AH 460 falls in 1068, the year used here.",
        rare = true,
    ),

)
