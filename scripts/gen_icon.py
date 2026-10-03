#!/usr/bin/env python3
"""生成 SoulPet 像素史莱姆图标（多分辨率 mipmap）。"""
import struct, zlib, os

W, H = 32, 32
BODY = (90, 200, 250, 255)
BODY_DARK = (50, 160, 220, 255)
EYE = (30, 40, 60, 255)
BLUSH = (255, 150, 170, 255)
SHINE = (255, 255, 255, 180)
BG = (0, 0, 0, 0)

def render():
    """画 32x32 的 HAPPY 史莱姆，返回 RGBA 像素数组"""
    img = [[BG] * W for _ in range(H)]
    def px(x, y, w=1, h=1, c=BODY):
        for dy in range(h):
            for dx in range(w):
                if 0 <= x+dx < W and 0 <= y+dy < H:
                    img[y+dy][x+dx] = c
    top, bottom, left, right = 9, 27, 6, 26   # HAPPY pose
    for y in range(top, bottom + 1):
        edge = 3 - (y - top) if y < top + 3 else (1 if y > bottom - 2 else 0)
        px(left + edge, y, right - left - edge * 2, 1, BODY)
    px(left + 1, bottom, right - left - 2, 1, BODY_DARK)
    px(left + 4, top + 3, 3, 2, SHINE)
    eyeY = top + 8
    px(11, eyeY, 3, 1, EYE); px(18, eyeY, 3, 1, EYE)
    px(11, eyeY + 1, 1, 1, EYE); px(20, eyeY + 1, 1, 1, EYE)
    px(8, eyeY + 5, 3, 1, BLUSH); px(21, eyeY + 5, 3, 1, BLUSH)
    px(15, eyeY + 5, 2, 1, EYE)
    return img

def write_png(img, size, path):
    """最近邻放大到 size 并写 PNG（纯 stdlib，无 PIL 依赖）"""
    raw = b""
    for y in range(size):
        sy = y * H // size
        raw += b"\x00"
        for x in range(size):
            sx = x * W // size
            raw += bytes(img[sy][sx])
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c))
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)

BASE = os.path.join(os.path.dirname(__file__), "../app/src/main/res")
img = render()
for dpi, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    write_png(img, size, f"{BASE}/mipmap-{dpi}/ic_launcher.png")
    print(f"mipmap-{dpi}: {size}x{size} ok")
write_png(img, 512, os.path.join(os.path.dirname(__file__), "../icon-512.png"))
print("icon-512.png ok (GitHub/商店用)")
