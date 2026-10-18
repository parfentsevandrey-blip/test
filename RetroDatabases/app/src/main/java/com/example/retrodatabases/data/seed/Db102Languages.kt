package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1020 to 1030: Ibn Sina's Canon of Medicine, Ibn Hazm's Ring of the Dove and Guido of Arezzo's Micrologus. */
val db102Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.LANGUAGE,
        section = "Medicine",
        title = "Ibn Sina's Canon of Medicine, a five-book encyclopedia of medicine",
        originalTitle = "al-Qanun fi al-tibb",
        author = "Ibn Sina (Avicenna)",
        year = 1025,
        summary = "Ibn Sina's Canon of Medicine, a five-book Arabic encyclopedia, is usually dated to about 1025 and stayed a standard medical text for centuries.",
        details = "The five books cover general principles, simple drugs, diseases of each part of the body, diseases that affect the whole body, and compound remedies. The Canon was later translated into Latin and read in European medical schools, and it became one of the most widely used medical books of the Middle Ages.",
    ),

    entry(
        databaseId = 102,
        kind = Kind.LANGUAGE,
        section = "Love",
        title = "The Ring of the Dove, Ibn Hazm's treatise on love and lovers",
        originalTitle = "Tawq al-hamama",
        author = "Ibn Hazm of Cordoba",
        year = 1022,
        summary = "Ibn Hazm of Cordoba wrote The Ring of the Dove, an Arabic treatise on love and lovers, in 1022.",
        details = "The book mixes theory, anecdote and verse about love, and it is one of the best-known Arabic treatises on the subject. A. J. Arberry's English translation appeared in London in 1953, although one account gives 1951, and some accounts date the work only approximately.",
        rare = true,
    ),

    entry(
        databaseId = 102,
        kind = Kind.LANGUAGE,
        section = "Music",
        title = "Guido of Arezzo's Micrologus, a short treatise on music and chant",
        originalTitle = "Micrologus",
        author = "Guido of Arezzo",
        year = 1026,
        summary = "Guido of Arezzo's Micrologus, a short Latin treatise on music and the teaching of chant, was written about 1026.",
        details = "Accounts give 1025 or 1026 for its date. The treatise is linked with Guido's method of naming notes by the syllables ut, re, mi, fa, sol and la, while the claim that Guido invented the four-line staff is disputed, and some writers tie the staff's introduction to a prologue to his antiphoner dated about 1030.",
        rare = true,
    ),

)
