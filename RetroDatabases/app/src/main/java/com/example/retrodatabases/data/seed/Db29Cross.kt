package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1750 to 1760: a difference tone and a lighthouse's flashes, each in Python and C. */
val db29Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.CROSS,
        section = "Acoustics",
        title = "Tartini's difference tones in Python and C",
        originalTitle = "Trattato di musica secondo la vera scienza dell'armonia",
        author = "Giuseppe Tartini",
        year = 1754,
        summary = "Two tones of 300 and 400 vibrations a second give a lower difference tone of 100 vibrations, and two programs in different languages print the same lines.",
        details = "Tartini described the lower tone that two sounding notes produce, in his treatise of 1754. The pair of tones is an illustrative choice, and the Python and C programs print the same two lines, the difference tone and the ratio of the pair.",
        instructions = """
            1. Save the first program as tartini.py and the second as tartini.c.
            2. Run them: python3 tartini.py, then gcc -o tartini tartini.c && ./tartini
        """,
        example = """
            ### tartini.py
            from fractions import Fraction as F

            f1, f2 = 300, 400      # two tones in vibrations a second, an illustrative pair
            print("difference tone:", f2 - f1, "Hz")
            print("ratio of the pair:", F(f2, f1))
            ### tartini.c
            #include <stdio.h>

            int main(void) {
                int f1 = 300; /* first tone, in vibrations a second, an illustrative pair */
                int f2 = 400; /* second tone */
                int a = f1, b = f2;
                while (b != 0) {
                    int t = a % b;
                    a = b;
                    b = t;
                }
                printf("difference tone: %d Hz\n", f2 - f1);
                printf("ratio of the pair: %d/%d\n", f2 / a, f1 / a);
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.CROSS,
        section = "Lighthouses",
        title = "Flashes of a lighthouse counted in Python and C",
        originalTitle = "Eddystone Lighthouse",
        author = "John Smeaton",
        year = 1759,
        summary = "A light that flashes every fifteen seconds shows 240 flashes in an hour, and the Python and C programs count them the same way.",
        details = "Smeaton's Eddystone light of 1759 was a lighthouse on an exposed reef. The period of fifteen seconds is illustrative and is not the rhythm of the historical light, and both programs print the counts for one and three hours.",
        instructions = """
            1. Save the first program as flashes.py and the second as flashes.c.
            2. Run them: python3 flashes.py, then gcc -o flashes flashes.c && ./flashes
        """,
        example = """
            ### flashes.py
            period_seconds = 15   # one flash every 15 seconds, an illustrative rhythm
            for hours in (1, 3):
                seconds = hours * 3600
                print(f"flashes in {hours} hour{'' if hours == 1 else 's'}: {seconds // period_seconds}")
            ### flashes.c
            #include <stdio.h>

            int main(void) {
                int period_seconds = 15; /* one flash every 15 seconds, an illustrative rhythm */
                int hours[2] = {1, 3};
                for (int i = 0; i < 2; i++) {
                    int seconds = hours[i] * 3600;
                    printf("flashes in %d hour%s: %d\n", hours[i], hours[i] == 1 ? "" : "s", seconds / period_seconds);
                }
                return 0;
            }
        """,
    ),

)
