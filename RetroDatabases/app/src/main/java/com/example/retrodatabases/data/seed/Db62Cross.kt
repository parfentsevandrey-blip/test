package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1420 to 1430: an illustrative tribute shared among three partners, and a binary search of a sorted catasto list, each in Python and C. */
val db62Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.CROSS,
        section = "Tribute",
        title = "Sharing a tribute of 1000 among three partners in Python and C",
        originalTitle = "Triple Alliance",
        author = "Tenochtitlan, Texcoco and Tlacopan",
        year = 1428,
        summary = "The Triple Alliance of 1428 joined three cities, and a script checks that the Python and C programs share an illustrative tribute of 1000 the same way.",
        details = "The shares in the example are illustrative and are not the historical tribute of the alliance. The program gives the first partner the extra unit when the tribute does not divide evenly, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### tribute.py
            tribute = 1000
            partners = ["Tenochtitlan", "Texcoco", "Tlacopan"]
            base, extra = divmod(tribute, len(partners))
            for i, name in enumerate(partners):
                share = base + (1 if i < extra else 0)
                print(name, share)
            ### tribute.c
            #include <stdio.h>

            int main(void) {
                const char *partners[] = {"Tenochtitlan", "Texcoco", "Tlacopan"};
                int tribute = 1000, n = 3;
                int base = tribute / n, extra = tribute % n;
                for (int i = 0; i < n; i++) {
                    int share = base + (i < extra ? 1 : 0);
                    printf("%s %d\n", partners[i], share);
                }
                return 0;
            }
            ### run.sh
            python3 tribute.py > python.txt
            cc -o tribute_c tribute.c
            ./tribute_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.CROSS,
        section = "Tax records",
        title = "Finding a household in a sorted catasto list in Python and C",
        originalTitle = "Catasto",
        author = "Florentine tax officers",
        year = 1427,
        summary = "A sorted list of household names in the catasto of 1427 can be searched by halving it, and a script checks that the Python and C programs find the same position.",
        details = "The names in the example are illustrative and are not taken from the catasto's returns. Both programs halve the list at each step, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### find_household.py
            names = ["Alberti", "Bardi", "Cambi", "Donati", "Medici", "Pazzi", "Strozzi"]
            target = "Medici"
            lo, hi = 0, len(names) - 1
            found = None
            while lo <= hi:
                mid = (lo + hi) // 2
                if names[mid] == target:
                    found = mid
                    break
                if names[mid] < target:
                    lo = mid + 1
                else:
                    hi = mid - 1
            print(target, "is at position", found)
            ### find_household.c
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                const char *names[] = {"Alberti", "Bardi", "Cambi", "Donati", "Medici", "Pazzi", "Strozzi"};
                const char *target = "Medici";
                int lo = 0, hi = 6, found = -1;
                while (lo <= hi) {
                    int mid = (lo + hi) / 2;
                    int cmp = strcmp(names[mid], target);
                    if (cmp == 0) {
                        found = mid;
                        break;
                    }
                    if (cmp < 0) lo = mid + 1;
                    else hi = mid - 1;
                }
                printf("%s is at position %d\n", target, found);
                return 0;
            }
            ### run.sh
            python3 find_household.py > python.txt
            cc -o find_household_c find_household.c
            ./find_household_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
