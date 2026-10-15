package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1590 to 1600: Vieta's cipher reading, Ricci's memory art, Libavius's chemistry, the Bishops' Ban and the Jesuit plan of studies. */
val db45Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.PRACTICE,
        section = "Ciphers",
        title = "Vieta's reading of the Spanish cipher",
        originalTitle = "The Spanish cipher",
        author = "Francois Vieta",
        year = 1590,
        summary = "Vieta read Spanish dispatches for Henry IV in a cipher of several hundred symbols, by counting how often each symbol occurred and by finding patterns in the messages.",
        details = "The reading was done for the French king around 1590, and accounts differ on the exact years and on how many symbols the cipher had. The Spanish court is said to have blamed magic for the success, a charge that is reported rather than proved.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.PRACTICE,
        section = "Memory",
        title = "Ricci's memory art in Chinese",
        originalTitle = "Xiguo jifa",
        author = "Matteo Ricci",
        year = 1596,
        summary = "Ricci's Xiguo jifa of 1596 taught the Western art of memory to Chinese readers, placing items in imagined rooms and walking through them to recall.",
        details = "The book was written in Chinese, so that educated readers could learn a classical method that Ricci had learned in Europe. The date 1596 is the usual one for the book, and it is among the early Chinese works that Ricci wrote for his Chinese friends.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.PRACTICE,
        section = "Chemistry",
        title = "Libavius's Alchymia, the chemistry manual",
        originalTitle = "Alchymia",
        author = "Andreas Libavius",
        year = 1597,
        summary = "Libavius's Alchymia of 1597 set out chemical operations, vessels and furnaces in an ordered manual, and it is often named the first chemistry textbook.",
        details = "The book was printed at Frankfurt in 1597 and describes distillation, the making of salts and the use of apparatus in plain steps. Accounts differ on whether a book of this kind deserves the name textbook, but it is among the earliest such works in print.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.PRACTICE,
        section = "Books and licences",
        title = "The Bishops' Ban on satires of 1599",
        originalTitle = "Bishops' Ban",
        author = "Archbishop Whitgift and Bishop Bancroft",
        year = 1599,
        summary = "In June 1599 the English bishops ordered that certain satires be called in and burned, an early case of official control over printed verse.",
        details = "The order named satirical verse and some books, and it forbade further printing of such works. Accounts differ on how many copies were burned in practice, so the record gives only the order and its date.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "The Jesuit Ratio studiorum of 1599",
        originalTitle = "Ratio atque institutio studiorum Societatis Iesu",
        author = "Jesuit Order under Claudio Acquaviva",
        year = 1599,
        summary = "The Jesuit plan of studies of 1599 set out the subjects, hours and methods of Latin, arts and mathematics in the Society's colleges, and it guided Jesuit teaching for centuries.",
        details = "The Ratio was issued in its final form in 1599 after a provisional plan of 1586, and it fixed how the colleges taught grammar, rhetoric, philosophy and mathematics. It stayed the Jesuit plan of studies for centuries, with later revisions.",
    ),

)
