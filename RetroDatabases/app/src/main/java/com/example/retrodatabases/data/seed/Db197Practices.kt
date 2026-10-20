package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 70 to 80: the Jewish tax of two drachmas, moved from the Temple to the Capitol after the fall of Jerusalem in 70. */
val db197Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.PRACTICE,
        section = "Tax",
        title = "The Jewish tax of two drachmas moved from the Temple to the Capitol after 70",
        originalTitle = "Fiscus Iudaicus",
        author = "Vespasian and his officials",
        year = 70,
        summary = "After the Temple at Jerusalem fell in 70, Vespasian ordered the two-drachma tax that Jews had paid to the Temple to be paid to Jupiter on the Capitol at Rome.",
        details = "Josephus reports the order in his Jewish War, and Suetonius describes collectors under Domitian who pursued people who lived as Jews without paying or who hid their origin. Nerva's coins proclaimed that the abuse of the tax was removed, and accounts differ on how far the levy was really ended.",
        rare = true,
    ),

)
