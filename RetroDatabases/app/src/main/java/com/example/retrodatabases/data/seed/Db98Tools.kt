package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1067: the Leipian, a Song dictionary of Chinese characters arranged under radicals. */
val db98Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.TOOL,
        section = "Dictionaries",
        title = "The Leipian, a Song dictionary of Chinese characters sorted under radicals",
        originalTitle = "Leipian",
        author = "Sima Guang and four chief editors",
        year = 1067,
        summary = "The Leipian is a Song dictionary of Chinese characters arranged under radicals, finished in 1066 with Sima Guang as final editor, and printed copies were sent to Emperor Yingzong in 1067.",
        details = "Work on the dictionary began in 1039 under Emperor Renzong, and its radical scheme follows the Shuowen Jiezi, the Han dictionary of characters. The year 1067 is the date of the first presentation, while the completed text is dated to 1066.",
        rare = true,
    ),

)
