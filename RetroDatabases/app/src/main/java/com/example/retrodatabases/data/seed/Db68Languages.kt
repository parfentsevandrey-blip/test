package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1361 to 1366: a Javanese court poem, a Latin book of women, a French poem with letters, Petrarch's letters, a Chinese miscellany and the English statute of pleading. */
val db68Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.LANGUAGE,
        section = "Javanese",
        title = "The Nagarakretagama, a Javanese court poem completed in 1365",
        originalTitle = "Nagarakretagama",
        author = "Mpu Prapanca",
        year = 1365,
        summary = "The Nagarakretagama is an Old Javanese court poem in praise of the Majapahit kingdom, usually dated to 1365.",
        details = "Prapanca wrote the poem in Old Javanese verse about King Hayam Wuruk and the court at Majapahit. The surviving copies are much later than the poem, so the wording of the original is uncertain, and the date 1365 is the one usually given.",
        rare = true,
    ),

    entry(
        databaseId = 68,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Boccaccio's Latin lives of famous women",
        originalTitle = "De mulieribus claris",
        author = "Giovanni Boccaccio",
        year = 1362,
        summary = "Boccaccio wrote a Latin book of short lives of famous women in the early 1360s, drawn from classical and Christian sources.",
        details = "The book is usually dated 1361 to 1362, so 1362 is an approximate year that names the end of that range. It was dedicated to a noblewoman, Andrea Acciaiuoli, and it covers subjects from many centuries.",
    ),

    entry(
        databaseId = 68,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Machaut's Voir Dit, a French poem with letters",
        originalTitle = "Le Livre du Voir Dit",
        author = "Guillaume de Machaut",
        year = 1365,
        summary = "Machaut's Voir Dit mixes narrative verse, songs and a sequence of letters between the poet and a young admirer, and it was written in the early 1360s.",
        details = "The poem is usually dated to about 1363 to 1365, so 1365 is the late end of an approximate range. Scholars differ on how much of the correspondence is real and how much was written for the poem.",
    ),

    entry(
        databaseId = 68,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Petrarch's letters arranged as the Familiares",
        originalTitle = "Rerum familiarium libri",
        author = "Francesco Petrarca",
        year = 1366,
        summary = "Petrarch gathered his Latin letters into books that he called the Familiares, arranging them over several years.",
        details = "He began the collection in 1359, and the arrangement of the books is usually given as finished by 1366, so 1366 is an approximate end date. Some letters were rewritten before they were copied into the books, so the collection is partly a literary edit.",
        rare = true,
    ),

    entry(
        databaseId = 68,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Tao Zongyi's notes written while resting from the fields",
        originalTitle = "Chuogeng lu",
        author = "Tao Zongyi",
        year = 1366,
        summary = "Tao Zongyi's Chuogeng lu is a Chinese miscellany of notes on drama, fiction and history, completed in the late Yuan period.",
        details = "The title means notes written while resting from farming, and the work is dated to 1366 in the usual accounts. Later copies vary in wording, so the text is known in several versions.",
        rare = true,
    ),

    entry(
        databaseId = 68,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "The English statute that made pleading in English",
        originalTitle = "Statute of Pleading",
        author = "The English parliament",
        year = 1362,
        summary = "The Statute of Pleading of 1362 ordered that pleas in the king's courts be made in English rather than French.",
        details = "The order changed the spoken language of pleading more than the language of the court record. Court records stayed in Latin and French for centuries after the statute.",
    ),

)
