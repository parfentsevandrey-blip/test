"""Telegram-бот: по клику — рекомендованная цена и почему именно она.

    export TELEGRAM_BOT_TOKEN=...
    python -m agent.bot

Наружу отдаётся только рекомендация. Полный разбор — приведение соседей к нашему
этажу, бюджет въезда, сравнение с локацией — считается по каждому лоту всегда, но
показывается по кнопке: менеджеру нужна цена и три аргумента, а не двадцать таблиц.
Ничего из показанного бот не считает сам — всё приходит из agent.lotreport.
"""

from __future__ import annotations

import asyncio
import logging
import os
from typing import Iterable

from aiogram import Bot, Dispatcher, F
from aiogram.filters import Command
from aiogram.types import CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message

from .lotreport import LotReport, build_report
from .models import Apartment
from .narrative import explain_price
from .registry import load_registry, portfolio_checks
from .render import full_report
from .sources import Sources

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger(__name__)

SOURCES = Sources(allow_demo_ops=os.getenv("DEMO_OPS", "1") == "1")

# Telegram режет сообщение на 4096 символах; таблицы разбора длиннее.
TG_LIMIT = 3900

_CACHE: dict[str, LotReport] = {}


def registry() -> list[Apartment]:
    return SOURCES.apply_ops(load_registry())


def build_all() -> list[LotReport]:
    """Считает отчёты по всему портфелю и обновляет кэш.

    Реестр читается целиком и один раз: провайдеру аналогов нужен весь список наших
    лотов, иначе квартиры в одном ЖК сравниваются сами с собой.
    """
    reports = [
        build_report(
            a,
            SOURCES.comps.fetch_comps(a),
            SOURCES.location_comps(a),
            price_list=SOURCES.house_price_list(a),
        )
        for a in registry()
    ]
    _CACHE.clear()
    _CACHE.update({r.apartment.id: r for r in reports})
    return reports


async def reports(*, refresh: bool = False) -> list[LotReport]:
    """Отчёты по портфелю. Расчёт блокирующий — уводим в поток."""
    if refresh or not _CACHE:
        return await asyncio.to_thread(build_all)
    return list(_CACHE.values())


async def report_for(apartment_id: str, *, refresh: bool = False) -> LotReport | None:
    if refresh or apartment_id not in _CACHE:
        await reports(refresh=True)
    return _CACHE.get(apartment_id)


def money(rub: float) -> str:
    return f"{rub / 1e6:.1f} млн ₽"


def thousands(v: float) -> str:
    return f"{v / 1000:,.0f}".replace(",", " ")


def badge(r: LotReport) -> str:
    if r.recommendation.binding == "нет данных":
        return "❓"
    return "⏸" if r.recommendation.delta_pct > -0.005 else "🔻"


def chunks(text: str, limit: int = TG_LIMIT) -> list[str]:
    """Режет длинный текст по строкам, чтобы не рвать таблицы посередине строки."""
    out: list[str] = []
    buf: list[str] = []
    size = 0
    for line in text.split("\n"):
        if size + len(line) + 1 > limit and buf:
            out.append("\n".join(buf))
            buf, size = [], 0
        buf.append(line)
        size += len(line) + 1
    if buf:
        out.append("\n".join(buf))
    return out


async def send_long(message: Message, text: str, markup: InlineKeyboardMarkup) -> None:
    """Моноширинные таблицы разбора: <pre> держит колонки, клавиатура — на последнем куске."""
    parts = chunks(text)
    for i, part in enumerate(parts):
        await message.answer(
            f"<pre>{part}</pre>",
            reply_markup=markup if i == len(parts) - 1 else None,
            parse_mode="HTML",
        )


# --------------------------------------------------------------------------- экраны


def objects_keyboard(items: Iterable[LotReport]) -> InlineKeyboardMarkup:
    rows = [
        [
            InlineKeyboardButton(
                text=(
                    f"{badge(r)} {r.apartment.complex_name.strip()} · "
                    f"{r.apartment.area:g} м² · {money(r.recommendation.price)}"
                ),
                callback_data=f"lot:{r.apartment.id}",
            )
        ]
        for r in items
    ]
    rows.append([InlineKeyboardButton(text="📊 Сводка по портфелю", callback_data="digest")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


def card_keyboard(apartment_id: str) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(text="Почему", callback_data=f"why:{apartment_id}"),
                InlineKeyboardButton(text="Полный разбор", callback_data=f"deep:{apartment_id}"),
            ],
            [
                InlineKeyboardButton(text="Соседи по дому", callback_data=f"peers:{apartment_id}"),
                InlineKeyboardButton(text="Альтернативы", callback_data=f"alt:{apartment_id}"),
            ],
            [InlineKeyboardButton(text="↻ Пересчитать", callback_data=f"recalc:{apartment_id}")],
            [InlineKeyboardButton(text="⬅️ К списку", callback_data="list")],
        ]
    )


def card_text(r: LotReport) -> str:
    """Карточка лота: рекомендованная цена и обоснование. Больше наружу ничего."""
    a = r.apartment
    rec = r.recommendation
    lines = [
        f"<b>{a.complex_name.strip()}</b> — {a.address}",
        f"{a.rooms} комн. · {a.area:g} м² · этаж {a.floor}/{a.floors_total} · {a.finish.value}",
        f"Сейчас: {money(a.price)} ({thousands(a.price_per_sqm)} тыс ₽/м²)",
        "",
        f"➜ <b>{rec.headline}</b>",
    ]
    if rec.corridor[0] != rec.corridor[1]:
        lines.append(f"Коридор торга: {money(rec.corridor[0])} — {money(rec.corridor[1])}")
    if r.finishing_cost:
        lines.append(
            f"Бюджет въезда для покупателя: <b>{money(rec.move_in)}</b> "
            f"(доводка {thousands(r.finishing_cost)} тыс ₽/м²)"
        )
    if a.days_on_market is not None:
        lines.append(f"В экспозиции: {a.days_on_market} дн.")
    lines.append(f"Уверенность: {rec.confidence:.0%}")

    lines += ["", "<b>Почему:</b>"]
    lines += [f"• {x}" for x in rec.reasons]
    if rec.caveats:
        lines += [""] + [f"⚠️ {x}" for x in rec.caveats]
    return "\n".join(lines)


dp = Dispatcher()


@dp.message(Command("start", "objects"))
async def cmd_start(message: Message) -> None:
    await message.answer("Считаю по портфелю…")
    items = await reports(refresh=True)
    await message.answer(
        "Агент по ценообразованию. Выберите объект — назову рекомендованную цену "
        "и почему именно она.",
        reply_markup=objects_keyboard(items),
    )


@dp.callback_query(F.data == "list")
async def show_list(call: CallbackQuery) -> None:
    await call.answer()
    await call.message.edit_text(
        "Объекты в продаже:", reply_markup=objects_keyboard(await reports())
    )


@dp.callback_query(F.data.startswith(("lot:", "recalc:")))
async def show_lot(call: CallbackQuery) -> None:
    kind, apartment_id = call.data.split(":", 1)
    await call.answer("Считаю…" if kind == "recalc" else None)
    r = await report_for(apartment_id, refresh=kind == "recalc")
    if r is None:
        await call.message.answer("Объект не найден в реестре.")
        return
    text = card_text(r)
    if call.message.text == text:  # Telegram отвергает правку без изменений
        return
    await call.message.edit_text(
        text, reply_markup=card_keyboard(apartment_id), parse_mode="HTML"
    )


@dp.callback_query(F.data.startswith("why:"))
async def show_why(call: CallbackQuery) -> None:
    await call.answer("Готовлю объяснение…")
    apartment_id = call.data.split(":", 1)[1]
    r = await report_for(apartment_id)
    if r is None:
        return
    # Модель только пересказывает готовый расчёт и не может изменить цену:
    # на вход ей идут выводы отчёта, а не сырые лоты. См. narrative.SYSTEM_PRICE.
    text = await asyncio.to_thread(explain_price, r)
    await call.message.answer(
        text, reply_markup=card_keyboard(apartment_id), parse_mode="HTML"
    )


@dp.callback_query(F.data.startswith("deep:"))
async def show_deep(call: CallbackQuery) -> None:
    await call.answer("Собираю разбор…")
    apartment_id = call.data.split(":", 1)[1]
    r = await report_for(apartment_id)
    if r is None:
        return
    await send_long(call.message, full_report(r), card_keyboard(apartment_id))


@dp.callback_query(F.data.startswith("peers:"))
async def show_peers(call: CallbackQuery) -> None:
    await call.answer()
    apartment_id = call.data.split(":", 1)[1]
    r = await report_for(apartment_id)
    if r is None:
        return
    if not r.peers:
        await call.message.answer(
            "По этому ЖК нет выгрузки расширения — сопоставимых лотов не с чем сравнивать.",
            reply_markup=card_keyboard(apartment_id),
        )
        return

    a = r.apartment
    lines = [
        f"<b>Соседи по дому, приведённые к {a.floor}-му этажу</b>",
        r.floor_premium.summary if r.floor_premium else "",
        "",
    ]
    for p in r.peers:
        c = p.comp
        lines.append(
            f"• {c.area:g} м², {c.floor}/{c.floors_total} — {money(c.price)} "
            f"({thousands(c.price_per_sqm)} тыс ₽/м²) → <b>{thousands(p.adjusted_ppsm)}</b> "
            f"· {c.finish.value}"
        )
    lines.append(
        f"\n<b>Наш лот</b>: {a.floor}/{a.floors_total} — {money(a.price)} "
        f"({thousands(a.price_per_sqm)} тыс ₽/м²)"
    )
    if r.parity_gap is not None:
        lines.append(
            f"Расхождение с медианой соседей после приведения: <b>{r.parity_gap:+.2%}</b>"
        )
    await call.message.answer(
        "\n".join(x for x in lines if x != ""),
        reply_markup=card_keyboard(apartment_id),
        parse_mode="HTML",
    )


@dp.callback_query(F.data.startswith("alt:"))
async def show_alternatives(call: CallbackQuery) -> None:
    await call.answer()
    apartment_id = call.data.split(":", 1)[1]
    r = await report_for(apartment_id)
    if r is None:
        return
    ready = [x for x in r.alternatives if x.ready][:8]
    if not ready:
        await call.message.answer(
            "По этой локации нет выгрузок соседних проектов — сравнивать бюджет въезда "
            "не с чем. Нужна выгрузка расширения по конкурирующим ЖК района.",
            reply_markup=card_keyboard(apartment_id),
        )
        return

    lines = [
        "<b>Что покупатель купит вместо нашего лота</b>",
        f"Наш бюджет въезда: <b>{money(r.move_in)}</b> "
        f"({thousands(r.move_in_ppsm)} тыс ₽/м² готовой квартиры)",
        "",
    ]
    for x in ready:
        c = x.comp
        link = f' — <a href="{c.url}">Циан</a>' if c.url else ""
        lines.append(
            f"• {x.project.name if x.project else '—'}, {c.area:g} м², "
            f"{c.floor}/{c.floors_total} — <b>{money(x.move_in)}</b> "
            f"({x.budget_delta / 1e6:+.1f} млн) · {c.finish.value}{link}"
        )
    if r.location and r.location_rank:
        lines.append(
            f"\nПо метру готовой квартиры лот <b>{r.location_rank}-й</b> из "
            f"{len(r.location) + 1} проектов локации."
        )
    await call.message.answer(
        "\n".join(lines),
        reply_markup=card_keyboard(apartment_id),
        parse_mode="HTML",
        disable_web_page_preview=True,
    )


@dp.callback_query(F.data == "digest")
async def show_digest(call: CallbackQuery) -> None:
    await call.answer("Собираю сводку…")
    items = await reports()

    total = sum(r.apartment.price for r in items)
    recommended = sum(r.recommendation.price for r in items)
    actionable = [r for r in items if r.recommendation.delta_pct <= -0.005]
    blind = [r for r in items if r.recommendation.binding == "нет данных"]

    lines = [
        f"<b>Портфель: {len(items)} объектов на {money(total)}</b>",
        f"С учётом рекомендаций: {money(recommended)} ({recommended / total - 1:+.1%})",
        "",
    ]
    if actionable:
        lines.append("<b>Требуют решения:</b>")
        lines += [
            f"🔻 {r.apartment.complex_name.strip()} ({r.apartment.area:g} м²): "
            f"{money(r.apartment.price)} → {money(r.recommendation.price)} "
            f"({r.recommendation.delta_pct:+.1%})"
            for r in actionable
        ]
    else:
        lines.append("Объектов, требующих изменения цены, нет.")

    if blind:
        lines += ["", "<b>Нет выгрузки по ЖК — цена не проверена:</b>"]
        lines += [
            f"❓ {r.apartment.complex_name.strip()} ({r.apartment.area:g} м²)" for r in blind
        ]

    notes = portfolio_checks([r.apartment for r in items])
    if notes:
        lines += ["", "<b>Нестыковки в собственном прайсе:</b>"] + [f"⚠️ {n}" for n in notes]

    for note in SOURCES.notes:
        lines.append(f"\n<i>{note}</i>")

    await call.message.answer("\n".join(lines), parse_mode="HTML")


async def main() -> None:
    token = os.getenv("TELEGRAM_BOT_TOKEN")
    if not token:
        raise SystemExit("Задайте TELEGRAM_BOT_TOKEN (получить у @BotFather)")
    await dp.start_polling(Bot(token))


if __name__ == "__main__":
    asyncio.run(main())
