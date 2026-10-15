package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1570 to 1580: Tycho's test of a new star, watching a great comet, a Spanish questionnaire, engraving atlas maps and drawing town views. */
val db47Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.PRACTICE,
        section = "Observation",
        title = "Tycho's naked-eye test of the nova of 1572, in De nova stella",
        originalTitle = "De nova stella",
        author = "Tycho Brahe",
        year = 1573,
        summary = "Tycho's book of 1573 reported his measures of the new star of 1572 against the fixed stars, and his test found no parallax that would place the star near the Earth.",
        details = "Tycho saw the new star in Cassiopeia from November 1572 and compared its place with nearby stars over the following weeks, and the book was printed at Copenhagen in 1573. For Tycho the lack of a daily shift placed the star among the fixed stars, although the test was limited by the accuracy of his instruments.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.PRACTICE,
        section = "Observation",
        title = "Observing the great comet of 1577 from several towns",
        originalTitle = "Observations of the comet of 1577",
        author = "Tycho Brahe and other observers",
        year = 1577,
        summary = "The great comet of 1577 was watched from many towns in Europe, and observers compared their sightings to judge how far away the comet lay.",
        details = "The comet was seen in November 1577, and Tycho recorded it with the stars near its path to fix its place. Accounts differ on how well the sightings agree, and Tycho's argument that the comet lay beyond the Moon was published only in 1588.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.PRACTICE,
        section = "Surveys",
        title = "Answering Philip II's questionnaire for the relaciones geograficas, 1577",
        originalTitle = "Relaciones geograficas",
        author = "Philip II of Spain",
        year = 1577,
        summary = "In 1577 Philip II sent a questionnaire to local officials in the Spanish Indies, asking for geographic and social accounts of each place.",
        details = "The questionnaire was issued in 1577 and the replies, called relaciones geograficas, came in over the following years, many of them in the early 1580s. Accounts differ on how many replies survive and on how many places answered, so no total is given here.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.PRACTICE,
        section = "Printing",
        title = "Engraving maps on copper plates for a printed atlas, 1570",
        originalTitle = "Theatrum orbis terrarum",
        author = "Abraham Ortelius",
        year = 1570,
        summary = "An atlas map of 1570 was engraved backwards on a copper plate, printed in ink on paper, and often coloured by hand, so that each sheet was a small piece of craft.",
        details = "Ortelius's maps were engraved by skilled craftsmen in Antwerp, and the plates were printed in editions that could be coloured by hand, as many surviving copies show. Accounts differ on which engraver cut which map, since the work was shared out among several hands.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.PRACTICE,
        section = "Views",
        title = "Drawing bird's-eye town views for the Civitates of 1572",
        originalTitle = "Civitates orbis terrarum",
        author = "Georg Braun and Frans Hogenberg",
        year = 1572,
        summary = "The Civitates orbis terrarum of 1572 printed engraved bird's-eye views of cities, a new kind of town picture in print.",
        details = "The first volume appeared at Cologne in 1572, and the work grew into several volumes of town views and plans. Accounts differ on how far each view was drawn from life, since many were built from earlier prints and sketches.",
    ),

)
