package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1770 to 1780: a plumb line at a mountain, cooling iron balls, green plants in light, chemical affinity tables and prison inspections. */
val db27Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.PRACTICE,
        section = "Geodesy",
        title = "Maskelyne's plumb line at Schiehallion",
        originalTitle = "Schiehallion experiment",
        author = "Nevil Maskelyne and Charles Hutton",
        year = 1774,
        summary = "A plumb line hung near a Scottish mountain was pulled aside by its mass, and the deflection gave an estimate of the mean density of the Earth.",
        details = "Maskelyne led the survey in 1774 at Schiehallion in Perthshire, and Hutton reduced the observations to a figure for the Earth's mean density, about four and a half times that of water. Accounts differ on the exact value and on the year the report was printed, which is given as 1778 here.",
        rare = true,
    ),

    entry(
        databaseId = 27,
        kind = Kind.PRACTICE,
        section = "Geology",
        title = "Buffon's cooling iron balls and the age of the Earth",
        originalTitle = "Epoques de la nature",
        author = "Georges-Louis Leclerc, comte de Buffon",
        year = 1778,
        summary = "Buffon heated iron balls and timed how fast they cooled, and used the result to estimate how long the Earth had taken to cool.",
        details = "Buffon described the cooling experiments in his Epoques de la nature of 1778, and from the times he computed that the Earth had cooled over about seventy-five thousand years. Accounts differ on the date of the first experiments, which some place before 1774. The estimate was far longer than the biblical chronology allowed.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.PRACTICE,
        section = "Plant physiology",
        title = "Ingenhousz's green plants in sunlight",
        originalTitle = "Experiments upon Vegetables",
        author = "Jan Ingenhousz",
        year = 1779,
        summary = "Ingenhousz showed that green plants give off a gas in sunlight and that the gas helps a flame to burn.",
        details = "Ingenhousz published the experiments in London in 1779, after building on Priestley's earlier observations that plants restore air. Accounts differ on how much credit Priestley's work deserves for the discovery. The date given is that of the printed book.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.PRACTICE,
        section = "Chemistry",
        title = "Bergman's affinity tables for elective attractions",
        originalTitle = "De attractionibus electivis",
        author = "Torbern Bergman",
        year = 1775,
        summary = "Bergman's tables listed the order in which one substance displaces another when they are mixed in solution.",
        details = "Bergman's Latin treatise of 1775 gave tables of elective attractions, which chemists used to predict which substances would combine and which would be displaced. The tables were copied and extended by later chemists for decades. The date given is the printed year of the treatise.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.PRACTICE,
        section = "Prisons",
        title = "Howard's inspection of English prisons",
        originalTitle = "The State of the Prisons in England and Wales",
        author = "John Howard",
        year = 1777,
        summary = "Howard visited prisons across England and Wales and reported their conditions in print, setting a method for later prison inspection.",
        details = "Howard's report of 1777 described the prisons he had visited, with the condition of their buildings, the diet and the fees charged to prisoners. It set out what an inspector should measure in each prison, and later reformers used the same method.",
    ),

)
