import math,sys,csv
def tile_xy(lat,lon,z):
    n=2**z; x=int((lon+180.0)/360.0*n)
    lr=math.radians(lat)
    y=int((1-math.log(math.tan(lr)+1/math.cos(lr))/math.pi)/2*n)
    return x,y
def qk(x,y,z):
    s=""
    for i in range(z,0,-1):
        d=0;m=1<<(i-1)
        if x&m:d+=1
        if y&m:d+=2
        s+=str(d)
    return s
def qk_of(lat,lon,z=9):
    x,y=tile_xy(lat,lon,z); return qk(x,y,z)
if __name__=="__main__":
    lat,lon=float(sys.argv[1]),float(sys.argv[2])
    k=qk_of(lat,lon)
    print("quadkey",k)
    for r in csv.DictReader(open("ms-links.csv")):
        if r["QuadKey"]==k: print(r["Location"],r["Size"],r["Url"][:150])
