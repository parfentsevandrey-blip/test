package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 200 to 210: Hua Tuo's surgery under the anaesthetic drink mafeisan, placed at the year of his death. */
val db184Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.PRACTICE,
        section = "Medicine",
        title = "Hua Tuo's surgery under an anaesthetic drink, told in the Book of the Later Han",
        originalTitle = "Hou Hanshu, biography of Hua Tuo",
        author = "Hua Tuo, with Fan Ye as compiler of the Book of the Later Han",
        year = 208,
        summary = "Hua Tuo, a Chinese physician, is said to have operated on patients who had first drunk mafeisan, a drink that made them insensible to pain.",
        details = "The Records of the Three Kingdoms gives Hua Tuo's life only briefly, and the account of the drink is told in the Book of the Later Han, compiled in the fifth century, more than two centuries after his death. The year 208 is the usual date of his death, after Cao Cao had him jailed, and the sources do not date the surgery, so 208 is a placed year; the ingredients of mafeisan are unknown.",
        rare = true,
    ),

)
