package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 530 to 540: Cassiodorus's Variae of about 537 and Gildas's On the Ruin of Britain, a Latin tract usually dated about 540. */
val db151Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Cassiodorus's Variae, twelve books of official letters and edicts written in the names of the Ostrogothic kings, collected about 537",
        originalTitle = "Variae",
        author = "Cassiodorus Senator",
        year = 537,
        summary = "Cassiodorus gathered into twelve books the letters, edicts and appointment formulas issued in the names of the Ostrogothic kings of Italy, many of them drafted by Cassiodorus himself.",
        details = "The usual date for the twelve books is about 537, but some scholars place their completion after 540 and others in the 540s, so the year is a convention. The collection is a chosen model of chancery style rather than a full archive, and its letters give set formulas for officials in a fixed Latin style.",
        rare = true,
    ),

    entry(
        databaseId = 151,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Gildas's On the Ruin of Britain, a Latin sermon-history that rebukes five British kings and the clergy, usually dated about 540",
        originalTitle = "De excidio et conquestu Britanniae",
        author = "Gildas",
        year = 540,
        summary = "Gildas, a British churchman, wrote in Latin about 540 a tract on the ruin of Britain that blames the kings and clergy of the Britons for the Saxon conquests.",
        details = "Gildas names five British kings and rebukes the clergy, so the tract is a sermon as much as a history of the island and its Roman past. It is usually dated to about 540, though some accounts place it in the 530s or later in the 540s, and the text survives in medieval manuscripts.",
        rare = true,
    ),

)
