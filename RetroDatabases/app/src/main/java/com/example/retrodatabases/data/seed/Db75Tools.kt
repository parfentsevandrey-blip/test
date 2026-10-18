package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1290 to 1299: Zhu Shijie's introduction to mathematics, Lanfranc's surgical handbook of Paris and Fleta, an English law treatise. */
val db75Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Zhu Shijie's introduction to mathematical studies of 1299",
        originalTitle = "Suanxue qimeng",
        author = "Zhu Shijie",
        year = 1299,
        summary = "Zhu Shijie completed the Suanxue qimeng in 1299, an introductory mathematics book that prepared readers for his later work on algebra.",
        details = "The book is an introduction for students, and it came before the Jade Mirror of 1303, a larger work on algebra with four unknowns. The year 1299 is the usual date for its completion.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.TOOL,
        section = "Surgery",
        title = "Lanfranc's Chirurgia magna, a surgical handbook completed in Paris in 1296",
        originalTitle = "Chirurgia magna",
        author = "Lanfranc of Milan",
        year = 1296,
        summary = "Lanfranc of Milan completed his Chirurgia magna in 1296, a Latin surgical handbook on wounds, fractures and operations that was finished in Paris.",
        details = "The work was begun at Lyons and finished in Paris, and it is said to be dedicated to Philip IV of France. Accounts differ on the date of its first printing, which is given as 1490 or 1498.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.TOOL,
        section = "Law",
        title = "Fleta, an English law treatise written in the Fleet prison about 1290",
        originalTitle = "Fleta",
        author = "An anonymous lawyer of the Fleet prison",
        year = 1290,
        summary = "Fleta is a Latin treatise on English law written about 1290, which follows the plan of Bracton's earlier treatise and takes its name from the Fleet prison in London where it was written.",
        details = "Its author is unknown, and the work is dated to about 1290, with some accounts placing it in the years up to 1293. It is an early practical law book for the courts and shows how English law was taught and used at the time.",
        rare = true,
    ),

)
