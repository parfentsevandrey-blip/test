package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Telegraph, postal and railway systems of 1840 to 1850: the Washington line, shared railway accounts and the penny post. */
val db20Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.SYSTEM,
        section = "Electric telegraph",
        title = "Morse telegraph line from Washington to Baltimore",
        originalTitle = "Washington and Baltimore telegraph line of 1844",
        author = "Samuel Finley Breese Morse and Alfred Vail",
        year = 1844,
        summary = "The line of about forty miles carried the message What hath God wrought from the Capitol in Washington to Baltimore on 24 May 1844.",
        details = "Congress paid for the line in 1843, and the wire ran on poles along the Baltimore and Ohio railroad. Morse sent the message from the Capitol, and Alfred Vail received it in Baltimore.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.SYSTEM,
        section = "Railway organisation",
        title = "Railway Clearing House, the shared accounts of the railways",
        originalTitle = "Railway Clearing House",
        author = "Railway companies of Great Britain",
        year = 1842,
        summary = "The Railway Clearing House divided the receipts of through traffic among the companies that carried it, so a journey across several lines could be settled in one place.",
        details = "The clearing house was set up in 1842 to apportion receipts between companies. In 1847 it told member stations to adopt London time, as the record of railway time describes.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.SYSTEM,
        section = "Electric telegraph",
        title = "Electric Telegraph Company, a public telegraph company in Britain",
        originalTitle = "Electric Telegraph Company",
        author = "Board of directors of the company",
        year = 1846,
        summary = "The Electric Telegraph Company was founded in London in 1846 and sold telegraph messages to the public along railway routes.",
        details = "It built lines alongside railways and between towns, using needle instruments. The company was later taken into state ownership.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.SYSTEM,
        section = "Postal reform",
        title = "Uniform penny post of Britain",
        originalTitle = "Uniform Penny Postage",
        author = "Rowland Hill and Parliament",
        year = 1840,
        summary = "From 1840 a letter of up to half an ounce could be sent anywhere in Britain for one penny, paid in advance with an adhesive stamp.",
        details = "Rowland Hill's plan of uniform postage, with payment by stamp, took effect in 1840. The reform cut the cost of letters sharply and became a model for other postal systems.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.SYSTEM,
        section = "Electric telegraph",
        title = "Magnetic Telegraph Company of the United States",
        originalTitle = "Magnetic Telegraph Company",
        author = "Company proprietors",
        year = 1845,
        summary = "A company formed in 1845 to build telegraph lines in the United States, one of several firms that followed Morse's line of 1844.",
        details = "The company built lines between eastern cities in the years after Morse's success. Its early routes are not described in detail here.",
    ),
)
