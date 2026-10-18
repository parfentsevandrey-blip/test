package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1120 to 1130: a field measured in perches turned into acres, in Python and in C. */
val db92Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.CROSS,
        section = "Land",
        title = "Acres from square perches in Python and C",
        originalTitle = "Square perches of a field",
        author = "No single author",
        year = 1125,
        summary = "A field measured in perches becomes acres when its square perches are divided by 160, and a script checks that the Python and C programs print the same figures.",
        details = "The example uses two illustrative fields of 40 by 64 perches and 30 by 45 perches. The English acre holds 160 square perches, a fixed relation between the two units, and the year 1125 is the working date of Hugh's Practica geometriae, a manual of measuring land, though the record makes no claim that the book uses acres.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### acres.py
            FIELDS = [(40, 64), (30, 45)]   # field sides in perches, illustrative
            for length, width in FIELDS:
                square_perches = length * width
                acres = square_perches // 160   # 160 square perches to the acre
                rest = square_perches % 160
                print(f"{length} by {width} perches: {square_perches} square perches = {acres} acres and {rest} square perches")
            ### acres.c
            #include <stdio.h>

            int main(void) {
                int sides[2][2] = {{40, 64}, {30, 45}};   /* field sides in perches, illustrative */
                for (int i = 0; i < 2; i++) {
                    int length = sides[i][0];
                    int width = sides[i][1];
                    int square_perches = length * width;
                    int acres = square_perches / 160;     /* 160 square perches to the acre */
                    int rest = square_perches % 160;
                    printf("%d by %d perches: %d square perches = %d acres and %d square perches\n",
                           length, width, square_perches, acres, rest);
                }
                return 0;
            }
            ### run.sh
            python3 acres.py > python.txt
            cc -o acres_c acres.c
            ./acres_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
