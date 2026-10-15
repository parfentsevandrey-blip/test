package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1540 to 1550: a religious order, a northern university, a general council, a coinage debasement and the New Laws for the Indies. */
val db50Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.SYSTEM,
        section = "Religious orders",
        title = "The Society of Jesus, approved by Pope Paul III in 1540",
        originalTitle = "Regimini militantis Ecclesiae",
        author = "Pope Paul III",
        year = 1540,
        summary = "The Society of Jesus was approved by the pope in 1540, and its colleges later made mathematics a regular part of their teaching.",
        details = "The bull Regimini militantis Ecclesiae, dated 27 September 1540, gave papal approval to the order founded by Ignatius of Loyola and his companions. Jesuit colleges later taught mathematics, and Christopher Clavius, a Jesuit, helped prepare the Gregorian calendar of 1582.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The Albertina, the university of Konigsberg, founded in 1544",
        originalTitle = "Academia Albertina",
        author = "Duke Albert of Prussia",
        year = 1544,
        summary = "Duke Albert of Prussia founded the Albertina at Konigsberg in 1544, the first university in Prussia, to train learned men for his church and state.",
        details = "The university opened in 1544 and took the duke's name. Its aim was to supply Lutheran clergy and officials for Prussia, and the town where it stood also saw the printing of Mazvydas's Lithuanian catechism in 1547.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Trent, first met in December 1545",
        originalTitle = "Concilium Tridentinum",
        author = "Pope Paul III",
        year = 1545,
        summary = "The Council of Trent first met on 13 December 1545, and through sessions that ran until 1563 it fixed Catholic teaching on scripture, the sacraments and the reform of the clergy.",
        details = "The council was called by Pope Paul III and met at Trent in northern Italy, with breaks in its work over the following years. Its decrees on the Bible and on books were followed by later lists of prohibited works.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.SYSTEM,
        section = "Money",
        title = "The Great Debasement of English coin under Henry VIII",
        originalTitle = "The Great Debasement",
        author = "Henry VIII",
        year = 1544,
        summary = "From 1544 the English mint under Henry VIII cut the silver in its coins, so that each coin held less of the precious metal than before.",
        details = "Accounts differ on when the first cut began, with some dating it to 1542 and others to 1544, and the debasement went on until 1551. The mint added base metal to the silver, so the same weight of metal made more coins, each of them worth less.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The New Laws for the Indies, issued in 1542",
        originalTitle = "Leyes Nuevas",
        author = "Emperor Charles V",
        year = 1542,
        summary = "The New Laws of 1542 set royal limits on the grants of Indian labour to Spanish settlers and changed how the American colonies were governed.",
        details = "Charles V issued the laws in 1542 after complaints by friars and missionaries about the treatment of the peoples of the Americas. Settlers resisted parts of them, and some rules were softened within a year, so the laws are read as both a reform and a compromise.",
    ),

)
