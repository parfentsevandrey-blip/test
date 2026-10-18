package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 950 to 960: a Japanese anthology of 951 and a Danish runestone of about 950. */
val db109Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Gosen wakashu, the second imperial anthology of waka, ordered in 951",
        originalTitle = "Gosen wakashu",
        author = "Emperor Murakami and five poets of the waka office",
        year = 951,
        summary = "Emperor Murakami ordered the Gosen wakashu in 951, the second imperial anthology of Japanese poems, and five poets compiled it in a new waka office at the palace.",
        details = "The office sat in the Pear Pavilion (nashitsubo) wing of the palace, and one count gives the anthology twenty volumes and 1,426 poems. Accounts differ on when the work was finished, and some place its completion anywhere in the 950s.",
        rare = true,
    ),

    entry(
        databaseId = 109,
        kind = Kind.LANGUAGE,
        section = "Old Norse",
        title = "Gorm's runestone at Jelling, a memorial to his queen Thyra",
        originalTitle = "Jelling runestone I, Old Norse runic inscription",
        author = "King Gorm of Denmark",
        year = 950,
        summary = "The smaller Jelling runestone, raised by King Gorm of Denmark as a memorial to his queen Thyra, is dated to about 950 and carries a runic inscription.",
        details = "The inscription is cut in runes of the Younger futhark, the runic alphabet of the Viking Age, and it names Thyra as the person the memorial honours. Its original site is not known, and the stone has stood in its present place since about 1630.",
    ),

)
