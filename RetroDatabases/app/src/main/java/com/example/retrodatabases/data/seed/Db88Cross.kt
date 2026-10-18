package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1170: the water held by a tide mill's pond in one tide, computed in Python and C with illustrative numbers. */
val db88Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.CROSS,
        section = "Water power",
        title = "The water held by a tide mill's pond in Python and C",
        originalTitle = "Tide pond",
        author = "Medieval millers",
        year = 1170,
        summary = "A tide mill's pond holds water from one tide, and a script checks that the Python and C programs print the same volume and milling hours.",
        details = "The pond area, the head of water and the rate of release are illustrative numbers, not measurements of the Woodbridge mill, and the run script compares the two outputs line by line. The example multiplies area by head to get the volume and divides by the release rate to get the hours of milling.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### pond.py
            AREA = 500       # square metres of pond, illustrative
            HEAD = 2         # metres of water held on the ebb, illustrative
            RATE = 100       # cubic metres let out each hour, illustrative
            volume = AREA * HEAD
            print("water held:", volume, "cubic metres")
            print("hours of milling:", volume // RATE)
            print("water in two tides:", 2 * volume, "cubic metres")
            ### pond.c
            #include <stdio.h>

            int main(void) {
                const int area = 500;   /* square metres of pond, illustrative */
                const int head = 2;     /* metres of water held on the ebb, illustrative */
                const int rate = 100;   /* cubic metres let out each hour, illustrative */
                int volume = area * head;
                printf("water held: %d cubic metres\n", volume);
                printf("hours of milling: %d\n", volume / rate);
                printf("water in two tides: %d cubic metres\n", 2 * volume);
                return 0;
            }
            ### run.sh
            python3 pond.py > python.txt
            cc -o pond_c pond.c
            ./pond_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
