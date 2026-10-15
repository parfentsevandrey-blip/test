package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1680 to 1690: Mabillon on charters, Wallis's logic, the Jesuits' Latin Confucius, Knorr von Rosenroth's Latin Kabbala, Hickes's Old English grammar and Simon's New Testament text. */
val db36Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.LANGUAGE,
        section = "Writing",
        title = "Mabillon's rules for old charters, De re diplomatica",
        originalTitle = "De re diplomatica libri VI",
        author = "Jean Mabillon",
        year = 1681,
        summary = "Mabillon's six books set out rules for judging old charters by their script, formulas and seals, and they founded the study of diplomatics.",
        details = "Mabillon, a Benedictine of the Congregation of Saint-Maur, printed the work at Paris in 1681 after long study of monastic archives. Its rules for telling a genuine charter from a forgery shaped the historical method that followed.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "Wallis's Latin logic textbook for students",
        originalTitle = "Institutio logicae ad communes usus accommodata",
        author = "John Wallis",
        year = 1687,
        summary = "Wallis's Latin logic, written for Oxford undergraduates, sets out the rules of terms and arguments in a plain textbook.",
        details = "Wallis wrote it in Latin for the students of Oxford, and its roots go back to an earlier treatise that stayed unpublished, perhaps from the 1650s. Later editions followed the first printing of 1687.",
        rare = true,
    ),

    entry(
        databaseId = 36,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Jesuits' Latin Confucius of 1687",
        originalTitle = "Confucius Sinarum philosophus, sive Scientia Sinensis latine exposita",
        author = "Philippe Couplet and fellow Jesuits",
        year = 1687,
        summary = "A group of Jesuit scholars put the Chinese classics into Latin and printed the book in Paris, with a chronology of Chinese rulers.",
        details = "The volume of 1687 was edited by Philippe Couplet, with other Jesuits who had worked in China, and it presented the teaching of Confucius to European readers in Latin. Its chronology of Chinese rulers gave those readers a dated frame for Chinese history.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Knorr von Rosenroth's Kabbala denudata in Latin",
        originalTitle = "Kabbala denudata, seu doctrina Hebraeorum transcendentalis et metaphysica atque theologica",
        author = "Christian Knorr von Rosenroth",
        year = 1684,
        summary = "Knorr von Rosenroth's Kabbala denudata put parts of the Zohar and other Jewish mystical writings into Latin, so that Christian scholars could read them without Hebrew.",
        details = "The work was printed in two volumes, the first at Sulzbach in 1677 and the second in 1684. Its Latin renderings of the Zohar, the central book of Jewish mysticism, made those texts available to European readers.",
        rare = true,
    ),

    entry(
        databaseId = 36,
        kind = Kind.LANGUAGE,
        section = "Old English",
        title = "Hickes's grammars of Old English and Gothic",
        originalTitle = "Institutiones grammaticae Anglo-Saxonicae et Moeso-Gothicae",
        author = "George Hickes",
        year = 1689,
        summary = "Hickes gave Latin grammars of Anglo-Saxon, or Old English, and of Gothic, so that scholars could read the early Germanic languages with a working guide.",
        details = "The grammars were printed at Oxford, from the Sheldonian Theatre, with a 1689 imprint, though the licence to print is dated 1688. The volume was first planned as a companion to Junius's dictionary of the old languages, and it prepared Hickes's later and larger work on the northern tongues.",
        rare = true,
    ),

    entry(
        databaseId = 36,
        kind = Kind.LANGUAGE,
        section = "Scripture",
        title = "Simon's critical history of the New Testament text",
        originalTitle = "Histoire critique du texte du Nouveau Testament",
        author = "Richard Simon",
        year = 1689,
        summary = "Simon's critical history showed that the Greek New Testament survived in copies and early versions that differed from one another, and it asked scholars to compare them before trusting any printed text.",
        details = "Simon, a priest of the French Oratory, printed the book at Rotterdam in 1689. Its comparison of copies and versions made the text of the New Testament itself a subject of historical study.",
        rare = true,
    ),

)
