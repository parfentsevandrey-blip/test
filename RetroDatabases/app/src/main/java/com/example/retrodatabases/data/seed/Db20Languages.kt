package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages and notations of 1840 to 1850: Babbage's cards, Grassmann's extension algebra, raised type, topology and two language resources. */
val db20Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.LANGUAGE,
        section = "Analytical Engine notation",
        title = "Babbage's operation and variable cards",
        originalTitle = "Notation of the Analytical Engine (1840 lectures)",
        author = "Charles Babbage",
        year = 1840,
        summary = "Babbage wrote programs for the Analytical Engine as numbered operation cards and variable cards, each naming an operation or a store location.",
        details = "Babbage explained the notation in lectures at Turin in 1840, and Menabrea printed a description of the Engine in 1842. This record uses 1840 as the first public form of the notation.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.LANGUAGE,
        section = "Linear extension",
        title = "Grassmann's Ausdehnungslehre, the theory of extension",
        originalTitle = "Die lineale Ausdehnungslehre, ein neuer Zweig der Mathematik",
        author = "Hermann Grassmann",
        year = 1844,
        summary = "Grassmann's book of 1844 gave an algebra of extended quantities whose product changes sign when two factors are swapped, the root of the later exterior product.",
        details = "The book was published at Leipzig in 1844. Its rules for combining directed quantities in space and in higher dimensions later became the exterior algebra used in geometry and physics.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.LANGUAGE,
        section = "Tactile alphabets",
        title = "Moon's raised-letter type for the blind",
        originalTitle = "Embossed Roman type for the blind",
        author = "William Moon",
        year = 1845,
        summary = "Moon's embossed Roman letters let blind readers learn the ordinary alphabet by touch, and missions later carried the type to Asia and Africa.",
        details = "Moon made his type public in 1845. Missions carried it across Asia, Africa and Australia from the 1850s.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.LANGUAGE,
        section = "Topology",
        title = "Listing's Topologie, the name for the study of position",
        originalTitle = "Vorstudien zur Topologie",
        author = "Johann Benedict Listing",
        year = 1847,
        summary = "Listing used the name Topologie for the study of properties of figures that survive bending and stretching without tearing.",
        details = "His preliminary studies appeared in the Gottingen studies in 1847. Later work by Riemann and Poincare built on the same questions about holes and connection.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.LANGUAGE,
        section = "Lexicography",
        title = "Liddell and Scott's Greek-English Lexicon",
        originalTitle = "A Greek-English Lexicon",
        author = "Henry George Liddell and Robert Scott",
        year = 1843,
        summary = "The Greek-English lexicon of Liddell and Scott, first published at Oxford in 1843, became the standard English dictionary of ancient Greek.",
        details = "The first edition appeared in 1843, and later editors revised the work many times. It is still used as a reference by students of ancient Greek.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.LANGUAGE,
        section = "Sanskrit texts",
        title = "Muller's Rig-Veda edition with Sayana's commentary",
        originalTitle = "Rig-Veda-Sanhita, the Sacred Hymns of the Brahmans",
        author = "Friedrich Max Muller",
        year = 1849,
        summary = "Muller's printed edition of the oldest Sanskrit hymns, with the commentary of Sayana, began to appear in 1849 and made the text widely available to scholars.",
        details = "The first volume was published in 1849, and the full edition was completed in 1874 in six volumes. It gave European scholars a printed Sanskrit text of the Rig-Veda together with the medieval commentary.",
        rare = true,
    ),
)
