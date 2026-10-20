package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 244 to 248: the seminar of Plotinus at Rome and the Secular Games of Philip the Arab. */
val db180Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.PRACTICE,
        section = "Philosophy",
        title = "Plotinus's seminar at Rome, where pupils read and questioned their teacher",
        originalTitle = "Plotinou schole",
        author = "Plotinus",
        year = 244,
        summary = "From about 244 Plotinus taught a circle of pupils at Rome, reading texts with them and answering their questions in sessions that his pupil Porphyry describes.",
        details = "Porphyry says that Plotinus came to Rome in the reign of Gordian III, and 244 is the usual year given for the move. The sessions Porphyry describes combined the reading of texts with questions from the pupils and answers from the teacher.",
        rare = true,
    ),

    entry(
        databaseId = 180,
        kind = Kind.PRACTICE,
        section = "Rome",
        title = "The Secular Games of 248, Rome's thousandth-year festival under Philip the Arab",
        originalTitle = "Ludi saeculares",
        author = "Philip the Arab",
        year = 248,
        summary = "Philip the Arab marked the thousand years of Rome in 248 with the Secular Games, a public festival of games and shows that the sources date to that year.",
        details = "The games marked a thousand years since the founding of the city, and they followed the older rite of the Secular Games, which the Roman state held at long intervals. The sources date the festival to 248 and describe public games and shows held in the city for the anniversary.",
    ),

)
