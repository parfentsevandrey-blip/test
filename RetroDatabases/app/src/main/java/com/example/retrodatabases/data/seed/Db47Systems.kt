package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Institutions of 1570 to 1580: a debating house at Fatehpur Sikri, a Paris academy of verse and music, two universities, and an observatory at Istanbul. */
val db47Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.SYSTEM,
        section = "Debates",
        title = "Akbar's Ibadat Khana at Fatehpur Sikri, founded in 1575",
        originalTitle = "Ibadat Khana",
        author = "Emperor Akbar",
        year = 1575,
        summary = "Akbar's Ibadat Khana, the house of worship at Fatehpur Sikri, was built in 1575 as a hall where scholars of several faiths debated before the emperor.",
        details = "Akbar gathered Muslim, Hindu, Jain and Zoroastrian scholars there, and later Christian visitors joined the debates in the hall. Accounts differ on how far the debates changed Akbar's religious policy, and the building's date is commonly given as 1575.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The Academie de Poesie et de Musique of Paris, founded in 1570",
        originalTitle = "Academie de Poesie et de Musique",
        author = "Jean-Antoine de Baif",
        year = 1570,
        summary = "The Academie de Poesie et de Musique was founded at Paris in 1570 to set French verse to measured music, following the classical rule of long and short syllables.",
        details = "Baif obtained a royal patent for the academy in 1570, and its members, with the musician Joachim Thibault de Courville, tried to fit French verse to measured music by the length of its syllables. Accounts differ on how long the academy kept meeting.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "Leiden University, founded in 1575",
        originalTitle = "Leiden University",
        author = "William of Orange",
        year = 1575,
        summary = "Leiden University was founded in 1575 by William of Orange and the States of Holland, and it became one of the leading universities of the Dutch Republic.",
        details = "The university received its charter in 1575 to train ministers and lawyers for the new Protestant state. Its teaching of mathematics and astronomy grew in the decades that followed.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Helmstedt, founded in 1576",
        originalTitle = "Academia Julia",
        author = "Duke Julius of Brunswick-Wolfenbuttel",
        year = 1576,
        summary = "The University of Helmstedt was founded in 1576 by Duke Julius of Brunswick-Wolfenbuttel, and it took the name Academia Julia from him.",
        details = "The university opened at Helmstedt in 1576 with faculties of theology, law and medicine, and the duke's name stayed in its title. Accounts give 1576 as the opening year, and the university became a centre of learning in the Lutheran north of Germany.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.SYSTEM,
        section = "Observatories",
        title = "Taqi al-Din's observatory at Istanbul, built in 1577",
        originalTitle = "Istanbul observatory",
        author = "Taqi al-Din",
        year = 1577,
        summary = "Taqi al-Din's observatory at Istanbul, built in 1577, was an Ottoman centre for watching the heavens, and it was closed in 1580.",
        details = "Taqi al-Din, a scholar of astronomy and of clockwork, built the observatory and observed the stars there for a few years. Accounts differ on the reasons for its closure in 1580, and most of what is known of its instruments comes from his own writings.",
        rare = true,
    ),

)
