package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1810 to 1820: printed tables, a factor table, a star catalogue, a bibliography, a journal and a dictionary. */
val db23Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.TOOL,
        section = "Mathematical tables",
        title = "Barlow's tables of squares, cubes and reciprocals",
        originalTitle = "Tables of Squares, Cubes, Square Roots, Cube Roots, and Reciprocals (1814)",
        author = "Peter Barlow",
        year = 1814,
        summary = "A printed table of squares, cubes, roots and reciprocals, so that a calculator could look up a value instead of working it out.",
        details = "Barlow's tables were printed in London in 1814 and were used as a reference by calculators through the nineteenth century. The record uses the 1814 date of the first edition.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.TOOL,
        section = "Factor tables",
        title = "Burckhardt's factor tables",
        originalTitle = "Table des diviseurs pour tous les nombres non premiers (1814)",
        author = "Johann Karl Burckhardt",
        year = 1814,
        summary = "A table that gives the smallest prime factor of each composite number in its range, so a reader can see at once whether a number is prime.",
        details = "Burckhardt's factor tables began appearing in Paris in 1814 and continued in later volumes for larger ranges. The record uses the 1814 date of the first volume.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.TOOL,
        section = "Star catalogues",
        title = "Bessel's Fundamenta Astronomiae",
        originalTitle = "Fundamenta Astronomiae (1818)",
        author = "Friedrich Wilhelm Bessel",
        year = 1818,
        summary = "A catalogue of star positions reduced from James Bradley's old observations, made at Greenwich in the 1750s.",
        details = "Bessel published the catalogue in 1818 and built it from Bradley's observations. The record uses the 1818 date of publication.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.TOOL,
        section = "Bibliography",
        title = "Brunet's book-trade manual",
        originalTitle = "Manuel du libraire et de l'amateur de livres (1810)",
        author = "Jacques-Charles Brunet",
        year = 1810,
        summary = "A bibliographic handbook that listed books by author and title, with notes on editions, for booksellers and book collectors.",
        details = "Brunet's handbook appeared in 1810 and grew through later editions. The record uses the 1810 date of the first edition.",
        rare = true,
    ),

    entry(
        databaseId = 23,
        kind = Kind.TOOL,
        section = "Journals",
        title = "Gergonne's Annales of mathematics",
        originalTitle = "Annales de mathematiques pures et appliquees (1810)",
        author = "Joseph Diaz Gergonne",
        year = 1810,
        summary = "A journal of pure and applied mathematics in which papers, problems and solutions were printed for a reading public.",
        details = "Gergonne founded the journal at Nimes in 1810 and edited it from there. The record uses the 1810 founding date.",
        rare = true,
    ),

    entry(
        databaseId = 23,
        kind = Kind.TOOL,
        section = "Reference works",
        title = "Barlow's New Mathematical Dictionary",
        originalTitle = "A New Mathematical and Philosophical Dictionary (1814)",
        author = "Peter Barlow",
        year = 1814,
        summary = "A dictionary of mathematics and natural philosophy, arranged by subject, for students and engineers who needed quick definitions.",
        details = "Barlow's dictionary was printed in London in 1814, the same year as his tables. The record uses the 1814 date of the first edition.",
        rare = true,
    ),
)
