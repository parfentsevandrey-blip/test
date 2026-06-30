#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
run_report.py — оркестратор «одной командой» для еженедельного отчёта по рынку
недвижимости Нидерландов.

Запускает весь конвейер из одного входного JSON и единого набора путей:

    1. загрузка данных     (JSON → dict)
    2. валидация контракта  (validate.validate, если модуль доступен)
    3. сборка Word          (generate_report.build_report → reports/<week_end>_…_RU.docx)
    4. HTML-превью          (preview_html.render)
    5. тело письма          (build_email.build_html / build_text)
    6. обновление истории   (generate_report.update_history — антиповтор)
    7. QA-сводка            (что собралось, сколько графиков, дубли, пути файлов)

Все компоненты вызываются как библиотеки (in-process), а не подпроцессами —
так ошибки ловятся как исключения, а не парсятся из stdout. Графики рендерятся
ОДИН раз в детерминированный per-week каталог и переиспользуются и для Word, и
для превью.

Пример:
    python3 run_report.py --data data/week_2026-06-30.json
    python3 run_report.py --data data/week_2026-06-30.json --strict
    python3 run_report.py --data data/week_2026-06-30.json --no-history-update --no-charts

Флаги:
    --data PATH            (обязательный) JSON с данными недели
    --out DIR              каталог для артефактов (по умолчанию: reports/)
    --history PATH         файл истории (по умолчанию: data/history.json рядом с --data)
    --strict               warnings валидации становятся блокирующими
    --skip-validate        пропустить шаг валидации
    --no-history-update    не дописывать историю (только проверка дублей)
    --no-charts            не рендерить графики (текстовый/быстрый прогон)
    -q / --quiet           меньше логов (только предупреждения и итог)
    -v / --verbose         подробные логи (debug)

Коды выхода:
    0  успех
    1  ошибка валидации (errors, либо warnings в --strict)
    2  фатальная ошибка (битый JSON, не удалось собрать Word, ошибка записи)
    3  частичный успех (graceful degradation — например, графики пропущены, а в
       --strict это становится ошибкой; см. --strict + --no-charts)
"""

import argparse
import json
import logging
import os
import sys
from datetime import datetime

# --------------------------------------------------------------------------- #
#  Коды выхода
# --------------------------------------------------------------------------- #
EXIT_OK = 0
EXIT_VALIDATION = 1
EXIT_FATAL = 2
EXIT_PARTIAL = 3

log = logging.getLogger("run_report")


# --------------------------------------------------------------------------- #
#  Мягкий импорт компонентов
#  Каждый модуль импортируется отдельно: отсутствие одного (например,
#  необязательного validate.py или charts.py без matplotlib) не должно ронять
#  весь конвейер.
# --------------------------------------------------------------------------- #
def _soft_import(name):
    try:
        return __import__(name), None
    except Exception as e:  # noqa: BLE001 — фиксируем причину, продолжаем
        return None, e


generate_report, _err_gr = _soft_import("generate_report")
preview_html, _err_pv = _soft_import("preview_html")
build_email, _err_be = _soft_import("build_email")
validate_mod, _err_val = _soft_import("validate")   # опциональный модуль
charts_mod, _err_charts = _soft_import("charts")    # matplotlib может отсутствовать


# --------------------------------------------------------------------------- #
#  Вспомогательные функции
# --------------------------------------------------------------------------- #
def _setup_logging(level):
    handler = logging.StreamHandler(sys.stderr)
    handler.setFormatter(logging.Formatter("%(levelname)-7s %(message)s"))
    root = logging.getLogger()
    root.handlers[:] = [handler]
    root.setLevel(level)


def _human_size(path):
    try:
        n = os.path.getsize(path)
    except OSError:
        return "?"
    for unit in ("Б", "КБ", "МБ", "ГБ"):
        if n < 1024 or unit == "ГБ":
            return f"{n:.0f} {unit}" if unit == "Б" else f"{n:.1f} {unit}"
        n /= 1024.0
    return f"{n:.1f} ГБ"


def _load_data(path):
    """Загрузить и распарсить входной JSON с понятными сообщениями об ошибках."""
    if not os.path.exists(path):
        raise FileNotFoundError(f"файл данных не найден: {path}")
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except UnicodeDecodeError as e:
        raise ValueError(f"файл не в UTF-8 ({path}): {e}") from e
    except json.JSONDecodeError as e:
        raise ValueError(
            f"некорректный JSON в {path}: строка {e.lineno}, столбец {e.colno}: {e.msg}"
        ) from e
    if not isinstance(data, dict):
        raise ValueError(f"корень JSON должен быть объектом, а не {type(data).__name__}: {path}")
    return data


def _derive_paths(args, data):
    """Вычислить единые выходные пути из --out и week_end (один источник правды)."""
    out_dir = args.out or "reports"
    week_end = (data.get("week_end") or data.get("report_date")
                or datetime.now().strftime("%Y-%m-%d"))
    base = f"{week_end}_dutch_realestate_RU"
    paths = {
        "out_dir":     out_dir,
        "week_end":    week_end,
        "docx":        os.path.join(out_dir, f"{base}.docx"),
        "preview":     os.path.join(out_dir, f"preview_{week_end}.html"),
        "email_html":  os.path.join(out_dir, f"email_{week_end}.html"),
        "email_txt":   os.path.join(out_dir, f"email_{week_end}.txt"),
        # детерминированный per-week каталог графиков (воспроизводимость, без коллизий)
        "assets_dir":  os.path.join(out_dir, "assets", week_end),
    }
    return paths


def _period(data):
    """Человекочитаемый период (переиспользует generate_report при наличии)."""
    if generate_report is not None and hasattr(generate_report, "fmt_period"):
        try:
            return generate_report.fmt_period(data.get("week_start", ""),
                                              data.get("week_end", ""))
        except Exception:  # noqa: BLE001
            pass
    return f"{data.get('week_start', '?')} — {data.get('week_end', '?')}"


def _count_items(data):
    """Подсчёт пунктов по сегментам/подразделам (для QA-сводки)."""
    summary = {}
    for seg in data.get("segments", []) or []:
        sid = seg.get("id", "?")
        subs = seg.get("subsections", {})
        per_sub = {}
        if isinstance(subs, dict):
            for key, items in subs.items():
                per_sub[key] = len(items or [])
        else:  # list-форма
            for s in subs or []:
                per_sub[s.get("id") or s.get("key") or "?"] = len(s.get("items") or [])
        summary[sid] = per_sub
    return summary


# --------------------------------------------------------------------------- #
#  Шаги конвейера
# --------------------------------------------------------------------------- #
def step_validate(data, history_path, strict, skip):
    """Вернуть (ok, blocking, n_errors, n_warnings). blocking → надо остановиться."""
    if skip:
        log.info("[2/7] валидация пропущена (--skip-validate)")
        return True, False, 0, 0
    if validate_mod is None or not hasattr(validate_mod, "validate"):
        log.warning("[2/7] модуль validate недоступен — шаг валидации пропущен "
                    "(%s)", _err_val if _err_val else "функция validate() не найдена")
        return True, False, 0, 0

    log.info("[2/7] валидация контракта данных…")
    try:
        # Пробуем расширенную сигнатуру validate(data, history=..., strict=...),
        # с откатом к минимальной validate(data).
        try:
            report = validate_mod.validate(data, history=history_path, strict=strict)
        except TypeError:
            report = validate_mod.validate(data)
    except Exception as e:  # noqa: BLE001 — валидатор не должен ронять прогон сам по себе
        log.error("валидатор завершился с ошибкой: %s", e)
        # Считаем это ошибкой валидации, чтобы не рендерить вслепую.
        return False, True, 1, 0

    errors, warnings = _read_validation_report(report)
    for msg in errors:
        log.error("  валидация: %s", msg)
    for msg in warnings:
        log.warning("  валидация: %s", msg)

    n_err, n_warn = len(errors), len(warnings)
    if n_err:
        log.error("[2/7] валидация: %d ошибок, %d предупреждений → стоп", n_err, n_warn)
        return False, True, n_err, n_warn
    if strict and n_warn:
        log.error("[2/7] --strict: %d предупреждений считаются блокирующими → стоп", n_warn)
        return False, True, n_err, n_warn
    log.info("[2/7] валидация пройдена (%d предупреждений)", n_warn)
    return True, False, n_err, n_warn


def _read_validation_report(report):
    """Привести произвольную форму отчёта валидатора к (errors[str], warnings[str])."""
    def _as_msgs(seq):
        out = []
        for it in (seq or []):
            if isinstance(it, str):
                out.append(it)
            elif isinstance(it, dict):
                path = it.get("path")
                msg = it.get("message") or it.get("msg") or str(it)
                out.append(f"{path}: {msg}" if path else msg)
            else:
                out.append(str(it))
        return out

    if report is None:
        return [], []
    if isinstance(report, dict):
        return _as_msgs(report.get("errors")), _as_msgs(report.get("warnings"))
    # объект с атрибутами
    return _as_msgs(getattr(report, "errors", None)), _as_msgs(getattr(report, "warnings", None))


def step_render_charts(data, assets_dir, no_charts):
    """
    Отрисовать графики один раз. Возвращает
        (pngs_abs, requested, rendered, reason)
    где pngs_abs = {chart_id: абсолютный_путь_png}.
    """
    charts_list = data.get("charts") or []
    requested = len(charts_list)
    if no_charts:
        log.info("[charts] пропущены (--no-charts)")
        return {}, requested, 0, "disabled" if requested else "none"
    if not charts_list:
        return {}, 0, 0, "none"
    if charts_mod is None or not hasattr(charts_mod, "render_charts"):
        log.warning("[charts] matplotlib/charts недоступны — графики пропущены (%s)",
                    _err_charts if _err_charts else "render_charts не найдена")
        return {}, requested, 0, "no_matplotlib"

    log.info("[charts] рендер %d графиков → %s", requested, assets_dir)
    try:
        os.makedirs(assets_dir, exist_ok=True)
        pngs = charts_mod.render_charts(charts_list, assets_dir) or {}
    except Exception as e:  # noqa: BLE001
        log.error("[charts] сбой рендера графиков: %s", e)
        return {}, requested, 0, "error"

    rendered = len(pngs)
    if rendered < requested:
        missing = [c.get("id") for c in charts_list if c.get("id") not in pngs]
        log.warning("[charts] отрисовано %d/%d, пропущены: %s",
                    rendered, requested, ", ".join(str(m) for m in missing))
    else:
        log.info("[charts] отрисовано %d/%d", rendered, requested)
    return pngs, requested, rendered, "ok"


def step_build_word(data, docx_path, pngs, assets_dir):
    """
    Собрать Word. generate_report.build_report сам умеет рендерить графики, но мы
    уже отрисовали их в per-week каталог — поэтому подкладываем готовый кэш в
    charts.render_charts на время сборки, чтобы исключить двойной рендер и
    коллизии путей.
    """
    if generate_report is None or not hasattr(generate_report, "build_report"):
        raise RuntimeError(f"модуль generate_report недоступен: {_err_gr}")

    os.makedirs(os.path.dirname(docx_path) or ".", exist_ok=True)

    # Кэш: подменяем render_charts внутри generate_report так, чтобы он отдавал
    # уже отрисованные per-week PNG вместо повторного рендера в reports/assets/.
    gr_charts = getattr(generate_report, "_charts", None)
    patched = False
    if pngs and gr_charts is not None and hasattr(gr_charts, "render_charts"):
        _orig = gr_charts.render_charts

        def _cached(charts_list, _assets_dir, _pngs=pngs, _real=_orig, _dir=assets_dir):
            # Если все запрошенные графики уже есть в кэше — отдаём кэш.
            ids = [c.get("id") for c in (charts_list or [])]
            if ids and all(i in _pngs for i in ids):
                return dict(_pngs)
            # иначе — честный рендер в наш per-week каталог
            return _real(charts_list, _dir)

        gr_charts.render_charts = _cached
        patched = True

    log.info("[3/7] сборка Word → %s", docx_path)
    try:
        out = generate_report.build_report(data, docx_path)
    finally:
        if patched:
            gr_charts.render_charts = _orig
    return out


def step_preview(data, preview_path, pngs, assets_dir):
    """Собрать HTML-превью. Ссылки на PNG делаем относительными от каталога превью."""
    if preview_html is None or not hasattr(preview_html, "render"):
        log.warning("[4/7] preview_html недоступен — превью пропущено (%s)", _err_pv)
        return None
    rel = _relativize_pngs(pngs, os.path.dirname(os.path.abspath(preview_path)))
    log.info("[4/7] HTML-превью → %s", preview_path)
    html_str = preview_html.render(data, rel)
    os.makedirs(os.path.dirname(preview_path) or ".", exist_ok=True)
    with open(preview_path, "w", encoding="utf-8") as f:
        f.write(html_str)
    return preview_path


def _relativize_pngs(pngs, base_dir):
    """{id: abs_png} → {id: путь относительно base_dir} (валидные ссылки в HTML)."""
    rel = {}
    for cid, p in (pngs or {}).items():
        try:
            rel[cid] = os.path.relpath(os.path.abspath(p), base_dir)
        except Exception:  # noqa: BLE001 — на разных дисках и т.п.
            rel[cid] = p
    return rel


def step_email(data, html_path, txt_path, file_link):
    """Собрать тело письма (HTML + текст)."""
    if build_email is None or not hasattr(build_email, "build_html"):
        log.warning("[5/7] build_email недоступен — письмо пропущено (%s)", _err_be)
        return None, None
    log.info("[5/7] тело письма → %s, %s", html_path, txt_path)
    html_body = build_email.build_html(data, file_link)
    txt_body = build_email.build_text(data, file_link)
    os.makedirs(os.path.dirname(html_path) or ".", exist_ok=True)
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html_body)
    with open(txt_path, "w", encoding="utf-8") as f:
        f.write(txt_body)
    return html_path, txt_path


def step_history(data, history_path, no_update):
    """
    Проверить дубли против истории и (если не --no-history-update) дописать её.
    Возвращает (dups[list], added, total).
    """
    if generate_report is None or not hasattr(generate_report, "check_duplicates"):
        return [], 0, 0
    if not history_path:
        return [], 0, 0

    dups = []
    try:
        dups = generate_report.check_duplicates(history_path, data)
    except Exception as e:  # noqa: BLE001
        log.warning("[6/7] проверка дублей не удалась: %s", e)

    if dups:
        log.warning("[6/7] обнаружено %d материалов из прошлых отчётов:", len(dups))
        for seg, title, when in dups[:10]:
            log.warning("        - [%s] %s…  (был в отчёте %s)", seg, title, when)

    added = total = 0
    if no_update:
        log.info("[6/7] история не обновляется (--no-history-update)")
    else:
        try:
            added, total = generate_report.update_history(history_path, data)
            log.info("[6/7] история обновлена: +%d новых из %d (%s)",
                     added, total, history_path)
        except Exception as e:  # noqa: BLE001
            log.error("[6/7] не удалось обновить историю: %s", e)
    return dups, added, total


# --------------------------------------------------------------------------- #
#  QA-сводка
# --------------------------------------------------------------------------- #
def print_qa_summary(data, paths, item_counts, charts_info, dups, added, total,
                     val_info, artifacts):
    requested, rendered, reason = charts_info
    line = "─" * 64

    print()
    print(line)
    print("  QA-СВОДКА ПРОГОНА")
    print(line)
    print(f"  Период:           {_period(data)}")
    print(f"  Дата отчёта:      {data.get('report_date', '?')}")
    print(f"  Заголовок:        {(data.get('headline') or '').strip()[:70]}")

    n_err, n_warn = val_info
    if validate_mod is None:
        print("  Валидация:        пропущена (модуль недоступен)")
    else:
        print(f"  Валидация:        ошибок {n_err}, предупреждений {n_warn}")

    print(f"  Главные выводы:   {len(data.get('key_takeaways') or [])}")
    print(f"  Глоссарий:        {len(data.get('glossary') or [])} терминов")
    print(f"  Источники:        {len(data.get('sources') or [])}")

    print("  Материалы по сегментам:")
    grand = 0
    for sid, subs in item_counts.items():
        total_seg = sum(subs.values())
        grand += total_seg
        detail = ", ".join(f"{k}:{v}" for k, v in subs.items() if v) or "—"
        print(f"      • {sid:<12} {total_seg:>3} ({detail})")
    print(f"      Всего пунктов: {grand}")

    if requested:
        suffix = {"ok": "", "disabled": " (--no-charts)",
                  "no_matplotlib": " (нет matplotlib)", "error": " (ошибка рендера)"}.get(reason, "")
        print(f"  Графики:          {rendered}/{requested}{suffix}")
    else:
        print("  Графики:          нет в данных")

    print(f"  Дубли (история):  {len(dups)}")
    if not (added == 0 and total == 0):
        print(f"  История:          +{added} новых из {total}")

    print("  Артефакты:")
    if artifacts:
        for label, p in artifacts:
            exists = os.path.exists(p)
            size = _human_size(p) if exists else "НЕ СОЗДАН"
            mark = " " if exists else "!"
            print(f"    {mark} {label:<10} {p}  [{size}]")
    else:
        print("      (нет)")
    print(line)


# --------------------------------------------------------------------------- #
#  main
# --------------------------------------------------------------------------- #
def parse_args(argv=None):
    ap = argparse.ArgumentParser(
        prog="run_report.py",
        description="Оркестратор еженедельного отчёта по недвижимости Нидерландов "
                    "(валидация → графики → Word → превью → письмо → история → QA).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--data", required=True, help="JSON с данными недели (обязательный)")
    ap.add_argument("--out", default="reports",
                    help="каталог для артефактов (по умолчанию: reports)")
    ap.add_argument("--history", default=None,
                    help="файл истории (по умолчанию: data/history.json рядом с --data)")
    ap.add_argument("--strict", action="store_true",
                    help="warnings валидации становятся блокирующими")
    ap.add_argument("--skip-validate", action="store_true",
                    help="пропустить шаг валидации")
    ap.add_argument("--no-history-update", action="store_true",
                    help="не дописывать историю (только проверка дублей)")
    ap.add_argument("--no-charts", action="store_true",
                    help="не рендерить графики")
    ap.add_argument("--file-link", default=None,
                    help="ссылка на .docx (Drive) для тела письма")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("-q", "--quiet", action="store_true", help="меньше логов")
    g.add_argument("-v", "--verbose", action="store_true", help="подробные логи")
    return ap.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    level = logging.DEBUG if args.verbose else logging.WARNING if args.quiet else logging.INFO
    _setup_logging(level)

    # ---- 1. загрузка данных --------------------------------------------------
    log.info("[1/7] загрузка данных: %s", args.data)
    try:
        data = _load_data(args.data)
    except (FileNotFoundError, ValueError) as e:
        log.error("%s", e)
        return EXIT_FATAL

    # ---- 1b. слой памяти и форсайта (тренды/сюжеты/календарь) ----------------
    try:
        import memory as _memory
        if not args.no_history_update:
            _memory.update_state(data)            # копим metrics/threads/calendar в data/state/
        _st = _memory.load_state()
        _trends = _memory.trend_chart_specs(_st.get("metrics", {}), data.get("week_end") or "", min_points=3)
        if _trends:
            data["charts"] = (data.get("charts") or []) + _trends
            log.info("[1b] память: +%d трендовых графиков (неделя-к-неделе)", len(_trends))
        else:
            log.info("[1b] память: state ok; трендов пока нет (нужно ≥3 недель данных)")
    except Exception as e:  # noqa: BLE001 — слой памяти не должен ронять отчёт
        log.warning("[1b] слой памяти пропущен: %s", e)

    # история по умолчанию — data/history.json рядом с входным файлом
    history_path = args.history
    if history_path is None:
        history_path = os.path.join(os.path.dirname(os.path.abspath(args.data)), "history.json")

    paths = _derive_paths(args, data)
    item_counts = _count_items(data)
    exit_code = EXIT_OK

    # ---- 2. валидация --------------------------------------------------------
    ok, blocking, n_err, n_warn = step_validate(data, history_path, args.strict, args.skip_validate)
    val_info = (n_err, n_warn)
    if blocking and not args.skip_validate:
        # печатаем краткую сводку даже при остановке — оператору полезно
        artifacts = []
        print_qa_summary(data, paths, item_counts, (len(data.get("charts") or []), 0, "none"),
                         [], 0, 0, val_info, artifacts)
        return EXIT_VALIDATION

    # ---- charts (один раз, до Word и превью) ---------------------------------
    pngs, requested, rendered, reason = step_render_charts(data, paths["assets_dir"], args.no_charts)
    charts_info = (requested, rendered, reason)

    # graceful degradation / strict по графикам.
    # --no-charts — осознанный выбор оператора, это НЕ деградация (код 0).
    # А вот сбой рендера / отсутствие matplotlib / частичный рендер — деградация.
    if requested and rendered < requested and reason != "disabled":
        if args.strict:
            log.error("--strict: отрисовано не все графики (%d/%d) → ошибка", rendered, requested)
        else:
            log.warning("графики отрисованы частично (%d/%d) → частичный успех",
                        rendered, requested)
        exit_code = max(exit_code, EXIT_PARTIAL)

    # ---- 3. Word -------------------------------------------------------------
    artifacts = []
    try:
        docx_out = step_build_word(data, paths["docx"], pngs, paths["assets_dir"])
        artifacts.append(("Word", docx_out))
    except Exception as e:  # noqa: BLE001
        log.error("[3/7] не удалось собрать Word: %s", e)
        print_qa_summary(data, paths, item_counts, charts_info, [], 0, 0, val_info, artifacts)
        return EXIT_FATAL

    # ---- 4. превью -----------------------------------------------------------
    try:
        prev = step_preview(data, paths["preview"], pngs, paths["assets_dir"])
        if prev:
            artifacts.append(("Превью", prev))
    except Exception as e:  # noqa: BLE001
        log.error("[4/7] не удалось собрать превью: %s", e)
        exit_code = max(exit_code, EXIT_PARTIAL)

    # ---- 5. письмо -----------------------------------------------------------
    try:
        eh, et = step_email(data, paths["email_html"], paths["email_txt"], args.file_link)
        if eh:
            artifacts.append(("Письмо HTML", eh))
        if et:
            artifacts.append(("Письмо txt", et))
    except Exception as e:  # noqa: BLE001
        log.error("[5/7] не удалось собрать письмо: %s", e)
        exit_code = max(exit_code, EXIT_PARTIAL)

    # ---- 6. история ----------------------------------------------------------
    dups, added, total = step_history(data, history_path, args.no_history_update)

    # ---- 7. QA-сводка --------------------------------------------------------
    log.info("[7/7] QA-сводка")
    print_qa_summary(data, paths, item_counts, charts_info, dups, added, total,
                     val_info, artifacts)

    if exit_code == EXIT_OK:
        log.info("Готово: %s", paths["docx"])
    else:
        log.warning("Готово с замечаниями (код %d): см. QA-сводку выше", exit_code)
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
