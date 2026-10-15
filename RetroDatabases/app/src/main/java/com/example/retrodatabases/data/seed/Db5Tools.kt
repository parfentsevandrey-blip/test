package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 2020 to 2026: AI assistants and agents, runtimes, package managers, testing and platform tools that developers used every day. */
val db5Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "Testing",
        title = "Playwright",
        originalTitle = "Playwright",
        author = "Microsoft",
        year = 2020,
        summary = "A browser-automation library that drives Chromium, Firefox and WebKit through one API in JavaScript, Python, Java and .NET.",
        details = "Microsoft released Playwright in 2020. Its tests run headless in continuous integration, and one API covers the three browser engines and four languages. " +
            "Some write-ups date version 1.0 to May 2020, so the year is the figure to rely on.",
        example = """
            import { test, expect } from '@playwright/test';

            test('home page has a heading', async ({ page }) => {
              await page.goto('https://example.com');
              await expect(page.getByRole('heading')).toBeVisible();
            });
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "Developer platforms",
        title = "Backstage",
        originalTitle = "Backstage developer portal",
        author = "Spotify, then the Cloud Native Computing Foundation",
        year = 2020,
        summary = "An open-source framework for building an internal developer portal with a software catalogue and plugins.",
        details = "Spotify made Backstage public on 16 March 2020. The Cloud Native Computing Foundation accepted it as a Sandbox project later that year. " +
            "Companies use its catalogue to list services, owners and documentation.",
        example = """
            apiVersion: backstage.io/v1alpha1
            kind: Component
            metadata:
              name: retro-api
              description: Catalogue service for the retro databases
            spec:
              type: service
              lifecycle: production
              owner: team-catalogue
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "AI assistants",
        title = "GitHub Copilot",
        originalTitle = "GitHub Copilot",
        author = "GitHub and OpenAI",
        year = 2022,
        summary = "An AI assistant inside the editor that suggests lines and whole functions while the developer types.",
        details = "GitHub Copilot began as a technical preview in 2021 and became generally available in June 2022. It suggests code from the comments and the surrounding file. " +
            "Its training on public code raised questions about licences.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "AI assistants",
        title = "ChatGPT",
        originalTitle = "ChatGPT",
        author = "OpenAI",
        year = 2022,
        summary = "A chat assistant that explains errors, drafts functions and writes tests for programmers after its public launch.",
        details = "OpenAI released ChatGPT to the public on 30 November 2022. Many programmers used it to explain stack traces, draft functions and write tests, " +
            "and it changed how teams asked each other for help.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "JavaScript runtimes",
        title = "Bun 1.0",
        originalTitle = "Bun 1.0",
        author = "Jarred Sumner and the Bun community",
        year = 2023,
        summary = "A JavaScript runtime with a bundled package manager, test runner and bundler, aimed at running Node.js code faster.",
        details = "Bun reached version 1.0 on 8 September 2023. It bundles a package manager, a test runner and a bundler in one binary, and it runs TypeScript without a build step. " +
            "Compatibility with Node.js was not complete at 1.0.",
        instructions = """
            1. Save the example as hello.ts.
            2. Run it: bun run hello.ts
            3. The output lists the greetings joined with a semicolon.
        """,
        example = """
            // Bun runs TypeScript directly, with no build step.
            const names: string[] = ["Ann", "Bob"];
            console.log(names.map((name) => `Hello, <DOLLAR>{name}`).join("; "));
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "Infrastructure",
        title = "OpenTofu",
        originalTitle = "OpenTofu (fork of Terraform)",
        author = "The OpenTF group, then the Linux Foundation",
        year = 2023,
        summary = "A fork of Terraform under the MPL licence, started after HashiCorp moved Terraform to the Business Source License.",
        details = "HashiCorp moved Terraform to the Business Source License on 10 August 2023. A group of companies announced a fork, which became OpenTofu under the Linux Foundation in September 2023. " +
            "Its first release, 1.6.0, came out in January 2024.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "AI assistants",
        title = "Cursor",
        originalTitle = "Cursor (Anysphere)",
        author = "Anysphere",
        year = 2023,
        summary = "An AI-first code editor forked from Visual Studio Code, with chat and inline edits that can read the whole project.",
        details = "Anysphere was founded in 2022 by four MIT classmates. It shipped Cursor in 2023 as a fork of Visual Studio Code, and its chat and inline edits could draw on the whole project. " +
            "It became one of the best-known AI editors.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "JavaScript runtimes",
        title = "Deno 2",
        originalTitle = "Deno 2.0",
        author = "Ryan Dahl and Deno Land",
        year = 2024,
        summary = "The second major version of Deno, which imports npm packages directly and reads package.json and node_modules.",
        details = "Deno 2 was released on 10 October 2024. Its npm: import specifier brings npm packages into a Deno program, and existing Node projects can run with fewer changes. " +
            "Large frameworks were not all compatible at launch.",
        example = """
            import express from "npm:express@4";
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "Python tooling",
        title = "uv",
        originalTitle = "uv (Astral)",
        author = "Astral",
        year = 2024,
        summary = "A Python package and project manager written in Rust, which installs packages and Python versions much faster than pip.",
        details = "Astral released uv in February 2024. It resolves and installs dependencies in parallel and can install Python versions too. " +
            "Its first version offered pip-style commands, so existing projects could adopt it without changing their files.",
        example = """
            uv init demo
            cd demo
            uv add requests
            uv run python main.py
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "AI coding agents",
        title = "Codex CLI",
        originalTitle = "OpenAI Codex CLI",
        author = "OpenAI",
        year = 2025,
        summary = "An open-source coding agent that runs in the terminal, reads and edits files and runs commands on the developer's machine.",
        details = "OpenAI released Codex CLI on 16 April 2025 alongside its o3 and o4-mini models. The tool runs locally in the terminal, and its approval modes control " +
            "which edits and commands run without a prompt.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "AI coding agents",
        title = "Gemini CLI",
        originalTitle = "Gemini CLI (preview)",
        author = "Google",
        year = 2025,
        summary = "An open-source terminal agent built on Gemini models, with search and file tools and support for MCP servers.",
        details = "Google opened a public preview of Gemini CLI on 25 June 2025 under the Apache 2.0 licence. It uses Gemini 2.5 Pro with a one-million-token context window, " +
            "and developers can connect MCP servers to it.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "AI coding agents",
        title = "Claude Code",
        originalTitle = "Claude Code",
        author = "Anthropic",
        year = 2025,
        summary = "A terminal agent from Anthropic that works inside a repository, answers questions and applies code changes with the developer's permission.",
        details = "Claude Code started as a research preview in February 2025 and became generally available in May 2025. It runs in the terminal on a repository, " +
            "and the developer approves commands and edits according to the permission mode in use.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "AI coding agents",
        title = "Copilot coding agent",
        originalTitle = "GitHub Copilot coding agent",
        author = "GitHub",
        year = 2025,
        summary = "An agent inside GitHub that takes an issue, works in a branch and opens a draft pull request for a person to review.",
        details = "GitHub unveiled the Copilot coding agent at Microsoft Build on 19 May 2025. A developer assigns an issue to Copilot, the agent pushes commits to a draft pull request, " +
            "and a person must approve the workflow runs before CI starts.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.TOOL,
        section = "Compilers",
        title = "TypeScript 7",
        originalTitle = "TypeScript 7.0 (Go port)",
        author = "Microsoft and the TypeScript team",
        year = 2026,
        summary = "The first TypeScript release with the compiler rewritten in Go, which Microsoft says builds several times faster.",
        details = "Microsoft announced the Go port in March 2025. TypeScript 6.0 was the last release written in JavaScript, and TypeScript 7.0 reached general availability on 8 July 2026. " +
            "Type checking is meant to behave as it did in the 6.x line.",
    ),
)
