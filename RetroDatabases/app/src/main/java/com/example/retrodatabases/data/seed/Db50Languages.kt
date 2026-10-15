package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1540 to 1550: the earliest books printed in Basque, Finnish, Lithuanian and Icelandic, and two texts on translating and on the French tongue. */
val db50Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.LANGUAGE,
        section = "Basque",
        title = "Etxepare's Basque book, the earliest printed in Basque",
        originalTitle = "Linguae Vasconum Primitiae",
        author = "Bernard Etxepare",
        year = 1545,
        summary = "Etxepare's book of 1545 is the earliest known printed book in Basque, a set of poems that put the language into print.",
        details = "It was printed at Bordeaux in 1545, and its author was a priest from the Northern Basque Country. Its poems cover religion, love and a prison stay, and accounts differ on whether it is certainly the first book in Basque or only the earliest known one.",
        rare = true,
    ),

    entry(
        databaseId = 50,
        kind = Kind.LANGUAGE,
        section = "Finnish",
        title = "Agricola's ABC book, the earliest printed in Finnish",
        originalTitle = "Abckiria",
        author = "Mikael Agricola",
        year = 1543,
        summary = "Agricola's ABC book is the earliest book printed in Finnish, and it taught the letters together with prayers and the basics of the church's teaching.",
        details = "Most accounts date the first edition to 1543, and it was printed at Stockholm, since printing did not get under way in Finland until 1642. Only fragments from two pages of the first edition survive, so one account treats the date as uncertain.",
        rare = true,
    ),

    entry(
        databaseId = 50,
        kind = Kind.LANGUAGE,
        section = "Lithuanian",
        title = "Mazvydas's Catechism, the earliest printed in Lithuanian",
        originalTitle = "Katekizmas",
        author = "Martynas Mazvydas",
        year = 1547,
        summary = "Mazvydas's Lithuanian catechism of 1547 is the earliest book printed in Lithuanian, and it set down the church's teaching in the speech of the people.",
        details = "It was printed at Konigsberg in 1547, in the town where the duke of Prussia had founded a university in 1544, and it was written for Lutheran readers in Prussia. The catechism presents the church's teaching in the everyday speech of the Lithuanian people.",
        rare = true,
    ),

    entry(
        databaseId = 50,
        kind = Kind.LANGUAGE,
        section = "Icelandic",
        title = "Oddur's Icelandic New Testament, printed at Roskilde",
        originalTitle = "Nyja testamenti",
        author = "Oddur Gottskalksson",
        year = 1540,
        summary = "Oddur Gottskalksson's Icelandic New Testament of 1540 gave Icelanders the whole New Testament in their own tongue, in print.",
        details = "It was printed at Roskilde in Denmark in 1540, and it was the first complete New Testament in Icelandic. Oddur had translated it himself, and the book is a landmark of the Reformation in Iceland.",
        rare = true,
    ),

    entry(
        databaseId = 50,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Du Bellay's defence of the French tongue",
        originalTitle = "La deffence et illustration de la langue francoyse",
        author = "Joachim du Bellay",
        year = 1549,
        summary = "Du Bellay's manifesto of 1549 argued that French was fit for poetry and learning, and that writers should enrich it instead of copying Latin.",
        details = "It was printed at Paris in 1549 and is often read as the manifesto of the poets known as the Pleiade. Du Bellay urged French writers to borrow words from Greek and Latin and to coin new ones, and historians still debate how far his advice was followed.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.LANGUAGE,
        section = "Translation",
        title = "Dolet's rules for translating from one tongue to another",
        originalTitle = "La maniere de bien traduire d'une langue en aultre",
        author = "Etienne Dolet",
        year = 1540,
        summary = "Dolet's short book of 1540 set out how a translator should keep the sense of a text when it moves into another language.",
        details = "It was printed at Lyon in 1540, where Dolet had his own press. He was burned in Paris in 1546 for heresy, and his book is often read as an early statement that a translation should give the sense rather than the words.",
        rare = true,
    ),

)
