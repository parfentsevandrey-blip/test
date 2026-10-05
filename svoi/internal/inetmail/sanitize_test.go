package inetmail

import (
	"regexp"
	"strings"
	"testing"

	"golang.org/x/net/html"
)

func TestSanitizeKeepsWhatReads(t *testing.T) {
	in := `<html><head><title>t</title><style>p{color:red}</style></head><body bgcolor="#fff">` +
		`<h1 style="color:#336699;font-size:20px">Hello &amp; welcome</h1><p align="center">A <b>bold</b>, <i>italic</i> and <a href="https://example.com/a?b=1&c=2">link</a>.</p>` +
		`<table width="100%" cellpadding="4"><tr><td colspan="2" style="padding: 4px; border: 1px solid #ccc">cell</td></tr></table>` +
		`<ul><li>one</li><li>two</li></ul><img src="cid:logo@x" width="40" alt="logo"></body></html>`
	got := SanitizeHTML(in, SanitizeOptions{}).HTML
	for _, want := range []string{
		`<h1 style="color: #336699; font-size: 20px">Hello &amp; welcome</h1>`, `<p align="center">`, `<b>bold</b>`,
		`<a href="https://example.com/a?b=1&amp;c=2" target="_blank" rel="noopener noreferrer nofollow">link</a>`,
		`<table width="100%" cellpadding="4">`, `<td colspan="2" style="padding: 4px; border: 1px solid #ccc">cell</td>`,
		`<li>one</li>`, `<img src="cid:logo@x" width="40" alt="logo">`,
	} {
		if !strings.Contains(got, want) {
			t.Errorf("%q is missing from\n%s", want, got)
		}
	}
	for _, bad := range []string{"<html", "<head", "<body", "<style", "<title", "bgcolor"} {
		if strings.Contains(got, bad) {
			t.Errorf("%q must not be there:\n%s", bad, got)
		}
	}
	if strings.Contains(got, ">t<") {
		t.Errorf("the title text must go with its tag:\n%s", got)
	}
}

func TestSanitizeVectors(t *testing.T) {
	cases := []struct{ name, in string }{
		{"script", `<script>alert(1)</script><p>x</p>`},
		{"script in a deeper place", `<div><script src="//evil/x.js"></script>y</div>`},
		{"unclosed script", `<p>a</p><script>alert(1)`},
		{"onerror", `<img src=x onerror=alert(1)>`},
		{"onclick", `<p onclick="alert(1)" onmouseover=alert(2)>x</p>`},
		{"javascript link", `<a href="javascript:alert(1)">x</a>`},
		{"javascript link with a tab", `<a href=" jav&#x09;ascript:alert(1)">x</a>`},
		{"javascript link with a newline and entities", "<a href=\"java&#10;script&colon;alert(1)\">x</a>"},
		{"data link", `<a href="data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==">x</a>`},
		{"vbscript", `<a href="VBScript:msgbox(1)">x</a>`},
		{"svg", `<svg onload=alert(1)><script>alert(2)</script></svg>`},
		{"math", `<math><mi xlink:href="javascript:alert(1)">x</mi></math>`},
		{"iframe", `<iframe src="https://evil/"></iframe><iframe srcdoc="<script>alert(1)</script>"></iframe>`},
		{"object and embed", `<object data="x.swf"></object><embed src="x.swf">`},
		{"form", `<form action="https://evil/steal"><input name=p><button>Go</button></form>`},
		{"style element", `<style>@import url(https://evil/x.css); body{background:url(https://evil/p.gif)}</style>`},
		{"style attribute with url", `<div style="background:url(https://evil/p.gif)">x</div>`},
		{"style attribute with expression", `<div style="width:expression(alert(1))">x</div>`},
		{"style attribute with a position", `<div style="position:fixed;top:0;left:0;width:100%;height:100%">x</div>`},
		{"meta refresh", `<meta http-equiv="refresh" content="0;url=https://evil/">x`},
		{"base", `<base href="https://evil/"><a href="/x">x</a>`},
		{"link", `<link rel="stylesheet" href="https://evil/x.css">`},
		{"noscript trick", `<noscript><p title="</noscript><img src=x onerror=alert(1)>">`},
		{"title trick", `<title><img src=x onerror=alert(1)></title>`},
		{"textarea trick", `<textarea><img src=x onerror=alert(1)></textarea>`},
		{"xmp", `<xmp><img src=x onerror=alert(1)></xmp>`},
		{"comment trick", `<!--><img src=x onerror=alert(1)>-->`},
		{"cdata", `<![CDATA[<img src=x onerror=alert(1)>]]>`},
		{"nested quotes", `<a href="https://example.com" title='" onmouseover="alert(1)'>x</a>`},
		{"uppercase", `<IMG SRC=x ONERROR=alert(1)><SCRIPT>alert(1)</SCRIPT>`},
		{"null byte", "<scr\x00ipt>alert(1)</scr\x00ipt>"},
		{"template", `<template><script>alert(1)</script></template>`},
		{"srcset", `<img srcset="https://evil/p.gif 1x" src="cid:a">`},
		{"video", `<video src="https://evil/v.mp4" onerror=alert(1)></video>`},
		{"details toggle", `<details open ontoggle=alert(1)><summary>x</summary></details>`},
		{"a with target", `<a href="https://example.com" target="_top">x</a>`},
	}
	banned := regexpMust(`(?i)(<script|<style|<iframe|<object|<embed|<svg|<math|<form|<input|<meta|<link|<base|<video|<template|<textarea|<title|javascript:|vbscript:|data:text|expression\(|url\(|position:|srcset|evil|@import|<!--|\x00)`)
	okAttr := setOf("href", "target", "rel", "title", "src", "alt", "width", "height", "style", "align", "colspan")
	for _, c := range cases {
		got := SanitizeHTML(c.in, SanitizeOptions{}).HTML
		if m := banned.FindString(got); m != "" {
			t.Errorf("%s: %q survived in\n  in:  %s\n  out: %s", c.name, m, c.in, got)
		}
		// whatever is in the output is made of attributes that were meant to be there
		z := html.NewTokenizer(strings.NewReader(got))
		for tt := z.Next(); tt != html.ErrorToken; tt = z.Next() {
			if tt != html.StartTagToken && tt != html.SelfClosingTagToken {
				continue
			}
			for _, a := range z.Token().Attr {
				if !okAttr[a.Key] {
					t.Errorf("%s: the attribute %q must not be there:\n  out: %s", c.name, a.Key, got)
				}
			}
		}
	}
	// what stays in the odd cases is only what was harmless
	if got := SanitizeHTML(`<form action="https://evil/"><input name=p><button>Go</button></form>text`, SanitizeOptions{}).HTML; !strings.Contains(got, "Gotext") {
		t.Errorf("the words inside a form stay (as words): %q", got)
	}
	if got := SanitizeHTML(`<a href="https://example.com" target="_top">x</a>`, SanitizeOptions{}).HTML; strings.Count(got, "target=") != 1 || !strings.Contains(got, `target="_blank"`) {
		t.Errorf("links always open in a new window: %q", got)
	}
}

func TestSanitizeImages(t *testing.T) {
	in := `<img src="https://tracker.example/p.gif" alt="x"><img src="http://tracker.example/q.gif"><img src="/relative.png">` +
		`<img src="data:image/png;base64,iVBORw0KGgo="><img src="data:image/svg+xml;base64,PHN2Zz4="><img src="cid:ok@x">`
	res := SanitizeHTML(in, SanitizeOptions{})
	if res.Blocked != 4 || res.Remote != 0 {
		t.Errorf("blocked %d remote %d", res.Blocked, res.Remote)
	}
	if strings.Contains(res.HTML, "tracker.example") || strings.Contains(res.HTML, "relative.png") || strings.Contains(res.HTML, "svg") {
		t.Errorf("nothing from the Internet: %s", res.HTML)
	}
	if !strings.Contains(res.HTML, `src="data:image/png;base64,iVBORw0KGgo="`) || !strings.Contains(res.HTML, `src="cid:ok@x"`) {
		t.Errorf("the pictures that belong to the letter stay: %s", res.HTML)
	}
	res = SanitizeHTML(in, SanitizeOptions{RemoteImages: true})
	if res.Remote != 1 || !strings.Contains(res.HTML, `src="https://tracker.example/p.gif"`) || strings.Contains(res.HTML, "http://tracker") {
		t.Errorf("https pictures may stay when asked for, http never: remote %d %s", res.Remote, res.HTML)
	}
}

func TestSanitizeStructure(t *testing.T) {
	// tags are closed in the right order and what was left open is closed
	got := SanitizeHTML(`<div><p>one<b>two</div>three`, SanitizeOptions{}).HTML
	if got != `<div><p>one<b>two</b></p></div>three` {
		t.Errorf("got %s", got)
	}
	// an end tag that was never opened is ignored
	if got := SanitizeHTML(`x</b></div>y`, SanitizeOptions{}).HTML; got != "xy" {
		t.Errorf("got %s", got)
	}
	// the nesting is bounded
	deep := strings.Repeat("<div>", 500) + "x" + strings.Repeat("</div>", 500)
	if got := SanitizeHTML(deep, SanitizeOptions{}).HTML; strings.Count(got, "<div>") > maxNesting || !strings.Contains(got, "x") {
		t.Errorf("depth: %d", strings.Count(got, "<div>"))
	}
	// text is escaped
	if got := SanitizeHTML(`1 &lt; 2 &amp; 3 > 2 "quoted"`, SanitizeOptions{}).HTML; strings.ContainsAny(got, "<>") && !strings.Contains(got, "&lt;") {
		t.Errorf("got %s", got)
	}
}

func TestStyleFilter(t *testing.T) {
	for in, want := range map[string]string{
		"color: red; font-size: 12px":                                "color: red; font-size: 12px",
		"COLOR:Red !important":                                       "color: Red",
		"background: #fff url(x.png)":                                "",
		"background-color: rgb(1, 2, 3)":                             "background-color: rgb(1, 2, 3)",
		"font-family: 'Segoe UI', Arial, sans-serif":                 "font-family: 'Segoe UI', Arial, sans-serif",
		"margin: 0 auto; behavior: url(x.htc)":                       "margin: 0 auto",
		"width: calc(100% - 10px)":                                   "width: calc(100% - 10px)",
		"background-image: url(https://evil/)":                       "",
		"color: red; position: absolute; z-index: 9":                 "color: red",
		"content: 'x'":                                               "",
		"color: \\72 ed":                                             "",
		"font-family: a;b:c":                                         "font-family: a",
		"width: 10px; height: var(--x)":                              "width: 10px",
		"border: 1px solid #ccc; border-radius: 4px; display: block": "border: 1px solid #ccc; border-radius: 4px; display: block",
	} {
		if got := cleanStyle(in); got != want {
			t.Errorf("%q: got %q, want %q", in, got, want)
		}
	}
}

func TestHTMLToText(t *testing.T) {
	in := `<html><head><title>Ignore</title><style>p{}</style></head><body><h1>Title</h1><p>First   paragraph
with a <a href="https://x.example">link</a> and <b>bold</b> text.</p><ul><li>one</li><li>two</li></ul>` +
		`<p>Line one<br>line two</p><script>alert(1)</script><table><tr><td>a</td><td>b</td></tr></table>&amp; end &lt;3</body></html>`
	got := HTMLToText(in)
	want := "Title\n\nFirst paragraph with a link and bold text.\n\n• one\n• two\n\nLine one\nline two\n\na b\n\n& end <3"
	if got != want {
		t.Errorf("got:\n%q\nwant:\n%q", got, want)
	}
	if strings.Contains(got, "alert") || strings.Contains(got, "Ignore") {
		t.Errorf("scripts and titles are not text: %q", got)
	}
}

func regexpMust(s string) *regexp.Regexp { return regexp.MustCompile(s) }
