package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1780 to 1790: printed tables, reference works and the yearly ephemerides that calculators relied on. */
val db26Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.TOOL,
        section = "Tables",
        title = "Hutton's Mathematical Tables",
        originalTitle = "Mathematical Tables",
        author = "Charles Hutton",
        year = 1785,
        summary = "A book of logarithms and trigonometric tables that calculators used for the next century.",
        details = "Published in London in 1785, the tables gave common and hyperbolic logarithms, sines, tangents and secants. Several later editions kept the book in use.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.TOOL,
        section = "Reference",
        title = "The Encyclopedie methodique",
        originalTitle = "Encyclopedie methodique",
        author = "Charles-Joseph Panckoucke and others",
        year = 1782,
        summary = "A reference work arranged by subject, with volumes of mathematics and of arts and crafts issued from 1782.",
        details = "The series was planned as a new edition of Diderot's Encyclopedie, and its mathematical volumes gave methods and tables for calculators. It was still being published in the 1790s.",
        rare = true,
    ),

    entry(
        databaseId = 26,
        kind = Kind.TOOL,
        section = "Ephemerides",
        title = "The Connaissance des temps for 1785",
        originalTitle = "Connaissance des temps",
        author = "Bureau des longitudes of Paris",
        year = 1785,
        summary = "The yearly French ephemeris, with positions of the planets and the Moon for astronomers and navigators.",
        details = "Each annual volume gave daily positions for the year ahead, so that observers could plan their work. It was one of the reference books that calculators checked their results against.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.TOOL,
        section = "Ephemerides",
        title = "The Berliner Astronomisches Jahrbuch for 1785",
        originalTitle = "Astronomisches Jahrbuch fuer das Jahr 1785",
        author = "Johann Elert Bode",
        year = 1785,
        summary = "A yearly volume of planetary and lunar positions, computed in Berlin for the year ahead.",
        details = "Bode edited the yearly Jahrbuch from 1776. Its tables were used by astronomers across Germany, and the volumes were issued one year in advance.",
        rare = true,
    ),

    entry(
        databaseId = 26,
        kind = Kind.TOOL,
        section = "Journals",
        title = "The Philosophical Transactions of 1785",
        originalTitle = "Philosophical Transactions of the Royal Society of London, volume 75",
        author = "The Royal Society",
        year = 1785,
        summary = "The learned journal of the Royal Society, where new tables, observations and calculations were printed.",
        details = "The 1785 volume printed papers on electricity, astronomy and mathematics. Authors sent their tables there so that other calculators could check them.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.TOOL,
        section = "Journals",
        title = "The Memoires of the Paris Academy for 1785",
        originalTitle = "Memoires de l'Academie royale des sciences, 1785",
        author = "Academie royale des sciences",
        year = 1785,
        summary = "The memoirs of the Paris Academy, which printed the new mathematical work of the period.",
        details = "The 1785 volume contained Coulomb's papers on electricity, among other mathematics. It was the main outlet for French research.",
    ),
)
