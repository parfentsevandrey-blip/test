package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1490 to 1500: a Castilian grammar, a Catalan romance, an Italian Bible, a Greek lexicon, an English-Latin wordbook and a Reuchlin dialogue on Hebrew names. */
val db55Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.LANGUAGE,
        section = "Grammars",
        title = "Nebrija's grammar of Castilian",
        originalTitle = "Gramatica de la lengua castellana",
        author = "Elio Antonio de Nebrija",
        year = 1492,
        summary = "Nebrija's grammar of 1492 set out the rules of Castilian on the model of Latin grammar, and it is often called the first grammar written for a modern European vernacular.",
        details = "The grammar was printed at Salamanca in 1492 and dedicated to Queen Isabella. In its preface Nebrija answered the question of what a grammar of Castilian was for by saying that language had always been the companion of empire.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.LANGUAGE,
        section = "Romances",
        title = "Tirant lo Blanc, the Catalan romance in print",
        originalTitle = "Tirant lo Blanc",
        author = "Joanot Martorell, with Marti Joan de Galba",
        year = 1490,
        summary = "Tirant lo Blanc, a long chivalric romance in Catalan, was printed at Valencia in 1490, and that edition is the one usually named as the first printed form of the text.",
        details = "The romance was written in Catalan by Joanot Martorell, who died before it was finished, and Marti Joan de Galba is credited with completing it. The Valencia printing of 1490 is the edition usually cited as the first, so the date given here is the year of that printing.",
        rare = true,
    ),

    entry(
        databaseId = 55,
        kind = Kind.LANGUAGE,
        section = "Bibles",
        title = "Malermi's Italian Bible of 1490",
        originalTitle = "Biblia volgare",
        author = "Nicolo Malermi",
        year = 1490,
        summary = "Malermi's Bible, printed at Venice in 1490, gave the whole Bible in Italian, and it is usually named as the first complete Bible printed in that language.",
        details = "The translation was made by Malermi, a monk of the Camaldolese order, and printed at Venice in 1490. The record gives the year of the first printing only, since later editions followed in the years after it.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.LANGUAGE,
        section = "Lexicons",
        title = "The Suda, a Greek encyclopedic lexicon in print",
        originalTitle = "Lexicon graecum",
        author = "Suidas, edited by Demetrius Chalcondyles",
        year = 1499,
        summary = "The Suda, a Byzantine Greek lexicon of encyclopedic scope, was first printed at Milan in November 1499, and it is regarded as the largest one-volume Greek book of the fifteenth century.",
        details = "Demetrius Chalcondyles edited the text, and the printers Johannes Bissolus and Benedictus Mangius produced it for him at Milan on 15 November 1499. The book runs to 516 folio leaves, and the name Suidas attached to the work is regarded as an error, while the title is linked to a Greek word for treasury.",
        rare = true,
    ),

    entry(
        databaseId = 55,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Reuchlin's dialogue on the wonder-working Hebrew word",
        originalTitle = "De verbo mirifico",
        author = "Johannes Reuchlin",
        year = 1494,
        summary = "Reuchlin's dialogue of 1494 argued that the Hebrew names of God carry a wonder-working power, and it brought Hebrew learning into the religious debates of the printing age.",
        details = "The book was printed at Basel in 1494 in the form of a dialogue among three speakers, one of them Reuchlin himself under a Greek name. It is often read as the start of Reuchlin's Christian Kabbalah, which he developed in his later books.",
        rare = true,
    ),

    entry(
        databaseId = 55,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "The Promptorium parvulorum, an English-Latin wordbook",
        originalTitle = "Promptorium parvulorum",
        author = "Anonymous, compiled about 1440, printed by Wynkyn de Worde",
        year = 1499,
        summary = "The Promptorium parvulorum, a wordbook that gives English words with their Latin equivalents, was printed in London in 1499 by Wynkyn de Worde, and it is one of the earliest English-Latin wordbooks in print.",
        details = "The wordbook was compiled in the middle of the fifteenth century for teaching boys, and its date of compilation is given as about 1440. The printed edition of 1499 is the first printed form, so the record uses that year.",
        rare = true,
    ),

)
