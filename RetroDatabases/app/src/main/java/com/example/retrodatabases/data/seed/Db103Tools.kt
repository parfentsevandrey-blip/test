package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1010 to 1013: an algebra for a vizier, an optics treatise written in Cairo and a Song encyclopedia of imperial records. */
val db103Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Al-Karaji's al-Fakhri, an algebra named for the vizier Fakhr al-Mulk",
        originalTitle = "al-Fakhri fi'l-jabr wa'l-muqabala",
        author = "Abu Bakr al-Karaji",
        year = 1010,
        summary = "Al-Karaji's al-Fakhri is an algebra book, named for the vizier Fakhr al-Mulk, that works with powers of the unknown and with sums of powers.",
        details = "The book is usually dated to about 1010, but accounts of al-Karaji's dates differ and some place him in the late tenth century, so the year is approximate. Its argument on the sum of cubes survives in later expositions.",
        rare = true,
    ),

    entry(
        databaseId = 103,
        kind = Kind.TOOL,
        section = "Optics",
        title = "Ibn al-Haytham's Book of Optics, seven books written in Cairo",
        originalTitle = "Kitab al-Manazir",
        author = "Ibn al-Haytham",
        year = 1011,
        summary = "Ibn al-Haytham's Kitab al-Manazir, the Book of Optics in seven books, treats vision, light, reflection and refraction and was written in Cairo from about 1011.",
        details = "Most accounts date the work to 1011 to 1021, the years of the author's confinement in Cairo under the caliph al-Hakim, while one account starts it in 1010. The Latin translation made at the end of the twelfth century carried the book into European optics.",
    ),

    entry(
        databaseId = 103,
        kind = Kind.TOOL,
        section = "Reference",
        title = "The Cefu yuangui, a Song encyclopedia of imperial records, completed in 1013",
        originalTitle = "Cefu yuangui",
        author = "Wang Qinruo and Yang Yi",
        year = 1013,
        summary = "The Cefu yuangui is a Song reference work of imperial records and precedents, compiled for Emperor Zhenzong and completed in 1013 in 1000 juan.",
        details = "The compilers sorted the records under topics such as the conduct of rulers and state rites, so the work served officials as a guide to precedent. Its compilation is usually dated from about 1005, and the completion year is given as 1013.",
        rare = true,
    ),

)
