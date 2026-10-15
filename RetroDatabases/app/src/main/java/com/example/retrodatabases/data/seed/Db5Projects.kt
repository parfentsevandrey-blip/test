package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Built products of 2020 to 2026: finished programs with an explanation of how they work and instructions for running them. */
val db5Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.PROJECT,
        section = "Command-line tools",
        title = "A password generator with secrets",
        originalTitle = "Random password generator",
        author = "Written in the style of the 2021 security guides",
        year = 2021,
        summary = "A command-line tool that prints a random password from the operating system's secure random source.",
        details = "Python's secrets module draws from the operating system's cryptographic random source, unlike the random module, which is meant for simulations. " +
            "This tool builds a password from letters, digits and symbols, with the length given on the command line.",
        instructions = """
            1. Save the code as passgen.py.
            2. Run it: python3 passgen.py 20
            3. Each run prints a new password.
        """,
        example = """
            #!/usr/bin/env python3
            # A password generator built on the secrets module, which uses the operating system's secure random source.
            import secrets
            import string
            import sys

            ALPHABET = string.ascii_letters + string.digits + "!@#%^&*()-_=+"

            def make_password(length):
                return "".join(secrets.choice(ALPHABET) for _ in range(length))

            if __name__ == "__main__":
                length = int(sys.argv[1]) if len(sys.argv) > 1 else 16
                print(make_password(length))
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.PROJECT,
        section = "Browser extensions",
        title = "A Chrome extension in Manifest V3",
        originalTitle = "Manifest V3 extension",
        author = "Written in the style of the 2021 extension guides",
        year = 2021,
        summary = "A toolbar button that runs a script in the current page through a service worker, the Manifest V3 model.",
        details = "Chrome began accepting Manifest V3 extensions in 2021 and was phasing out Manifest V2 after that. Background pages gave way to service workers, " +
            "and page scripts run through the scripting API rather than through a persistent page. This extension shows an alert on the current tab.",
        instructions = """
            1. Put the two files below in one folder.
            2. Open chrome://extensions, switch on developer mode and choose Load unpacked.
            3. Click the toolbar button on any web page.
        """,
        example = """
            ### manifest.json
            {
              "manifest_version": 3,
              "name": "Page greeter",
              "version": "1.0",
              "action": { "default_title": "Greet this page" },
              "background": { "service_worker": "background.js" },
              "permissions": ["scripting", "activeTab"]
            }
            ### background.js
            chrome.action.onClicked.addListener(async (tab) => {
              await chrome.scripting.executeScript({
                target: { tabId: tab.id },
                func: () => alert("Hello from the service worker"),
              });
            });
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.PROJECT,
        section = "Microcontrollers",
        title = "A blinking LED on a Raspberry Pi Pico",
        originalTitle = "MicroPython blink",
        author = "Written in the style of the 2021 Pico tutorials",
        year = 2021,
        summary = "A MicroPython program that toggles the onboard LED every half second, the first program most Pico owners write.",
        details = "The Pico launched in January 2021 with MicroPython support, so a short script can drive the board's pins. " +
            "On the original Pico the onboard LED is on GPIO 25. The loop toggles it every half second and never returns.",
        instructions = """
            1. Flash the MicroPython firmware from the Raspberry Pi website.
            2. Copy the code to the board as main.py.
            3. Reset the board, and the LED starts to blink.
        """,
        example = """
            from machine import Pin
            import time

            led = Pin(25, Pin.OUT)
            while True:
                led.toggle()
                time.sleep(0.5)
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.PROJECT,
        section = "Automation",
        title = "A GitHub Action that comments on new pull requests",
        originalTitle = "Welcome comment workflow",
        author = "Written in the style of the 2021 GitHub Actions guides",
        year = 2021,
        summary = "A workflow that runs when a pull request opens and posts a welcome comment with the GitHub CLI.",
        details = "GitHub Actions runs the workflow files in a repository when an event happens. This one listens for opened pull requests, gives its job permission to write comments, " +
            "and calls the GitHub CLI with the pull request address.",
        instructions = """
            1. Save the file below as .github/workflows/welcome.yml.
            2. Commit and push it.
            3. Open a pull request and read the new comment.
        """,
        example = """
            name: welcome
            on:
              pull_request:
                types: [opened]
            jobs:
              comment:
                runs-on: ubuntu-latest
                permissions:
                  pull-requests: write
                steps:
                  - run: gh pr comment "<DOLLAR>PR_URL" --body "Thanks for the pull request!"
                    env:
                      PR_URL: <DOLLAR>{{ github.event.pull_request.html_url }}
                      GH_TOKEN: <DOLLAR>{{ github.token }}
        """,
    ),
)
