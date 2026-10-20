package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 670 to 680: the red dots that marked the vowels of Arabic writing, placed at 673, the year Ziyad died. */
val db137Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.PRACTICE,
        section = "Arabic",
        title = "The red dots that marked the vowels of Arabic writing, credited to Abu al-Aswad al-Du'ali of Basra",
        originalTitle = "harakat",
        author = "Abu al-Aswad al-Du'ali",
        year = 673,
        summary = "Red dots placed above, below or on the letters marked the short vowels of Arabic writing, a practice credited to Abu al-Aswad al-Du'ali of Basra.",
        details = "No source dates the dots: accounts name either Ali, who died in 661, or Ziyad ibn Abihi, governor of Iraq until his death in 673, so 673 is the placed year, the year of that death. Accounts also differ on whether the dot for the vowel u sits on the letter or in front of it, and al-Khalil ibn Ahmad later replaced the dots with signs.",
        rare = true,
    ),

)
