package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1830 to 1840: counting people and cases, dividing labour in manufacture, copper plates by electrolysis and national registration. */
val db21Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.PRACTICE,
        section = "Social statistics",
        title = "Quetelet's average man",
        originalTitle = "Sur l'homme et le developpement de ses facultes, essai de physique sociale (1835)",
        author = "Adolphe Quetelet",
        year = 1835,
        summary = "A method that describes a population by its average and by the spread of its measures, so that the mean becomes the typical man and the deviations show how far others depart from him.",
        details = "Quetelet's 1835 book applied the error curve of astronomy to human measures such as height and weight, and he called the mean the average man. The idea shaped later social statistics, and it was also criticised for treating the mean as a norm. The record uses the 1835 publication date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.PRACTICE,
        section = "Manufacture",
        title = "Babbage's Economy of Machinery and Manufactures",
        originalTitle = "On the Economy of Machinery and Manufactures (1832)",
        author = "Charles Babbage",
        year = 1832,
        summary = "A study of how machines, the division of labour and the cost of each task decide what a factory should make and how it should make it, illustrated from many trades.",
        details = "Babbage's book was published in London in 1832, and further editions followed in the next few years. It uses the making of pins and other small goods to show how dividing a task among workers lowers its cost. The record uses the 1832 first edition.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.PRACTICE,
        section = "Medicine",
        title = "Louis's numerical method in medicine",
        originalTitle = "Recherches sur les effets de la saignee (1835)",
        author = "Pierre Charles Alexandre Louis",
        year = 1835,
        summary = "A way of judging a treatment by counting cases in tables, comparing patients treated early with patients treated late, instead of by the impressions of a single physician.",
        details = "Louis compared the course of pneumonia and other inflammatory illnesses in patients bled early and in patients bled later, and he reported the counts in tables. His 1835 work is an early example of the numerical method in medicine. The record uses the 1835 publication date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.PRACTICE,
        section = "Electroplating",
        title = "Jacobi's electrotyping of printing plates",
        originalTitle = "Report on galvanoplastic copies, St Petersburg (1838)",
        author = "Moritz von Jacobi",
        year = 1838,
        summary = "A process that deposits copper by electrolysis on a mould of a printing block or type, so that an exact copper plate can be printed from the mould.",
        details = "Jacobi reported the process at St Petersburg in 1838, and it was soon used for printing plates and engravings. Spencer in England worked on the same process in the same years, and accounts of priority differ. The record uses the 1838 report.",
        rare = true,
    ),

    entry(
        databaseId = 21,
        kind = Kind.PRACTICE,
        section = "Registration",
        title = "Civil registration of births, deaths and marriages in England",
        originalTitle = "Births and Deaths Registration Act (1836)",
        author = "Parliament of the United Kingdom",
        year = 1837,
        summary = "A national practice of recording every birth, death and marriage through local registrars, whose returns were gathered by a central office in London.",
        details = "The 1836 Act set up the system, and registration began on 1 July 1837 under the General Register Office. The returns from the local registrars gave the first complete national counts of vital events. The record uses the 1837 start date.",
    ),
)
