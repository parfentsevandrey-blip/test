package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 0 to 10: one placed text, the Rites of Zhou, placed at 9 for the founding of the Xin. */
val db204Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Rites of Zhou, a classic of six offices, undated in the sources and placed at 9",
        originalTitle = "Zhou li",
        author = "Ascribed to the Duke of Zhou, with no author named in the sources",
        year = 9,
        summary = "The Rites of Zhou sets out the offices of an ideal government in six groups, and the record is placed at 9, the year Wang Mang founded the Xin, not the year the text was made.",
        details = "The sources do not date the text, and modern accounts place its making anywhere from the late Warring States period to the Western Han, so 9 is the year of the anchor and not of the text. Han scholars of the Old Text school promoted the book, and Wang Mang, who founded the Xin in 9, is usually credited with promoting it too, which is why his founding year serves as the anchor.",
        rare = true,
    ),

)
