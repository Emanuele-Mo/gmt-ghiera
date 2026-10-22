"""Genera la foto della Luna piena vista dalla Terra (nord in alto) dalla mappa LRO della NASA.
Proiezione ortografica della faccia visibile, sfondo trasparente.
Uso: python3 strumenti/genera_luna.py sorgenti/lroc_color_2k.jpg app/src/main/res/drawable-nodpi/luna.png
"""
import sys
import numpy as np
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
SIZE, SS = 400, 3                     # lato finale e sovracampionamento per bordi morbidi
tex = np.asarray(Image.open(src).convert("RGB"), dtype=np.float64) / 255.0
th, tw, _ = tex.shape

n = SIZE * SS
c = (n - 1) / 2
y, x = np.mgrid[0:n, 0:n]
X = (x - c) / (n / 2)                 # -1..1, destra = est selenografico (Mare Crisium)
Y = -(y - c) / (n / 2)                # -1..1, alto = nord
r2 = X * X + Y * Y
inside = r2 <= 1.0
Z = np.sqrt(np.clip(1 - r2, 0, 1))
lat = np.arcsin(np.clip(Y, -1, 1))
lon = np.arctan2(X, Z)

u = ((lon / (2 * np.pi)) + 0.5) * (tw - 1)
v = (0.5 - lat / np.pi) * (th - 1)
# interpolazione bilineare
u0 = np.floor(u).astype(int); v0 = np.floor(v).astype(int)
u1 = np.clip(u0 + 1, 0, tw - 1); v1 = np.clip(v0 + 1, 0, th - 1)
fu = (u - u0)[..., None]; fv = (v - v0)[..., None]
u0 = np.clip(u0, 0, tw - 1); v0 = np.clip(v0, 0, th - 1)
col = (tex[v0, u0] * (1 - fu) * (1 - fv) + tex[v0, u1] * fu * (1 - fv)
       + tex[v1, u0] * (1 - fu) * fv + tex[v1, u1] * fu * fv)

# leggero oscuramento al bordo, come nelle foto reali della Luna piena
mu = Z[..., None]
col = col * (0.80 + 0.20 * np.power(mu, 0.35))
col = np.clip(np.power(col, 1.6) * 1.25, 0, 1)   # piu' contrasto: mari scuri come nella realta'

rgba = np.zeros((n, n, 4))
rgba[..., :3] = col
rgba[..., 3] = inside.astype(float)
img = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA").resize((SIZE, SIZE), Image.LANCZOS)
img.save(dst, optimize=True)
print("salvata", dst, img.size)
