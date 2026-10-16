package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1410 to 1420: Hus's treatise on Czech spelling and Horapollo's Greek book on Egyptian signs. */
val db63Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.LANGUAGE,
        section = "Spelling",
        title = "Hus's treatise on Czech spelling",
        originalTitle = "De orthographia Bohemica",
        author = "Jan Hus",
        year = 1410,
        summary = "Hus's treatise on Czech spelling, written about 1410, is linked to the diacritic marks that modern Czech uses.",
        details = "Accounts give the treatise a window of 1406 to 1412, and this record uses about 1410. The surviving text is a copy made about 1450 to 1460, and the authorship and origin of the diacritics are disputed.",
        rare = true,
    ),

    entry(
        databaseId = 63,
        kind = Kind.LANGUAGE,
        section = "Hieroglyphs",
        title = "Horapollo's Hieroglyphica, a Greek book on Egyptian signs",
        originalTitle = "Hieroglyphica",
        author = "Horapollo",
        year = 1419,
        summary = "Horapollo's Greek book on Egyptian signs was found on the island of Andros and read the signs as pictures of ideas, a reading that later scholars rejected.",
        details = "The Andros find is dated 1419 in some accounts and 1422 in others, and this record uses 1419. The book was first printed at Venice in 1505, and its readings were set aside after Champollion showed in 1822 that many signs stood for sounds.",
        rare = true,
    ),

)
