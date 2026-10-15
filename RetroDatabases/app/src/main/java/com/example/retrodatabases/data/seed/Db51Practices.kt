package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1530 to 1540: triangulation for maps, diminutions for the recorder, a writing manual, lute tablature and a practical arithmetic. */
val db51Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.PRACTICE,
        section = "Surveying",
        title = "Gemma Frisius's triangulation for mapping",
        originalTitle = "Libellus de locorum describendorum ratione",
        author = "Gemma Frisius",
        year = 1533,
        summary = "Gemma Frisius's Libellus of 1533 described triangulation, the method of fixing a far place by sightings taken from both ends of a measured baseline.",
        details = "The Libellus was printed at Antwerp in 1533, and the sightings from the two ends of the baseline cross at the far point, which is then placed on the map. The method spread through Germany, Austria and the Netherlands in the decades that followed.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.PRACTICE,
        section = "Music",
        title = "Ganassi's diminutions for the recorder",
        originalTitle = "La Fontegara",
        author = "Silvestro Ganassi",
        year = 1535,
        summary = "Ganassi's La Fontegara of 1535 taught recorder players to divide a long note into faster notes, a practice called diminution.",
        details = "The book was printed at Venice in 1535, and it teaches the division of notes into shorter values by set patterns. Its examples are musical rather than mathematical, but a reader can count how many notes each pattern puts into one long note.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.PRACTICE,
        section = "Writing",
        title = "Palatino's writing manual of 1540",
        originalTitle = "Libro nuovo d'imparare a scrivere",
        author = "Giovanni Battista Palatino",
        year = 1540,
        summary = "Palatino's Libro nuovo of 1540 is a writing manual of copybook alphabets, with letters drawn for the pen so that a learner could copy them stroke by stroke.",
        details = "The manual was printed at Rome in 1540, and writing masters of the period used such copybooks to train clerks and scribes. Its letters were drawn for the pen rather than for the press, and the book is a practical guide to hand writing.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.PRACTICE,
        section = "Music",
        title = "Gerle's lute tablature, Musica teusch",
        originalTitle = "Musica teusch",
        author = "Hans Gerle",
        year = 1532,
        summary = "Gerle's Musica teusch of 1532 taught German players to read tablature, the notation that shows the fingered strings instead of the pitches.",
        details = "The book was printed at Nuremberg in 1532, and it gives tablature for the lute and for the viol. Tablature shows the strings and frets to press rather than the pitches, so a player reads the book's signs directly without knowing the staff.",
        rare = true,
    ),

    entry(
        databaseId = 51,
        kind = Kind.PRACTICE,
        section = "Arithmetic",
        title = "Gemma Frisius's practical arithmetic of 1540",
        originalTitle = "Arithmeticae practicae methodus facilis",
        author = "Gemma Frisius",
        year = 1540,
        summary = "Gemma Frisius's easy method of practical arithmetic, printed in 1540, taught the four rules and the rule of three to merchants and surveyors.",
        details = "The book was printed at Antwerp in 1540 as a textbook for merchants who needed reckoning in daily trade. Its title promises an easy method.",
    ),

)
