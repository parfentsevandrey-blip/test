from scrapling.parser import Selector
html = """<html><body><div class="product" id="p1"><h2>Phone</h2><span class="price">$499</span></div>
<div class="product" id="p2"><h2>Laptop</h2><span class="price">$1299</span></div>
<div class="product" id="p3"><h2>Tablet</h2><span class="price">$799</span></div></body></html>"""
page = Selector(html, url="https://example.com")
print("css:", page.css('.product h2::text').getall())
print("xpath:", page.xpath('//span[@class="price"]/text()').getall())
print("find_all:", [e.attrib['id'] for e in page.find_all('div', class_='product')])
print("find_by_text:", page.find_by_text('Laptop').tag)
first = page.css('.product')[0]
print("find_similar:", [e.attrib['id'] for e in first.find_similar()])
print("auto css selector:", first.generate_css_selector, "| xpath:", first.generate_xpath_selector)
print("regex:", page.css('.price::text').re(r'\d+'))
print("next sibling:", first.next.attrib['id'], "parent:", first.parent.tag)

from scrapling.fetchers import Fetcher
p = Fetcher.get('https://quotes.toscrape.com/', impersonate='chrome')
print("HTTP status:", p.status, "| quotes:", len(p.css('.quote')))
print("first quote:", p.css('.quote .text::text').get()[:60])
print("markdown preview:", p.markdown()[:150].replace("\n"," "))
