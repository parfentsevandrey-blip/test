package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1640 to 1650: a royal printing house, a trading post's daily register, a public library, an excise and a calendar office. */
val db40Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "The Imprimerie royale, the French royal printing house",
        originalTitle = "Imprimerie royale",
        author = "Louis XIII of France",
        year = 1640,
        summary = "The French crown's own printing house, set up in the Louvre so that official books were made in one state workshop.",
        details = "Louis XIII set it up in 1640 at Cardinal Richelieu's urging, and it was housed in the Louvre from the same year. Sebastien Cramoisy was its first director, and its name changed under successive regimes until the Imprimerie nationale name took hold in 1870.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.SYSTEM,
        section = "Trade records",
        title = "The Dejima daily register of the Dutch trading post",
        originalTitle = "Dagregister van Deshima",
        author = "Dutch East India Company",
        year = 1641,
        summary = "A dated daily register kept by the Dutch trading post at Dejima in Nagasaki Bay, recording ships, goods and visitors day by day.",
        details = "The Dutch were moved to the artificial island of Dejima in 1641, and from that year the post kept a daily register of its affairs that ran until the 1850s. The series of dated entries is a rare record of foreign trade under Japan's restricted trade policy.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "The Mazarine Library in Paris",
        originalTitle = "Bibliotheque Mazarine",
        author = "Cardinal Jules Mazarin",
        year = 1643,
        summary = "A library founded from a cardinal's collection of books in Paris, and often called the first public library in the city.",
        details = "Mazarin founded the library in 1643 and built up its collection of printed books and manuscripts over the following years. Its founding turned a private collection into a resource for scholars, which is why it is counted among the first public libraries of Paris.",
        rare = true,
    ),

    entry(
        databaseId = 40,
        kind = Kind.SYSTEM,
        section = "Taxation",
        title = "The English excise on goods",
        originalTitle = "Excise ordinance of 1643",
        author = "Long Parliament",
        year = 1643,
        summary = "A tax on beer, ale and other goods, collected by excise officers at the point of making or sale, which parliament introduced during the civil war.",
        details = "Parliament set up the excise in 1643 to pay its armies, and collectors had to inspect brewers and sellers at each stage of production and sale. Its office-based way of checking every batch and every sale became a model for later taxes on goods.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.SYSTEM,
        section = "Astronomy",
        title = "The Beijing Board of Astronomy under Jesuit direction",
        originalTitle = "Shixian li",
        author = "Adam Schall von Bell",
        year = 1645,
        summary = "A Chinese calendar office run by Jesuit astronomers, whose Shixian calendar the new Qing court adopted in 1645.",
        details = "Schall had worked on a calendar reform for the late Ming court, and the Qing took up his calendar after they took Beijing in 1644. The Board of Astronomy then used European methods to predict the seasons and eclipses for the imperial calendar.",
        rare = true,
    ),

)
