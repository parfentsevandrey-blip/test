/* ============================================================================
 *  Service worker (MV3). У расширения нет попапа: вся работа — в панели на самой
 *  выдаче Циан. Клик по иконке расширения в панели браузера разворачивает эту
 *  панель на странице. Если контент-скрипт ещё не загрузился (или это не Циан) —
 *  открываем cian.ru.
 * ========================================================================== */
chrome.action.onClicked.addListener(async (tab) => {
  if (!tab || tab.id == null) return;
  const onCian = /:\/\/([a-z0-9-]+\.)?cian\.ru\//i.test(tab.url || "");
  try {
    // bridge.js (isolated world) услышит и пробросит DOM-событие в MAIN-world панель
    await chrome.tabs.sendMessage(tab.id, { type: "cian-excel-toggle" });
  } catch (e) {
    if (onCian) {
      // вкладка Циан открыта раньше расширения — перезагрузим, чтобы внедрился скрипт
      try { await chrome.tabs.reload(tab.id); } catch (e2) { /* ignore */ }
    } else {
      // не на Циан — откроем сайт
      try { await chrome.tabs.create({ url: "https://www.cian.ru/" }); } catch (e2) { /* ignore */ }
    }
  }
});
