package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 952: decimal fractions multiplied by scaling to whole numbers, in Python and in C. */
val db109Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.CROSS,
        section = "Fractions",
        title = "Multiplying decimal fractions by scaling to whole numbers, in Python and C",
        originalTitle = "Multiplying decimal fractions by scaling to whole numbers, in Python and C",
        author = "Abu'l-Hasan al-Uqlidisi",
        year = 952,
        summary = "Python and C programs multiply decimal fractions by scaling both to whole numbers and then placing the point, and a script checks that both print the same four products.",
        details = "The decimal fractions of al-Uqlidisi's book are the earliest direct treatment known, but this run uses only the plain method of scaling to whole numbers and no passage of the book. Both programs print 2.25 x 1.5 = 3.375, 0.5 x 0.25 = 0.125, 1.2 x 3.4 = 4.08 and 0.75 x 0.8 = 0.600, so the run tests the arithmetic.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### decimal_products.py
            def parse(text):
                whole, _, frac = text.partition(".")
                return int(whole + frac), len(frac)


            def show(value, places):
                digits = str(value).rjust(places + 1, "0")
                return digits[:-places] + "." + digits[-places:] if places else digits


            for a_text, b_text in [("2.25", "1.5"), ("0.5", "0.25"), ("1.2", "3.4"), ("0.75", "0.8")]:
                a, pa = parse(a_text)
                b, pb = parse(b_text)
                print(f"{a_text} x {b_text} = {show(a * b, pa + pb)}")
            ### decimal_products.c
            #include <stdio.h>
            #include <string.h>

            /* Read a decimal fraction such as 2.25: its digits as one whole number, and the places after the point. */
            static long parse(const char *text, int *places) {
                long value = 0;
                int after = 0;
                *places = 0;
                for (const char *p = text; *p; p++) {
                    if (*p == '.') {
                        after = 1;
                    } else {
                        value = value * 10 + (*p - '0');
                        if (after) (*places)++;
                    }
                }
                return value;
            }

            /* Print a whole number with the decimal point placed the given number of digits from the right. */
            static void show(long value, int places) {
                char digits[32];
                char padded[40];
                snprintf(digits, sizeof digits, "%ld", value);
                int need = places + 1;
                int len = (int)strlen(digits);
                int pad = need > len ? need - len : 0;
                for (int i = 0; i < pad; i++) padded[i] = '0';
                strcpy(padded + pad, digits);
                len = (int)strlen(padded);
                if (places == 0) {
                    printf("%s", padded);
                } else {
                    printf("%.*s.%s", len - places, padded, padded + len - places);
                }
            }

            int main(void) {
                const char *as[] = {"2.25", "0.5", "1.2", "0.75"};
                const char *bs[] = {"1.5", "0.25", "3.4", "0.8"};
                for (int k = 0; k < 4; k++) {
                    int pa = 0, pb = 0;
                    long a = parse(as[k], &pa);
                    long b = parse(bs[k], &pb);
                    printf("%s x %s = ", as[k], bs[k]);
                    show(a * b, pa + pb);
                    printf("\n");
                }
                return 0;
            }
            ### run.sh
            python3 decimal_products.py > python.txt
            cc -o decimal_products_c decimal_products.c
            ./decimal_products_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
