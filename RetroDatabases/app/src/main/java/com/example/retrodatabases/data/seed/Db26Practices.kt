package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1780 to 1790: counting stars, weighing gases and weighing everything, and a network of weather observers. */
val db26Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Herschel's counts of stars in fields",
        originalTitle = "On the construction of the heavens",
        author = "William Herschel",
        year = 1785,
        summary = "Stars were counted in many small fields of the sky, so that the density of stars could be compared from place to place.",
        details = "Herschel's method sampled the sky by counting the stars in equal fields, and he used the counts to map the shape of the Milky Way. The method is an early form of sampling.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.PRACTICE,
        section = "Chemistry",
        title = "Cavendish's synthesis of water",
        originalTitle = "Experiments on air",
        author = "Henry Cavendish",
        year = 1781,
        summary = "Hydrogen and air were burned together, and the water formed was weighed, which showed that water is a compound.",
        details = "Cavendish reported the result in 1781. Careful weighing of the reactants and products was the method that other chemists used to test their theories.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.PRACTICE,
        section = "Chemistry",
        title = "Lavoisier and Laplace's ice calorimeter",
        originalTitle = "Memoire sur la chaleur",
        author = "Antoine Lavoisier and Pierre-Simon Laplace",
        year = 1783,
        summary = "Heat was measured by the amount of ice that melted, so that the heat given off by a reaction could be counted.",
        details = "The ice calorimeter was used in 1782 and 1783 to measure the heat from animal respiration and from combustion. Measuring by weight was the new practice that made the results comparable.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.PRACTICE,
        section = "Meteorology",
        title = "The Palatine network of weather observers",
        originalTitle = "Societas Meteorologica Palatina",
        author = "Karl Theodor's Palatine Academy",
        year = 1780,
        summary = "Observers in many towns recorded the same instruments at the same hours and sent their figures to Mannheim.",
        details = "The network was founded in 1780 and ran until 1795. Its standard hours and printed forms are an early example of a shared procedure for collecting data across many sites.",
        rare = true,
    ),

    entry(
        databaseId = 26,
        kind = Kind.PRACTICE,
        section = "Chemistry",
        title = "Lavoisier's balance rule: weigh every input and output",
        originalTitle = "Traite elementaire de chimie, preface",
        author = "Antoine Lavoisier",
        year = 1789,
        summary = "Every reaction was run in closed vessels, and the masses before and after were compared, so that no matter was lost unseen.",
        details = "Lavoisier stated the rule in his 1789 textbook and applied it in every experiment he described. The balance became the chemist's main instrument, and the rule became a check that all later calculations had to pass.",
    ),
)
