package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1119: the sailor's guide rule from Zhu Yu's account, written in Python and in C. */
val db93Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.CROSS,
        section = "Navigation",
        title = "The sailor's guide rule in Python and C",
        originalTitle = "Pingzhou Ketan",
        author = "Zhu Yu",
        year = 1119,
        summary = "The guide rule from Zhu Yu's account is written once in Python and once in C, and a script checks that both programs print the same choice for each sky.",
        details = "The four cases are the same as in the Python guide of this batch: clear day, overcast day, clear night and overcast night. The run script compares the two outputs line by line, and the rule is a short summary of the account, not a translation of its wording.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### navigator.py
            def guide(daytime, overcast):
                if overcast:
                    return "magnetic needle"
                if daytime:
                    return "sun"
                return "stars"

            CASES = [
                ("clear day", True, False),
                ("overcast day", True, True),
                ("clear night", False, False),
                ("overcast night", False, True),
            ]
            for label, daytime, overcast in CASES:
                print(f"{label}: steer by the {guide(daytime, overcast)}")
            ### navigator.c
            #include <stdio.h>

            static const char *guide(int daytime, int overcast) {
                if (overcast)
                    return "magnetic needle";
                if (daytime)
                    return "sun";
                return "stars";
            }

            int main(void) {
                const char *labels[] = {"clear day", "overcast day", "clear night", "overcast night"};
                const int daytime[] = {1, 1, 0, 0};
                const int overcast[] = {0, 1, 0, 1};
                for (int i = 0; i < 4; i++) {
                    printf("%s: steer by the %s\n", labels[i], guide(daytime[i], overcast[i]));
                }
                return 0;
            }
            ### run.sh
            python3 navigator.py > python.txt
            cc -o navigator_c navigator.c
            ./navigator_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
