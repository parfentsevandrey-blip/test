import CryptoKit
import Foundation

/// Изолятор: сайт, который собирает о вас всё, до чего дотянется (банки, госуслуги, маркетплейсы, соцсети),
/// живёт в отдельной «банке».
///
/// - Своё постоянное хранилище: вход и «доверенное устройство» сохраняются, но ни один другой сайт и ни один
///   контейнер этих cookie и данных не видят — и сам сайт не видит ничего за пределами банки.
/// - Вымышленное устройство («легенда»): экран, число ядер, память, видеокарта и шум отпечатка выдуманы,
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

    /// Вымышленное устройство для сайта. Значения — из самых распространённых у пользователей Mac,
    /// чтобы «легенда» не выделялась; постоянны для пары «сайт + секрет изолятора».
    nonisolated struct Legend: Sendable, Equatable {
        var screenWidth: Int
        var screenHeight: Int
        var cores: Int
        var memory: Int
        /// Секрет шума отпечатка: постоянный, чтобы сайт видел одно и то же «устройство».
        var noiseSecret: String
    }

    static func legend(site: String, secret: String) -> Legend {
        let digest = Array(SHA256.hash(data: Data("\(secret)|\(site)".utf8)))
        let screens = [(1440, 900), (1512, 982), (1680, 1050), (1728, 1117), (1920, 1080), (2560, 1440)]
        let screen = screens[Int(digest[0]) % screens.count]
        let cores = [8, 8, 10, 12][Int(digest[1]) % 4]
        return Legend(screenWidth: screen.0, screenHeight: screen.1, cores: cores, memory: 8,
                      noiseSecret: digest[2..<14].map { String(format: "%02x", $0) }.joined())
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
            case .devices: "пустой список"
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
    static func script(_ legend: Legend, event: String) -> String {
        source
            .replacingOccurrences(of: "__WARD_EVENT__", with: event)
            .replacingOccurrences(of: "__WARD_SW__", with: String(legend.screenWidth))
            .replacingOccurrences(of: "__WARD_SH__", with: String(legend.screenHeight))
            .replacingOccurrences(of: "__WARD_CORES__", with: String(legend.cores))
            .replacingOccurrences(of: "__WARD_MEM__", with: String(legend.memory))
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
        mask(fn, 'get ' + key);
        Object.defineProperty(target, key, { ...desc, get: fn });
      };
      const denied = (name) => new DOMException('The request is not allowed by the user agent or the platform in the current context.', name || 'NotAllowedError');

      // Устройство из «легенды»: правдоподобный Mac, а не ваш.
      const W = __WARD_SW__, H = __WARD_SH__;
      getter(Navigator.prototype, 'hardwareConcurrency', __WARD_CORES__);
      if ('deviceMemory' in Navigator.prototype) getter(Navigator.prototype, 'deviceMemory', __WARD_MEM__);
      getter(Screen.prototype, 'width', W);
      getter(Screen.prototype, 'height', H);
      getter(Screen.prototype, 'availWidth', W);
      getter(Screen.prototype, 'availHeight', H - 25);
      getter(Screen.prototype, 'availTop', 25);
      getter(Screen.prototype, 'availLeft', 0);
      getter(Screen.prototype, 'colorDepth', 30);
      getter(Screen.prototype, 'pixelDepth', 30);

      // Геопозиция: «доступ запрещён», как если бы вы нажали «Не разрешать».
      if (typeof Geolocation !== 'undefined') {
        const fail = (error) => {
          signal('location');
          if (typeof error === 'function') setTimeout(() => error({ code: 1, message: 'User denied Geolocation', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 }), 30);
        };
        method(Geolocation.prototype, 'getCurrentPosition', function (ok, error) { fail(error); });
        method(Geolocation.prototype, 'watchPosition', function (ok, error) { fail(error); return 1; });
      }

      // Камера, микрофон, экран, список устройств.
      if (typeof MediaDevices !== 'undefined') {
        method(MediaDevices.prototype, 'getUserMedia', function (c) {
          signal(c && c.video ? 'camera' : 'microphone');
          if (c && c.video && c.audio) signal('microphone');
          return Promise.reject(denied());
        });
        method(MediaDevices.prototype, 'getDisplayMedia', function () { signal('screen'); return Promise.reject(denied()); });
        method(MediaDevices.prototype, 'enumerateDevices', function () { signal('devices'); return Promise.resolve([]); });
      }

      // Уведомления.
      if (typeof Notification !== 'undefined') {
        try { Object.defineProperty(Notification, 'permission', { get: mask(function () { return 'denied'; }, 'get permission'), configurable: true }); } catch (e) {}
        const request = function () { signal('notifications'); return Promise.resolve('denied'); };
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

      // Датчики движения: у «легенды» их нет.
      const sensorEvents = new Set(['devicemotion', 'deviceorientation', 'deviceorientationabsolute']);
      method(EventTarget.prototype, 'addEventListener', (() => {
        const original = EventTarget.prototype.addEventListener;
        return function (type, ...rest) {
          if (this === window && sensorEvents.has(String(type).toLowerCase())) { signal('sensors'); return; }
          return original.call(this, type, ...rest);
        };
      })());
      for (const name of ['DeviceMotionEvent', 'DeviceOrientationEvent']) {
        const C = window[name];
        if (C && typeof C.requestPermission === 'function') {
          try { Object.defineProperty(C, 'requestPermission', { value: mask(function () { signal('sensors'); return Promise.resolve('denied'); }, 'requestPermission'), configurable: true }); } catch (e) {}
        }
      }

      // Хранилище из чужих фреймов и «вечное» хранилище.
      if (typeof Document !== 'undefined') {
        method(Document.prototype, 'requestStorageAccess', function () { signal('storage'); return Promise.reject(denied()); });
        method(Document.prototype, 'hasStorageAccess', function () { return Promise.resolve(false); });
      }
      if (typeof StorageManager !== 'undefined') {
        method(StorageManager.prototype, 'persist', function () { signal('storage'); return Promise.resolve(false); });
      }

      // Опрос разрешений отвечает так же, как отвечают сами функции.
      if (typeof Permissions !== 'undefined') {
        const states = { geolocation: 'denied', notifications: 'denied', push: 'denied', camera: 'denied', microphone: 'denied',
                         'clipboard-read': 'denied', 'persistent-storage': 'denied', 'storage-access': 'denied',
                         accelerometer: 'denied', gyroscope: 'denied', magnetometer: 'denied', 'display-capture': 'denied' };
        const original = Permissions.prototype.query;
        method(Permissions.prototype, 'query', function (descriptor) {
          const name = descriptor && descriptor.name;
          if (name && states[name]) {
            const status = Object.create(typeof PermissionStatus !== 'undefined' ? PermissionStatus.prototype : Object.prototype, {
              state: { value: states[name], enumerable: true }, name: { value: name, enumerable: true }, onchange: { value: null, writable: true },
            });
            return Promise.resolve(status);
          }
          return original.call(this, descriptor);
        });
      }
    })();
    """#
}
