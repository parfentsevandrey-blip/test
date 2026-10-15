package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1540 to 1549: a public mathematical contest, a copybook for handwriting, dead reckoning, silver mining at Potosi and the English service book. */
val db50Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.PRACTICE,
        section = "Contests",
        title = "The public contest of Ferrari and Tartaglia in Milan, 1548",
        originalTitle = "Cartelli",
        author = "Lodovico Ferrari and Niccolo Tartaglia",
        year = 1548,
        summary = "In 1548 Ferrari and Tartaglia set each other problems in a public contest at Milan, a challenge that mathematicians used to win fame.",
        details = "The contest followed a quarrel over the cubic method that Tartaglia had shared with Cardano, and it was held in Milan in August 1548. Accounts differ on the exact days of the contest, and they usually give Ferrari the victory.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.PRACTICE,
        section = "Handwriting",
        title = "Palatino's copybook for the chancery hand",
        originalTitle = "Libro nuovo d'imparare a scrivere",
        author = "Giovanni Battista Palatino",
        year = 1540,
        summary = "Palatino's copybook of 1540 taught writers the chancery hand through printed models of each letter.",
        details = "It was printed at Rome in 1540, and its alphabets show the chancery and italic forms that secretaries used in letters and documents. Like other copybooks of its time, it gave learners printed models to copy by hand.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.PRACTICE,
        section = "Navigation",
        title = "Dead reckoning from logged courses, as pilots kept it",
        originalTitle = "Arte de navegar",
        author = "Pedro de Medina",
        year = 1545,
        summary = "Pilots of the 1540s estimated a ship's position from the courses and distances logged each day, and Medina's navigation book of 1545 taught the method.",
        details = "Medina's Arte de navegar was printed at Valladolid in 1545, and it covered the courses, distances and compass that navigators used. The estimate ignores currents and leeway, so a pilot corrected it whenever a sighting of land or sun could be taken.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.PRACTICE,
        section = "Mining",
        title = "Silver mining at Potosi, begun in 1545",
        originalTitle = "Cerro Rico",
        author = "Spanish colonial miners",
        year = 1545,
        summary = "By the usual account the silver mine at Potosi was opened in 1545, and the crown's royal fifth made it a major source of silver for Spain.",
        details = "The usual account dates the discovery of silver on the Cerro Rico to 1545, though accounts differ on the finder and on the exact date. The crown took a fifth of the silver as the royal fifth, which was assessed on every batch of bars.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.PRACTICE,
        section = "Worship",
        title = "Saying the English service from the 1549 prayer book",
        originalTitle = "The Booke of the Common Prayer",
        author = "Thomas Cranmer",
        year = 1549,
        summary = "The 1549 prayer book put the English church service into English for the whole parish, so that the rites were said in the vernacular.",
        details = "It was issued in 1549 under Edward VI and was in use in parish churches from that summer. Later revisions in 1552 and 1559 changed the text, so the 1549 book is the first of several English service books.",
    ),

)
