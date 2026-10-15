package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1900 to 1910: research agendas, standards, units and a method for fluid flow. */
val db14Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.PRACTICE,
        section = "Research agendas",
        title = "Hilbert's 23 problems",
        originalTitle = "Mathematische Probleme (1900)",
        author = "David Hilbert",
        year = 1900,
        summary = "A list of open problems in mathematics that Hilbert presented in 1900, which gave later research a shared agenda.",
        details = "Hilbert spoke at the 1900 congress in Paris, and the printed list has 23 problems. The tenth asks for a method to decide whether a Diophantine equation has integer solutions, and in 1970 it was shown that no such method exists.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.PRACTICE,
        section = "Standards",
        title = "Engineering Standards Committee",
        originalTitle = "Engineering Standards Committee, London (1901)",
        author = "Engineering Standards Committee",
        year = 1901,
        summary = "A British body that agrees standard sizes and sections for engineering products, so parts from different makers fit together.",
        details = "The committee was set up in London in 1901 and began with steel sections and tramway rails. It was renamed in 1918 and became the British Standards Institution, incorporated in 1929.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.PRACTICE,
        section = "Fluid mechanics",
        title = "Boundary-layer theory",
        originalTitle = "Uber Flussigkeitsbewegung bei sehr kleiner Reibung (1904)",
        author = "Ludwig Prandtl",
        year = 1904,
        summary = "Flow near a solid surface is governed by a thin layer where friction matters, so the rest of the flow can be treated as frictionless.",
        details = "Prandtl's paper of 1904 explained how flow separates from a surface with this layer idea, and engineers used the method for decades in the design of wings and pipes.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.PRACTICE,
        section = "Time",
        title = "Daylight saving time proposal",
        originalTitle = "The Waste of Daylight (1907)",
        author = "William Willett",
        year = 1907,
        summary = "A proposal to move the clocks forward in summer, so that evenings gain daylight, set out in a pamphlet of 1907.",
        details = "Willett proposed moving the clocks forward by eighty minutes in four steps each spring and back again in the autumn. Britain did not adopt the idea until 1916, during the First World War.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.PRACTICE,
        section = "Standards",
        title = "London conference on electrical units",
        originalTitle = "International Conference on Electrical Units and Standards, London (1908)",
        author = "International conference held in London",
        year = 1908,
        summary = "An international conference in London agreed common standards for electrical units, so that measurements made in different countries could be compared.",
        details = "Laboratories from several countries took part in the 1908 conference, which set out international definitions of the electrical units used in trade and in laboratories.",
    ),
)
