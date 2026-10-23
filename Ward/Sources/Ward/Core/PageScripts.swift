import Foundation

extension Scripts {
    /// Процедурная косметика для страницы: селекторы с операторами uBlock (`:has-text`, `:upward`…).
    nonisolated static func procedural(_ selectors: [String]) -> String? {
        guard !selectors.isEmpty, let data = try? JSONSerialization.data(withJSONObject: selectors) else { return nil }
        return proceduralEngine.replacingOccurrences(of: "__WARD_PROCEDURAL__", with: String(decoding: data, as: UTF8.self))
    }

    /// Движок процедурной косметики. Работает в мире Ward: страница его не видит и не может сломать.
    nonisolated static let proceduralEngine = #"""
    (() => {
      'use strict';
      const RULES = __WARD_PROCEDURAL__;
      if (!RULES.length) return;
      const PROC = new Set(['has-text', '-abp-contains', 'contains', 'upward', 'nth-ancestor', 'matches-css', 'matches-css-before',
        'matches-css-after', 'min-text-length', 'xpath', 'remove', 'style', 'remove-attr', 'remove-class', 'watch-attr', 'matches-attr',
        'matches-path', 'matches-media', '-abp-has', 'has', 'not', 'if', 'if-not', 'others', 'matches-prop']);
      const ACTIONS = new Set(['remove', 'style', 'remove-attr', 'remove-class']);
      const PROC_RE = /:(has-text|-abp-contains|contains|upward|nth-ancestor|matches-css|matches-css-before|matches-css-after|min-text-length|xpath|matches-attr|matches-path|matches-media|-abp-has|if|if-not|others|matches-prop|watch-attr)\(/;
      const watched = new Set();

      const toRegex = (text, flags) => {
        const m = /^\/(.+)\/([gimsu]*)$/.exec(text);
        if (m) { try { return new RegExp(m[1], m[2]); } catch (e) { return null; } }
        return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), flags);
      };
      const closeParen = (s, open) => {
        let depth = 0;
        for (let i = open; i < s.length; i++) {
          const c = s[i];
          if (c === '\\') { i++; continue; }
          if (c === '(') depth++;
          else if (c === ')') { depth--; if (depth === 0) return i; }
        }
        return -1;
      };
      const parse = (raw) => {
        const steps = [];
        const s = raw.trim();
        let css = '';
        let brackets = 0;
        let i = 0;
        while (i < s.length) {
          const c = s[i];
          if (c === '\\') { css += c + (s[i + 1] || ''); i += 2; continue; }
          if (c === '[') brackets++;
          else if (c === ']') brackets--;
          if (c === ':' && brackets === 0) {
            const m = /^:(-?[a-z][-a-z]*)\(/.exec(s.slice(i));
            if (m && PROC.has(m[1])) {
              const open = i + m[0].length - 1;
              const close = closeParen(s, open);
              if (close < 0) return null;
              const arg = s.slice(open + 1, close);
              if ((m[1] === 'has' || m[1] === 'not') && !PROC_RE.test(arg)) { css += s.slice(i, close + 1); i = close + 1; continue; }
              if (m[1] === 'others' || m[1] === 'matches-prop') return null;
              steps.push({ type: 'css', sel: css });
              css = '';
              steps.push({ type: 'op', name: m[1], arg: arg.trim() });
              i = close + 1;
              continue;
            }
          }
          css += c;
          i++;
        }
        if (css.trim() || !steps.length) steps.push({ type: 'css', sel: css });
        return steps;
      };

      const textMatch = (arg) => {
        const re = toRegex(arg);
        return (text) => { if (!re) return false; re.lastIndex = 0; return re.test(text); };
      };
      const cssMatch = (arg, pseudo) => {
        const i = arg.indexOf(':');
        if (i < 0) return () => false;
        const prop = arg.slice(0, i).trim();
        const value = arg.slice(i + 1).trim();
        const re = /^\/.+\/[gimsu]*$/.test(value) ? toRegex(value) : new RegExp('^' + value.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*') + '$');
        return (el) => { try { return re && re.test(getComputedStyle(el, pseudo)[prop]); } catch (e) { return false; } };
      };
      const attrMatch = (arg) => {
        const eq = arg.indexOf('=');
        const unquote = (t) => t.trim().replace(/^"(.*)"$/, '$1').replace(/^'(.*)'$/, '$1');
        const name = unquote(eq < 0 ? arg : arg.slice(0, eq));
        const value = eq < 0 ? null : unquote(arg.slice(eq + 1));
        const nameRe = /^\/.+\/$/.test(name) ? toRegex(name) : null;
        const valueRe = value === null ? null : (/^\/.+\/[gimsu]*$/.test(value) ? toRegex(value) : new RegExp('^' + value.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*') + '$'));
        return (el) => {
          for (const a of el.attributes) {
            if (nameRe ? !nameRe.test(a.name) : a.name !== name) continue;
            if (!valueRe || valueRe.test(a.value)) return true;
          }
          return false;
        };
      };

      const query = (root, sel) => {
        const t = sel.trim();
        if (!t) return [root];
        try {
          if (root === document) return Array.from(document.querySelectorAll(t));
          if (/^[>+~]/.test(t)) {
            if (t[0] === '>') return Array.from(root.querySelectorAll(':scope ' + t));
            const rest = t.slice(1).trim();
            const out = [];
            for (let n = root.nextElementSibling; n; n = n.nextElementSibling) {
              if (n.matches(rest)) { out.push(n); if (t[0] === '+') break; }
              if (t[0] === '+') break;
            }
            return out;
          }
          return Array.from(root.querySelectorAll(t));
        } catch (e) { return []; }
      };

      const run = (steps, roots) => {
        let nodes = null;
        for (const st of steps) {
          if (st.type === 'css') {
            if (nodes === null) {
              nodes = [];
              for (const r of roots) nodes.push(...query(r, st.sel));
            } else if (st.sel.trim()) {
              const t = st.sel;
              if (/^\s|^[>+~]/.test(t)) {
                const next = [];
                for (const n of nodes) next.push(...query(n, /^\s+[^>+~]/.test(t) ? t.trim() : t));
                nodes = next;
              } else {
                nodes = nodes.filter((n) => { try { return n.matches(t.trim()); } catch (e) { return false; } });
              }
            }
            continue;
          }
          if (nodes === null) nodes = roots.slice();
          const arg = st.arg;
          switch (st.name) {
            case 'has-text': case '-abp-contains': case 'contains': {
              const test = textMatch(arg);
              nodes = nodes.filter((n) => test(n.textContent || ''));
              break;
            }
            case 'has': case '-abp-has': case 'if': {
              const inner = parse(arg);
              if (!inner) return [];
              nodes = nodes.filter((n) => run(inner, [n]).length > 0);
              break;
            }
            case 'if-not': {
              const inner = parse(arg);
              if (!inner) return [];
              nodes = nodes.filter((n) => run(inner, [n]).length === 0);
              break;
            }
            case 'not': {
              const inner = parse(arg);
              if (!inner) return [];
              nodes = nodes.filter((n) => run(inner, [n]).length === 0);
              break;
            }
            case 'upward': case 'nth-ancestor': {
              const n = parseInt(arg, 10);
              const out = [];
              for (const node of nodes) {
                let a = null;
                if (/^\d+$/.test(arg)) {
                  a = node;
                  for (let k = 0; k < n && a; k++) a = a.parentElement;
                } else if (node.parentElement) {
                  try { a = node.parentElement.closest(arg); } catch (e) { a = null; }
                }
                if (a && a !== document.documentElement && a !== document.body && !out.includes(a)) out.push(a);
              }
              nodes = out;
              break;
            }
            case 'matches-css': case 'matches-css-before': case 'matches-css-after': {
              const pseudo = st.name === 'matches-css-before' ? '::before' : st.name === 'matches-css-after' ? '::after' : null;
              const test = cssMatch(arg, pseudo);
              nodes = nodes.filter(test);
              break;
            }
            case 'min-text-length': {
              const n = parseInt(arg, 10) || 0;
              nodes = nodes.filter((el) => (el.textContent || '').length >= n);
              break;
            }
            case 'xpath': {
              const out = [];
              for (const node of nodes) {
                try {
                  const r = document.evaluate(arg, node, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
                  for (let k = 0; k < r.snapshotLength; k++) { const x = r.snapshotItem(k); if (x && x.nodeType === 1) out.push(x); }
                } catch (e) {}
              }
              nodes = out;
              break;
            }
            case 'matches-attr': {
              const test = attrMatch(arg);
              nodes = nodes.filter(test);
              break;
            }
            case 'matches-path': {
              const test = textMatch(arg);
              if (!test(location.pathname + location.search)) return [];
              break;
            }
            case 'matches-media': {
              try { if (!matchMedia(arg).matches) return []; } catch (e) { return []; }
              break;
            }
            case 'watch-attr':
              arg.split(',').map((a) => a.trim()).filter(Boolean).forEach((a) => watched.add(a));
              break;
            default:
              return [];
          }
          if (!nodes.length) return nodes;
        }
        return nodes || [];
      };

      const plans = [];
      const sheet = [];
      for (const raw of RULES) {
        const steps = parse(raw);
        if (!steps) continue;
        let action = null;
        const last = steps[steps.length - 1];
        if (last.type === 'op' && ACTIONS.has(last.name)) action = steps.pop();
        while (steps.length && steps[steps.length - 1].type === 'css' && !steps[steps.length - 1].sel.trim() && steps.length > 1) steps.pop();
        const pureCss = steps.every((s) => s.type === 'css');
        if (pureCss && action && action.name === 'style' && !/url\(|expression|@import/i.test(action.arg)) {
          sheet.push(steps.map((s) => s.sel).join('').trim() + ' { ' + action.arg + ' }');
          continue;
        }
        if (pureCss && !action) continue;
        plans.push({ steps, action });
      }

      let styleEl = null;
      const ensureStyle = () => {
        if (!sheet.length) return;
        if (!styleEl) { styleEl = document.createElement('style'); styleEl.textContent = sheet.join('\n'); }
        const parent = document.head || document.documentElement;
        if (parent && styleEl.parentNode !== parent) parent.appendChild(styleEl);
      };

      const applyAction = (el, action) => {
        if (!action) { el.style.setProperty('display', 'none', 'important'); return; }
        switch (action.name) {
          case 'remove': el.remove(); break;
          case 'style':
            for (const decl of action.arg.split(';')) {
              const i = decl.indexOf(':');
              if (i < 0) continue;
              const prop = decl.slice(0, i).trim();
              let value = decl.slice(i + 1).trim();
              const important = /!important$/.test(value);
              value = value.replace(/!important$/, '').trim();
              if (prop && !/url\(/i.test(value)) el.style.setProperty(prop, value, important ? 'important' : '');
            }
            break;
          case 'remove-attr': {
            const re = toRegex(action.arg);
            for (const a of Array.from(el.attributes)) if (re && (re.source === a.name || re.test(a.name))) el.removeAttribute(a.name);
            break;
          }
          case 'remove-class': {
            const re = toRegex(action.arg);
            for (const c of Array.from(el.classList)) if (re && (re.source === c || re.test(c))) el.classList.remove(c);
            break;
          }
        }
      };

      let slow = 0;
      const apply = () => {
        ensureStyle();
        const started = performance.now();
        for (const plan of plans) {
          let nodes;
          try { nodes = run(plan.steps, [document]); } catch (e) { continue; }
          for (const el of nodes) if (el instanceof Element) { try { applyAction(el, plan.action); } catch (e) {} }
        }
        slow = performance.now() - started > 40 ? Math.min(slow + 1, 10) : Math.max(slow - 1, 0);
      };

      let timer = null;
      const schedule = () => { if (!timer) timer = setTimeout(() => { timer = null; apply(); }, slow > 3 ? 1000 : 100); };
      const start = () => {
        apply();
        const options = { childList: true, subtree: true };
        if (watched.size) { options.attributes = true; options.attributeFilter = Array.from(watched); }
        new MutationObserver(schedule).observe(document.documentElement, options);
      };
      ensureStyle();
      if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once: true }); else start();
      window.addEventListener('load', schedule, { once: true });
    })();
    """#

    /// Автоотказ от cookie: вместо того чтобы просто прятать окно согласия, Ward нажимает «Отклонить всё».
    /// Тогда сайт получает именно отказ, а не «согласие по умолчанию».
    nonisolated static let consent = #"""
    (() => {
      'use strict';
      const CMPS = [
        { name: 'OneTrust', present: '#onetrust-banner-sdk, #onetrust-consent-sdk', reject: ['#onetrust-reject-all-handler', '.ot-pc-refuse-all-handler'] },
        { name: 'Cookiebot', present: '#CybotCookiebotDialog', reject: ['#CybotCookiebotDialogBodyButtonDecline', '#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll'] },
        { name: 'Didomi', present: '#didomi-host, #didomi-notice', reject: ['#didomi-notice-disagree-button', '.didomi-continue-without-agreeing'] },
        { name: 'Quantcast', present: '.qc-cmp2-container, #qc-cmp2-ui', reject: ['.qc-cmp2-summary-buttons button[mode="secondary"]'] },
        { name: 'TrustArc', present: '#truste-consent-track, .truste_box_overlay', reject: ['#truste-consent-required'] },
        { name: 'Usercentrics', present: '#usercentrics-root, #usercentrics-cmp-ui', shadow: '#usercentrics-root, #usercentrics-cmp-ui', reject: ['[data-testid="uc-deny-all-button"]', 'button[data-action-type="deny"]'] },
        { name: 'Sourcepoint', present: '.sp_choice_type_13, .message-container .message-component', reject: ['.sp_choice_type_13', 'button[title="Reject All"]', 'button[title="Reject all"]', 'button[title="Отклонить все"]'] },
        { name: 'Google', present: 'form[action*="consent." i], [aria-modal="true"] [jsname]', reject: ['button[aria-label^="Reject all"]', 'button[aria-label^="Отклонить все"]', 'button[aria-label^="Alle ablehnen"]', 'button[aria-label^="Tout refuser"]'] },
        { name: 'Funding Choices', present: '.fc-consent-root', reject: ['.fc-cta-do-not-consent'] },
        { name: 'Klaro', present: '.klaro', reject: ['.cm-btn-decline', '.cn-decline'] },
        { name: 'Complianz', present: '.cmplz-cookiebanner', reject: ['.cmplz-btn.cmplz-deny'] },
        { name: 'CookieYes', present: '.cky-consent-container', reject: ['.cky-btn-reject'] },
        { name: 'iubenda', present: '#iubenda-cs-banner', reject: ['.iubenda-cs-reject-btn'] },
        { name: 'Borlabs', present: '#BorlabsCookieBox, #BorlabsCookieBoxWrap', reject: ['a[data-cookie-refuse]', '.brlbs-btn-refuse-all'] },
        { name: 'Osano', present: '.osano-cm-dialog', reject: ['.osano-cm-denyAll', '.osano-cm-button--type_deny'] },
        { name: 'Termly', present: '[data-tid="banner-decline"]', reject: ['[data-tid="banner-decline"]'] },
        { name: 'Axeptio', present: '#axeptio_overlay', reject: ['#axeptio_btn_dismiss'] },
        { name: 'Cookie Notice', present: '#cookie-notice', reject: ['#cn-refuse-cookie'] },
        { name: 'Moove GDPR', present: '#moove_gdpr_cookie_info_bar', reject: ['.moove-gdpr-infobar-reject-btn'] },
        { name: 'CookieFirst', present: '.cookiefirst-root, [data-cookiefirst-widget]', reject: ['[data-cookiefirst-action="reject"]'] },
        { name: 'Shopify', present: '.shopify-pc__banner', reject: ['.shopify-pc__banner__btn-decline'] },
        { name: 'Ketch', present: '#lanyard_root', reject: ['#ketch-banner-button-secondary'] },
        { name: 'Civic', present: '#ccc', reject: ['#ccc-notify-reject', '#ccc-reject-settings'] },
        { name: 'consentmanager', present: '#cmpbox', reject: ['.cmpboxbtnno', '#cmpbntnotxt'] },
        { name: 'Evidon', present: '#_evidon_banner', reject: ['#_evidon-decline-button'] },
        { name: 'Wix', present: '[data-hook="ccsu-banner-wrapper"]', reject: ['[data-hook="ccsu-banner-decline-all"]'] },
        { name: 'HubSpot', present: '#hs-eu-cookie-confirmation', reject: ['#hs-eu-decline-button'] },
        { name: 'Cookie Script', present: '#cookiescript_injected', reject: ['#cookiescript_reject'] },
        { name: 'Sirdata', present: '#sd-cmp', reject: ['.sd-cmp-3cRQ2', 'button[title="Refuse"]'] },
      ];
      const TEXT = /^(отклонить(\s+(все|всё))?|отказаться(\s+от\s+всех)?|запретить(\s+все)?|не\s+принимать|только\s+(необходимые|обязательные)(\s+cookie)?|reject(\s+all)?(\s+cookies)?|decline(\s+all)?(\s+cookies)?|deny(\s+all)?|refuse(\s+all)?|disagree|only\s+(necessary|essential|required)(\s+cookies)?|(use\s+)?(necessary|essential|strictly\s+necessary)(\s+cookies)?\s+only|alle\s+ablehnen|ablehnen|nur\s+(notwendige|erforderliche)(\s+cookies)?|tout\s+refuser|refuser(\s+tout)?|continuer\s+sans\s+accepter|rechazar(\s+todo|\s+todas)?|rifiuta(\s+tutto|\s+tutti)?|weigeren|alles\s+weigeren|odrzuć(\s+wszystkie)?|odmítnout(\s+vše)?)$/i;
      const CONTAINER = /cookie|consent|gdpr|cmp|privacy|banner|notice/i;
      let done = false;
      let tries = 0;
      // Состояние — в мире Ward (страница его не видит): для самопроверки.
      const state = window.__wardConsent = { started: false, tries: 0, clicked: null };

      // Видимость не проверяется: окно согласия часто уже спрятано списком EasyList Cookie,
      // а отказ всё равно нужно отправить — иначе сайт считает, что вы «не возражали».
      const usable = (el) => !!el && !el.disabled;
      const post = (cmp) => {
        try { window.webkit.messageHandlers.wardEvent.postMessage({ type: 'consent', cmp, top: window === window.top }); } catch (e) {}
      };
      const click = (el, cmp) => {
        done = true;
        state.clicked = cmp;
        try { el.click(); } catch (e) { return; }
        post(cmp);
      };
      const roots = (shadowSel) => {
        const list = [document];
        if (shadowSel) document.querySelectorAll(shadowSel).forEach((h) => { if (h.shadowRoot) list.push(h.shadowRoot); });
        return list;
      };
      const known = () => {
        for (const cmp of CMPS) {
          let present = null;
          try { present = document.querySelector(cmp.present); } catch (e) {}
          if (!present) continue;
          for (const root of roots(cmp.shadow)) {
            for (const sel of cmp.reject) {
              let el = null;
              try { el = root.querySelector(sel); } catch (e) {}
              if (usable(el)) { click(el, cmp.name); return true; }
            }
          }
        }
        return false;
      };
      // Неизвестное окно согласия: кнопка «Отклонить» внутри элемента про cookie.
      const generic = () => {
        const buttons = document.querySelectorAll('button, a[role="button"], [role="button"], input[type="button"], input[type="submit"]');
        for (const b of buttons) {
          const text = (b.innerText || b.value || '').replace(/\s+/g, ' ').trim();
          const text2 = text || (b.textContent || '').replace(/\s+/g, ' ').trim();
          if (!text2 || text2.length > 40 || !TEXT.test(text2) || !usable(b)) continue;
          let inside = false;
          for (let a = b, k = 0; a && k < 8; a = a.parentElement, k++) {
            if (CONTAINER.test((a.id || '') + ' ' + (typeof a.className === 'string' ? a.className : '') + ' ' + (a.getAttribute && (a.getAttribute('aria-label') || '')))) { inside = true; break; }
          }
          if (inside) { click(b, 'по тексту кнопки'); return true; }
        }
        return false;
      };
      const attempt = () => {
        if (done || tries > 60) return;
        tries++;
        state.tries = tries;
        if (known()) return;
        if (tries > 1) generic();
      };
      let timer = null;
      const schedule = () => { if (!done && !timer) timer = setTimeout(() => { timer = null; attempt(); }, 300); };
      const start = () => {
        state.started = true;
        attempt();
        if (done) return;
        const mo = new MutationObserver(schedule);
        mo.observe(document.documentElement, { childList: true, subtree: true });
        // Окна согласия часто появляются по таймеру, а не при изменении страницы.
        for (const delay of [400, 1200, 2500, 5000, 9000]) setTimeout(attempt, delay);
        setTimeout(() => mo.disconnect(), 20000);
      };
      if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once: true }); else start();
    })();
    """#

    /// SponsorBlock: пропуск спонсорских вставок и напоминаний «подпишитесь» в роликах YouTube.
    /// Отрезки берутся из открытой базы SponsorBlock; Ward спрашивает их по первым 4 знакам хеша id ролика,
    /// так что сервер не знает, какой именно ролик вы смотрите.
    nonisolated static let sponsorBlock = #"""
    (() => {
      'use strict';
      if (!/(^|\.)(youtube\.com|youtube-nocookie\.com)$/.test(location.hostname)) return;
      let handler;
      try { handler = window.webkit.messageHandlers.wardSponsor; } catch (e) { return; }
      if (!handler) return;
      const LABELS = {
        sponsor: 'спонсорская вставка', selfpromo: 'самореклама', interaction: 'просьба подписаться', intro: 'заставка',
        outro: 'концовка', preview: 'анонс', music_offtopic: 'немузыкальная часть', filler: 'отступление', exclusive_access: 'реклама доступа',
      };
      let videoId = null;
      let segments = [];
      const done = new Set();
      let mutedBy = null;
      let fullShown = false;

      const currentId = () => {
        try {
          const u = new URL(location.href);
          if (u.pathname === '/watch') return u.searchParams.get('v');
          const m = /^\/(shorts|embed|live|v)\/([\w-]{11})/.exec(u.pathname);
          return m ? m[2] : null;
        } catch (e) { return null; }
      };

      let host = null;
      let box = null;
      const toast = (text, undo) => {
        if (!host) {
          host = document.createElement('div');
          const shadow = host.attachShadow({ mode: 'closed' });
          shadow.innerHTML = '<style>:host{all:initial}.t{position:fixed;left:24px;bottom:72px;z-index:2147483647;display:flex;gap:12px;align-items:center;padding:10px 14px;border-radius:14px;background:rgba(28,28,30,.86);color:#fff;font:13px -apple-system,system-ui,sans-serif;box-shadow:0 8px 28px rgba(0,0,0,.35);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px);transition:opacity .25s}.t.h{opacity:0;pointer-events:none}button{all:unset;cursor:pointer;color:#7fd3ff;font-weight:600}</style><div class="t h"><span></span><button>Отменить</button></div>';
          box = shadow.querySelector('.t');
        }
        const parent = document.fullscreenElement || document.webkitFullscreenElement || document.documentElement;
        if (host.parentNode !== parent) parent.appendChild(host);
        box.querySelector('span').textContent = text;
        const button = box.querySelector('button');
        button.style.display = undo ? '' : 'none';
        button.onclick = () => { if (undo) undo(); box.classList.add('h'); };
        box.classList.remove('h');
        clearTimeout(toast.timer);
        toast.timer = setTimeout(() => box.classList.add('h'), 5000);
      };

      const report = (category, seconds) => {
        try { window.webkit.messageHandlers.wardEvent.postMessage({ type: 'sponsor', category, seconds }); } catch (e) {}
      };

      const load = async () => {
        const id = currentId();
        if (id === videoId) return;
        videoId = id;
        segments = [];
        done.clear();
        fullShown = false;
        if (!id || !/^[\w-]{11}$/.test(id)) return;
        try {
          const result = await handler.postMessage({ videoID: id });
          if (videoId === id && Array.isArray(result)) segments = result;
        } catch (e) {}
      };

      const video = () => document.querySelector('#movie_player video.html5-main-video') || document.querySelector('video.html5-main-video') || document.querySelector('video');

      const tick = () => {
        if (!segments.length) return;
        const v = video();
        if (!v || v.readyState < 1) return;
        const player = document.getElementById('movie_player');
        if (player && player.classList.contains('ad-showing')) return;
        const t = v.currentTime;
        let inMute = null;
        for (const s of segments) {
          const start = s.segment[0];
          const end = s.segment[1];
          if (s.actionType === 'full') {
            if (!fullShown) { fullShown = true; toast('Весь ролик — ' + (LABELS[s.category] || s.category)); }
            continue;
          }
          if (s.actionType === 'mute') { if (t >= start && t < end) inMute = s; continue; }
          if (s.actionType !== 'skip' || done.has(s.UUID) || end - start < 0.5) continue;
          if (t >= start && t < end - 0.3) {
            done.add(s.UUID);
            v.currentTime = end;
            report(s.category, end - start);
            toast('Пропущено: ' + (LABELS[s.category] || s.category), () => { v.currentTime = start; });
            break;
          }
        }
        if (inMute && !mutedBy) { mutedBy = { uuid: inMute.UUID, was: v.muted }; v.muted = true; toast('Звук выключен: ' + (LABELS[inMute.category] || inMute.category)); }
        else if (!inMute && mutedBy) { v.muted = mutedBy.was; mutedBy = null; }
      };

      load();
      setInterval(load, 1000);
      document.addEventListener('yt-navigate-finish', load);
      setInterval(tick, 250);
    })();
    """#

    /// Назойливые плашки поверх статьи: «Подпишитесь», «Отключите блокировщик», рассылки, «Скачайте приложение»,
    /// затемнение страницы и запрет прокрутки. Ward прячет такие слои и возвращает прокрутку.
    ///
    /// Слои ищутся не перебором всей страницы, а «прощупыванием» экрана: `elementsFromPoint` в нескольких точках
    /// и подъём к ближайшему предку с `position: fixed`. Это дёшево даже на тяжёлых сайтах.
    /// Окна, которые вы открыли сами (щелчок или клавиша за секунду до появления), не трогаются, как и шапки сайтов
    /// с навигацией, видеоплееры и всё, где вы сейчас печатаете. Содержимое, которого сайт не прислал, Ward
    /// не показывает — он убирает только то, что закрывает уже загруженную страницу.
    nonisolated static let annoyances = #"""
    (() => {
      'use strict';
      if (window.top !== window || window.__wardAnnoyances) return;
      const state = window.__wardAnnoyances = { hidden: 0, unlocked: 0, kinds: [], scans: 0, layers: 0, skipped: [] };
      const NAG = /(subscri|sign\s*up|sign\s*in\s+to\s+(continue|read|keep)|log\s*in\s+to\s+(continue|read)|create\s+(a\s+)?(free\s+)?account|register\s+(now|for\s+free)|newsletter|claim\s+(this|your)\s+offer|unlimited\s+access|continue\s+reading|keep\s+reading|already\s+a\s+(subscriber|member)|special\s+offer|limited[-\s]time\s+offer|get\s+the\s+app|open\s+in\s+(the\s+)?app|download\s+(our|the)\s+app|ad\s*-?\s*block|disable\s+your\s+(ad|content)\s*block|whitelist\s+(us|this)|allowlist|support\s+(our\s+)?journalism|paywall|подпиш|подписк|оформит|рассылк|зарегистрир|войдите,?\s+чтобы|продолжить\s+чтение|читать\s+дальше|в\s+приложении|скачайте\s+приложение|блокировщик|отключите\s+(adblock|блокир)|abonn|inscri|anmelden|abonnieren|registrieren|werbeblocker|suscr|iscriviti|abbonati)/i;
      const COOKIE = /cookie|consent|gdpr|cmp|privacy-?center/i;
      // Классы, которыми сайты запирают страницу под плашкой: запрет прокрутки и «туман» над статьёй.
      const LOCK = /((stop|restrict|no|disable|lock|prevent|block)[-_]?scroll|scroll[-_]?(lock|block|disabled|restrict)|modal[-_]?open|overflow[-_]?hidden|is[-_]?locked|paywall)/i;
      let lastUser = 0;
      for (const type of ['mousedown', 'keydown', 'touchstart']) addEventListener(type, () => { lastUser = Date.now(); }, true);
      // Решённые слои (убраны или точно не плашка) и сколько раз слой уже смотрели: плашки появляются
      // с анимацией и меняют размер, поэтому «пока не похоже» — не окончательный ответ.
      const decided = new WeakSet();
      const looks = new WeakMap();
      const style = (el, pseudo) => { try { return getComputedStyle(el, pseudo || null); } catch (e) { return null; } };
      const textOf = (el) => (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 3000);
      const signature = (el) => (el.id || '') + ' ' + (typeof el.className === 'string' ? el.className : '') + ' ' + (el.getAttribute('aria-label') || '');
      const post = (kind) => {
        state.kinds.push(kind);
        try { window.webkit.messageHandlers.wardEvent.postMessage({ type: 'annoyance', kind }); } catch (e) {}
      };

      // Все предки с position: fixed — от ближнего к корню. Плашку часто кладут в «обёртку» нулевого
      // размера (fixed, без высоты), а настоящий слой — внутрь, тоже fixed.
      const fixedChain = (el) => {
        const chain = [];
        for (let a = el, k = 0; a && a !== document.body && a !== document.documentElement && k < 40; a = a.parentElement, k++) {
          const s = style(a);
          if (s && s.position === 'fixed') chain.push(a);
        }
        return chain;
      };

      const visibleBox = (el) => {
        const r = el.getBoundingClientRect();
        const w = Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0));
        const h = Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
        return { r, w, h };
      };

      // Слой, который точно не плашка (решение окончательное).
      const semanticallyProtected = (el, nag) => {
        if (el.matches('header, nav, [role="banner"], [role="navigation"], [role="menu"], [role="listbox"], [role="tooltip"]')) return true;
        if (el.querySelector('video, audio, [role="navigation"], nav')) return true;
        if (COOKIE.test(signature(el))) return true;   // окна согласия — забота автоотказа от cookie
        // Много ссылок — меню; но у плашки «подпишитесь» внизу бывают «Условия», «Конфиденциальность», тарифы.
        const links = el.querySelectorAll('a[href]').length;
        if ((links > 8 && !nag) || links > 30) return true;
        return false;
      };

      // Слой, который сейчас нельзя трогать, но потом — можно (анимация появления, вы печатаете).
      const notNow = (el, s) => {
        if (document.fullscreenElement || document.webkitFullscreenElement) return true;
        if (document.activeElement && el.contains(document.activeElement) && /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName)) return true;
        if (s.visibility === 'hidden' || s.display === 'none' || Number(s.opacity) === 0) return true;
        return false;
      };

      const hide = (el, kind) => {
        el.style.setProperty('display', 'none', 'important');
        decided.add(el);
        state.hidden++;
        post(kind);
      };

      // root — самый внешний fixed-слой (его и прячем), chain — все fixed-слои от точки до него.
      const inspect = (root, chain) => {
        if (!root || decided.has(root)) return false;
        const n = (looks.get(root) || 0) + 1;
        looks.set(root, n);
        if (n > 25) return false;
        // Вы только что что-то нажали — значит, окно, скорее всего, открыли сами.
        if (Date.now() - lastUser < 1200) { state.skipped.push('user:' + (root.id || root.tagName)); return false; }
        const s = style(root);
        if (!s || notNow(root, s)) return false;
        const text = textOf(root);
        const nag = NAG.test(text) || chain.some((el) => NAG.test(signature(el)));
        if (semanticallyProtected(root, nag)) { decided.add(root); return false; }
        // Размер берём у самого крупного слоя цепочки: обёртка может быть нулевой высоты.
        let box = null;
        for (const el of chain) {
          const b = visibleBox(el);
          if (!box || b.w * b.h > box.w * box.h) box = b;
        }
        const { r, w, h } = box;
        const vw = innerWidth, vh = innerHeight;
        if (w < 40 || h < 30) return false;
        const coverage = (w * h) / (vw * vh);
        if (coverage > 0.6) {
          // Слой на весь экран: окно-уговор или пустая «шторка» (затемнение, градиент поверх статьи).
          if (nag || text.length < 12) { hide(root, nag ? 'modal' : 'backdrop'); return true; }
          decided.add(root);
          return false;
        }
        const bottom = r.bottom >= vh - 4 && r.top > vh * 0.25;
        const top = r.top <= 4 && r.bottom < vh * 0.6;
        if (nag && w > vw * 0.45 && (bottom || top)) { hide(root, bottom ? 'bottom-sheet' : 'top-bar'); return true; }
        // Небольшое всплывающее окно с формой рассылки или кнопкой «Подписаться».
        if (nag && coverage > 0.03 && root.querySelector('input[type="email"], button, a[role="button"], [role="button"]')) { hide(root, 'popup'); return true; }
        return false;
      };

      // Запрет прокрутки и «некликабельная» страница, которые оставляют такие окна.
      const unlock = () => {
        let changed = false;
        for (const el of [document.documentElement, document.body]) {
          if (!el) continue;
          // Классы-замки (stopScroll, restrictScroll, modal-open…): вместе с ними уходят и их псевдоэлементы-«туман».
          for (const name of Array.from(el.classList)) {
            if (LOCK.test(name)) { el.classList.remove(name); changed = true; }
          }
          const s = style(el);
          if (!s) continue;
          if (s.overflowY === 'hidden' || s.overflowY === 'clip' || s.overflow === 'hidden') {
            el.style.setProperty('overflow', 'auto', 'important');
            el.style.setProperty('overflow-y', 'auto', 'important');
            changed = true;
          }
          if (s.pointerEvents === 'none') { el.style.setProperty('pointer-events', 'auto', 'important'); changed = true; }
          if (s.position === 'fixed') {
            const y = -parseFloat(s.top || '0') || 0;
            el.style.setProperty('position', 'static', 'important');
            if (el === document.body) {
              el.style.setProperty('height', 'auto', 'important');
              if (y > 0) scrollTo(0, y);
            }
            changed = true;
          }
        }
        // Размытая или «некликабельная» статья под окном.
        for (const el of document.body ? document.body.children : []) {
          const s = style(el);
          if (!s || s.position === 'fixed' || s.position === 'absolute') continue;
          if (s.filter && s.filter.includes('blur')) { el.style.setProperty('filter', 'none', 'important'); changed = true; }
          if (s.pointerEvents === 'none' && el.tagName !== 'SCRIPT') { el.style.setProperty('pointer-events', 'auto', 'important'); changed = true; }
        }
        if (changed) state.unlocked++;
      };

      // «Туман» над статьёй, который рисуют вместе с плашкой: пустой слой с градиентом, сквозь который
      // проходят щелчки (поэтому его не видно в elementsFromPoint), или псевдоэлемент ::before/::after.
      let fogSheet = null;
      // Псевдоэлемент-«туман»: у html и body — любой fixed/absolute с градиентом; у шапки и меню — высотой
      // почти в экран (меню такой высоты не бывает); у остальных — только fixed на весь экран.
      const fogPseudo = (el, kind) => {
        for (const pseudo of ['::before', '::after']) {
          const s = style(el, pseudo);
          if (!s || s.content === 'none' || s.content === 'normal' || s.display === 'none') continue;
          if (s.position !== 'fixed' && s.position !== 'absolute') continue;
          if (!s.backgroundImage.includes('gradient')) continue;
          const tall = parseFloat(s.height) >= innerHeight * 0.8 || (s.top === '0px' && s.bottom === '0px');
          if (kind === 'bar' && !tall) continue;
          if (kind === 'other' && !(s.position === 'fixed' && tall)) continue;
          if (!fogSheet) {
            fogSheet = document.createElement('style');
            fogSheet.textContent = '[data-ward-unfog]::before,[data-ward-unfog]::after{display:none!important}';
            (document.head || document.documentElement).appendChild(fogSheet);
          }
          el.setAttribute('data-ward-unfog', '');
          return true;
        }
        return false;
      };
      const clearFog = () => {
        const layers = document.querySelectorAll('body > *, body > * > *');
        for (const el of layers) {
          if (decided.has(el)) continue;
          const s = style(el);
          if (!s || s.position !== 'fixed' || s.pointerEvents !== 'none' || s.display === 'none') continue;
          if (!s.backgroundImage.includes('gradient') || textOf(el).length > 0) continue;
          const r = el.getBoundingClientRect();
          if (r.width < innerWidth * 0.5 || r.height < 60) continue;
          decided.add(el);
          el.style.setProperty('display', 'none', 'important');
        }
        fogPseudo(document.documentElement, 'root');
        if (document.body) fogPseudo(document.body, 'root');
        for (const el of document.querySelectorAll('header, nav, [role="banner"], [role="navigation"]')) fogPseudo(el, 'bar');
        for (const el of document.querySelectorAll('body > *')) fogPseudo(el, 'other');
      };

      const POINTS = [[0.5, 0.5], [0.5, 0.94], [0.5, 0.82], [0.2, 0.92], [0.8, 0.92], [0.5, 0.06], [0.5, 0.3], [0.5, 0.7],
                      [0.88, 0.85], [0.12, 0.85], [0.85, 0.15], [0.15, 0.15], [0.85, 0.5], [0.15, 0.5], [0.85, 0.65], [0.15, 0.65]];
      const scan = () => {
        if (!document.body) return;
        state.scans++;
        let hid = false;
        const checked = new Set();
        for (const [x, y] of POINTS) {
          let stack = [];
          try { stack = document.elementsFromPoint(innerWidth * x, innerHeight * y); } catch (e) { continue; }
          for (const el of stack.slice(0, 6)) {
            const chain = fixedChain(el);
            const root = chain[chain.length - 1];
            if (!root || checked.has(root) || !root.contains(stack[0])) continue;
            checked.add(root);
            state.layers++;
            if (inspect(root, chain)) hid = true;
          }
        }
        if (hid) clearFog();
        if (state.hidden > 0) unlock();   // и сразу, и если сайт вернул запрет прокрутки позже
      };

      let timer = null;
      const schedule = () => { if (!timer) timer = setTimeout(() => { timer = null; scan(); }, 350); };
      const start = () => {
        scan();
        const mo = new MutationObserver(schedule);
        mo.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] });
        // Плашки любят появляться по таймеру, после анимации и после прокрутки.
        for (const delay of [800, 2000, 4000, 8000, 15000, 30000]) setTimeout(scan, delay);
        addEventListener('scroll', schedule, { passive: true });
        setTimeout(() => mo.disconnect(), 120000);
      };
      if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once: true }); else start();
    })();
    """#
}
