package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1140 to 1150: a Latin pilgrim's book of about 1140, a Latin Qur'an, a Korean history, a Greek history, a Sanskrit chronicle and a German rhymed chronicle. */
val db90Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Codex Calixtinus, a Latin book of Santiago's liturgy, miracles and pilgrim guide",
        originalTitle = "Liber Sancti Jacobi",
        author = "Compilers at Santiago de Compostela",
        year = 1140,
        summary = "The Codex Calixtinus is a Latin manuscript of about 1140 from Santiago de Compostela, gathering the liturgy of Saint James, tales of his miracles and a guide for pilgrims.",
        details = "The manuscript has five books and is known for its pilgrim guide and its music, and it is one of the manuscripts that show the organistrum. The date of about 1140 is the usual one, and accounts differ on the years in which its separate books were written.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Robert of Ketton's Latin Qur'an, the Lex Mahumet pseudoprophete",
        originalTitle = "Lex Mahumet pseudoprophete",
        author = "Robert of Ketton",
        year = 1143,
        summary = "Robert of Ketton finished a complete Latin translation of the Qur'an in 1143, made at the request of Peter the Venerable of Cluny.",
        details = "Peter the Venerable commissioned the work in 1142 so that his monks could read the text for refutation, and the translation was completed in 1143. The translation was copied and read in the West for centuries, and it is the earliest complete Latin rendering of the Qur'an.",
        rare = true,
    ),

    entry(
        databaseId = 90,
        kind = Kind.LANGUAGE,
        section = "Korean",
        title = "Kim Busik's Samguk sagi, a history of the Three Kingdoms of Korea",
        originalTitle = "Samguk sagi",
        author = "Kim Busik",
        year = 1145,
        summary = "Kim Busik completed the Samguk sagi in 1145, a history of Silla, Goguryeo and Baekje written in literary Chinese for the Goryeo court.",
        details = "The history was ordered by King Injong of Goryeo and covers the three kingdoms from their founding to their fall. It is a different text from Iryeon's Samguk yusa of about 1281, which keeps the same kingdoms in a looser miscellany.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Anna Komnene's Alexiad, a history of her father's reign",
        originalTitle = "Alexias",
        author = "Anna Komnene",
        year = 1148,
        summary = "Anna Komnene wrote the Alexiad, a Greek history of the reign of Alexios I Komnenos, in the late 1140s after she had withdrawn to a convent.",
        details = "The work is written in fifteen books in Greek prose, and it praises her father while criticising the crusaders. The date of about 1148 is an estimate from her retirement, and accounts differ on whether she finished it before or after 1150.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.LANGUAGE,
        section = "Sanskrit",
        title = "Kalhana's Rajatarangini, a chronicle of the kings of Kashmir",
        originalTitle = "Rajatarangini",
        author = "Kalhana",
        year = 1148,
        summary = "Kalhana completed the Rajatarangini about 1148, a Sanskrit chronicle in verse of the kings of Kashmir from the legendary past to his own time.",
        details = "The chronicle has eight books, and its last book is the most detailed for the twelfth century. Accounts differ between 1148 and 1149 for the completion, so 1148 is the year usually given.",
        rare = true,
    ),

    entry(
        databaseId = 90,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "The Kaiserchronik, a German rhymed history of the Roman emperors",
        originalTitle = "Kaiserchronik",
        author = "An anonymous cleric of Regensburg",
        year = 1150,
        summary = "The Kaiserchronik is a Middle High German rhymed history of the Roman emperors, written at Regensburg about the middle of the twelfth century.",
        details = "The chronicle runs from Julius Caesar to Conrad III, and its author is unidentified. Older accounts date it to about 1150, while the later reckoning puts it after 1146, and the attribution to Konrad der Pfaffe has been given up.",
        rare = true,
    ),

)
