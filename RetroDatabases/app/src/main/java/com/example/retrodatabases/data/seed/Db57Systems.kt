package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1470 to 1480: the Vatican Library, Tubingen's university, a Florentine tax survey, the Spanish Inquisition and the Roman jubilee cycle. */
val db57Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "The Vatican Library, founded in 1475",
        originalTitle = "Bibliotheca Apostolica Vaticana",
        author = "Pope Sixtus IV",
        year = 1475,
        summary = "Sixtus IV founded the papal library in 1475 and made the humanist Bartolomeo Platina its first librarian.",
        details = "The library was given its own staff and a place in the papal palace, and its early holdings were manuscripts that scholars could consult. Platina, a humanist who also wrote on food and health, was the first prefect of the library.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "Eberhard's university at Tubingen, founded in 1477",
        originalTitle = "Universitas Tubingensis",
        author = "Count Eberhard im Bart",
        year = 1477,
        summary = "Count Eberhard of Wurttemberg founded the university at Tubingen in 1477, and it opened with faculties of arts, law, medicine and theology.",
        details = "The founding had papal approval, and the university was meant to train clergy and officials for the count's lands. Accounts of its first students vary, so the record gives only the founding year.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.SYSTEM,
        section = "Taxes and records",
        title = "The Florentine catasto of 1480, a survey of property",
        originalTitle = "Catasto",
        author = "Florentine government",
        year = 1480,
        summary = "In 1480 Florence ordered a survey of household property, and its returns survive as a record of the city's households.",
        details = "Households declared their property and dependants, and the declarations were used to set the city's taxes. Historians use the surviving returns to count households, and accounts differ on the exact procedure, so the record gives only the year of the survey.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.SYSTEM,
        section = "Courts",
        title = "The Spanish Inquisition, set up in 1478",
        originalTitle = "Exigit sincerae devotionis affectus",
        author = "Pope Sixtus IV, at the request of Ferdinand and Isabella",
        year = 1478,
        summary = "A bull of 1478 let the Spanish monarchs appoint inquisitors, and the Spanish Inquisition began to sit at Seville in 1480.",
        details = "The bull was issued at the request of Ferdinand and Isabella, the Catholic monarchs of Castile and Aragon. The first tribunals sat at Seville from 1480, and the record names the bull by its opening Latin words.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.SYSTEM,
        section = "Church calendar",
        title = "The Roman jubilee fixed at twenty-five-year intervals in 1470",
        originalTitle = "Ineffabilis summi providentia",
        author = "Pope Paul II",
        year = 1470,
        summary = "Paul II's bull of 1470 fixed the Holy Year, the Roman jubilee, at intervals of twenty-five years.",
        details = "Earlier jubilees had been spaced at longer and varying intervals, and the new rule shaped the Holy Years of 1475 and later. The record names the bull by its opening Latin words.",
    ),

)
