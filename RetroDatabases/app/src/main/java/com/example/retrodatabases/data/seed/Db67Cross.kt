package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1375 and 1377: a Byzantine year in Python and C, and Chinese numerals for four years of the decade in Python and C. */
val db67Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "The Byzantine year of a date in Python and C",
        originalTitle = "Anno mundi",
        author = "Byzantine chronographers",
        year = 1377,
        summary = "The Byzantine era numbered years from the creation of the world and began its year on 1 September, and a Python program and a C program give the Byzantine year of three dates and print the same result.",
        details = "The rule is that the Byzantine year is the Christian year plus 5508 from January to August and plus 5509 from September to December, which gives 6961 for the fall of Constantinople on 29 May 1453. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### byzantine.py
            DATES = [(1377, 6, 21), (1378, 3, 27), (1378, 10, 1)]   # year, month, day
            for year, month, day in DATES:
                # the Byzantine year starts on 1 September, so September to December count one year further
                am = year + (5509 if month >= 9 else 5508)
                print(f"{year:04d}-{month:02d}-{day:02d} is AM {am}")
            ### byzantine.c
            #include <stdio.h>

            int main(void) {
                /* dates as year, month, day; the Byzantine year starts on 1 September */
                int years[] = {1377, 1378, 1378};
                int months[] = {6, 3, 10};
                int days[] = {21, 27, 1};
                for (int i = 0; i < 3; i++) {
                    int am = years[i] + (months[i] >= 9 ? 5509 : 5508);
                    printf("%04d-%02d-%02d is AM %d\n", years[i], months[i], days[i], am);
                }
                return 0;
            }
            ### run.sh
            python3 byzantine.py > python.txt
            cc -o byzantine_c byzantine.c
            ./byzantine_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.CROSS,
        section = "Numerals",
        title = "A year written in Chinese numerals in Python and C",
        originalTitle = "Chinese numerals",
        author = "Chinese clerks and mathematicians",
        year = 1375,
        summary = "Chinese numerals write a number with a digit word and a place word for thousand, hundred and ten, and a Python program and a C program write four years of the decade in that form and print the same result.",
        details = "A zero inside a number is read as ling, and zeros at the end are left out, which is the rule the two programs share. The example is written in romanized words, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### chinese.py
            DIGITS = ["ling", "yi", "er", "san", "si", "wu", "liu", "qi", "ba", "jiu"]
            PLACES = ["qian", "bai", "shi", ""]     # thousands, hundreds, tens, units

            def chinese_numeral(number):
                words = []
                pending = False
                for position, char in enumerate(str(number)):
                    value = int(char)
                    if value == 0:
                        pending = bool(words)       # a zero inside the number is read as ling
                        continue
                    if pending:
                        words.append("ling")
                        pending = False
                    words.append(DIGITS[value])
                    if PLACES[position]:
                        words.append(PLACES[position])
                return " ".join(words)

            for year in [1307, 1370, 1375, 1380]:
                print(year, chinese_numeral(year))
            ### chinese.c
            #include <stdio.h>

            static const char *DIGITS[] = {"ling", "yi", "er", "san", "si", "wu", "liu", "qi", "ba", "jiu"};
            static const char *PLACES[] = {"qian", "bai", "shi", ""};

            int main(void) {
                int years[] = {1307, 1370, 1375, 1380};
                for (int k = 0; k < 4; k++) {
                    char text[16];
                    const char *words[16];
                    int count = 0;
                    int pending = 0;
                    sprintf(text, "%d", years[k]);
                    for (int position = 0; position < 4; position++) {
                        int value = text[position] - '0';
                        if (value == 0) {
                            pending = count > 0;
                            continue;
                        }
                        if (pending) {
                            words[count++] = "ling";
                            pending = 0;
                        }
                        words[count++] = DIGITS[value];
                        if (PLACES[position][0] != '\0')
                            words[count++] = PLACES[position];
                    }
                    printf("%d", years[k]);
                    for (int i = 0; i < count; i++)
                        printf(" %s", words[i]);
                    printf("\n");
                }
                return 0;
            }
            ### run.sh
            python3 chinese.py > python.txt
            cc -o chinese_c chinese.c
            ./chinese_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
