package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1430 to 1440: the double hours of the day and the sexagenary year names, each in Python and C. */
val db61Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.CROSS,
        section = "Time",
        title = "The double hours of the day in Python and C",
        originalTitle = "Shichen",
        author = "The court reckoners of China and Korea",
        year = 1434,
        summary = "The twelve double hours of the day are computed in Python and in C, and a script checks that both programs print the same name for every hour.",
        details = "The two programs use the same integer rule for the double hour, which places hour h in slot (h + 1) // 2 of twelve. The run script compares the two outputs line by line, so a mismatch in either language would show at once.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### double_hours.py
            NAMES = ["ja", "chuk", "in", "myo", "jin", "sa", "o", "mi", "sin", "yu", "sul", "hae"]

            for hour in range(0, 24):
                index = ((hour + 1) // 2) % 12
                print(f"{hour:02d}:00 {NAMES[index]}")
            ### double_hours.c
            #include <stdio.h>

            static const char *NAMES[] = {"ja", "chuk", "in", "myo", "jin", "sa", "o", "mi", "sin", "yu", "sul", "hae"};

            int main(void) {
                for (int hour = 0; hour < 24; hour++) {
                    int index = ((hour + 1) / 2) % 12;
                    printf("%02d:00 %s\n", hour, NAMES[index]);
                }
                return 0;
            }
            ### run.sh
            python3 double_hours.py > python.txt
            cc -o double_hours_c double_hours.c
            ./double_hours_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.CROSS,
        section = "Calendar",
        title = "The sexagenary names of the 1430s in Python and C",
        originalTitle = "Ganzhi",
        author = "The calendar makers of China",
        year = 1430,
        summary = "The stem and branch names of the years 1430 to 1440 are computed in Python and in C, and a script checks that both programs print the same names.",
        details = "Both programs count from year 4, when the sixty-year cycle begins with jia-zi, and take the position modulo sixty. The run script compares the two outputs, so a mistake in either language would show as a difference.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### sexagenary.py
            STEMS = ["jia", "yi", "bing", "ding", "wu", "ji", "geng", "xin", "ren", "gui"]
            BRANCHES = ["zi", "chou", "yin", "mao", "chen", "si", "wu", "wei", "shen", "you", "xu", "hai"]

            for year in range(1430, 1441):
                index = (year - 4) % 60
                print(year, STEMS[index % 10] + "-" + BRANCHES[index % 12])
            ### sexagenary.c
            #include <stdio.h>

            static const char *STEMS[] = {"jia", "yi", "bing", "ding", "wu", "ji", "geng", "xin", "ren", "gui"};
            static const char *BRANCHES[] = {"zi", "chou", "yin", "mao", "chen", "si", "wu", "wei", "shen", "you", "xu", "hai"};

            int main(void) {
                for (int year = 1430; year <= 1440; year++) {
                    int index = (year - 4) % 60;
                    printf("%d %s-%s\n", year, STEMS[index % 10], BRANCHES[index % 12]);
                }
                return 0;
            }
            ### run.sh
            python3 sexagenary.py > python.txt
            cc -o sexagenary_c sexagenary.c
            ./sexagenary_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
