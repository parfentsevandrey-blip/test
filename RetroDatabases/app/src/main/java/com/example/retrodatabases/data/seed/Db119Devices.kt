package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 850 to 860: the Book of Ingenious Devices by the Banu Musa brothers, with its valves, automatic flute and self-trimming lamp. */
val db119Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "The Book of Ingenious Devices of the Banu Musa, with valves, an automatic flute and a self-trimming lamp",
        originalTitle = "Kitab al-Hiyal",
        author = "Muhammad, Ahmad and al-Hasan, sons of Musa ibn Shakir",
        year = 850,
        summary = "The Book of Ingenious Devices, written in Baghdad by three brothers and usually dated to about 850, describes about a hundred mechanical devices, among them valves, an automatic flute and a self-trimming lamp.",
        details = "Some sources credit the mechanical side of the work mainly to Ahmad, and the book draws partly on the work of Heron and Philo of Byzantium while adding the brothers' own designs. Its valves include plug, tap, conical and float types, and the float valve regulates a level without anyone adjusting it.",
    ),

)
