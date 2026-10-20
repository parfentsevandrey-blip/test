package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 230 to 240: Zhang Yi's Guangya, a word dictionary that extends the Erya, compiled at the Wei court. */
val db181Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Zhang Yi's Guangya, a dictionary of words that extends the Erya",
        originalTitle = "Guangya",
        author = "Zhang Yi",
        year = 230,
        summary = "Zhang Yi's Guangya is a dictionary of words that extends the older Erya, compiled by a scholar at the Wei court about 230.",
        details = "The sources date the Guangya to roughly 230 and place Zhang Yi at the court academy of Wei in the reign of Emperor Ming, but they give no presentation date, so 230 is the year used here. Counts of its entries differ between the sources, so this record gives none. It is a word list in the manner of the Erya, and it is not the character dictionary of Xu Shen.",
        rare = true,
    ),

)
