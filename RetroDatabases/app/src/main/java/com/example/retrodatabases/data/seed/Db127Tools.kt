package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 770 to 780: the Sindhind, Arabic astronomical tables made at Baghdad from Brahmagupta's treatise in the early 770s. */
val db127Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.TOOL,
        section = "Arabic",
        title = "The Sindhind, Arabic astronomical tables made at Baghdad from Brahmagupta's treatise in the early 770s",
        originalTitle = "Sindhind",
        author = "Brahmagupta, with Muhammad ibn Ibrahim al-Fazari and Ya'qub ibn Tariq as translators",
        year = 771,
        summary = "The Sindhind, a set of Arabic astronomical tables based on Brahmagupta's Sanskrit treatise, was made at Baghdad under the caliph al-Mansur in the early 770s.",
        details = "Indian scholars brought the Brahmasphutasiddhanta, composed in 628, to Baghdad about 770, and al-Mansur had it rendered into Arabic with tables for the sun, the moon and the five planets, and a sine table. Accounts differ on the dates of the visit and of the translation, and 771 is the year used here; no complete copy survives, and the work is known from fragments and from later Arabic tables that drew on it.",
        rare = true,
    ),

)
