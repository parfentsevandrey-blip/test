package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1870 to 1880: measurement standards, postal and weather cooperation, and criminal identification. */
val db17Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.PRACTICE,
        section = "Standards",
        title = "The Metre Convention",
        originalTitle = "Convention du Metre (1875)",
        author = "Seventeen signatory states",
        year = 1875,
        summary = "A treaty signed in Paris that created an international bureau to keep the standards of the metre and the kilogram.",
        details = "The convention was signed on 20 May 1875 and set up the International Bureau of Weights and Measures near Paris. The member states shared the cost of keeping the standards and of checking national copies against them.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.PRACTICE,
        section = "Postal union",
        title = "The Bern treaty and the General Postal Union",
        originalTitle = "Traite de Berne (1874)",
        author = "Delegates of the postal administrations",
        year = 1874,
        summary = "A treaty signed at Bern that founded a union of postal administrations, so that letters could cross borders under common rules.",
        details = "The treaty was signed at Bern on 9 October 1874 and founded the General Postal Union, which took the name Universal Postal Union in 1878. Member states agreed to carry one another's mail under the same rules, and the union kept a central office at Bern.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.PRACTICE,
        section = "Meteorology",
        title = "The Vienna meteorological congress of 1873",
        originalTitle = "International Meteorological Congress, Vienna (1873)",
        author = "Delegates of the national meteorological services",
        year = 1873,
        summary = "An international congress that set up a committee to coordinate weather observations, so that records from different countries could be compared.",
        details = "The congress met at Vienna in 1873 and created the International Meteorological Committee, which set common observing practice for national weather services. Common practice of this kind made records from different countries comparable.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.PRACTICE,
        section = "Identification",
        title = "Bertillon's identification system",
        originalTitle = "Bertillonage",
        author = "Alphonse Bertillon",
        year = 1879,
        summary = "A method of identifying people by body measurements, a standard photograph and a card filed in the order of those measurements.",
        details = "Bertillon, a clerk in the Paris police, introduced the system in 1879. Accounts differ on the year of the first trial, and on how many measurements the card recorded.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.PRACTICE,
        section = "Units",
        title = "The CGS system of units",
        originalTitle = "Centimetre, gram, second system",
        author = "British Association committee on electrical standards",
        year = 1874,
        summary = "A system of measurement built on the centimetre, the gram and the second, which became the common basis of units in physics.",
        details = "The British Association's committee on electrical standards worked on the system in the early 1870s. Practical engineering kept older units for daily use while physics adopted the centimetre-gram-second base.",
    ),
)
