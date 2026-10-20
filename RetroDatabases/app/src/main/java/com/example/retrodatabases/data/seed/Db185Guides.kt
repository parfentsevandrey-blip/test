package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 190 to 200: the six channels of the Shanghan lun, sorted into three yang and three yin. */
val db185Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.GUIDE,
        section = "Medicine",
        title = "How the six channels of cold damage split into three yang and three yin",
        originalTitle = "Liu jing, the six channels",
        author = "Zhang Zhongjing",
        year = 200,
        summary = "The six channels of the Shanghan lun, a Han treatise on cold damage, split into three yang and three yin, and a short program lists them in order with their groups.",
        details = "The treatise is usually dated to about 200 and is attributed to Zhang Zhongjing, but the received text is traditionally credited to editing by Wang Shuhe in the third century, so accounts differ on how much of it is original. The yang channels are greater yang, yangming and lesser yang, and the yin channels are greater yin, lesser yin and terminal yin, and the example counts each group so the output can be checked by hand.",
        instructions = """
            1. Save the example as channels.py.
            2. Run it: python3 channels.py
        """,
        example = """
            CHANNELS = [
                ("taiyang", "yang"),
                ("yangming", "yang"),
                ("shaoyang", "yang"),
                ("taiyin", "yin"),
                ("shaoyin", "yin"),
                ("jueyin", "yin"),
            ]
            for position, (name, group) in enumerate(CHANNELS, start=1):
                print(position, name, group)
            print("yang channels:", sum(1 for _, group in CHANNELS if group == "yang"))
            print("yin channels:", sum(1 for _, group in CHANNELS if group == "yin"))
        """,
        rare = true,
    ),

)
