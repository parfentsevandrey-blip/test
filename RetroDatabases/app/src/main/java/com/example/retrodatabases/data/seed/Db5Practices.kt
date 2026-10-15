package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 2020 to 2026: working habits, security rules, licensing, supply-chain habits and the new AI-assisted ways of working. */
val db5Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Version control",
        title = "Default branch named main",
        originalTitle = "init.defaultBranch",
        author = "The Git project and GitHub",
        year = 2020,
        summary = "New repositories start on main instead of master, because Git 2.28 made the first branch name configurable.",
        details = "Git 2.28 added the init.defaultBranch setting in July 2020, and GitHub began creating new repositories with main from 1 October 2020. " +
            "Existing repositories kept their names, and many teams renamed their branches over the following years.",
        instructions = """
            1. Save the commands below as default.sh.
            2. Run it: sh default.sh
            3. The last line prints main.
        """,
        example = """
            git init -q -b main demo
            cd demo
            git branch --show-current
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Security",
        title = "Zero trust architecture",
        originalTitle = "NIST SP 800-207",
        author = "NIST (Rose, Borchert, Mitchell and Connelly)",
        year = 2020,
        summary = "A design in which every access request is checked, and no network location is trusted by default.",
        details = "NIST published SP 800-207 in August 2020. It describes a policy engine that decides each request from the identity, the device and the context. " +
            "The document became a common reference for security programmes.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Licences",
        title = "Docker Desktop licence change",
        originalTitle = "Docker Desktop subscription rules",
        author = "Docker, Inc.",
        year = 2021,
        summary = "Large companies had to buy a Docker Desktop subscription from 31 August 2021, while small businesses and individuals kept it free.",
        details = "Companies with 250 or more employees, or more than 10 million dollars in revenue, needed a paid plan. They had until 31 January 2022 to move to one. " +
            "Some teams began looking at alternatives such as Podman and Colima.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Supply chain",
        title = "Software bill of materials mandate",
        originalTitle = "Executive Order 14028",
        author = "The US government",
        year = 2021,
        summary = "A US executive order asked that software sold to federal agencies come with a software bill of materials.",
        details = "Executive Order 14028, on improving the nation's cybersecurity, was signed on 12 May 2021. It asked for a software bill of materials, a list of components, " +
            "for software sold to federal agencies. The requirement spread to other buyers and to many procurement rules.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Security incidents",
        title = "Log4Shell response",
        originalTitle = "CVE-2021-44228",
        author = "The Apache Logging project and the security community",
        year = 2021,
        summary = "The December 2021 flaw in the Log4j logging library, which many teams found only through their dependencies.",
        details = "CVE-2021-44228 was disclosed around 10 December 2021 and affected Log4j 2 versions that performed lookups in logged messages. " +
            "Many teams learned that Log4j was inside their software only when they searched their dependencies, which made inventories and scanners part of everyday work.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Authentication",
        title = "Passkeys",
        originalTitle = "Passkeys (WebAuthn)",
        author = "The FIDO Alliance, Apple, Google and Microsoft",
        year = 2022,
        summary = "Sign-in with a key pair stored on the device, so no password is typed and no secret is stored on the server.",
        details = "In May 2022 Apple, Google and Microsoft announced support for passkeys built on the FIDO Alliance and W3C WebAuthn standards. " +
            "The private key stays on the device, which makes phishing much harder, and developers add passkey sign-in with the same WebAuthn APIs.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Accessibility",
        title = "WCAG 2.2",
        originalTitle = "Web Content Accessibility Guidelines 2.2",
        author = "The W3C Web Accessibility Initiative",
        year = 2023,
        summary = "The accessibility guideline update that added criteria for focus visibility, dragging and target size.",
        details = "WCAG 2.2 became a W3C Recommendation on 5 October 2023. It adds nine success criteria, including focus appearance and minimum target size, " +
            "and it keeps the earlier conformance levels, so existing audits still apply.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Supply chain",
        title = "Trusted publishing",
        originalTitle = "PyPI Trusted Publishers",
        author = "The PyPI maintainers",
        year = 2023,
        summary = "Publish packages from CI with short-lived tokens from an identity provider, so no long-lived upload token is stored.",
        details = "PyPI introduced Trusted Publishers in April 2023, built on OpenID Connect. GitHub Actions was the only supported provider until April 2024, " +
            "when GitLab CI/CD, Google Cloud and ActiveState were added. Projects no longer keep an upload token as a secret.",
        example = """
            permissions:
              id-token: write
            steps:
              - uses: pypa/gh-action-pypi-publish@release/v1
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Licensing",
        title = "Forks after licence changes",
        originalTitle = "OpenTofu and Valkey",
        author = "The open-source communities",
        year = 2024,
        summary = "When a company changes a project's licence, the community sometimes forks it under a new name, as with OpenTofu and Valkey.",
        details = "Terraform moved to a source-available licence in August 2023, and OpenTofu was announced as a fork soon after. Redis changed its licence in March 2024, " +
            "and the Valkey fork began the same spring under the Linux Foundation. Forks gave users a choice of maintainers and licences.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "Supply chain",
        title = "XZ Utils backdoor",
        originalTitle = "CVE-2024-3094",
        author = "An attacker using the name Jia Tan, and the xz maintainers",
        year = 2024,
        summary = "A backdoor planted over about two years in the xz compression library, caught by a Microsoft engineer who noticed slow SSH logins.",
        details = "Andres Freund noticed unusually slow SSH logins and found the backdoor in xz versions 5.6.0 and 5.6.1, which he reported on 29 March 2024. " +
            "It was rated CVSS 10.0 (CVE-2024-3094). The attacker had gained the project's trust gradually, which made the case a lesson about maintainer security.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.PRACTICE,
        section = "AI-assisted work",
        title = "Vibe coding",
        originalTitle = "Vibe coding (term)",
        author = "Andrej Karpathy, then the wider community",
        year = 2025,
        summary = "Building software by describing it to an AI and accepting the output without reading the code closely.",
        details = "Karpathy coined the term in a post on 2 February 2025. It spread quickly and drifted: some people use it for any AI-assisted coding, " +
            "while others reserve it for code nobody reviews. Teams argued about where it was safe to use.",
    ),
)
