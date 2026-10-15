package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1640 to 1650: Descartes's textbook, a book of light and shadow, a reference of common errors, a lunar atlas, a planetary treatise and a defence of indivisibles. */
val db40Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.TOOL,
        section = "Natural philosophy",
        title = "Descartes's Principia philosophiae, the textbook of natural philosophy",
        originalTitle = "Principia philosophiae",
        author = "Rene Descartes",
        year = 1644,
        summary = "Descartes's Latin textbook of natural philosophy, which treated matter, motion and the heavens as a system of vortices and laws of motion.",
        details = "The book was printed in Amsterdam in 1644 by the Elzevier house, and Descartes wrote it as a course text for the universities. Its second part states the rules of motion that he used, and for a generation it was the main text of the Cartesian school.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.TOOL,
        section = "Light and shadow",
        title = "Kircher's Ars magna lucis et umbrae, the great art of light and shadow",
        originalTitle = "Ars magna lucis et umbrae",
        author = "Athanasius Kircher",
        year = 1646,
        summary = "A large Latin book on light and shadow, with descriptions of optical and mirror effects that Kircher used to amaze his readers.",
        details = "Kircher printed the book in Rome in 1646, and a revised and much enlarged edition followed in 1671 with a fuller account of the magic lantern. Its contents run from shadows and refraction to mirrors that show hidden images.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.TOOL,
        section = "Reference",
        title = "Browne's Pseudodoxia Epidemica, an examination of common errors",
        originalTitle = "Pseudodoxia Epidemica",
        author = "Thomas Browne",
        year = 1646,
        summary = "A long English reference book that tested popular beliefs about animals, plants, minerals and history, one by one, against the evidence.",
        details = "Browne published the first edition in London in 1646, and later editions were expanded during his lifetime. Each common belief is set out and then weighed, which made the book a standard reference for readers who wanted to check what they had been told.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Hevelius's Selenographia, a large atlas of the Moon",
        originalTitle = "Selenographia, sive Lunae descriptio",
        author = "Johannes Hevelius",
        year = 1647,
        summary = "A large book of engraved maps of the Moon, drawn from the author's own telescopic observations in Danzig.",
        details = "Hevelius printed the book in Danzig in 1647 with plates that he engraved himself, and it was one of the first printed atlases of the Moon. It was far larger than the few drawings Galileo had published in 1610, and it set a model for detailed lunar maps that followed.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.TOOL,
        section = "Planetary motion",
        title = "Boulliau's Astronomia philolaica on the planets' orbits",
        originalTitle = "Astronomia philolaica",
        author = "Ismael Boulliau",
        year = 1645,
        summary = "A Latin treatise on the planets that argued for elliptical orbits and set out a new rule for how fast each planet moves.",
        details = "Boulliau printed the book in Paris in 1645, and it is a long argument about the orbits of the planets and the motion of the Sun. Kepler had put forward elliptical orbits in 1609, and Boulliau's book carried the idea into the printed tables and rules of the next generation.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Cavalieri's Exercitationes geometricae sex, six defences of indivisibles",
        originalTitle = "Exercitationes geometricae sex",
        author = "Bonaventura Cavalieri",
        year = 1647,
        summary = "Six geometrical exercises in which Cavalieri defended his method of indivisibles against critics and applied it to new problems.",
        details = "Cavalieri printed the book in Bologna in 1647, after his method of 1635 had drawn criticism from several mathematicians. The method compares the sums of very thin slices of two figures, and the book shows how to use it on problems about solids and curves.",
    ),

)
