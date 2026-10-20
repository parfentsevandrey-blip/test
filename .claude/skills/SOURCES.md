# Установленные скилы

| Скил | Откуда | Коммит | Лицензия | Зачем |
|---|---|---|---|---|
| pdf-design | github.com/jamditis/claude-skills-journalism, папка pdf-design | e3e2172 | MIT | вёрстка отчёта в HTML и печать в PDF через Chromium; сетка страницы, правило «не урезать поля — урезать текст», просмотр страниц |
| frontend-design | github.com/anthropics/skills, skills/frontend-design | dbd4588 | Apache 2.0 | дисциплина дизайна: своя палитра и типографика вместо шаблонных приёмов, самопроверка по скриншотам |
| investment-memo-generator | github.com/travisjneuman/.claude, skills/investment-memo-generator | 7a2b5ca | MIT | каркас записки для инвестора: самостоятельная первая страница, ясная рекомендация, риски с мерами |
| flip-brief | этот проект | — | — | правила нового флип-отчёта и запуск конвейера flipfinder |

У pdf-design не скопирован шаблон templates/democracy-day-proposal.html (172 КБ, чужой
фирменный стиль) — в проекте используется свой шаблон flipfinder/templates/brief.html.

Рассмотрены и не установлены: real-estate-analyzer (travisjneuman) — рассчитан на
американскую жилую недвижимость; pdf (anthropics) и docx — уже доступны в сессии как
anthropic-skills:pdf и anthropic-skills:docx; canvas-design и theme-factory — для плакатов
и слайдов, не для отчётов.
