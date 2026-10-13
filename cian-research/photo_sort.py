"""Сортировка фото объявления SigLIP 2: интерьер квартиры или нет + склейка почти одинаковых кадров."""
import torch
from PIL import Image

MODEL = 'google/siglip2-base-patch16-256'
INSIDE = ['a photo of a living room in an apartment', 'a photo of a bedroom in an apartment',
          'a photo of a kitchen in an apartment', 'a photo of a bathroom or toilet in an apartment',
          'a photo of a hallway inside an apartment', 'a photo of an empty unfinished apartment room with concrete walls',
          'a photo of an empty room with white walls in an apartment', 'a photo of a balcony or loggia of an apartment']
OUTSIDE = ['a photo of a building facade', 'a view from a window over the city', 'a photo of a courtyard, park or street',
           'a floor plan drawing', 'a map', 'a 3d rendering of a modern residential building', 'an advertising banner with text',
           'a photo of a building lobby or elevator hall', 'a photo of a staircase in an apartment building entrance',
           'a photo of an underground parking', 'a photo of a playground']
_S = {}


def _load():
    if not _S:
        from transformers import AutoModel, AutoProcessor
        _S['m'] = AutoModel.from_pretrained(MODEL).eval()
        _S['p'] = AutoProcessor.from_pretrained(MODEL)
        with torch.no_grad():
            t = _S['m'].get_text_features(**_S['p'](text=INSIDE + OUTSIDE, padding='max_length', max_length=64, return_tensors='pt'))
        _S['t'] = torch.nn.functional.normalize(t, dim=-1)
    return _S


def sort_photos(paths, dup=0.93):
    """→ (фото квартиры без дублей, отброшено как не-интерьер, отброшено как дубль)."""
    s = _load()
    embs, inside = [], []
    with torch.no_grad():
        for k in range(0, len(paths), 16):
            ims = [Image.open(p).convert('RGB') for p in paths[k:k + 16]]
            e = torch.nn.functional.normalize(s['m'].get_image_features(**s['p'](images=ims, return_tensors='pt')), dim=-1)
            embs.append(e)
            inside += [j < len(INSIDE) for j in (e @ s['t'].T).argmax(-1).tolist()]
    e = torch.cat(embs) if embs else torch.zeros(0)
    keep, outside, dups, kept_e = [], [], [], []
    for i, p in enumerate(paths):
        if not inside[i]:
            outside.append(p)
        elif any(float(e[i] @ k) >= dup for k in kept_e):
            dups.append(p)
        else:
            keep.append(p)
            kept_e.append(e[i])
    return keep, outside, dups
