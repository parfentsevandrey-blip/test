package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1600 to 1610: a pendulum test, a library catalogue, Bayer's star letters, a dissection of veins and an equal scale for tuning. */
val db44Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.PRACTICE,
        section = "Experiment",
        title = "Galileo's pendulum tests of 1602",
        originalTitle = "Lettera a Guidobaldo del Monte",
        author = "Galileo Galilei",
        year = 1602,
        summary = "Galileo timed pendulums with his own pulse and reported that a swing seemed to take the same time for wide and narrow arcs.",
        details = "He described the tests in a letter of November 1602 to Guidobaldo del Monte, and the question of equal swings was one he returned to for years. The claim of equal times holds only approximately for small arcs, which is why later makers of clocks were careful about the size of the swing.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.PRACTICE,
        section = "Libraries",
        title = "Thomas James's catalogue of the Bodleian books",
        originalTitle = "Catalogus librorum bibliothecae Bodleianae",
        author = "Thomas James",
        year = 1605,
        summary = "The first printed catalogue of the Bodleian Library listed its books by subject and gave each book's shelf mark, so that a reader could find it on the shelf.",
        details = "James printed the catalogue at Oxford in 1605 for the library that Bodley had refitted. Its arrangement by the classes of learning, with shelf marks beside each title, is a method that later library catalogues kept.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.PRACTICE,
        section = "Star names",
        title = "Bayer's Greek letters for the stars",
        originalTitle = "Uranometria",
        author = "Johann Bayer",
        year = 1603,
        summary = "Bayer named the brighter stars of each constellation with Greek letters, roughly in order of brightness, in his star atlas of 1603.",
        details = "The atlas was printed at Augsburg in 1603, and the naming scheme is still used for many stars. Bayer followed brightness only roughly, so the letters do not always match the modern measured magnitudes.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.PRACTICE,
        section = "Anatomy",
        title = "Fabricius's dissections of the venous valves",
        originalTitle = "De venarum ostiolis",
        author = "Hieronymus Fabricius ab Aquapendente",
        year = 1603,
        summary = "By opening and tying veins in the anatomy theatre, Fabricius showed small valves inside them that let blood move only toward the heart.",
        details = "His book on the valves was printed at Padua in 1603, and his demonstrations were given to students in the theatre there. Accounts differ on how far Fabricius understood the function of the valves, and William Harvey later built his theory of the circulation on related observations.",
        rare = true,
    ),

    entry(
        databaseId = 44,
        kind = Kind.PRACTICE,
        section = "Music",
        title = "Stevin's equal semitones for tuning",
        originalTitle = "Van de Spiegheling der Singconst",
        author = "Simon Stevin",
        year = 1605,
        summary = "Stevin proposed in 1605 that the octave be divided into twelve equal parts, so that every key of an instrument would sound equally well in tune.",
        details = "The proposal is set out in his Van de Spiegheling der Singconst of 1605, which treats music as a branch of mathematics. Accounts differ on how far Stevin's equal division was used by musicians of the time, and earlier writers had suggested similar schemes.",
    ),

)
