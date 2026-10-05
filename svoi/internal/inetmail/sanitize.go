package inetmail

import (
	"net/url"
	"regexp"
	"strings"

	"golang.org/x/net/html"
)

// The HTML of a letter is somebody else's code. SanitizeHTML keeps what makes a letter readable - text, paragraphs,
// lists, tables, links, colours, pictures that are part of the letter - and drops everything else: scripts, styles
// sheets, forms, frames, objects, event handlers, links that are not http(s), mailto or tel, pictures that would be
// fetched from somewhere on the Internet (they tell the sender that, and when, and from where the letter was opened).
// The interface shows the result in a sandboxed frame that may load nothing at all, so this is the second wall, not the
// only one.

var allowedTags = setOf(
	"a", "abbr", "acronym", "address", "article", "aside", "b", "bdi", "bdo", "big", "blockquote", "br", "caption", "center",
	"cite", "code", "col", "colgroup", "dd", "del", "dfn", "div", "dl", "dt", "em", "figcaption", "figure", "font", "footer",
	"h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "i", "img", "ins", "kbd", "li", "main", "mark", "nav", "ol", "p", "pre",
	"q", "s", "samp", "section", "small", "span", "strike", "strong", "sub", "summary", "sup", "table", "tbody", "td", "tfoot",
	"th", "thead", "time", "tr", "tt", "u", "ul", "var", "wbr",
)

// dropTags are removed together with what is inside them.
var dropTags = setOf(
	"script", "style", "title", "template", "noscript", "iframe", "object", "embed", "applet", "frame", "frameset", "noframes",
	"noembed", "xmp", "plaintext", "textarea", "select", "option", "optgroup", "svg", "math", "canvas", "audio", "video", "map",
	"datalist", "dialog", "link", "meta", "base", "param", "source", "track", "head",
)

var voidTags = setOf("br", "hr", "img", "col", "wbr", "link", "meta", "base", "param", "source", "track", "area", "input", "embed")

func setOf(names ...string) map[string]bool {
	m := make(map[string]bool, len(names))
	for _, n := range names {
		m[n] = true
	}
	return m
}

const (
	maxNesting      = 64
	maxHTMLOut      = 4 << 20
	maxDataImageLen = 2 << 20
)

var (
	numRe     = regexp.MustCompile(`^\d{1,4}(\.\d{1,2})?%?$`)
	colourRe  = regexp.MustCompile(`^(#[0-9a-fA-F]{3,8}|[a-zA-Z]{3,20}|rgba?\([0-9 ,.%/]{5,40}\))$`)
	faceRe    = regexp.MustCompile(`^[\p{L}\p{N} ,'_-]{1,100}$`)
	langRe    = regexp.MustCompile(`^[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8}){0,3}$`)
	cidRe     = regexp.MustCompile(`^cid:[A-Za-z0-9._@%+=~$!*'()-]{1,200}$`)
	dataImgRe = regexp.MustCompile(`^data:image/(png|jpeg|jpg|gif|webp|bmp);base64,[A-Za-z0-9+/=]+$`)
)

// SanitizeOptions tunes SanitizeHTML.
type SanitizeOptions struct {
	// RemoteImages lets https pictures stay (the person asked to see them); otherwise they are dropped.
	RemoteImages bool
}

// SanitizeResult is a cleaned fragment of HTML.
type SanitizeResult struct {
	HTML string
	// Blocked counts the pictures that were left out because they live on the Internet (or are not pictures at all).
	Blocked int
	// Remote counts the pictures on the Internet that were kept (SanitizeOptions.RemoteImages).
	Remote int
}

// SanitizeHTML cleans the HTML of a letter. The result is a fragment (no <html>, <head> or <body>), well formed, in
// which every attribute value is quoted and escaped.
func SanitizeHTML(src string, opt SanitizeOptions) SanitizeResult {
	var out strings.Builder
	var res SanitizeResult
	z := html.NewTokenizer(strings.NewReader(src))
	z.SetMaxBuf(1 << 20)
	var stack []string
	skip, skipDepth := "", 0
loop:
	for {
		if out.Len() > maxHTMLOut {
			break
		}
		switch z.Next() {
		case html.ErrorToken:
			break loop
		case html.TextToken:
			if skip == "" {
				out.WriteString(html.EscapeString(string(z.Text())))
			}
		case html.StartTagToken, html.SelfClosingTagToken:
			raw := z.Token() // (the attributes are read from the token: the tokenizer's own buffers are reused)
			tag := raw.Data
			selfClosing := raw.Type == html.SelfClosingTagToken
			if skip != "" {
				if tag == skip && !selfClosing {
					skipDepth++
				}
				continue
			}
			if dropTags[tag] {
				if !selfClosing && !voidTags[tag] {
					skip, skipDepth = tag, 1
				}
				continue
			}
			if !allowedTags[tag] || len(stack) >= maxNesting {
				continue // the tag goes, what is inside stays
			}
			out.WriteString("<" + tag)
			for _, a := range raw.Attr {
				if v, ok := cleanAttr(tag, strings.ToLower(a.Key), a.Val, opt, &res); ok {
					out.WriteString(" " + strings.ToLower(a.Key) + `="` + html.EscapeString(v) + `"`)
				}
			}
			if tag == "a" {
				out.WriteString(` target="_blank" rel="noopener noreferrer nofollow"`)
			}
			out.WriteString(">")
			if !voidTags[tag] {
				stack = append(stack, tag)
			}
		case html.EndTagToken:
			name, _ := z.TagName()
			tag := string(name)
			if skip != "" {
				if tag == skip {
					if skipDepth--; skipDepth == 0 {
						skip = ""
					}
				}
				continue
			}
			if !allowedTags[tag] || voidTags[tag] {
				continue
			}
			at := -1
			for i := len(stack) - 1; i >= 0; i-- {
				if stack[i] == tag {
					at = i
					break
				}
			}
			if at < 0 {
				continue
			}
			for i := len(stack) - 1; i >= at; i-- {
				out.WriteString("</" + stack[i] + ">")
			}
			stack = stack[:at]
		}
	}
	for i := len(stack) - 1; i >= 0; i-- {
		out.WriteString("</" + stack[i] + ">")
	}
	res.HTML = out.String()
	return res
}

// cleanAttr decides whether an attribute stays and in what form.
func cleanAttr(tag, key, val string, opt SanitizeOptions, res *SanitizeResult) (string, bool) {
	val = strings.TrimSpace(val)
	switch key {
	case "title":
		return clip(stripControl(val), 300), val != ""
	case "lang":
		return val, langRe.MatchString(val)
	case "dir":
		v := strings.ToLower(val)
		return v, v == "ltr" || v == "rtl" || v == "auto"
	case "style":
		v := cleanStyle(val)
		return v, v != ""
	case "align", "valign":
		v := strings.ToLower(val)
		switch v {
		case "left", "right", "center", "justify", "top", "middle", "bottom", "baseline":
			return v, true
		}
		return "", false
	case "width", "height", "border", "cellpadding", "cellspacing", "colspan", "rowspan", "start", "value", "size":
		return val, numRe.MatchString(val)
	case "bgcolor", "color":
		return val, colourRe.MatchString(val)
	case "nowrap":
		return "nowrap", true
	case "face":
		return val, tag == "font" && faceRe.MatchString(val)
	case "type":
		return val, tag == "ol" && (val == "1" || val == "a" || val == "A" || val == "i" || val == "I")
	case "alt":
		return clip(stripControl(val), 300), tag == "img"
	case "href":
		if tag != "a" {
			return "", false
		}
		return cleanHref(val)
	case "src":
		if tag != "img" {
			return "", false
		}
		return cleanImageSrc(val, opt, res)
	}
	return "", false
}

func stripControl(s string) string {
	return strings.Map(func(r rune) rune {
		if r < 32 || r == 127 {
			return ' '
		}
		return r
	}, s)
}

// cleanHref lets a link through if it goes somewhere a person may follow: a web page, a mailbox, a phone number, or a
// place in the same letter.
func cleanHref(v string) (string, bool) {
	// browsers ignore tabs, line breaks and spaces inside a scheme ("java\tscript:"), so they are removed before looking
	c := strings.Map(func(r rune) rune {
		if r <= 32 || r == 127 {
			return -1
		}
		return r
	}, v)
	if c == "" || len(c) > 2000 {
		return "", false
	}
	if strings.HasPrefix(c, "#") {
		return c, true
	}
	u, err := url.Parse(c)
	if err != nil {
		return "", false
	}
	switch strings.ToLower(u.Scheme) {
	case "http", "https":
		if u.Host == "" {
			return "", false
		}
	case "mailto", "tel":
	default:
		return "", false
	}
	return c, true
}

func cleanImageSrc(v string, opt SanitizeOptions, res *SanitizeResult) (string, bool) {
	c := strings.Map(func(r rune) rune {
		if r <= 32 || r == 127 {
			return -1
		}
		return r
	}, v)
	lc := strings.ToLower(c)
	switch {
	case strings.HasPrefix(lc, "cid:"):
		return c, cidRe.MatchString(c)
	case strings.HasPrefix(lc, "data:"):
		if len(c) <= maxDataImageLen && dataImgRe.MatchString(lc) {
			return c, true
		}
		res.Blocked++
		return "", false
	case strings.HasPrefix(lc, "https://") && opt.RemoteImages:
		if u, err := url.Parse(c); err == nil && u.Host != "" && len(c) <= 2000 {
			res.Remote++
			return c, true
		}
	}
	res.Blocked++
	return "", false
}

// cssProps are the style properties that stay; they are about how things look, never about what they load or where
// they sit on the page.
var cssProps = setOf(
	"color", "background-color", "background", "font", "font-family", "font-size", "font-style", "font-weight", "font-variant",
	"line-height", "letter-spacing", "word-spacing", "text-align", "text-decoration", "text-indent", "text-transform",
	"text-shadow", "white-space", "vertical-align", "direction", "unicode-bidi",
	"margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
	"padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
	"border", "border-top", "border-right", "border-bottom", "border-left", "border-color", "border-style", "border-width",
	"border-radius", "border-collapse", "border-spacing",
	"width", "height", "min-width", "max-width", "min-height", "max-height", "display", "float", "clear",
	"list-style", "list-style-type", "opacity", "table-layout", "word-break", "overflow-wrap", "box-sizing",
)

var cssValueRe = regexp.MustCompile(`^[\p{L}\p{N}\s#.,%()/'+*:_-]{1,200}$`)

// cleanStyle keeps the harmless declarations of a style attribute.
func cleanStyle(s string) string {
	if len(s) > 4000 {
		return ""
	}
	var kept []string
	for _, decl := range splitDecls(s) {
		i := strings.IndexByte(decl, ':')
		if i <= 0 {
			continue
		}
		prop := strings.ToLower(strings.TrimSpace(decl[:i]))
		val := strings.TrimSpace(strings.TrimSuffix(strings.TrimSpace(decl[i+1:]), "!important"))
		if !cssProps[prop] || val == "" || !cssValueRe.MatchString(val) {
			continue
		}
		lv := strings.ToLower(val)
		if strings.Contains(lv, "url(") || strings.Contains(lv, "expression") || strings.Contains(lv, "image") ||
			strings.Contains(lv, "javascript") || strings.Contains(lv, "var(") || strings.Contains(lv, "attr(") {
			continue
		}
		kept = append(kept, prop+": "+val)
	}
	return strings.Join(kept, "; ")
}

// splitDecls splits "a: b; c: d(e;f)" at the semicolons that are not inside brackets or quotes.
func splitDecls(s string) []string {
	var out []string
	depth, start := 0, 0
	var quote byte
	for i := 0; i < len(s); i++ {
		c := s[i]
		switch {
		case quote != 0:
			if c == quote {
				quote = 0
			}
		case c == '"' || c == '\'':
			quote = c
		case c == '(':
			depth++
		case c == ')':
			if depth > 0 {
				depth--
			}
		case c == ';' && depth == 0:
			out = append(out, s[start:i])
			start = i + 1
		}
	}
	return append(out, s[start:])
}

// HTMLToText turns HTML into the text a person would read in a plain-text letter: no tags, paragraphs and list items
// on their own lines. It is what the list of letters shows as a snippet and what a letter that only has HTML is
// searched by.
func HTMLToText(src string) string {
	var out strings.Builder
	z := html.NewTokenizer(strings.NewReader(src))
	z.SetMaxBuf(1 << 20)
	skip, depth := "", 0
	space := false // a space is owed before the next word
	lastNL := func() int {
		s := out.String()
		n := 0
		for i := len(s) - 1; i >= 0 && s[i] == '\n'; i-- {
			n++
		}
		return n
	}
	nl := func(n int) {
		if out.Len() == 0 {
			return
		}
		for have := lastNL(); have < n; have++ {
			out.WriteByte('\n')
		}
		space = false
	}
loop:
	for {
		switch z.Next() {
		case html.ErrorToken:
			break loop
		case html.TextToken:
			if skip != "" {
				continue
			}
			raw := string(z.Text()) // (Text can be read only once per token)
			words := strings.Fields(raw)
			if len(words) == 0 {
				space = space || raw != ""
				continue
			}
			if raw[0] <= ' ' {
				space = true
			}
			if space && out.Len() > 0 && lastNL() == 0 {
				out.WriteByte(' ')
			}
			out.WriteString(strings.Join(words, " "))
			space = raw[len(raw)-1] <= ' '
		case html.StartTagToken, html.SelfClosingTagToken:
			name, _ := z.TagName()
			tag := string(name)
			if skip != "" {
				if tag == skip {
					depth++
				}
				continue
			}
			if dropTags[tag] && !voidTags[tag] {
				skip, depth = tag, 1
				continue
			}
			switch tag {
			case "br":
				nl(1)
			case "p", "div", "tr", "table", "ul", "ol", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "section", "article", "header", "footer", "hr":
				nl(2)
			case "li":
				nl(1)
				out.WriteString("• ")
			}
		case html.EndTagToken:
			name, _ := z.TagName()
			tag := string(name)
			if skip != "" {
				if tag == skip {
					if depth--; depth == 0 {
						skip = ""
					}
				}
				continue
			}
			switch tag {
			case "table", "ul", "ol", "blockquote", "pre":
				nl(2)
			case "p", "div", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article", "li":
				nl(1)
			case "td", "th":
				space = true
			}
		}
	}
	lines := strings.Split(out.String(), "\n")
	for i, l := range lines {
		lines[i] = strings.TrimRight(l, " ")
	}
	text := strings.Join(lines, "\n")
	for strings.Contains(text, "\n\n\n") {
		text = strings.ReplaceAll(text, "\n\n\n", "\n\n")
	}
	return strings.TrimSpace(text)
}
