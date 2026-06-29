/* ============================================================================
 *  Мост (isolated world). Контент-скрипт в MAIN-world (content.js) не имеет
 *  доступа к chrome.* — поэтому сообщение от service worker принимаем здесь, в
 *  isolated world, и пробрасываем в MAIN через DOM-событие на общем window
 *  (оба мира делят один DOM). Так клик по иконке расширения разворачивает панель.
 * ========================================================================== */
(() => {
  "use strict";
  if (window.__cianExcelBridge) return;
  window.__cianExcelBridge = true;

  try {
    chrome.runtime.onMessage.addListener((msg) => {
      if (msg && msg.type === "cian-excel-toggle") {
        try { window.dispatchEvent(new CustomEvent("cian-excel-toggle")); } catch (e) { /* ignore */ }
      }
    });
  } catch (e) { /* ignore */ }

  // Диагностика: если через 5 c в DOM нет панели MAIN-скрипта — значит content.js
  // не внедрился (например, MAIN-world не поддерживается старым браузером). Тихо
  // сообщим в консоль, чтобы было видно при отладке.
  setTimeout(() => {
    try {
      if (!document.getElementById("cian-excel-host")) {
        console.warn("[cian-excel] панель не найдена в DOM: content.js (MAIN) не запустился. " +
          "Обновите страницу; нужен Chrome/Edge/Яндекс свежей версии (поддержка world:MAIN).");
      }
    } catch (e) { /* ignore */ }
  }, 5000);
})();
