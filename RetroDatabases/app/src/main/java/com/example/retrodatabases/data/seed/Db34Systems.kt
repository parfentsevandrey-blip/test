package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1700 to 1710: an academy, a calendar experiment, two schools and a porcelain factory. */
val db34Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The Berlin Academy of Sciences",
        originalTitle = "Societas Scientiarum Berolinensis",
        author = "Elector Frederick III of Brandenburg, on Leibniz's proposal",
        year = 1700,
        summary = "A learned society founded in Berlin in 1700 to study the sciences and to print its papers in regular volumes.",
        details = "Leibniz, who had proposed the society, was its first president. Its papers were printed in the Miscellanea Berolinensia from 1710, and the society became one of the standing scientific bodies of northern Germany.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.SYSTEM,
        section = "Insurance",
        title = "The Sun Fire Office",
        originalTitle = "Sun Fire Office",
        author = "London proprietors of the Sun Fire Office",
        year = 1710,
        summary = "A London company founded in 1710 to insure buildings and their contents against fire.",
        details = "Fire insurance of this kind spread in London after the great fires of the seventeenth century. The company's policies covered houses and goods, and it was one of the early fire offices in the city.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.SYSTEM,
        section = "Education",
        title = "The Moscow school of mathematics and navigation",
        originalTitle = "Shkola matematicheskikh i navigatskikh nauk",
        author = "Peter the Great",
        year = 1701,
        summary = "A school in Moscow that trained officers in arithmetic, geometry and navigation for the growing Russian fleet.",
        details = "Peter founded the school in 1701 in a Moscow tower, and Leonty Magnitsky taught there. Its textbook, the Arithmetic of 1703, was written for the school's pupils, and the school trained the navigators that the new fleet needed.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.SYSTEM,
        section = "Education",
        title = "The Collegiate School of Connecticut",
        originalTitle = "Collegiate School",
        author = "Congregational ministers of Connecticut",
        year = 1701,
        summary = "A college founded in 1701 to train ministers in the Connecticut colony, which later moved and took the name Yale.",
        details = "The founders were Congregational ministers who wanted an educated clergy in the colony. The school moved to New Haven in 1716, and it was named Yale in 1718 after a benefactor, so early records use the older name.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.SYSTEM,
        section = "Manufacture",
        title = "The Meissen porcelain manufactory",
        originalTitle = "Koenigliche Porzellanmanufaktur Meissen",
        author = "Augustus the Strong, elector of Saxony",
        year = 1710,
        summary = "A royal factory set up near Dresden to make hard porcelain, the first such works in Europe.",
        details = "Johann Friedrich Boettger made hard porcelain in 1708 after a search that began with attempts to make gold, and the royal factory was set up at Meissen in 1710. Its methods and designs were copied across Europe in the following decades.",
    ),

)
