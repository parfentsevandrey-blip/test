package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 570 to 580: dating events from the Arab Year of the Elephant, the traditional year of Muhammad's birth. */
val db147Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.PRACTICE,
        section = "Arabic",
        title = "Dating by the Year of the Elephant, an Arab reference year traditionally set at 570",
        originalTitle = "Am al-Fil, the Year of the Elephant",
        author = "Arab tradition",
        year = 570,
        summary = "Arab writers used the Year of the Elephant, the year of Abraha's failed attack on Mecca, as a reference point, so that events could be dated by the years after it.",
        details = "Arab tradition ties the attack, in which an army with elephants failed to take Mecca, to the year of Muhammad's birth, which is set at about 570. The year rests on tradition rather than on a dated record, so 570 is a traditional date, and the practice is known from later Arabic writing.",
        rare = true,
    ),

)
