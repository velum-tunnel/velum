from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/assets/logo_velum_mark.png"

image = Image.open(SOURCE).convert("RGBA")
alpha = image.getchannel("A")
threshold = 24
pixels = alpha.load()
width, height = image.size
seen = bytearray(width * height)
components = []

for y in range(height):
    for x in range(width):
        index = y * width + x
        if seen[index] or pixels[x, y] < threshold:
            continue
        queue = deque([(x, y)])
        seen[index] = 1
        count = 0
        min_x = max_x = x
        min_y = max_y = y
        while queue:
            current_x, current_y = queue.popleft()
            count += 1
            min_x = min(min_x, current_x)
            max_x = max(max_x, current_x)
            min_y = min(min_y, current_y)
            max_y = max(max_y, current_y)
            for next_x, next_y in (
                (current_x - 1, current_y),
                (current_x + 1, current_y),
                (current_x, current_y - 1),
                (current_x, current_y + 1),
            ):
                if 0 <= next_x < width and 0 <= next_y < height:
                    next_index = next_y * width + next_x
                    if not seen[next_index] and pixels[next_x, next_y] >= threshold:
                        seen[next_index] = 1
                        queue.append((next_x, next_y))
        components.append((count, (min_x, min_y, max_x + 1, max_y + 1)))

for count, box in sorted(components, reverse=True)[:30]:
    print(count, box)
