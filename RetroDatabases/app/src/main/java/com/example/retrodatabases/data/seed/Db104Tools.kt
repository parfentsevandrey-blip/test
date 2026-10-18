package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1000: al-Biruni's survey of the calendars and eras of many peoples, and the Hakimite astronomical tables of Ibn Yunus. */
val db104Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.TOOL,
        section = "Chronology",
        title = "Al-Biruni's Chronology of Ancient Nations, a survey of calendars and eras",
        originalTitle = "al-Athar al-baqiya 'an al-qurun al-khaliya",
        author = "Abu Rayhan al-Biruni",
        year = 1000,
        summary = "Al-Biruni completed his Chronology of Ancient Nations in 1000, a reference book that sets out the calendars, eras and festivals of many peoples.",
        details = "It sets the calendars of the Persians, Sogdians, Arabs, Jews and Christians side by side, and explains how each people counted years, months and feast days. Because it lays the systems out together, a reader can match a date in one tradition with the same moment in another.",
        rare = true,
    ),

    entry(
        databaseId = 104,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Ibn Yunus's Hakimite Zij, a set of astronomical tables for Cairo",
        originalTitle = "Zij al-Kabir al-Hakimi",
        author = "Ibn Yunus",
        year = 1000,
        summary = "Ibn Yunus of Cairo completed his large set of astronomical tables, the Hakimite Zij, about 1000, and dedicated it to the Fatimid caliph al-Hakim.",
        details = "The tables were commissioned under the caliph al-Aziz and finished in the reign of al-Hakim, and the observations behind them run from 977 to 1003. Slightly more than half of the work survives, and its opening part gathers readings taken by Ibn Yunus and earlier observers, while Ibn Yunus died in 1009.",
        rare = true,
    ),

)
