package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1830 to 1840: an early telegraph line, a network of magnetic observatories, two societies and a uniform postal service. */
val db21Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.SYSTEM,
        section = "Telegraph",
        title = "Gauss and Weber's telegraph at Gottingen",
        originalTitle = "Gauss's letter of November 1833 on the telegraph line",
        author = "Carl Friedrich Gauss and Wilhelm Weber",
        year = 1833,
        summary = "A copper wire line that carried electromagnetic signals between the Gottingen observatory and the physics cabinet, one of the early working telegraph lines.",
        details = "The line was built in 1833 with Gauss at the observatory and Weber at the physics cabinet, and a reversing switch that Gauss called a commutator let the operator send signals in either direction. Accounts of its length differ: one gives about three kilometres, another about a mile, and Gauss's letter projects about 8000 feet. The service is reported to have run until 1845, and the endpoints differ between accounts.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.SYSTEM,
        section = "Geomagnetism",
        title = "Magnetischer Verein, the magnetic union of observatories",
        originalTitle = "Magnetischer Verein (1836)",
        author = "Carl Friedrich Gauss and Wilhelm Weber, with Alexander von Humboldt",
        year = 1836,
        summary = "A network of magnetic observatories in several countries that made their readings at agreed times, so the earth's magnetism could be compared across places.",
        details = "Gauss and Weber founded the union in 1836 with Humboldt's support, and observers made their readings together on fixed days. The network ran for about five years, until about 1841. The record uses the 1836 founding year.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.SYSTEM,
        section = "Statistics",
        title = "Statistical Society of London",
        originalTitle = "Journal of the Statistical Society of London (1838)",
        author = "Founders including Charles Babbage",
        year = 1834,
        summary = "A society founded in London in 1834 to collect and publish numerical facts about society, which later became the Royal Statistical Society.",
        details = "The society was founded in 1834, and its early work gathered numerical facts on population, education and trade. Its journal began in 1838, and the society received its royal charter much later. The record uses the 1834 founding year.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.SYSTEM,
        section = "Science organisations",
        title = "British Association for the Advancement of Science",
        originalTitle = "First meeting of the British Association at York (1831)",
        author = "Founding members of the British Association",
        year = 1831,
        summary = "A British society that held its meetings in a different town each year, so that scientists could meet, share results and bring their work before the public.",
        details = "The first meeting was held at York in 1831, and the association then met in another town each year. Its annual reports printed the papers read and the work of its committees. The record uses the York meeting year.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.SYSTEM,
        section = "Steam navigation",
        title = "Great Western, the Bristol to New York steamship service",
        originalTitle = "Great Western steamship (1838)",
        author = "Great Western Steamship Company",
        year = 1838,
        summary = "A paddle steamship built for a regular service between Bristol and New York, which crossed the Atlantic under steam in 1838.",
        details = "The ship was built for a regular Atlantic service from Bristol, and it reached New York under steam in 1838. Other steamers made the crossing in the same year. The record uses the 1838 crossing.",
    ),
)
