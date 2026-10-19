package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 750 to 760: the Man'yoshu with its last dated poem of 759, the Kaifuso of 751 and Ibn al-Muqaffa's Arabic Kalila wa Dimna of about 750. */
val db129Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Man'yoshu, a Japanese poetry anthology whose last dated poem is of 759",
        originalTitle = "Man'yoshu",
        author = "Otomo no Yakamochi (leading candidate)",
        year = 759,
        summary = "The Man'yoshu, the oldest surviving Japanese poetry anthology, has twenty books, and its last dated poem, by Otomo no Yakamochi, is of 759.",
        details = "Its poems are written in man'yogana, a script that uses Chinese characters for their sounds and so records the Old Japanese words. Accounts differ on who compiled it and when it was finished, since some sources place completion soon after 759 and others in the 780s.",
    ),

    entry(
        databaseId = 129,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Kaifuso, the oldest surviving anthology of Chinese-style poems by Japanese poets, compiled in 751",
        originalTitle = "Kaifuso",
        author = "Compiler unknown",
        year = 751,
        summary = "The Kaifuso, an anthology of poems in Classical Chinese by Japanese poets, was compiled in 751 and is the oldest surviving collection of its kind.",
        details = "751 is the usual date of its compilation, and the poems imitate the forms of Tang verse. The anthology shows a Japanese court writing in Classical Chinese, so it belongs to the history of written Japanese as much as to poetry.",
        rare = true,
    ),

    entry(
        databaseId = 129,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Ibn al-Muqaffa's Arabic Kalila wa Dimna, a version of Indian fables made about 750",
        originalTitle = "Kalila wa Dimna",
        author = "Ibn al-Muqaffa",
        year = 750,
        summary = "Ibn al-Muqaffa's Arabic Kalila wa Dimna, a collection of animal fables from an Indian source, was made in the eighth century and is dated to about 750 in many accounts.",
        details = "The Arabic follows a Middle Persian version made in the sixth century, which in turn drew on the Indian Panchatantra. The date is disputed, since sources place the translation anywhere in the eighth century, the translator's death is dated about 757, and the earliest surviving Arabic copies are of the thirteenth and fourteenth centuries.",
    ),

)
