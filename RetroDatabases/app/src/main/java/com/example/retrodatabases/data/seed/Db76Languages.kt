package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1280 to 1288: Alfonso X's songs to the Virgin, the Zohar, a Korean history in Classical Chinese, a Japanese tale collection, a Hebrew book of fables and a Dutch world history in rhyme. */
val db76Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.LANGUAGE,
        section = "Galician-Portuguese",
        title = "Alfonso X's Cantigas de Santa Maria, songs to the Virgin in Galician-Portuguese",
        originalTitle = "Cantigas de Santa Maria",
        author = "Alfonso X of Castile and his court poets",
        year = 1280,
        summary = "Alfonso X's Cantigas de Santa Maria are more than four hundred songs of praise to the Virgin, written in Galician-Portuguese during his reign and set to music.",
        details = "The songs were composed over several decades of Alfonso's reign, from about the 1250s to the 1280s, and the richest manuscript, the Codice Rico, is usually dated to the late thirteenth century. Its musical notation and miniatures make it a key witness to the songs, and the attribution of the whole collection to Alfonso X is traditional rather than proven.",
    ),

    entry(
        databaseId = 76,
        kind = Kind.LANGUAGE,
        section = "Aramaic",
        title = "The Zohar, a mystical commentary on the Torah written mostly in Aramaic",
        originalTitle = "Sefer ha-Zohar",
        author = "Moses de Leon, attributed to Shimon bar Yochai",
        year = 1286,
        summary = "The Zohar is a mystical commentary on the Torah, written mostly in Aramaic, that began to circulate in Castile in the 1280s.",
        details = "The book claims the second-century sage Shimon bar Yochai as its source, but most scholars credit the bulk of the text to Moses de Leon, a Castilian kabbalist. The year 1286 is often given for its appearance, and scholars differ on how much of the text one man wrote and how much was added later.",
    ),

    entry(
        databaseId = 76,
        kind = Kind.LANGUAGE,
        section = "Classical Chinese",
        title = "The Samguk yusa, Iryeon's memorabilia of the Three Kingdoms of Korea",
        originalTitle = "Samguk yusa",
        author = "Iryeon and later Korean compilers",
        year = 1281,
        summary = "The Samguk yusa is a Korean collection of histories, legends and Buddhist tales in Classical Chinese, begun by the monk Iryeon in the early 1280s.",
        details = "Accounts give its composition as 1281 to 1285, and some sources give publication as 1281 to 1283, so the dates are approximate. Scholars also differ on how much of the text Iryeon wrote, since his name appears in only one part, and the oldest surviving printing is usually dated to 1512 or 1513.",
        rare = true,
    ),

    entry(
        databaseId = 76,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Shasekishu, Muju Ichien's collection of Buddhist tales and anecdotes",
        originalTitle = "Shasekishu",
        author = "Muju Ichien",
        year = 1283,
        summary = "The Shasekishu is a Japanese collection of Buddhist tales, legends and anecdotes that the monk Muju Ichien completed in 1283.",
        details = "Its stories are told in a mixed Japanese and Chinese style and cover monks, poets and ordinary people of the Kamakura period. The year 1283 is the date usually given for its completion, and the book is one of the best-known Japanese collections of Buddhist tales from its century.",
        rare = true,
    ),

    entry(
        databaseId = 76,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "The Meshal ha-Kadmoni, a Hebrew book of fables by Isaac ibn Sahula",
        originalTitle = "Meshal ha-Kadmoni",
        author = "Isaac ibn Sahula",
        year = 1281,
        summary = "The Meshal ha-Kadmoni is a Hebrew book of fables that Isaac ibn Sahula completed in 1281.",
        details = "Ibn Sahula was a Jewish writer in Castile, and his fables draw on the older tradition of animal tales. The year 1281 is the date usually given for its composition, and the book was copied for later readers.",
        rare = true,
    ),

    entry(
        databaseId = 76,
        kind = Kind.LANGUAGE,
        section = "Middle Dutch",
        title = "Jacob van Maerlant's Spiegel historiael, a world history in rhymed Dutch",
        originalTitle = "Spiegel historiael",
        author = "Jacob van Maerlant",
        year = 1283,
        summary = "Jacob van Maerlant's Spiegel historiael is a history of the world in rhymed Middle Dutch verse, written from 1283 to 1288 by a poet of Flanders.",
        details = "Its title follows Vincent of Beauvais's Speculum historiale, which the poem adapts for a Dutch audience, and the author had already written a natural history in Dutch. The usual dates of writing are 1283 to 1288, so the year given here is the first of that range.",
        rare = true,
    ),

)
