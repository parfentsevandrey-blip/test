package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1630 to 1640: Galileo's two sciences, Pell's plea for mathematics, Mersenne's music book, an English arithmetic, Desargues's conics and Blaeu's atlas. */
val db41Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.TOOL,
        section = "Mechanics",
        title = "Galileo's Two New Sciences",
        originalTitle = "Discorsi e dimostrazioni matematiche intorno a due nuove scienze",
        author = "Galileo Galilei",
        year = 1638,
        summary = "Galileo's Two New Sciences of 1638 set out the strength of materials and the laws of falling and thrown bodies in mathematical form, and it was printed outside Italy.",
        details = "The book was printed by the Elzevirs at Leiden in 1638, after the Roman Inquisition had forbidden Galileo to publish in Italy. It treats uniform motion, the parabolic path of a projectile and the rule that the distances fallen in equal intervals follow the odd numbers, and it shaped the mechanics of the following century.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Pell's An Idea of Mathematics",
        originalTitle = "An Idea of Mathematics",
        author = "John Pell",
        year = 1638,
        summary = "John Pell's An Idea of Mathematics of 1638 argued that the subject should be studied as the foundation of other learning.",
        details = "Pell printed the short book in London in 1638, and it is more a plea for mathematical study than a textbook of rules. His name later became attached to a type of equation through an error, since the problem had been studied long before him.",
        rare = true,
    ),

    entry(
        databaseId = 41,
        kind = Kind.TOOL,
        section = "Music",
        title = "Mersenne's Harmonie universelle on music and instruments",
        originalTitle = "Harmonie universelle, contenant la theorie et la pratique de la musique",
        author = "Marin Mersenne",
        year = 1636,
        summary = "Mersenne's Harmonie universelle of 1636 gathered the theory and practice of music, with the lengths of strings, the instruments of the day and the tuning of the scale.",
        details = "The book was printed in Paris in 1636, and it describes the monochord and the musical instruments of the period. Mersenne also discussed the tuning of the scale, a question that later writers on temperament kept returning to.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Wingate's Arithmetique made easie for English schools",
        originalTitle = "Arithmetique made Easie",
        author = "Edmund Wingate",
        year = 1630,
        summary = "Wingate's Arithmetique made Easie of 1630 taught the rules of arithmetic in plain English for schools and merchants, and it was reprinted for many years.",
        details = "The book was printed in London in 1630 and taught the rules in plain English, which was the aim of English writers of the time. Its rules became common stock for English schools, and later editions kept the same plan.",
        rare = true,
    ),

    entry(
        databaseId = 41,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Desargues's Brouillon project on conic sections",
        originalTitle = "Brouillon project d'une atteinte aux evenemens des rencontres du cone avec un plan",
        author = "Girard Desargues",
        year = 1639,
        summary = "Desargues's Brouillon project of 1639 treated conic sections by projection and stated a theorem on involutions that became a basic result of projective geometry.",
        details = "The pamphlet was printed in Paris in 1639 in a small edition, and it put the conics in one family by projection from a point. Pascal read it, but the pamphlet was lost for a long time and was found again in the nineteenth century.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Blaeu's Atlas Novus of 1635, a two-volume set of maps",
        originalTitle = "Atlas Novus",
        author = "Willem Janszoon Blaeu",
        year = 1635,
        summary = "Blaeu's Atlas Novus of 1635 gathered engraved maps of the known world into a two-volume atlas printed at Amsterdam.",
        details = "The atlas was printed at Amsterdam in 1635 in two volumes, and its maps were engraved and coloured in the Blaeu workshop. The workshop later produced the larger Atlas Maior, which carried the same idea of a full atlas of the world.",
    ),

)
