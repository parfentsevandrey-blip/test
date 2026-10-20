package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 120 to 130: Zhang Heng's Lingxian, a Chinese treatise on the heavens that accounts date to about 120. */
val db192Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Zhang Heng's Lingxian, a treatise on the celestial sphere of about 120",
        originalTitle = "Lingxian",
        author = "Zhang Heng",
        year = 120,
        summary = "Zhang Heng's Lingxian is a treatise on the heavens, dated by accounts to about 120, and it is a central work of his celestial-sphere cosmology.",
        details = "The title is also rendered as Spiritual Constitution of the Universe, and English renderings differ. Accounts put the treatise at about 120, so 120 is an approximate year and not the day of a known completion.",
        rare = true,
    ),

)
