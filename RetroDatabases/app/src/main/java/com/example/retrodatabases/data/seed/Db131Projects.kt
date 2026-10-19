package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 730 to 740: the six ministries and twenty-four bureaus of Tang government, as the handbook of 738 gives them, listed and counted by a short program. */
val db131Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.PROJECT,
        section = "Tang",
        title = "The six ministries and twenty-four bureaus of Tang government, from the Liudian of 738, listed in a short program",
        originalTitle = "Tang liudian",
        author = "Zhang Jiuling and others (attributed)",
        year = 738,
        summary = "The six ministries of Tang government, each with four bureaus, make twenty-four bureaus in all, and a short program lists and counts them from the handbook of 738.",
        details = "The handbook sets out the six ministries of the Department of State Affairs, and each ministry's first bureau bears the ministry's own name. The count uses the usual date of 738 for the handbook, and the figures describe the ministry structure, not the staff of any one year.",
        rare = true,
        instructions = """
            1. Save the example as ministries.py.
            2. Run it: python3 ministries.py
        """,
        example = """
            MINISTRIES = {
                "Personnel": ["Libu", "Sifeng", "Sixun", "Kaogong"],
                "Revenue": ["Hubu", "Duzhi", "Jinbu", "Cangbu"],
                "Rites": ["Libu", "Cibu", "Shanbu", "Zhuke"],
                "War": ["Bingbu", "Zhifang", "Jiabu", "Kubu"],
                "Punishments": ["Xingbu", "Duguan", "Bibu", "Simen"],
                "Works": ["Gongbu", "Tuntian", "Yubu", "Shuibu"],
            }

            for ministry, bureaus in MINISTRIES.items():
                print(ministry + ":", len(bureaus), "bureaus")
            print("ministries:", len(MINISTRIES))
            print("bureaus in all:", sum(len(bureaus) for bureaus in MINISTRIES.values()))
            print("first bureau of the Personnel ministry:", MINISTRIES["Personnel"][0])
        """,
    ),

)
