package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 220 to 230: Chen Qun's nine-rank grading of candidates for office and Ardashir I's victory that founded Sasanian rule. */
val db182Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.SYSTEM,
        section = "Office",
        title = "Chen Qun's nine-rank system for grading candidates for office",
        originalTitle = "Jiupin zhongzheng zhi",
        author = "Chen Qun",
        year = 220,
        summary = "Chen Qun's nine-rank system placed candidates for office in nine grades on the reports of local raters, and it was set up in the Wei state in 220.",
        details = "Chen Qun proposed the system in the year Cao Pi took the throne of Wei, and local officials graded the candidates of each commandery in nine ranks. Histories often read the grades through family standing, which is why the system is linked with the great families, and the Sui period later replaced it with examinations.",
        rare = true,
    ),

    entry(
        databaseId = 182,
        kind = Kind.SYSTEM,
        section = "Persia",
        title = "Ardashir I's victory at Hormizdagan and the founding of Sasanian rule",
        originalTitle = "Shahanshah",
        author = "Ardashir I",
        year = 224,
        summary = "Ardashir I defeated the Arsacid king Artabanus IV at Hormizdagan in 224, and his victory opened the Sasanian rule that governed Persia until the mid-seventh century.",
        details = "The battle is dated 28 April 224 in one account, and its site is not firmly identified. Accounts differ on the crowning at Ctesiphon, given as 226 in one account and 227 in another, and after the victory Ardashir took the title shahanshah, King of Kings.",
    ),

)
