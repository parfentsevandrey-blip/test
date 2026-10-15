package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1510 to 1518: counting on ruled lines, Erasmus's Latin dialogues, chiaroscuro woodcut, Leonardo's mirror writing and almanac forecasting. */
val db53Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.PRACTICE,
        section = "Dating",
        title = "Roman-style day counts in humanist letters",
        originalTitle = "Ante diem",
        author = "Humanist letter writers of the period",
        year = 1516,
        summary = "Humanist letters of the 1510s were often dated by counting days toward the Kalends, Nones or Ides, in the form a.d. III Kal. Ian.",
        details = "The count is inclusive, so the target day counts as the first day, and pridie names the day just before the Kalends, Nones or Ides. Accounts differ on how far the practice spread beyond writers who knew Latin well.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "Erasmus's Colloquies, Latin speech practised in dialogues",
        originalTitle = "Familiarium colloquiorum formulae",
        author = "Desiderius Erasmus",
        year = 1518,
        summary = "Erasmus's Colloquies of 1518 are short Latin dialogues on everyday life, written so that students could practise speaking Latin in the schoolroom.",
        details = "The dialogues were reprinted in many later editions and used in schools. The book is a teaching practice rather than a grammar, since its speakers use Latin in ordinary talk.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.PRACTICE,
        section = "Printing",
        title = "Ugo da Carpi's chiaroscuro woodcut, printed from several blocks",
        originalTitle = "Chiaroscuro",
        author = "Ugo da Carpi",
        year = 1516,
        summary = "Ugo da Carpi printed woodcuts in several tones of colour from separate blocks, a method for which he is said to have won a papal privilege.",
        details = "One block carried the outline and others carried the light and shade, so that a single print could show more than one tone of colour. Accounts differ on the year of the privilege, which is given as 1516 in some accounts.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.PRACTICE,
        section = "Notebooks",
        title = "Leonardo's mirror writing in his notebooks",
        originalTitle = "Scrittura a specchio",
        author = "Leonardo da Vinci",
        year = 1510,
        summary = "Leonardo wrote many notebook pages in mirror script, written from right to left with the letters reversed, so that they can be read only with a mirror.",
        details = "The practice runs through notebooks of about 1510, and historians give different reasons for it, from a wish for privacy to ease of writing for a left-handed writer. Accounts differ on which reason is right.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.PRACTICE,
        section = "Prognostication",
        title = "Forecasting a year from the planets, as the almanacs taught",
        originalTitle = "Prognosticatio",
        author = "Almanac makers such as Johannes Stoeffler",
        year = 1518,
        summary = "Almanac makers of the 1510s told readers how to forecast weather and events from the positions of the planets and the signs of the zodiac.",
        details = "The rules combined planetary positions with the calendar to give each season a forecast. Accounts differ on how far readers trusted these forecasts, and the flood predicted for 1524 is the best-known example.",
    ),

)
