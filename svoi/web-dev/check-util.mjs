#!/usr/bin/env node
// Unit-ish checks for the pure helpers in internal/web/ui/js/util.js
// (device-name → DNS label port, name rule, path and address helpers).
//
//   node web-dev/check-util.mjs
import assert from "node:assert/strict";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const u = await import(pathToFileURL(path.join(here, "../internal/web/ui/js/util.js")).href);

let n = 0;
function eq(actual, expected, what) {
  assert.equal(actual, expected, `${what}: got ${JSON.stringify(actual)}, want ${JSON.stringify(expected)}`);
  n++;
}

// --- dnsLabel: the four examples from docs/UI-API.md → "Device names"
eq(u.dnsLabel("Кухонный ноутбук"), "kukhonnyy-noutbuk", "docs example 1");
eq(u.dnsLabel("Мой NAS (дача)"), "moy-nas-dacha", "docs example 2");
eq(u.dnsLabel("Café Zürich"), "cafe-zurich", "docs example 3");
eq(u.dnsLabel("ЖЁСТКИЙ ДИСК"), "zhyostkiy-disk", "docs example 4");

// --- dnsLabel: the cases of TestSanitizeName in internal/identity/identity_test.go
eq(u.dnsLabel("My Laptop!"), "my-laptop", "go case: punctuation");
eq(u.dnsLabel("  NAS  "), "nas", "go case: surrounding blanks");
eq(u.dnsLabel("Телефон"), "telefon", "go case: Cyrillic word");
eq(u.dnsLabel("Щука, ёж & Юля"), "shchuka-yozh-yulya", "go case: mixed punctuation");
eq(u.dnsLabel("日本語"), "device", "go case: nothing transliterable");
eq(u.dnsLabel("a--b__c"), "a-b-c", "go case: runs collapse");
eq(u.dnsLabel("x".repeat(80)), "x".repeat(32), "go case: cut to 32");

// --- dnsLabel: the rest of the rules
eq(u.dnsLabel(""), "device", "empty → device");
eq(u.dnsLabel("   "), "device", "blank → device");
eq(u.dnsLabel("!!!"), "device", "nothing usable → device");
eq(u.dnsLabel("ъь"), "device", "only dropped letters → device");
eq(u.dnsLabel("nas"), "nas", "already a label");
eq(u.dnsLabel("Home-Server.01"), "home-server-01", "dot becomes a hyphen");
eq(u.dnsLabel("  --nas__2--  "), "nas-2", "runs collapse, ends trimmed");
eq(u.dnsLabel("Подъезд"), "podezd", "ъ dropped");
eq(u.dnsLabel("Щука и ёж"), "shchuka-i-yozh", "multi-letter mappings");
eq(u.dnsLabel("Цех Хью Чая"), "tsekh-khyu-chaya", "ts / kh / yu / ch / ya");
eq(u.dnsLabel("Їжак Ґанок Єнот"), "yizhak-ganok-yenot", "Ukrainian letters");
eq(u.dnsLabel("Ўсё"), "usyo", "Belarusian ў");
eq(u.dnsLabel("Комната 42"), "komnata-42", "digits kept");
eq(u.dnsLabel("ёж"), "yozh", "decomposed ё is composed first (NFC)");
eq(u.dnsLabel("йод"), "yod", "decomposed й is composed first (NFC)");
eq(u.dnsLabel("İstanbul"), "istanbul", "lone combining mark after lowercasing is dropped");
eq(u.dnsLabel("Ålesund Øst"), "alesund-st", "accents stripped; ø has no base letter → hyphen");
eq(u.dnsLabel("東京 laptop"), "laptop", "other scripts become hyphens and get trimmed");
const long = u.dnsLabel("очень длинное имя для домашнего сервера на даче");
assert.ok(long.length <= 32, "max 32 chars"); n++;
assert.ok(!long.endsWith("-") && !long.startsWith("-"), "no hyphen at the ends after the cut"); n++;
eq(long, "ochen-dlinnoe-imya-dlya-domashne", "cut at 32");
eq(u.dnsLabel("a".repeat(31) + " b"), "a".repeat(31), "trailing hyphen removed after the cut");

// --- uniqueLabel mirrors UniqueName (TestSanitizeName's last case and the 32-char cut)
eq(u.uniqueLabel("nas", new Set(["nas", "nas-2"])), "nas-3", "go case: UniqueName");
eq(u.uniqueLabel("nas", ["phone"]), "nas", "free name kept");
eq(u.uniqueLabel("x".repeat(32), ["x".repeat(32)]), "x".repeat(30) + "-2", "suffix fits in 32 chars");

// --- the form rule (after whitespace → "-") stays as before
eq(u.normalizeDeviceName("  Кухонный  ноутбук "), "Кухонный-ноутбук", "whitespace → single hyphen");
eq(u.DEVICE_NAME_RE.test(u.normalizeDeviceName("Кухонный ноутбук")), true, "Cyrillic with a space is accepted");
eq(u.DEVICE_NAME_RE.test(u.normalizeDeviceName("nas.home_2")), true, "dot / underscore accepted");
eq(u.DEVICE_NAME_RE.test(u.normalizeDeviceName("Мой NAS (дача)")), false, "parentheses are refused by the form");
eq(u.DEVICE_NAME_RE.test(u.normalizeDeviceName("-nas")), false, "must start with a letter or digit");
eq(u.DEVICE_NAME_RE.test("x".repeat(64)), false, "at most 63 chars");

// --- paths and addresses
eq(u.joinPath("/", "a"), "/a", "joinPath root");
eq(u.joinPath("/a/b/", "c"), "/a/b/c", "joinPath trailing slash");
eq(u.parentPath("/a/b"), "/a", "parentPath");
eq(u.parentPath("/a"), "/", "parentPath to root");
eq(u.baseName("/a/b.txt"), "b.txt", "baseName");
eq(u.isValidHostPort("127.0.0.1:22"), true, "host:port");
eq(u.isValidHostPort("[::1]:8080"), true, "IPv6 host:port");
eq(u.isValidHostPort("localhost"), false, "port required");
eq(u.isValidHostPort("127.0.0.1:70000"), false, "port range");

console.log(`util checks: ${n} assertions passed`);
