import os
from scrapling.parser import Selector
db = os.path.join(os.path.dirname(os.path.abspath(__file__)), "adaptive.db")
old = '<div><p class="price" id="cost">$10</p><p>other</p></div>'
new = '<section><div class="wrap"><span class="amount-new" data-x="1">$12</span><p>other</p></div></section>'
p1 = Selector(old, url="https://shop.example", adaptive=True, storage_args={"storage_file": db, "url": "https://shop.example"})
print("before:", p1.css('#cost', auto_save=True)[0].text)
p2 = Selector(new, url="https://shop.example", adaptive=True, storage_args={"storage_file": db, "url": "https://shop.example"})
print("plain css after redesign:", p2.css('#cost'))
print("adaptive after redesign:", [e.text for e in p2.css('#cost', adaptive=True)])
