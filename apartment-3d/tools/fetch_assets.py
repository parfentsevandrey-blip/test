import json, os, sys, urllib.request
def get(u): return urllib.request.urlopen(urllib.request.Request(u, headers={'User-Agent':'plan3d-asset-fetch/1.0 (curl compatible)'}), timeout=90).read()
def ph_model(id, res='1k'):
    f = json.loads(get(f'https://api.polyhaven.com/files/{id}'))
    g = f['gltf'][res]['gltf']
    os.makedirs(id, exist_ok=True)
    open(f'{id}/{id}.gltf','wb').write(get(g['url']))
    for rel, inc in g.get('include', {}).items():
        p = os.path.join(id, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p,'wb').write(get(inc['url']))
    print('model', id, 'ok')
def ph_tex(id, res='1k', maps=('Diffuse','nor_gl','Rough')):
    f = json.loads(get(f'https://api.polyhaven.com/files/{id}'))
    os.makedirs('tex', exist_ok=True)
    for m in maps:
        if m not in f: print('  no', m, 'for', id); continue
        u = f[m][res]['jpg']['url']
        open(f'tex/{id}_{m.lower()}.jpg','wb').write(get(u))
    print('tex', id, 'ok')
def khr(name):
    u=f'https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/{name}/glTF-Binary/{name}.glb'
    open(f'{name}.glb','wb').write(get(u)); print('khr', name, 'ok')
kind=sys.argv[1]
for a in sys.argv[2:]:
    try: {'m':ph_model,'t':ph_tex,'k':khr}[kind](a)
    except Exception as e: print('FAIL', a, e)
