package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 180 to 190: the Yellow Turban rising of 184 and the Yamatai kingship of Himiko, dated by the accounts to 189 or 190. */
val db186Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.SYSTEM,
        section = "Han China",
        title = "The Yellow Turban rising of 184, followers of the Way of Great Peace in thirty-six sections",
        originalTitle = "Huangjin zhi luan",
        author = "Zhang Jue and his followers",
        year = 184,
        summary = "The Yellow Turbans rose in 184 under Zhang Jue, a leader of the Way of Great Peace, and their followers were grouped into thirty-six sections.",
        details = "The rising began in the jiazi year of 184, and its slogan promised great fortune under heaven in that year. Accounts differ on when the revolt ended, with dates from 185 to 205 given, so this record dates the rising by its start.",
    ),

    entry(
        databaseId = 186,
        kind = Kind.SYSTEM,
        section = "Japan",
        title = "The Yamatai kingship of Himiko, a woman ruler chosen after long war in Wa",
        originalTitle = "Himiko",
        author = "Chen Shou, compiler of the Records of the Three Kingdoms",
        year = 189,
        summary = "Himiko became ruler of a realm called Yamatai in Wa after years of war, a reign that accounts date to 189 or 190.",
        details = "The Wei zhi, completed in 297, is the main early text for her and does not date her installation to a year. Accounts differ between 189 and 190, and this record uses 189, the earlier date.",
    ),

)
