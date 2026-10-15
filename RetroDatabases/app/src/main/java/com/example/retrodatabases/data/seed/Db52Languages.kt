package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1520 to 1530: Luther's German New Testament, Tyndale's English one, Bembo's Tuscan, two Hebrew works and Trissino's letters. */
val db52Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.LANGUAGE,
        section = "German Bible",
        title = "Luther's September Testament of 1522",
        originalTitle = "Das Newe Testament Deutzsch",
        author = "Martin Luther",
        year = 1522,
        summary = "Luther's German New Testament, printed at Wittenberg in September 1522, turned the Greek text into the German that ordinary readers could follow.",
        details = "Luther made the translation while held at the Wartburg castle in 1521 and 1522, working from the Greek text that Erasmus had printed in 1519. The Wittenberg edition of September 1522 sold quickly, and its German became the model for later printed Bibles in that language.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.LANGUAGE,
        section = "English Bible",
        title = "Tyndale's English New Testament from the Greek",
        originalTitle = "The newe Testament",
        author = "William Tyndale",
        year = 1526,
        summary = "Tyndale's English New Testament, translated from the Greek and printed abroad, was the first printed English version of the New Testament made from that language.",
        details = "Printing began at Cologne in 1525 and was stopped, and the complete edition came out at Worms in 1526, so accounts differ on the year of first printing and 1526 is used here. Copies were smuggled into England, where the book was banned, and later English translations drew on Tyndale's wording.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Bembo's rules for the Tuscan tongue",
        originalTitle = "Prose della volgar lingua",
        author = "Pietro Bembo",
        year = 1525,
        summary = "Bembo's dialogue on the vernacular named Petrarch and Boccaccio as the models of literary Italian, and it gave the Tuscan of their age a fixed form.",
        details = "Bembo drafted the three books of the dialogue in the first years of the 1500s, and they were printed at Venice in 1525. The book argued for Tuscan writers as the standard, and its advice on spelling and grammar shaped Italian printing for centuries.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Tory's Champ fleury on the proportion of letters",
        originalTitle = "Champ fleury, auquel est contenu lart et science de la deue et vraye proportion des lettres",
        author = "Geoffroy Tory",
        year = 1529,
        summary = "Tory's Champ fleury of 1529, written in French, set out the proportions of the printed capitals and urged accents on French letters to show sounds the plain alphabet missed.",
        details = "The book was printed at Paris in 1529, and Tory tied the shapes of the capitals to the proportions of the human body, an idea that printers and writers debated for years. Accounts differ on how far his accents were adopted, since French printing took up only some of his marks.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Pagninus's Thesaurus of the holy tongue",
        originalTitle = "Thesaurus linguae sanctae",
        author = "Santes Pagninus",
        year = 1529,
        summary = "Pagninus's Thesaurus of 1529 was a Hebrew lexicon that listed words under their roots, following the root dictionary of David Kimhi.",
        details = "Britannica dates the Thesaurus to 1529, and the Catholic Encyclopedia places a Lyon edition in the same year, and the book went through many reprints. Its arrangement by three-letter roots let a reader find a Hebrew word in the Bible under the root from which it came.",
        rare = true,
    ),

    entry(
        databaseId = 52,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Trissino's new letters for Italian spelling",
        originalTitle = "Epistola de le lettere nuovamente aggiunte",
        author = "Gian Giorgio Trissino",
        year = 1524,
        summary = "Trissino's letter of 1524 proposed new letters for Italian spelling, to show sounds that the Latin alphabet had no sign for.",
        details = "The proposal was printed in 1524 as a letter and set out a reform of Italian writing with new signs for sounds the old alphabet could not show. The new letters did not pass into common use, and later Italian printers kept to the existing alphabet.",
        rare = true,
    ),

)
