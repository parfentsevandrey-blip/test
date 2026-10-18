package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1273 to 1277: Castilian astronomy, a French romance and play, a Persian poem of couplets and two Latin works of learning. */
val db77Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.LANGUAGE,
        section = "Castilian",
        title = "Alfonso X's Libro del saber de astronomia, written in Castilian",
        originalTitle = "Libro del saber de astronomia",
        author = "Alfonso X and the scholars of his court",
        year = 1276,
        summary = "Alfonso X's Libro del saber de astronomia is a Castilian compendium of astronomy and astrological lore, compiled at his court in the years 1276 to 1279.",
        details = "The king's scholars drew on Arabic, Latin and Hebrew sources, and the book was written in Castilian rather than Latin, which made its learning available to a wider readership. The compilation is usually dated to 1276 to 1279, and the printed edition was made at Madrid in the 1860s, more than five centuries after the book was made.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Jean de Meun's continuation of the Romance of the Rose",
        originalTitle = "Le Roman de la Rose",
        author = "Jean de Meun",
        year = 1275,
        summary = "Jean de Meun added a continuation of some 17,000 lines to Guillaume de Lorris's unfinished Romance of the Rose, written in French about 1275.",
        details = "Guillaume de Lorris had left his part unfinished about 1230, and most accounts date Jean de Meun's continuation to about 1275 to 1280, with the exact years disputed. Its satire of clergy and its long debates on love made the whole poem one of the most widely copied French works of the later Middle Ages.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Adam de la Halle's Jeu de la feuillee, a play of Arras",
        originalTitle = "Le Jeu de la feuillee",
        author = "Adam de la Halle",
        year = 1276,
        summary = "Adam de la Halle wrote the Jeu de la feuillee at Arras about 1276, one of the earliest surviving French plays, and it mixes local mockery with fairy characters.",
        details = "The play mocks townspeople of Arras and brings on the fairy king Hellequin and the fairy Morgue. The date of about 1276 is the usual estimate, and the play is one of the few surviving French plays of its time.",
        rare = true,
    ),

    entry(
        databaseId = 77,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Rumi's Masnavi, six books of rhymed couplets",
        originalTitle = "Masnavi-ye Ma'navi",
        author = "Jalal al-Din Rumi",
        year = 1273,
        summary = "Rumi's Masnavi is a long mystical poem in six books of rhymed couplets, composed in Konya in the years up to Rumi's death in 1273.",
        details = "Rumi dictated the poem to his disciple Husam al-Din Chelebi, and it was written down as he spoke. Rumi died at Konya on 17 December 1273, and accounts differ on how much of the sixth book was completed before his death.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Aquinas's Summa theologiae, left unfinished in 1274",
        originalTitle = "Summa theologiae",
        author = "Thomas Aquinas",
        year = 1274,
        summary = "Thomas Aquinas's Summa theologiae was left unfinished when he died in March 1274, and it became one of the main textbooks of the later schools.",
        details = "He began the work in Rome around 1265 and arranged it in questions, each with objections, a reply and answers to the objections. Aquinas died on 7 March 1274 at Fossanova, and the third part breaks off at question 90, while its treatment of penance is still under way.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Giles of Rome's On the Governance of Rulers, written for a young prince",
        originalTitle = "De regimine principum",
        author = "Giles of Rome",
        year = 1277,
        summary = "Giles of Rome's De regimine principum is a Latin work on how rulers should govern, written about 1277 to 1280 for the heir to the French crown.",
        details = "Giles had studied under Thomas Aquinas in Paris, and he built the book on the moral and political works of Aristotle and on Christian teaching about kings. It was widely copied and translated, and its writing is usually dated to the late 1270s.",
        rare = true,
    ),

)
