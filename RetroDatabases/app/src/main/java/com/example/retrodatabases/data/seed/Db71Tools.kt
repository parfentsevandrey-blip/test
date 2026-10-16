package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1330 and 1339: a Majorcan sea chart and a Franciscan's account of his travels to China. */
val db71Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.TOOL,
        section = "Maps",
        title = "The 1339 Palma chart of Angelino Dulcert, now in Paris",
        originalTitle = "Carta nautica",
        author = "Angelino Dulcert",
        year = 1339,
        summary = "A portolan chart made at Palma on Majorca in 1339, now in the Bibliotheque nationale de France in Paris, is credited to the mapmaker Angelino Dulcert.",
        details = "Some scholars identify the maker with Angelino de Dalorto, so the attribution is disputed. The chart is one of the earliest known portolan charts made at Palma.",
        rare = true,
    ),

    entry(
        databaseId = 71,
        kind = Kind.TOOL,
        section = "Travel",
        title = "Odoric of Pordenone's account of his journeys, written down at Padua in 1330",
        originalTitle = "Relatio",
        author = "Odoric of Pordenone",
        year = 1330,
        summary = "Odoric of Pordenone, a Franciscan returning from China, had his account of his journeys written down at Padua in May 1330.",
        details = "The account describes his journey east through Persia and India to China and his return, and Odoric died in 1331. The record date is the Padua writing of 1330, when the account was dictated there.",
        rare = true,
    ),

)
