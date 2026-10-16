package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1330 to 1335: a Japanese essay book, a Castilian poem, an Italian poem in ottava rima and a psalter with Anglo-Norman captions. */
val db71Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Kenko's Essays in Idleness, written in Japanese",
        originalTitle = "Tsurezuregusa",
        author = "Yoshida Kenko",
        year = 1330,
        summary = "Yoshida Kenko's Tsurezuregusa, a book of short essays in Japanese prose, was written in the years around 1330.",
        details = "Kenko was a court official who became a Buddhist monk, and the essays are usually dated to 1330 to 1332, a span on which accounts do not fully agree. The title is commonly rendered as Essays in Idleness.",
    ),

    entry(
        databaseId = 71,
        kind = Kind.LANGUAGE,
        section = "Castilian",
        title = "Juan Ruiz's Book of Good Love, a Castilian poem of about 1330",
        originalTitle = "Libro de buen amor",
        author = "Juan Ruiz, Archpriest of Hita",
        year = 1330,
        summary = "Juan Ruiz's Libro de buen amor is a comic and satirical poem in Castilian verse, dated to the 1330s by one of two readings.",
        details = "Accounts give the poem the dates 1330 and 1343, and the two readings have long been debated. The verse is written in cuaderna via, four-line stanzas of fourteen-syllable lines.",
    ),

    entry(
        databaseId = 71,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Boccaccio's Filostrato, a love poem in ottava rima",
        originalTitle = "Il Filostrato",
        author = "Giovanni Boccaccio",
        year = 1335,
        summary = "Boccaccio's Filostrato, a long Italian poem about Troilus and Criseyde in ottava rima, was written about 1335.",
        details = "The poem is usually dated to about 1335, and the year is an estimate from the work and its place among Boccaccio's early writings. Chaucer later drew on it as the main source of his Troilus and Criseyde.",
    ),

    entry(
        databaseId = 71,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "The Anglo-Norman captions of the Luttrell Psalter",
        originalTitle = "Luttrell Psalter",
        author = "Sir Geoffrey Luttrell and the painters of his psalter",
        year = 1330,
        summary = "The Luttrell Psalter, an English illuminated psalter, has its picture captions written in Anglo-Norman French.",
        details = "The manuscript is usually dated to about 1325 to about 1340, and it was made for the Lincolnshire knight Sir Geoffrey Luttrell. The captions show that Anglo-Norman was still written in English books of the period.",
        rare = true,
    ),

)
