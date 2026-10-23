import CryptoKit
import Foundation

/// Изолятор: сайт, который собирает о вас всё, до чего дотянется (банки, госуслуги, маркетплейсы, соцсети),
/// живёт в отдельной «банке».
///
/// - Своё постоянное хранилище: вход и «доверенное устройство» сохраняются, но ни один другой сайт и ни один
///   контейнер этих cookie и данных не видят — и сам сайт не видит ничего за пределами банки.
/// - Вымышленное устройство («легенда»): свой постоянный шум отпечатка (холст, WebGL, звук, шрифты),
///   но правдоподобны и постоянны между запусками. Сайт узнаёт «своё устройство» и не просит лишних
///   подтверждений, но это устройство — не ваш Mac.
/// - Все запросы доступа отклоняются молча и записываются: геопозиция, камера, микрофон, экран, уведомления,
///   чтение буфера обмена, датчики, список устройств, доступ к хранилищу из чужих фреймов.
/// - Ссылки, по которым вы сами уходите на другой сайт, открываются уже вне банки.
nonisolated enum Isolation {
    /// Хранилище банки: постоянный идентификатор, выведенный из имени сайта.
    static func storeID(for site: String) -> UUID {
        let digest = Array(SHA256.hash(data: Data("ward-isolator|\(site)".utf8)))
        var b = Array(digest.prefix(16))
        b[6] = (b[6] & 0x0F) | 0x50   // версия 5: «имя → UUID»
        b[8] = (b[8] & 0x3F) | 0x80
        return UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }

    /// Вымышленное устройство для сайта: свой детерминированный шум отпечатка (холст, WebGL, звук, шрифты)
    /// и огрублённое время. Постоянно для пары «банка + секрет».
    ///
    /// Экран и число ядер нарочно не подменяются: детекторы сверяют screen.width с CSS-медиазапросами,
    /// а hardwareConcurrency — с воркерами, куда скрипты страницы не попадают. Такая подмена выдала бы
    /// «браузер с подделкой» — заметнее настоящего Mac. Шум же в стиле Safari 26 выглядит как обычный WebKit.
    nonisolated struct Legend: Sendable, Equatable {
        /// Секрет шума отпечатка: постоянный, чтобы сайт видел одно и то же «устройство».
        var noiseSecret: String
        /// Короткий номер «устройства» — для щита и панели.
        var tag: String
    }

    static func legend(site: String, secret: String) -> Legend {
        let digest = Array(SHA256.hash(data: Data("\(secret)|\(site)".utf8)))
        return Legend(noiseSecret: digest[2..<14].map { String(format: "%02x", $0) }.joined(),
                      tag: digest[0..<3].map { String(format: "%02X", $0) }.joined())
    }

    /// Что сайт пытался получить в изоляторе.
    nonisolated enum Attempt: String, CaseIterable, Sendable {
        case location, camera, microphone, screen, devices, notifications, clipboard, sensors, storage, fingerprint, trackers

        var title: String {
            switch self {
            case .location: "Геопозиция"
            case .camera: "Камера"
            case .microphone: "Микрофон"
            case .screen: "Запись экрана"
            case .devices: "Список устройств"
            case .notifications: "Уведомления"
            case .clipboard: "Буфер обмена"
            case .sensors: "Датчики движения"
            case .storage: "Хранилище вне сайта"
            case .fingerprint: "Отпечаток устройства"
            case .trackers: "Трекеры"
            }
        }

        var icon: String {
            switch self {
            case .location: "location.fill"
            case .camera: "camera.fill"
            case .microphone: "mic.fill"
            case .screen: "rectangle.dashed.badge.record"
            case .devices: "cable.connector"
            case .notifications: "bell.badge.fill"
            case .clipboard: "doc.on.clipboard.fill"
            case .sensors: "gyroscope"
            case .storage: "externaldrive.fill.badge.questionmark"
            case .fingerprint: "touchid"
            case .trackers: "eye.slash.fill"
            }
        }

        /// Что получил сайт вместо настоящего.
        var answer: String {
            switch self {
            case .location, .camera, .microphone, .screen, .notifications, .clipboard, .storage: "отказано"
            case .devices: "без названий и номеров"
            case .sensors: "датчиков нет"
            case .fingerprint: "вымышленное устройство"
            case .trackers: "заблокированы"
            }
        }
    }

    /// Сайты, известные жадным сбором данных об устройстве и поведении: им Ward предлагает изолятор.
    static let collectors: [String: String] = {
        var map: [String: String] = [:]
        for s in ["sberbank.ru", "sber.ru", "sberbank.com", "tbank.ru", "tinkoff.ru", "vtb.ru", "alfabank.ru", "gazprombank.ru",
                  "raiffeisen.ru", "psbank.ru", "sovcombank.ru", "pochtabank.ru", "rshb.ru", "open.ru", "mtsbank.ru", "ozonbank.ru"] {
            map[s] = "банк"
        }
        for s in ["gosuslugi.ru", "nalog.gov.ru", "mos.ru", "pfr.gov.ru", "sfr.gov.ru", "gibdd.ru"] { map[s] = "госсервис" }
        for s in ["ozon.ru", "wildberries.ru", "avito.ru", "aliexpress.ru", "aliexpress.com", "temu.com", "shein.com",
                  "megamarket.ru", "lamoda.ru", "dns-shop.ru", "mvideo.ru", "eldorado.ru"] { map[s] = "магазин" }
        for s in ["vk.com", "ok.ru", "mail.ru", "tiktok.com", "facebook.com", "instagram.com", "dzen.ru", "max.ru"] { map[s] = "соцсеть" }
        for s in ["mts.ru", "megafon.ru", "beeline.ru", "tele2.ru", "t2.ru", "yota.ru"] { map[s] = "оператор связи" }
        for s in ["taxi.yandex.ru", "go.yandex", "citymobil.ru", "delivery-club.ru", "samokat.ru", "kuper.ru"] { map[s] = "доставка" }
        return map
    }()

    static func category(of site: String) -> String? { collectors[site] }

    /// Скрипт изолятора — в мире страницы, до её собственных скриптов, во всех фреймах.
    /// `event` — имя сигнала (см. `relay`): страница не знает его и не может подделать.
    /// `deny` — изолятор: отказ во всех разрешениях. Без него (корпорация, маска) — только вымышленное
    /// устройство и огрублённое время.
    static func script(_ legend: Legend, event: String, deny: Bool = true) -> String {
        source
            .replacingOccurrences(of: "__WARD_DENY__", with: deny ? "true" : "false")
            .replacingOccurrences(of: "__WARD_EVENT__", with: event)
    }

    /// Переносит сигналы из мира страницы в мир Ward и дальше — в приложение.
    static func relay(token: String) -> String {
        """
        (() => {
          for (const what of [\(Attempt.allCases.map { "'\($0.rawValue)'" }.joined(separator: ", "))]) {
            document.addEventListener('\(token)-iso-' + what, () => {
              try { window.webkit.messageHandlers.wardEvent.postMessage({ type: 'isolation', what }); } catch (e) {}
            }, true);
          }
        })();
        """
    }

    static let source = #"""
    (() => {
      'use strict';
      const EVENT = '__WARD_EVENT__';
      const doc = document;
      const dispatch = EventTarget.prototype.dispatchEvent;
      const NativeEvent = Event;
      const signal = (what) => { try { dispatch.call(doc, new NativeEvent(EVENT + '-iso-' + what)); } catch (e) {} };
      const nativeToString = Function.prototype.toString;
      const masks = new WeakMap();
      const mask = (fn, name) => { masks.set(fn, 'function ' + name + '() {\n    [native code]\n}'); return fn; };
      const patchedToString = { toString() { return masks.has(this) ? masks.get(this) : nativeToString.call(this); } }.toString;
      mask(patchedToString, 'toString');
      try { Object.defineProperty(Function.prototype, 'toString', { value: patchedToString, configurable: true, writable: true }); } catch (e) {}

      const method = (target, key, impl) => {
        if (!target) return;
        const desc = Object.getOwnPropertyDescriptor(target, key);
        if (!desc || typeof desc.value !== 'function') return;
        const wrapped = { [key](...args) { return impl.apply(this, args); } }[key];
        Object.defineProperty(wrapped, 'length', { value: desc.value.length, configurable: true });
        mask(wrapped, key);
        Object.defineProperty(target, key, { ...desc, value: wrapped });
      };
      const getter = (target, key, value) => {
        if (!target) return;
        const desc = Object.getOwnPropertyDescriptor(target, key);
        if (!desc || !desc.get) return;
        const get = { get [key]() { return typeof value === 'function' ? value.call(this) : value; } };
        const fn = Object.getOwnPropertyDescriptor(get, key).get;
        masks.set(fn, (() => {
          try { return nativeToString.call(Object.getOwnPropertyDescriptor(Navigator.prototype, 'userAgent').get).replace('userAgent', key); }
          catch (e) { return 'function ' + key + '() {\n    [native code]\n}'; }
        })());
        Object.defineProperty(target, key, { ...desc, get: fn });
      };
      const denied = (name) => new DOMException('The request is not allowed by the user agent or the platform in the current context.', name || 'NotAllowedError');

      // Время: по ритму нажатий клавиш и точным таймерам узнают человека и устройство. Событиям — точность 8 мс,
      // performance.now — 1 мс: для сайтов незаметно, для «биометрии печати» — шум.
      const stamp = Object.getOwnPropertyDescriptor(Event.prototype, 'timeStamp');
      if (stamp && stamp.get) getter(Event.prototype, 'timeStamp', function () { return Math.floor(stamp.get.call(this) / 8) * 8; });
      if (typeof Performance !== 'undefined') {
        const now = Performance.prototype.now;
        method(Performance.prototype, 'now', function () { return Math.floor(now.call(this)); });
      }

      if (!__WARD_DENY__) return;

      // Отказы выглядят так, будто вы закрыли запрос, не ответив: у сайта обычное состояние «спросить»
      // (как в чистом Safari), а не «запрещено всё подряд» — такой набор сам по себе выдаёт подделку,
      // и защита от ботов (YouTube, банки) перестаёт пускать.

      // Геопозиция: ошибка «доступ не дан», как при закрытом запросе.
      if (typeof Geolocation !== 'undefined') {
        const fail = (error) => {
          signal('location');
          if (typeof error === 'function') setTimeout(() => error({ code: 1, message: 'User denied Geolocation', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 }), 30);
        };
        method(Geolocation.prototype, 'getCurrentPosition', function (ok, error) { fail(error); });
        method(Geolocation.prototype, 'watchPosition', function (ok, error) { fail(error); return 1; });
      }

      // Камера, микрофон, экран: отказ. Список устройств — настоящий «без разрешения»: WebKit и так отдаёт
      // его без названий и идентификаторов, а пустой список выглядел бы странно.
      if (typeof MediaDevices !== 'undefined') {
        method(MediaDevices.prototype, 'getUserMedia', function (c) {
          signal(c && c.video ? 'camera' : 'microphone');
          if (c && c.video && c.audio) signal('microphone');
          return new Promise((resolve, reject) => setTimeout(() => reject(denied()), 40));
        });
        method(MediaDevices.prototype, 'getDisplayMedia', function () { signal('screen'); return Promise.reject(denied()); });
        const enumerate = MediaDevices.prototype.enumerateDevices;
        if (enumerate) method(MediaDevices.prototype, 'enumerateDevices', function () { signal('devices'); return enumerate.call(this); });
      }

      // Уведомления: запрос закрыт без ответа — «default», подписка на push не выдаётся.
      if (typeof Notification !== 'undefined' && typeof Notification.requestPermission === 'function') {
        const request = function (callback) {
          signal('notifications');
          const answer = Notification.permission === 'granted' ? 'default' : Notification.permission;
          if (typeof callback === 'function') setTimeout(() => callback(answer), 0);
          return Promise.resolve(answer);
        };
        try { Object.defineProperty(Notification, 'requestPermission', { value: mask(request, 'requestPermission'), configurable: true, writable: true }); } catch (e) {}
      }
      if (typeof PushManager !== 'undefined') {
        method(PushManager.prototype, 'subscribe', function () { signal('notifications'); return Promise.reject(denied()); });
      }

      // Буфер обмена: записывать можно (кнопки «Скопировать»), читать — нет. Обычная вставка ⌘V работает.
      if (typeof Clipboard !== 'undefined') {
        method(Clipboard.prototype, 'readText', function () { signal('clipboard'); return Promise.reject(denied()); });
        method(Clipboard.prototype, 'read', function () { signal('clipboard'); return Promise.reject(denied()); });
      }

      // Датчики движения: на Mac их нет и так; если сайт всё же спросит — отказ.
      for (const name of ['DeviceMotionEvent', 'DeviceOrientationEvent']) {
        const C = window[name];
        if (C && typeof C.requestPermission === 'function') {
          try { Object.defineProperty(C, 'requestPermission', { value: mask(function () { signal('sensors'); return Promise.resolve('denied'); }, 'requestPermission'), configurable: true }); } catch (e) {}
        }
      }

      // Хранилище из чужих фреймов: встроенный во фрейм сайт не получает доступа к своим cookie. Сама страница
      // работает со своим хранилищем как обычно.
      if (typeof Document !== 'undefined' && window !== window.top) {
        method(Document.prototype, 'requestStorageAccess', function () { signal('storage'); return Promise.reject(denied()); });
      }
      if (typeof StorageManager !== 'undefined') {
        method(StorageManager.prototype, 'persist', function () { signal('storage'); return Promise.resolve(false); });
      }
    })();
    """#
}
