package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 972 to 978: a Song collection of tales, a Song official history of the Five Dynasties, and a Latin chronicle manuscript of Spain. */
val db107Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.LANGUAGE,
        section = "Song China",
        title = "The Taiping Guangji, a Song collection of 500 scrolls of tales compiled under Li Fang",
        originalTitle = "Taiping Guangji",
        author = "Li Fang and a team of compilers",
        year = 978,
        summary = "The Song court's Taiping Guangji, a collection of 500 scrolls of tales about unusual people and events, was compiled under Li Fang around 978.",
        details = "Accounts differ on the year, with 977 or 978 given in some and 981 in another. The compilers drew on several hundred earlier texts, many of them informal, and the collection is still read as a major source for early Chinese tales.",
        rare = true,
    ),

    entry(
        databaseId = 107,
        kind = Kind.LANGUAGE,
        section = "Song China",
        title = "The Old History of the Five Dynasties, a 150-chapter official history directed by Xue Juzheng",
        originalTitle = "Jiu Wudai shi",
        author = "Xue Juzheng and collaborators",
        year = 972,
        summary = "The Song court's Old History of the Five Dynasties is a 150-chapter official history of the north Chinese dynasties from 907 to 960, directed by Xue Juzheng.",
        details = "Xue Juzheng (912 to 981) directed the compilation, which is the older of the two official histories of that period. The work is dated 972 and was made under the first Song emperors.",
        rare = true,
    ),

    entry(
        databaseId = 107,
        kind = Kind.LANGUAGE,
        section = "Latin Spain",
        title = "The Codex Vigilanus, a Latin chronicle manuscript completed at Albelda in 976",
        originalTitle = "Codex Vigilanus, also called the Codex Albeldensis",
        author = "Vigila, the copyist",
        year = 976,
        summary = "The Codex Vigilanus is a Latin manuscript of Spanish chronicles and histories, completed at the monastery of San Martin at Albelda in 976.",
        details = "The book gathers historical texts from antiquity to the tenth century, and its earliest form is dated to 881, with later revisions carrying the account to 976. The manuscript is now kept at El Escorial under the designation Escorialensis d I 2.",
        rare = true,
    ),

)
