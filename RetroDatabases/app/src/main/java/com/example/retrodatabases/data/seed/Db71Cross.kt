package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1330 to 1336: Roman day labels for March 1336 and the epacts of the 1330s, each in Python and C. */
val db71Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "Roman day labels for March 1336 in Python and C",
        originalTitle = "Kalends, Nones and Ides",
        author = "Medieval notaries and chanceries",
        year = 1336,
        summary = "A day of the month was named by counting back to the Kalends, Nones or Ides, and a script checks that the Python and C programs print the same labels for March 1336.",
        details = "The labels follow the inclusive count used in medieval charters, and the example prints nine days of March 1336 in each language. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### roman_days.py
            DAYS = 31
            NONES = 7
            IDES = 15
            ROMAN = [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]

            def roman(n):
                out = ""
                for value, symbol in ROMAN:
                    while n >= value:
                        out += symbol
                        n -= value
                return out

            def label(day):
                if day == 1:
                    return "Kal. Mart."
                if day < NONES:
                    return "a.d. " + roman(NONES - day + 1) + " Non. Mart."
                if day == NONES:
                    return "Non. Mart."
                if day < IDES:
                    return "a.d. " + roman(IDES - day + 1) + " Id. Mart."
                if day == IDES:
                    return "Id. Mart."
                return "a.d. " + roman(DAYS + 2 - day) + " Kal. Apr."

            for day in [1, 2, 6, 7, 8, 14, 15, 16, 31]:
                print(day, "March 1336:", label(day))
            ### roman_days.c
            #include <stdio.h>
            #include <string.h>

            static void roman(int n, char *out) {
                static const int values[] = {10, 9, 5, 4, 1};
                static const char *symbols[] = {"X", "IX", "V", "IV", "I"};
                out[0] = '\0';
                for (int i = 0; i < 5; i++) {
                    while (n >= values[i]) {
                        strcat(out, symbols[i]);
                        n -= values[i];
                    }
                }
            }

            static void label(int day, char *out) {
                const int days = 31;
                const int nones = 7;
                const int ides = 15;
                char numeral[16];
                if (day == 1) {
                    strcpy(out, "Kal. Mart.");
                } else if (day < nones) {
                    roman(nones - day + 1, numeral);
                    sprintf(out, "a.d. %s Non. Mart.", numeral);
                } else if (day == nones) {
                    strcpy(out, "Non. Mart.");
                } else if (day < ides) {
                    roman(ides - day + 1, numeral);
                    sprintf(out, "a.d. %s Id. Mart.", numeral);
                } else if (day == ides) {
                    strcpy(out, "Id. Mart.");
                } else {
                    roman(days + 2 - day, numeral);
                    sprintf(out, "a.d. %s Kal. Apr.", numeral);
                }
            }

            int main(void) {
                const int dates[] = {1, 2, 6, 7, 8, 14, 15, 16, 31};
                char text[64];
                for (int i = 0; i < 9; i++) {
                    label(dates[i], text);
                    printf("%d March 1336: %s\n", dates[i], text);
                }
                return 0;
            }
            ### run.sh
            python3 roman_days.py > python.txt
            cc -o roman_c roman_days.c
            ./roman_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.CROSS,
        section = "Computus",
        title = "The epact of each year of the 1330s in Python and C",
        originalTitle = "Epact",
        author = "Medieval computists",
        year = 1332,
        summary = "The epact of a year is found from its golden number, and a script checks that the Python and C programs print the same epact for each year from 1330 to 1340.",
        details = "The golden number is the place of the year in the nineteen-year cycle, and the epact follows from it by a common formula. The run script compares the two outputs line by line, and the expected values run from 0 to 28.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### epact.py
            for year in range(1330, 1341):
                golden = year % 19 + 1
                print(year, (11 * (golden - 1)) % 30)
            ### epact.c
            #include <stdio.h>

            int main(void) {
                for (int year = 1330; year <= 1340; year++) {
                    int golden = year % 19 + 1;
                    printf("%d %d\n", year, (11 * (golden - 1)) % 30);
                }
                return 0;
            }
            ### run.sh
            python3 epact.py > python.txt
            cc -o epact_c epact.c
            ./epact_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
