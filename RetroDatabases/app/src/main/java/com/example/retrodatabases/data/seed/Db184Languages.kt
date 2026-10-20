package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 200 to 210: Sextus Empiricus's sceptical Outlines and the Elder futhark, the first runic alphabet, with its earliest finds. */
val db184Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Sextus Empiricus's Outlines of Pyrrhonism, the chief surviving account of Greek scepticism",
        originalTitle = "Pyrrhoneioi hypotyposeis",
        author = "Sextus Empiricus",
        year = 200,
        summary = "Sextus Empiricus's Outlines of Pyrrhonism is the chief surviving Greek account of sceptical doubt, set out in three books.",
        details = "No source dates the Outlines itself. Sextus is placed in the second or third century, and one suggestion has him famous by about 200, so 200 is an approximate year and not a date of composition.",
        rare = true,
    ),

    entry(
        databaseId = 184,
        kind = Kind.LANGUAGE,
        section = "Runic",
        title = "The Elder futhark, the first runic alphabet, with its earliest finds about 200",
        originalTitle = "Elder futhark",
        author = "Rune carvers of Denmark and Norway",
        year = 200,
        summary = "The Elder futhark is the first runic alphabet, and by about 200 its signs are cut on objects from Denmark and Norway.",
        details = "The Vimose comb from Funen is put at about 160, and a Vimose buckle near 200, so the year 200 follows the buckle and not the comb. Accounts differ on the Ovre Stabu spearhead, which is dated either to the later second century or to 210 to 240. The full row of twenty-four signs is first attested complete on the Kylver stone from Gotland, about 400.",
        rare = true,
    ),

)
