package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Treatises of 1260 to 1270: Roger Bacon's Opus majus, Yang Hui's commentary on the Nine Chapters, William of Moerbeke's Latin Archimedes and Witelo's Perspectiva. */
val db78Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.TOOL,
        section = "Learning",
        title = "Roger Bacon's Opus majus, a survey of learning sent to the pope in 1267",
        originalTitle = "Opus majus",
        author = "Roger Bacon",
        year = 1267,
        summary = "Roger Bacon completed the Opus majus in 1267 and sent it to Pope Clement IV, a survey of learning that argued for languages, mathematics, optics and experiment.",
        details = "The work has seven parts, among them the study of languages, mathematics, optics and experimental science, and Bacon wrote it at the pope's request. His shorter works of the same years, the Opus minus and the Opus tertium, are separate books, and accounts differ on the order in which the three were written.",
    ),

    entry(
        databaseId = 78,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Yang Hui's detailed explanations of the Nine Chapters, a Chinese book of 1261",
        originalTitle = "Xiangjie jiuzhang suanfa",
        author = "Yang Hui",
        year = 1261,
        summary = "Yang Hui published in 1261 a commentary on the Nine Chapters on the Mathematical Art, with worked problems and the counting-rod methods of its day.",
        details = "The book explains the problems of the Nine Chapters step by step, and 1261 is the date given for its first appearance. Yang Hui's later books are separate works and are not part of this record.",
        rare = true,
    ),

    entry(
        databaseId = 78,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "William of Moerbeke's Latin Archimedes, with On Floating Bodies in 1269",
        originalTitle = "De insidentibus in aqua",
        author = "William of Moerbeke",
        year = 1269,
        summary = "William of Moerbeke completed a Latin translation of Archimedes' On Floating Bodies in 1269, the text from which medieval readers learned the rule of floating and sinking.",
        details = "Moerbeke worked in the papal circle, and some accounts place the translation at Viterbo, while a copy in his own hand is kept in the Vatican Library. The year 1269 is the usual date for the translation, and accounts differ on which Greek manuscripts he used.",
        rare = true,
    ),

    entry(
        databaseId = 78,
        kind = Kind.TOOL,
        section = "Optics",
        title = "Witelo's Perspectiva, a Latin optics begun about 1270",
        originalTitle = "Perspectiva",
        author = "Witelo",
        year = 1270,
        summary = "Witelo began his Perspectiva about 1270, a Latin optics that drew on Euclid, Ptolemy and Alhazen and was widely read by later writers on sight.",
        details = "Dates given for the work begin in 1270 and run to the later 1270s, and the end date is not settled, so 1270 marks the start of that range. Witelo's dates of birth and death also vary between accounts, and his book is often confused with John Pecham's Perspectiva communis of the late 1270s.",
        rare = true,
    ),

)
