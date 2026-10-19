package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 860 to 870: the Diamond Sutra, printed from woodblocks at Dunhuang and dated 868 in its colophon. */
val db118Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.PRACTICE,
        section = "Printing",
        title = "The Diamond Sutra, printed from woodblocks and dated 868 in its colophon",
        originalTitle = "Jingang bore boluomi jing",
        author = "Wang Jie, who had it printed",
        year = 868,
        summary = "The Diamond Sutra, a Buddhist scroll printed from woodblocks, carries a colophon dating its printing to 868 and is usually cited as the oldest dated printed book.",
        details = "The colophon gives 11 May 868 and says the work was printed by Wang Jie for free distribution, and the scroll was found in a cave at Dunhuang in 1900. Earlier printed items survive, but few of them carry a date, so the claim rests on this colophon.",
    ),

)
