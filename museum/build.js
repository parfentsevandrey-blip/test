#!/usr/bin/env node
//
// Склейка музея в один самодостаточный index.html.
//
// Фрагменты пишут разные авторы, поэтому порядок задан здесь явно и не зависит
// от того, в каком порядке файлы легли на диск:
//
//   <style>   00-core.css, затем recon-*.css в алфавитном порядке
//   <body>    10-shell.html
//   <script>  пролог с window.MUSEUM -> data-*.js -> recon-*.js -> 90-core.js
//
// Ядро выполняется последним: к этому моменту MUSEUM.exhibits уже заполнен.

'use strict'

const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const BUILD = path.join(ROOT, 'build')
const OUT = path.join(__dirname, 'index.html')

const read = (f) => fs.readFileSync(path.join(BUILD, f), 'utf8')
const exists = (f) => fs.existsSync(path.join(BUILD, f))

const listed = fs.existsSync(BUILD) ? fs.readdirSync(BUILD).sort() : []
const pick = (re) => listed.filter((f) => re.test(f))

const cssFiles = ['00-core.css', ...pick(/^recon-.*\.css$/)]
const dataFiles = pick(/^data-.*\.js$/)
const reconFiles = pick(/^recon-.*\.js$/)

const missing = ['00-core.css', '10-shell.html', '90-core.js'].filter((f) => !exists(f))
if (missing.length) {
  console.error('Нет обязательных фрагментов: ' + missing.join(', '))
  process.exit(1)
}
if (!dataFiles.length) {
  console.error('Нет ни одного data-*.js — собирать нечего.')
  process.exit(1)
}

// Закрывающий тег внутри содержимого разорвал бы <style>/<script>. Ловим это на
// сборке, а не в браузере: молча экранировать чужой код опаснее, чем упасть.
const guard = (name, body, tag) => {
  const re = new RegExp('</\\s*' + tag, 'i')
  if (re.test(body)) throw new Error(`${name} содержит закрывающий </${tag} — разорвёт документ`)
  return body
}

const banner = (f) => `\n/* ${'='.repeat(8)} ${f} ${'='.repeat(8)} */\n`

const css = cssFiles
  .filter(exists)
  .map((f) => banner(f) + guard(f, read(f), 'style'))
  .join('\n')

const shell = guard('10-shell.html', read('10-shell.html'), 'script')

const prologue = `
// Реестр объявляется до всех фрагментов: data-*.js и recon-*.js только дополняют его.
window.MUSEUM = { exhibits: [], interludes: [], recon: {}, vestibule: null };
`

const js = [
  prologue,
  ...dataFiles.map((f) => banner(f) + guard(f, read(f), 'script')),
  ...reconFiles.map((f) => banner(f) + guard(f, read(f), 'script')),
  banner('90-core.js') + guard('90-core.js', read('90-core.js'), 'script'),
].join('\n')

const html = `<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Музей дизайна мобильных ОС · iOS и Android, 2007 — сегодня</title>
<meta name="description" content="Интерактивный музей истории дизайна мобильных интерфейсов. Все экраны воссозданы CSS и SVG, без единого скриншота.">
<meta name="color-scheme" content="dark">
<style>
${css}
</style>
</head>
<body>
${shell}
<script>
${js}
</script>
</body>
</html>
`

fs.writeFileSync(OUT, html)

const kb = (n) => (n / 1024).toFixed(1) + ' КБ'
console.log('Собрано: ' + path.relative(ROOT, OUT))
console.log('  CSS   ' + cssFiles.filter(exists).length + ' файлов, ' + kb(css.length))
console.log('  JS    ' + (dataFiles.length + reconFiles.length + 1) + ' файлов, ' + kb(js.length))
console.log('  Итого ' + kb(html.length) + ', строк: ' + html.split('\n').length)
