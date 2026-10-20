package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 130 to 140: counting forty-nine yarrow stalks to set the lines of a hexagram, placed at 132. */
val db191Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.PRACTICE,
        section = "Divination",
        title = "Counting forty-nine yarrow stalks to set the lines of a hexagram",
        originalTitle = "Dayan zhi shu",
        author = "Anonymous commentators",
        year = 132,
        summary = "The yarrow-stalk method splits forty-nine stalks into piles and counts them in fours, and three changes of the stalks give one line of a hexagram.",
        details = "The Xici, the Great Treatise appended to the Book of Changes, describes the procedure, but the Xici is anonymous, traditionally credited to Confucius, and its date is disputed. The year 132 is placed as the first year of the Yangjia era, the anchor, and not the date of the practice, and the stalk counts that remain after three changes, 24, 28, 32 and 36, give the line values six, seven, eight and nine.",
        rare = true,
    ),

)
