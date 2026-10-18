package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 935 to 940: a Japanese kana diary, an Old English battle poem, a skaldic poem and a Cordoba anthology. */
val db111Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.LANGUAGE,
        section = "Japan",
        title = "The Tosa Diary, a journal written in kana in the voice of a woman",
        originalTitle = "Tosa nikki",
        author = "Ki no Tsurayuki",
        year = 935,
        summary = "Ki no Tsurayuki's Tosa Diary, written in the kana syllabary about 935, follows a sea journey from Tosa province back to the capital in the voice of a woman.",
        details = "The diary records the voyage of 934 and 935 in short entries, with poems set among the prose, and it is one of the earliest major Japanese diaries written in kana rather than in Chinese characters. The author was one of the compilers of the Kokinshu, and about 935 is the usual date given for the diary.",
    ),

    entry(
        databaseId = 111,
        kind = Kind.LANGUAGE,
        section = "England",
        title = "The Battle of Brunanburh, an Old English victory poem entered under 937",
        originalTitle = "Brunanburh",
        author = "A poet of the Anglo-Saxon Chronicle",
        year = 937,
        summary = "The Old English poem on the battle of Brunanburh, entered under the year 937 of the Anglo-Saxon Chronicle, praises King Athelstan's victory in alliterative long lines.",
        details = "The poem names the peoples who fought against Athelstan, among them the Scots and a Norse king who came from Dublin, and its verse is built on alliterating stressed syllables in the Old English long line. Accounts differ on where the battle was fought and on how soon after 937 the poem was composed, since the surviving copies of the chronicle are later manuscripts.",
    ),

    entry(
        databaseId = 111,
        kind = Kind.LANGUAGE,
        section = "Iceland",
        title = "Hofudlausn, a skaldic poem of kennings composed by Egil Skallagrimsson in York",
        originalTitle = "Hofudlausn",
        author = "Egil Skallagrimsson",
        year = 936,
        summary = "Egil Skallagrimsson's Hofudlausn, the head-ransom, was composed in York about 936 in skaldic metre and asked King Eric Bloodaxe to spare his life.",
        details = "The poem is built from kennings, two-word figures such as whale-road for the sea, so its meaning is carried by a code that the audience had to unpack. The date rests on the saga account of Egil, which was written down in the thirteenth century and gives 936 for the poem's setting at Eric's court in York.",
        rare = true,
    ),

    entry(
        databaseId = 111,
        kind = Kind.LANGUAGE,
        section = "Spain",
        title = "The Unique Necklace, an Arabic anthology compiled in Cordoba by Ibn Abd Rabbih",
        originalTitle = "Al-Iqd al-Farid",
        author = "Ibn Abd Rabbih",
        year = 940,
        summary = "Ibn Abd Rabbih's Al-Iqd al-Farid, an Arabic anthology of poetry, sayings and history, is usually dated to about 940, around the year of its author's death in Cordoba.",
        details = "The work is arranged in books named after precious stones, and it gathers verses, proverbs, speeches and historical anecdotes into one collection for educated readers. The date about 940 is the usual one, so the work sits at the end of this decade rather than at a date that can be fixed exactly.",
        rare = true,
    ),

)
