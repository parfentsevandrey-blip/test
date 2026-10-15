package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1500 to 1510: the Lisbon India office, the Seville trade office, the German merchants' house at Venice, a Portuguese state in Asia and the imperial circles. */
val db54Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.SYSTEM,
        section = "Trade",
        title = "The Casa da India in Lisbon",
        originalTitle = "Casa da India",
        author = "Portuguese crown",
        year = 1501,
        summary = "The Casa da India was the royal office in Lisbon that managed the Portuguese trade with India, including the ships, the cargoes and the registers of each fleet.",
        details = "Its first written mention is a royal letter of 1501, and accounts differ on the founding year, which is given as 1500 in one common reference and as 1503 in another. The office handled the sailing of the annual fleets and kept the records of what they carried.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.SYSTEM,
        section = "Navigation",
        title = "The Casa de Contratacion at Seville",
        originalTitle = "Casa de Contratacion",
        author = "Spanish crown",
        year = 1503,
        summary = "The Casa de Contratacion was founded at Seville in 1503 to manage the Spanish trade with the Indies, keeping the ships' registers, the pilots' licences and the master chart.",
        details = "The Catholic Monarchs set up the office in 1503, and it became the centre for the records of voyages to the Americas. Its pilots were trained and examined there, and the official chart of the Spanish discoveries was kept in the office.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.SYSTEM,
        section = "Trade",
        title = "The Fondaco dei Tedeschi at Venice",
        originalTitle = "Fondaco dei Tedeschi",
        author = "Venetian Senate",
        year = 1505,
        summary = "The Fondaco dei Tedeschi was the German merchants' house at Venice, where foreign traders lodged and stored their goods under the city's rules, and a fire of 1505 led to its rebuilding.",
        details = "The rebuilding followed the fire of 1505, and the house was ready again in the years that followed. The city required the German merchants to lodge and trade there, so their goods and accounts could be watched in one place.",
        rare = true,
    ),

    entry(
        databaseId = 54,
        kind = Kind.SYSTEM,
        section = "Empire",
        title = "The Estado da India, the Portuguese state in Asia",
        originalTitle = "Estado da India",
        author = "Portuguese crown",
        year = 1505,
        summary = "The Estado da India was the Portuguese overseas state in Asia, and its first viceroy, Francisco de Almeida, was sent out from Lisbon in 1505.",
        details = "Almeida's appointment in 1505 is the usual starting date, and the state grew from a network of forts and trading posts around the Indian Ocean. Historians differ on when the Estado took a settled form, because its offices and its name developed over the following years.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.SYSTEM,
        section = "Empire",
        title = "The imperial circles of 1500",
        originalTitle = "Reichskreise",
        author = "Diet of Augsburg",
        year = 1500,
        summary = "The Diet of Augsburg of 1500 divided the Holy Roman Empire into imperial circles, districts that were meant to keep the peace and to raise forces for the empire.",
        details = "The circles grouped the territories of the empire into regions, and they were given duties for justice and defence. The arrangement was one of several reforms that the Diet passed in 1500 to strengthen the empire's government.",
    ),

)
