package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1550 to 1560: a critical apparatus for a Greek text and a comparison of bird and human skeletons. */
val db49Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.PRACTICE,
        section = "Editing",
        title = "Estienne's critical apparatus of the 1550 Greek Testament",
        originalTitle = "Novum Testamentum, Editio Regia",
        author = "Robert Estienne",
        year = 1550,
        summary = "Estienne's Greek Testament of 1550 set a critical apparatus of variant readings beside the Greek text, so that a reader could see where the witnesses disagreed.",
        details = "The edition was printed in Paris in 1550, the year before Estienne's numbered verses of 1551, and it was the work of the royal printer. Its apparatus is the part that makes the edition stand out, because it records the variant readings instead of hiding them.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.PRACTICE,
        section = "Anatomy",
        title = "Belon's bird and human skeletons side by side",
        originalTitle = "L'histoire de la nature des oyseaux",
        author = "Pierre Belon",
        year = 1555,
        summary = "Belon's natural history of birds, printed in 1555, set the skeleton of a bird beside that of a human, so that readers could match the bones one by one.",
        details = "The book was printed at Paris in 1555, and its paired drawings of birds and humans are an early example of comparative anatomy. Belon's method of matching the bones of different animals was a practice that later anatomists used to classify skeletons.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.PRACTICE,
        section = "Catechisms",
        title = "Canisius's catechism of 1555",
        originalTitle = "Summa doctrinae christianae",
        author = "Peter Canisius",
        year = 1555,
        summary = "Canisius's Summa doctrinae christianae, a catechism of questions and answers first printed at Vienna in 1555, was reprinted in very many editions.",
        details = "The first edition was printed at Vienna by Michael Zimmermann, and some lists give 1554, but the first printing is dated 1555. Accounts differ on the number of editions by Canisius's death in 1597, with one count giving 200 and another 300 or more.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.PRACTICE,
        section = "Psalmody",
        title = "Singing the psalms from the Geneva psalter of 1551",
        originalTitle = "Geneva psalter, 83 psalms",
        author = "Clement Marot, Theodore de Beza and Louis Bourgeois",
        year = 1551,
        summary = "The Geneva psalter gathered 83 psalms in French verse, Marot's 49 and Beza's 34, with tunes credited to Louis Bourgeois, for singing in Geneva's services.",
        details = "Psalm singing entered Geneva's services after Calvin's return in 1541, and the full 150 psalms came only in 1562. One account dates the printing to 1552, so the 1551 date is disputed.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.PRACTICE,
        section = "Ciphers",
        title = "The papal cipher secretary's office, by 1555",
        originalTitle = "Papal cipher office",
        author = "The papal court",
        year = 1555,
        summary = "By 1555 the papal court had a cipher office for its coded correspondence.",
        details = "The date of 1555 rests on one account, JSTOR Daily, so the start of the office is uncertain and the record gives only the date that account supports.",
        rare = true,
    ),



)
