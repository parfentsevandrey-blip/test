package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 860 to 870: al-Farghani's Elements of Astronomy, a placed record dated to the anchor of 861 (see its details). */
val db118Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "al-Farghani's Elements of Astronomy, a summary of Ptolemaic astronomy",
        originalTitle = "Kitab fi jawami' 'ilm al-nujum",
        author = "Ahmad ibn Muhammad al-Farghani",
        year = 861,
        summary = "al-Farghani's Elements of Astronomy, a summary of Ptolemaic astronomy, is placed here at 861, the year of the Roda Nilometer rebuild.",
        details = "This is a placed record. No date for the book is secure enough to give 861, so 861 is the year of the anchor, the rebuild of the Roda Nilometer, which some accounts credit to al-Farghani as designer. The book is also credited with the figure of 20,400 Arabic miles for the Earth's circumference, which is 56 2/3 miles to a degree times 360.",
        rare = true,
    ),

)
