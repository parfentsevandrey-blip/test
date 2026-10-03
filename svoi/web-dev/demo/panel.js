// A small guide for the demo page: what this is, and buttons that make things happen
// in the made-up network (a file arrives, a message comes, the NAS goes off).
// It lives in a shadow root so it cannot disturb the interface (or be styled by it).

const css = `
:host { all: initial; position: fixed; z-index: 900; right: 14px; bottom: 14px; font: 14px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #e9efe9; }
@media (max-width: 720px) { :host { right: 10px; bottom: calc(76px + env(safe-area-inset-bottom, 0px)); } .pill .more { display: none; } }
* { box-sizing: border-box; }
button { font: inherit; color: inherit; cursor: pointer; }
.pill { display: flex; align-items: center; gap: 8px; padding: 8px 14px 8px 10px; border: 1px solid rgba(255,255,255,.18); border-radius: 999px; background: rgba(18,24,22,.92); backdrop-filter: blur(8px); box-shadow: 0 6px 24px rgba(0,0,0,.35); }
.pill:hover { border-color: rgba(120,230,190,.6); }
.badge { background: #4be3b5; color: #06241b; font-weight: 700; font-size: 11px; letter-spacing: .06em; padding: 2px 8px; border-radius: 999px; }
.card { width: min(380px, calc(100vw - 16px)); max-height: min(78vh, 640px); overflow: auto; margin-bottom: 10px; padding: 16px 16px 14px; border: 1px solid rgba(255,255,255,.16); border-radius: 18px; background: rgba(18,24,22,.97); box-shadow: 0 16px 48px rgba(0,0,0,.5); }
h2 { margin: 0 0 6px; font-size: 17px; }
p { margin: 0 0 12px; color: #b9c6bf; }
h3 { margin: 14px 0 8px; font-size: 12px; letter-spacing: .07em; text-transform: uppercase; color: #8fa39a; }
.acts { display: grid; gap: 8px; }
.act { text-align: left; padding: 10px 12px; border-radius: 12px; border: 1px solid rgba(255,255,255,.14); background: rgba(255,255,255,.05); display: flex; gap: 10px; align-items: center; }
.act:hover { background: rgba(75,227,181,.12); border-color: rgba(75,227,181,.5); }
.act b { display: block; font-weight: 600; }
.act span { color: #9fb1a8; font-size: 12.5px; }
.ico { font-size: 18px; width: 24px; text-align: center; }
ul { margin: 0; padding-left: 18px; color: #b9c6bf; }
li { margin: 4px 0; }
.row { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
.row button { flex: 1 1 auto; padding: 8px 10px; border-radius: 10px; border: 1px solid rgba(255,255,255,.16); background: transparent; }
.row button:hover { background: rgba(255,255,255,.07); }
.close { float: right; border: 0; background: transparent; font-size: 20px; line-height: 1; color: #9fb1a8; padding: 0 2px; }
.sent { color: #4be3b5; font-size: 12.5px; min-height: 1.4em; margin-top: 8px; }
.notice { width: min(380px, calc(100vw - 16px)); margin-bottom: 10px; padding: 10px 14px; border-radius: 12px; background: rgba(18,24,22,.97); border: 1px solid rgba(75,227,181,.5); box-shadow: 0 10px 30px rgba(0,0,0,.45); }
`;

/** Start the made-up network over in another scenario: the page reloads itself. */
function restart(scenario) {
  try { sessionStorage.setItem("themesh.demo.scenario", scenario); } catch { /* blocked: fall back to the address */ }
  try { location.reload(); } catch { location.search = "?scenario=" + scenario; }
}

const call = (path) => fetch(path, { method: "POST", headers: { "X-Themesh": "1" } }).catch(() => {});
const enc = encodeURIComponent;

export function installDemoPanel() {
  const host = document.createElement("div");
  host.id = "themesh-demo-guide";
  const root = host.attachShadow({ mode: "open" });
  let nasOn = true;
  let open = false;
  let notice = "";
  let noticeTimer = 0;

  const render = () => {
    root.innerHTML = `<style>${css}</style>
      ${notice && !open ? `<div class="notice" role="status">${notice}</div>` : ""}
      ${open ? `<div class="card" role="dialog" aria-label="Подсказки к демо">
        <button class="close" data-a="close" aria-label="Закрыть">×</button>
        <h2>Это макет, а не сама программа The Mesh</h2>
        <p>Интерфейс здесь тот же, что в настоящей программе, но сеть выдуманная: устройства, фото на NAS, письма и чаты нарисованы, ничего не уходит в интернет. Настоящая программа — один файл, который запускается на ваших устройствах (Windows, macOS, Linux, Android через Termux): архивы и инструкция «Скачать и запустить» — в README репозитория.</p>
        <h3>Сделать так, чтобы что-то произошло</h3>
        <div class="acts">
          <button class="act" data-a="offer"><span class="ico">📤</span><div><b>Телефон присылает файл</b><span>появится предложение — можно принять</span></div></button>
          <button class="act" data-a="chat"><span class="ico">💬</span><div><b>Папа пишет сообщение</b><span>значок и уведомление появятся сами</span></div></button>
          <button class="act" data-a="mail"><span class="ico">✉️</span><div><b>Приходит письмо от NAS</b><span>откройте «Почту»</span></div></button>
          <button class="act" data-a="nas"><span class="ico">${nasOn ? "⏻" : "⚡"}</span><div><b>${nasOn ? "Выключить NAS" : "Включить NAS"}</b><span>посмотрите, как меняется состояние сети</span></div></button>
        </div>
        <h3>Что посмотреть самому</h3>
        <ul>
          <li>Нажмите на устройство — откроется его карточка.</li>
          <li>«Файлы» → nas → «Фото»: снимки открываются и листаются, музыка играет.</li>
          <li>«Настройки»: язык, светлая и тёмная тема.</li>
        </ul>
        <div class="row">
          <button data-a="first">Показать первый запуск</button>
          <button data-a="ready">Вернуть готовую сеть</button>
        </div>
        <div class="sent" id="sent"></div>
      </div>` : ""}
      <button class="pill" data-a="toggle" aria-expanded="${open}" aria-label="${open ? "Скрыть подсказки" : "Что здесь можно сделать?"}"><span class="badge">ДЕМО</span><span class="more">${open ? "Скрыть подсказки" : "Что здесь можно сделать?"}</span><span aria-hidden="true">${open ? "×" : "?"}</span></button>`;
  };

  window.addEventListener("themesh-demo-notice", (e) => {
    notice = String(e.detail || "");
    render();
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => { notice = ""; render(); }, 5000);
  });

  const say = (t) => { const el = root.getElementById("sent"); if (el) el.textContent = t; };

  root.addEventListener("click", async (e) => {
    const b = e.target.closest("[data-a]");
    if (!b) return;
    switch (b.dataset.a) {
      case "toggle": open = !open; render(); break;
      case "close": open = false; render(); break;
      case "offer": await call("/__mock/offer?from=phone&name=" + enc("Фото с дачи.jpg")); say("Телефон предлагает файл — загляните наверх страницы."); break;
      case "chat": await call("/__mock/chat?from=dad-pc&text=" + enc("Привет! Получил твои фотографии, спасибо 👍")); say("Папа написал — смотрите «Чат»."); break;
      case "mail": await call("/__mock/mail?from=nas"); say("Пришло письмо — смотрите «Почту»."); break;
      case "nas":
        nasOn = !nasOn;
        await call("/__mock/peer?name=nas&online=" + (nasOn ? 1 : 0));
        render();
        say(nasOn ? "NAS снова в сети." : "NAS выключен — он станет серым, а число «в сети» уменьшится.");
        break;
      case "first": restart("onboarding"); break;
      case "ready": restart("full"); break;
    }
  });

  render();
  const attach = () => document.body.appendChild(host);
  if (document.body) attach(); else document.addEventListener("DOMContentLoaded", attach);
}
