package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 994: the latitude of Rayy read by al-Khujandi, converted in Python and in C. */
val db105Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.CROSS,
        section = "Astronomy",
        title = "Rayy's latitude of 35 degrees 34 minutes 38.45 seconds in decimal degrees, in Python and C",
        originalTitle = "Rayy's latitude of 35 degrees 34 minutes 38.45 seconds in decimal degrees, in Python and C",
        author = "Abu Mahmud al-Khujandi",
        year = 994,
        summary = "A Python program and a C program turn the latitude of Rayy read by al-Khujandi in 994 into 35.5773 decimal degrees, and a script tests that both agree.",
        details = "The latitude comes from the same 994 readings as the tilt, and both programs convert the same minutes and seconds with the same formula, so the run tests the arithmetic rather than the readings. Each program prints four decimals, and the script compares the two outputs.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### latitude.py
            deg, minutes, seconds = 35, 34, 38.45
            latitude = deg + minutes / 60 + seconds / 3600
            print(f"{latitude:.4f}")
            ### latitude.c
            #include <stdio.h>

            int main(void) {
                double deg = 35, minutes = 34, seconds = 38.45;
                double latitude = deg + minutes / 60 + seconds / 3600;
                printf("%.4f\n", latitude);
                return 0;
            }
            ### run.sh
            python3 latitude.py > python.txt
            cc -o latitude_c latitude.c
            ./latitude_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
