from PIL import Image, ImageDraw, ImageFont, ImageFilter
F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def font(sz, bold=False): return ImageFont.truetype(FB if bold else F, sz)

def pin(dr, x, y, r=17, fill=(179,40,45), num=None, S=2):
    """Teardrop pin whose tip sits at (x,y). Coordinates already in device px."""
    cy = y - int(r*2.05)
    dr.ellipse([x-r, cy-r, x+r, cy+r], fill=fill, outline=(255,255,255), width=max(2,r//5))
    dr.polygon([(x-r*0.62, cy+r*0.72), (x+r*0.62, cy+r*0.72), (x, y)], fill=fill)
    dr.polygon([(x-r*0.62, cy+r*0.72), (x+r*0.62, cy+r*0.72), (x, y)],
               outline=(255,255,255), width=0)
    if num is not None:
        f = font(int(r*1.15), True)
        b = dr.textbbox((0,0), str(num), font=f)
        dr.text((x-(b[2]-b[0])/2-b[0], cy-(b[3]-b[1])/2-b[1]), str(num), font=f, fill=(255,255,255))
    return cy

def label(img, dr, x, y, text, sub=None, anchor='center', fs=21, bg=(255,255,255),
          fg=(28,34,46), sfg=(110,118,132), pad=13, radius=10):
    f  = font(fs, True); fs2 = int(fs*0.82); f2 = font(fs2, False)
    w1 = dr.textbbox((0,0), text, font=f); w1 = w1[2]-w1[0]
    w2 = 0
    if sub:
        b = dr.textbbox((0,0), sub, font=f2); w2 = b[2]-b[0]
    w = max(w1, w2) + pad*2
    h = fs + (int(fs2*1.35) if sub else 0) + pad*2 - 2
    if anchor == 'center': x0 = x - w//2
    elif anchor == 'left': x0 = x
    else: x0 = x - w
    y0 = y
    sh = Image.new('RGBA', img.size, (0,0,0,0))
    ImageDraw.Draw(sh).rounded_rectangle([x0, y0+3, x0+w, y0+h+3], radius, fill=(0,0,0,60))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(5)))
    dr.rounded_rectangle([x0, y0, x0+w, y0+h], radius, fill=bg+(255,), outline=(0,0,0,28), width=1)
    dr.text((x0+(w-w1)//2, y0+pad-3), text, font=f, fill=fg)
    if sub:
        dr.text((x0+(w-w2)//2, y0+pad+fs+1), sub, font=f2, fill=sfg)
    return x0, y0, x0+w, y0+h
