from pathlib import Path
from PIL import Image
from collections import deque

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
ASSETS = ROOT / "tools/assets"
SOURCE = ASSETS / "logo_velum_header.png"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
# Pusat geometris crop sedikit lebih tinggi daripada pusat visual monogram V.
# Offset proporsional ini menjaga semua density, adaptive, dan notifikasi tetap seragam.
VISUAL_OFFSET_Y = 0.055

# The title logo contains the exact V mark above the wordmark. The source PNG
# has stray transparent-color pixels outside the visible mark, so use the
# documented mark region rather than a global alpha bounding box.
source = Image.open(SOURCE).convert("RGBA")
left, top, right, bottom = 160, 20, 1040, 670
# Add a small transparent safety margin around the exact mark.
pad_x = max(1, int((right - left) * 0.045))
pad_y = max(1, int((bottom - top) * 0.045))
left = max(0, left - pad_x)
top = max(0, top - pad_y)
right = min(source.width, right + pad_x)
bottom = min(source.height, bottom + pad_y)
mark = source.crop((left, top, right, bottom))

# Remove isolated glow/pixel components below the mark. Keep the largest
# connected alpha component so the real V silhouette remains unchanged while
# detached white dots cannot reach launcher or notification sizes.
alpha = mark.getchannel("A")
ap = alpha.load()
mw, mh = mark.size
seen = bytearray(mw * mh)
largest = set()
for y in range(mh):
    for x in range(mw):
        idx = y * mw + x
        if seen[idx] or ap[x, y] < 24:
            continue
        queue = deque([(x, y)])
        seen[idx] = 1
        component = set()
        while queue:
            cx, cy = queue.popleft()
            component.add((cx, cy))
            for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                if 0 <= nx < mw and 0 <= ny < mh:
                    ni = ny * mw + nx
                    if not seen[ni] and ap[nx, ny] >= 24:
                        seen[ni] = 1
                        queue.append((nx, ny))
        if len(component) > len(largest):
            largest = component
clean_alpha = Image.new("L", (mw, mh), 0)
clean_pixels = clean_alpha.load()
for x, y in largest:
    clean_pixels[x, y] = ap[x, y]
mark.putalpha(clean_alpha)

# Make the launcher foreground square while preserving the original mark's
# proportions and transparent surroundings. This avoids the rigid hand-drawn V.
def foreground(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scale = min((size * 0.78) / mark.width, (size * 0.78) / mark.height)
    resized = mark.resize((round(mark.width * scale), round(mark.height * scale)), Image.Resampling.LANCZOS)
    offset_y = round(size * VISUAL_OFFSET_Y)
    x = (size - resized.width) // 2
    y = (size - resized.height) // 2 + offset_y
    canvas.alpha_composite(resized, (x, y))
    return canvas

for density, size in DENSITIES.items():
    directory = RES / f"mipmap-{density}"
    icon = foreground(size)
    icon.save(directory / "ic_launcher.png", optimize=True)
    icon.save(directory / "ic_launcher_round.png", optimize=True)

# Keep a reusable source crop for visual review and future deterministic rebuilds.
ASSETS.mkdir(parents=True, exist_ok=True)
mark.save(ASSETS / "logo_velum_mark.png", optimize=True)
nodpi = RES / "drawable-nodpi"
nodpi.mkdir(parents=True, exist_ok=True)
foreground(108).save(nodpi / "logo_velum_mark_adaptive.png", optimize=True)

# Android notification icons must be monochrome. Preserve the exact mark alpha
# and replace only its color with opaque white.
notification = foreground(24)
mask = notification.getchannel("A")
white = Image.new("RGBA", notification.size, (255, 255, 255, 0))
white.putalpha(mask)
white.save(nodpi / "logo_velum_mark_notification.png", optimize=True)
adaptive_mask = foreground(108).getchannel("A")
adaptive_white = Image.new("RGBA", (108, 108), (255, 255, 255, 0))
adaptive_white.putalpha(adaptive_mask)
adaptive_white.save(nodpi / "logo_velum_mark_monochrome.png", optimize=True)
print(f"source={SOURCE}")
print(f"mark_bbox=({left},{top},{right},{bottom})")
print(f"mark_size={mark.width}x{mark.height}")
