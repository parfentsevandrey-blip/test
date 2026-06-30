# Makefile — еженедельный отчёт по рынку недвижимости Нидерландов
#
# Одна команда для полного прогона:
#     make all WEEK=2026-06-30
#
# Дата недели берётся из переменной WEEK (по умолчанию — сегодня). Из неё
# выводятся все пути, чтобы нельзя было перепутать неделю между шагами.
#
# Цели: install validate report preview email all clean help
# По умолчанию (просто `make`) печатается help.

# --------------------------------------------------------------------------- #
#  Переменные
# --------------------------------------------------------------------------- #
PYTHON ?= python3
WEEK   ?= $(shell date +%F)

DATA      := data/week_$(WEEK).json
HISTORY   := data/history.json
OUT_DIR   := reports
DOCX      := $(OUT_DIR)/$(WEEK)_dutch_realestate_RU.docx
PREVIEW   := $(OUT_DIR)/preview_$(WEEK).html
EMAIL_HTML:= $(OUT_DIR)/email_$(WEEK).html
EMAIL_TXT := $(OUT_DIR)/email_$(WEEK).txt
ASSETS    := $(OUT_DIR)/assets/$(WEEK)

# Доп. флаги для run_report.py (например: make report STRICT=--strict)
STRICT    ?=
FILE_LINK ?=
RUN_FLAGS := --data $(DATA) --out $(OUT_DIR) --history $(HISTORY) $(STRICT)
ifneq ($(strip $(FILE_LINK)),)
RUN_FLAGS += --file-link "$(FILE_LINK)"
endif

.DEFAULT_GOAL := help
.PHONY: help install validate report preview email all clean

# --------------------------------------------------------------------------- #
#  help — список целей (по умолчанию)
# --------------------------------------------------------------------------- #
help:
	@echo "Еженедельный отчёт по недвижимости Нидерландов"
	@echo ""
	@echo "Использование: make <цель> [WEEK=YYYY-MM-DD] [STRICT=--strict]"
	@echo "Текущая WEEK = $(WEEK)   (data/week_$(WEEK).json)"
	@echo ""
	@echo "  install    установить зависимости (закреплённые версии)"
	@echo "  validate   проверить JSON недели по контракту (errors/warnings)"
	@echo "  report     валидация → графики → Word → превью → письмо → история → QA"
	@echo "  preview    собрать только HTML-превью (+ графики)"
	@echo "  email      собрать только тело письма (HTML + txt)"
	@echo "  all        синоним report (полный прогон одной командой)"
	@echo "  clean      удалить артефакты рендера из reports/ (история не трогается)"
	@echo "  help       показать эту справку"
	@echo ""
	@echo "Примеры:"
	@echo "  make install"
	@echo "  make all WEEK=2026-06-30"
	@echo "  make validate WEEK=2026-06-30 STRICT=--strict"
	@echo "  make report WEEK=2026-06-30 FILE_LINK=https://drive.google.com/..."

# --------------------------------------------------------------------------- #
#  install
# --------------------------------------------------------------------------- #
install:
	$(PYTHON) -m pip install -r requirements.txt

# --------------------------------------------------------------------------- #
#  validate — проверка контракта данных (предусловие report)
# --------------------------------------------------------------------------- #
validate:
	@test -f "$(DATA)" || { echo "Нет файла данных: $(DATA) (укажите WEEK=YYYY-MM-DD)"; exit 2; }
	$(PYTHON) validate.py --data $(DATA) --history $(HISTORY) $(STRICT)

# --------------------------------------------------------------------------- #
#  report / all — полный прогон одной командой через оркестратор
#  (run_report.py сам валидирует, рендерит графики один раз, собирает
#   Word/превью/письмо и обновляет историю; печатает QA-сводку и код выхода)
# --------------------------------------------------------------------------- #
report:
	@test -f "$(DATA)" || { echo "Нет файла данных: $(DATA) (укажите WEEK=YYYY-MM-DD)"; exit 2; }
	$(PYTHON) run_report.py $(RUN_FLAGS)

all: report

# --------------------------------------------------------------------------- #
#  preview — только HTML-превью (без записи истории)
# --------------------------------------------------------------------------- #
preview:
	@test -f "$(DATA)" || { echo "Нет файла данных: $(DATA) (укажите WEEK=YYYY-MM-DD)"; exit 2; }
	$(PYTHON) preview_html.py --data $(DATA) --out $(PREVIEW)

# --------------------------------------------------------------------------- #
#  email — только тело письма (без записи истории)
# --------------------------------------------------------------------------- #
email:
	@test -f "$(DATA)" || { echo "Нет файла данных: $(DATA) (укажите WEEK=YYYY-MM-DD)"; exit 2; }
	$(PYTHON) build_email.py --data $(DATA) \
	    --out-html $(EMAIL_HTML) --out-txt $(EMAIL_TXT) \
	    $(if $(strip $(FILE_LINK)),--file-link "$(FILE_LINK)",)

# --------------------------------------------------------------------------- #
#  clean — удалить артефакты рендера (history.json НЕ трогаем)
# --------------------------------------------------------------------------- #
clean:
	@echo "Удаляю артефакты рендера из $(OUT_DIR)/ …"
	rm -f  $(OUT_DIR)/*.docx $(OUT_DIR)/*.html $(OUT_DIR)/*.txt $(OUT_DIR)/*.png
	rm -rf $(OUT_DIR)/assets
	rm -rf __pycache__ */__pycache__
	@echo "Готово (data/history.json и входные данные сохранены)."
