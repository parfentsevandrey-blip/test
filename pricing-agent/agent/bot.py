"""Telegram-бот: по клику — вердикт по цене объекта.

    export TELEGRAM_BOT_TOKEN=...
    python -m agent.bot

Экран один и тот же на всех входах: список объектов → карточка → «почему / сценарии /
аналоги». Всё, что показывается, приходит из agent.pricing — бот не считает ничего сам.
"""

from __future__ import annotations

import asyncio
import logging
import os
from typing import Iterable

from aiogram import Bot, Dispatcher, F
from aiogram.filters import Command
from aiogram.types import CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message

from .analytics import analyse_lot
from .models import Action, Apartment, Verdict
from .narrative import explain
from .pricing import evaluate
from .registry import load_registry, portfolio_checks
from .sources import Sources

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger(__name__)

SOURCES = Sources(allow_demo_ops=os.getenv("DEMO_OPS", "1") == "1")

BADGE = {
    Action.CUT: "🔻",
    Action.RAISE: "🔺",
    Action.HOLD: "⏸",
    Action.MANUAL: "❓",
}


def registry() -> list[Apartment]:
    return SOURCES.apply_ops(load_registry())


def assess(apartment: Apartment) -> Verdict:
    return evaluate(
        apartment,
        SOURCES.comps.fetch_comps(apartment),
        baseline=SOURCES.valuation_for(apartment),
    )


def verdict_for(apartment_id: str) -> Verdict | None:
    apartment = next((a for a in registry() if a.id == apartment_id), None)
    return None if apartment is None else assess(apartment)


def money(rub: float) -> str:
    return f"{rub / 1e6:.1f} млн ₽"


def objects_keyboard(verdicts: Iterable[Verdict]) -> InlineKeyboardMarkup:
    rows = [
        [
            InlineKeyboardButton(
                text=(
                    f"{BADGE[v.action]} {v.apartment.complex_name} · "
                    f"{v.apartment.area:g} м² · {money(v.apartment.price)}"
                ),
                callback_data=f"lot:{v.apartment.id}",
            )
        ]
        for v in verdicts
    ]
    rows.append([InlineKeyboardButton(text="📊 Сводка по портфелю", callback_data="digest")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


def card_keyboard(apartment_id: str) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(text="Почему", callback_data=f"why:{apartment_id}"),
                InlineKeyboardButton(text="Сценарии", callback_data=f"scen:{apartment_id}"),
            ],
            [
                InlineKeyboardButton(text="Аналоги", callback_data=f"comps:{apartment_id}"),
                InlineKeyboardButton(text="Конкуренты", callback_data=f"rivals:{apartment_id}"),
            ],
            [InlineKeyboardButton(text="↻ Пересчитать", callback_data=f"lot:{apartment_id}")],
            [InlineKeyboardButton(text="⬅️ К списку", callback_data="list")],
        ]
    )


def card_text(v: Verdict) -> str:
    a = v.apartment
    lines = [
        f"<b>{a.complex_name}</b> — {a.address}",
        f"{a.rooms} комн. · {a.area:g} м² · этаж {a.floor}/{a.floors_total} · {a.finish.value}",
        "",
        f"Текущая цена: <b>{money(a.price)}</b> ({a.price_per_sqm / 1000:.0f} тыс ₽/м²)",
        f"Коридор рынка: {v.corridor[0] / 1000:.0f} — <b>{v.corridor[1] / 1000:.0f}</b> — "
        f"{v.corridor[2] / 1000:.0f} тыс ₽/м²",
        f"Наша позиция: <b>{v.our_percentile:.0f}-й перцентиль</b>",
    ]
    if a.days_on_market is not None:
        lines.append(f"В экспозиции: {a.days_on_market} дн.")

    lines.append("")
    if v.action is Action.MANUAL:
        lines.append(f"{BADGE[v.action]} <b>Требуется ручная оценка</b>")
    else:
        lines.append(
            f"{BADGE[v.action]} <b>{v.action.value.capitalize()}</b>: "
            f"{money(v.recommended_price)} ({v.delta_pct:+.1%})"
        )
    lines.append(f"Уверенность: {v.confidence:.0%} · аналогов: {len(v.comps)}")

    if v.warnings:
        lines += ["", "⚠️ " + v.warnings[0]]
    return "\n".join(lines)


dp = Dispatcher()


@dp.message(Command("start", "objects"))
async def cmd_start(message: Message) -> None:
    verdicts = [assess(a) for a in registry()]
    await message.answer(
        "Агент по ценообразованию. Выберите объект — покажу рыночный коридор "
        "и рекомендацию по цене.",
        reply_markup=objects_keyboard(verdicts),
    )


@dp.callback_query(F.data == "list")
async def show_list(call: CallbackQuery) -> None:
    verdicts = [assess(a) for a in registry()]
    await call.message.edit_text(
        "Объекты в продаже:", reply_markup=objects_keyboard(verdicts)
    )
    await call.answer()


@dp.callback_query(F.data.startswith("lot:"))
async def show_lot(call: CallbackQuery) -> None:
    await call.answer("Считаю…")
    v = verdict_for(call.data.split(":", 1)[1])
    if v is None:
        await call.message.answer("Объект не найден в реестре.")
        return
    await call.message.edit_text(
        card_text(v), reply_markup=card_keyboard(v.apartment.id), parse_mode="HTML"
    )


@dp.callback_query(F.data.startswith("why:"))
async def show_why(call: CallbackQuery) -> None:
    await call.answer("Готовлю объяснение…")
    apartment_id = call.data.split(":", 1)[1]
    v = verdict_for(apartment_id)
    if v is None:
        return
    # Вызов LLM блокирующий — уводим в поток, чтобы не морозить event loop бота.
    # В проде: очередь задач и кэш вердиктов, см. docs/IMPLEMENTATION.md.
    text = await asyncio.to_thread(explain, v)
    factors = "\n".join(f"• {s}" for s in v.signals)
    await call.message.answer(
        f"{text}\n\n<b>Что учтено:</b>\n{factors}",
        reply_markup=card_keyboard(apartment_id),
        parse_mode="HTML",
    )


@dp.callback_query(F.data.startswith("scen:"))
async def show_scenarios(call: CallbackQuery) -> None:
    await call.answer()
    apartment_id = call.data.split(":", 1)[1]
    v = verdict_for(apartment_id)
    if v is None:
        return
    if not v.scenarios:
        await call.message.answer("Сценарии не рассчитаны: недостаточно аналогов.")
        return
    body = "\n".join(
        f"<b>{s.name}</b>: {money(s.price)} — ориентировочно {s.expected_days} дн.\n<i>{s.comment}</i>"
        for s in v.scenarios
    )
    await call.message.answer(
        f"<b>Цена ↔ срок продажи</b>\n\n{body}\n\n"
        "<i>Срок — оценка по позиции в коридоре и темпу рынка, не гарантия.</i>",
        reply_markup=card_keyboard(apartment_id),
        parse_mode="HTML",
    )


@dp.callback_query(F.data.startswith("comps:"))
async def show_comps(call: CallbackQuery) -> None:
    await call.answer()
    apartment_id = call.data.split(":", 1)[1]
    v = verdict_for(apartment_id)
    if v is None:
        return
    rows = []
    for ac in v.comps[:7]:
        c = ac.comp
        mark = "сделка" if c.is_closed_deal else f"{c.days_on_market} дн. в продаже"
        rows.append(
            f"• {c.complex_name}, {c.area:g} м², {c.floor}/{c.floors_total} — "
            f"{c.price_per_sqm / 1000:.0f} тыс ₽/м² → после поправок "
            f"<b>{ac.adjusted_price_per_sqm / 1000:.0f}</b> ({mark})"
        )
    await call.message.answer(
        "<b>Аналоги, на которых построен коридор</b>\n\n" + "\n".join(rows),
        reply_markup=card_keyboard(apartment_id),
        parse_mode="HTML",
    )


@dp.callback_query(F.data.startswith("rivals:"))
async def show_rivals(call: CallbackQuery) -> None:
    await call.answer("Смотрю конкурентов…")
    apartment_id = call.data.split(":", 1)[1]
    apartment = next((a for a in registry() if a.id == apartment_id), None)
    if apartment is None:
        return

    c = analyse_lot(apartment, SOURCES.comps.fetch_comps(apartment))
    if not c.direct:
        await call.message.answer(
            "По этому ЖК нет выгрузки расширения — сопоставимых лотов не с чем сравнивать.",
            reply_markup=card_keyboard(apartment_id),
        )
        return

    lines = [
        f"<b>Конкуренты · {apartment.complex_name.strip()}</b>",
        f"Сопоставимых лотов: {len(c.direct)} · дешевле нас {len(c.cheaper)}",
        f"Медиана конкурентов: {c.median_ppsm / 1000:.0f} тыс ₽/м² "
        f"(<b>{c.raw_gap:+.1%}</b> к нашей цене)",
        f"Давление: <b>{c.pressure}</b>",
    ]
    if c.adjusted_gap is not None:
        lines.append(f"После поправок отрыв {c.adjusted_gap:+.1%}")

    if c.alternatives:
        lines += ["", "<b>Что покупатель увидит вместо нас:</b>"]
        for alt in c.alternatives:
            gap = alt.price_per_sqm / apartment.price_per_sqm - 1
            tail = f" · снижал на {alt.price_cut_pct:.0%}" if alt.price_cut_pct else ""
            link = f' — <a href="{alt.url}">Циан</a>' if alt.url else ""
            lines.append(
                f"• {alt.area:g} м², {alt.floor}/{alt.floors_total} — "
                f"{money(alt.price)} ({gap:+.0%}) · {alt.finish.value}{link}"
            )
            lines.append(
                f"  <i>{alt.seller_name or '—'}"
                + (f" · {alt.days_on_market} дн. в продаже" if alt.days_on_market else "")
                + f"{tail}</i>"
            )

    lines += ["", f"Снижали цену {c.cutting} из {len(c.direct)} · "
              f"переподавали {c.republishing} из {len(c.direct)}"]
    if c.median_exposure is not None:
        ours = f", у нас {apartment.days_on_market}" if apartment.days_on_market else ""
        lines.append(f"Медиана экспозиции {c.median_exposure:.0f} дн.{ours}")

    if c.rivals:
        lines += ["", "<b>Кто ещё продаёт здесь:</b>"]
        lines += [
            f"• {r.name} — {r.lots} лот(ов), медиана {r.median_ppsm / 1000:.0f} тыс, "
            f"снижали {r.cutting}/{r.lots}"
            for r in c.rivals[:5]
        ]

    await call.message.answer(
        "\n".join(lines),
        reply_markup=card_keyboard(apartment_id),
        parse_mode="HTML",
        disable_web_page_preview=True,
    )


@dp.callback_query(F.data == "digest")
async def show_digest(call: CallbackQuery) -> None:
    await call.answer("Собираю сводку…")
    apartments = registry()
    verdicts = [assess(a) for a in apartments]

    actionable = [v for v in verdicts if v.action in (Action.CUT, Action.RAISE)]
    total = sum(v.apartment.price for v in verdicts)
    recommended = sum(v.recommended_price for v in verdicts)

    lines = [
        f"<b>Портфель: {len(verdicts)} объектов на {money(total)}</b>",
        f"С учётом рекомендаций: {money(recommended)} ({recommended / total - 1:+.1%})",
        "",
    ]
    if actionable:
        lines.append("<b>Требуют решения:</b>")
        lines += [
            f"{BADGE[v.action]} {v.apartment.complex_name} ({v.apartment.area:g} м²): "
            f"{money(v.apartment.price)} → {money(v.recommended_price)} ({v.delta_pct:+.1%})"
            for v in actionable
        ]
    else:
        lines.append("Объектов, требующих изменения цены, нет.")

    manual = [v for v in verdicts if v.action is Action.MANUAL]
    if manual:
        lines += ["", "<b>Мало данных, нужна ручная оценка:</b>"]
        lines += [f"❓ {v.apartment.complex_name} ({v.apartment.area:g} м²)" for v in manual]

    notes = portfolio_checks(apartments)
    if notes:
        lines += ["", "<b>Нестыковки в собственном прайсе:</b>"] + [f"⚠️ {n}" for n in notes]

    await call.message.answer("\n".join(lines), parse_mode="HTML")


async def main() -> None:
    token = os.getenv("TELEGRAM_BOT_TOKEN")
    if not token:
        raise SystemExit("Задайте TELEGRAM_BOT_TOKEN (получить у @BotFather)")
    await dp.start_polling(Bot(token))


if __name__ == "__main__":
    asyncio.run(main())
