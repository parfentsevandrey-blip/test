package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1810 to 1820: a school plan, a probability essay, a rule for observation errors, a method for deaf pupils and a listening method. */
val db23Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.PRACTICE,
        section = "Education",
        title = "Bentham's Chrestomathia school plan",
        originalTitle = "Chrestomathia, first volume (1816)",
        author = "Jeremy Bentham",
        year = 1816,
        summary = "A plan of study for a school that taught useful subjects in a fixed order, so that each lesson built on the one before.",
        details = "Bentham published the first volume in 1816 and set out the scheme for teaching the useful arts. The record uses the 1816 date of the first volume.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.PRACTICE,
        section = "Probability",
        title = "Laplace's probability of testimony",
        originalTitle = "Essai philosophique sur les probabilites (1814)",
        author = "Pierre-Simon Laplace",
        year = 1814,
        summary = "A popular introduction to probability in which the reliability of a witness is combined with the chance of the event reported.",
        details = "Laplace's essay appeared in 1814 as a popular introduction to his theory of probability. The record uses the 1814 date of the first printed edition.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.PRACTICE,
        section = "Observation",
        title = "Gauss's accuracy of repeated observations",
        originalTitle = "Bestimmung der Genauigkeit der Beobachtungen (1816)",
        author = "Carl Friedrich Gauss",
        year = 1816,
        summary = "A method for judging how precisely a set of repeated measurements fixes a quantity, from the spread of their errors.",
        details = "Gauss's 1816 paper treated how the accuracy of observations is estimated from their errors. The record uses the 1816 date of the paper.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.PRACTICE,
        section = "Teaching the deaf",
        title = "Teaching deaf pupils by signs and written English",
        originalTitle = "Instruction at the Hartford asylum by signs and written English (1817)",
        author = "Thomas Hopkins Gallaudet and Laurent Clerc",
        year = 1817,
        summary = "A way of teaching deaf children to read and write English by linking the signs they already used with printed words.",
        details = "The method was used at the Hartford asylum from its opening in 1817. The record uses the 1817 date of that opening, and it overlaps with the school entry in this database.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.PRACTICE,
        section = "Medicine",
        title = "Laennec's mediate listening to the chest",
        originalTitle = "De l'auscultation mediate (1819)",
        author = "Rene Theophile Hyacinthe Laennec",
        year = 1819,
        summary = "Listening to the chest through a tube, so that a physician could hear the sounds inside the body without pressing an ear to the skin.",
        details = "Laennec described the method in his 1819 book, after first using a rolled paper tube. The record uses the 1819 date of the book.",
    ),
)
