package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1681 to 1687: a county natural history, a printing manual, an organ-tuning test, a method of discovery and a plant classification. */
val db36Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.PRACTICE,
        section = "County history",
        title = "Plot's natural history of Staffordshire",
        originalTitle = "The Natural History of Stafford-shire",
        author = "Robert Plot",
        year = 1686,
        summary = "Plot's natural history of Staffordshire set out a county's fossils, plants, antiquities and manufactures, gathered from local informants, as a model for surveying one county at a time.",
        details = "Plot printed the book at Oxford in 1686, the second of his county histories after the Natural History of Oxford-shire of 1677. He relied on correspondents in the parishes to send him what they had found, and the book is illustrated with engraved plates.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.PRACTICE,
        section = "Printing",
        title = "Moxon's Mechanick Exercises on the art of printing",
        originalTitle = "Mechanick Exercises on the Whole Art of Printing",
        author = "Joseph Moxon",
        year = 1683,
        summary = "Moxon's manual described in plain English the work of the compositor and the pressman, from setting type to pulling the press.",
        details = "The printing volume of Moxon's series appeared in London in 1683. It described the wooden press, the cases of type and the steps of making a printed sheet, and it is one of the earliest English manuals of the trade.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.PRACTICE,
        section = "Music",
        title = "Werckmeister's Orgel-Probe, a test of organ tuning",
        originalTitle = "Orgel-Probe",
        author = "Andreas Werckmeister",
        year = 1681,
        summary = "Werckmeister's Orgel-Probe gave organists a practical way to test the tuning of an organ by ear.",
        details = "The book is usually dated 1681, and Werckmeister's fuller account of tuning temperaments came in 1691. The test was meant for organs in use, where the organist had to hear whether the tuning held together.",
        rare = true,
    ),

    entry(
        databaseId = 36,
        kind = Kind.PRACTICE,
        section = "Logic",
        title = "Tschirnhaus's Medicina mentis, a method for discovery",
        originalTitle = "Medicina mentis, sive Tentamen genuinae logicae",
        author = "Ehrenfried Walther von Tschirnhaus",
        year = 1687,
        summary = "Tschirnhaus's book set out a method for finding new truths, as against the logic of the schools, which was built for judging arguments.",
        details = "It was printed in 1687, and its subtitle calls it an attempt at a genuine logic. Its interest lies in the claim that discovery needs a method of its own, which Tschirnhaus thought the schools had not supplied.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.PRACTICE,
        section = "Botany",
        title = "Ray's Methodus plantarum nova, a classification of plants",
        originalTitle = "Methodus plantarum nova",
        author = "John Ray",
        year = 1682,
        summary = "Ray's Methodus plantarum nova of 1682 was an early plan for sorting plants into groups by their parts, written before his larger Historia plantarum.",
        details = "The book was printed in London in 1682, four years before the first volume of the Historia plantarum. Its groups rest on the parts of the plant, including the flower and the seed.",
    ),

)
