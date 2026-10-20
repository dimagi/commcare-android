#!/usr/bin/env python3

"""
Write an index.html into a localization screenshots folder: one row per screen,
one column per locale, so the screens can be compared across languages at a glance.
"""

import html
import sys
from pathlib import Path

PAGE = """<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>Localization screenshots</title>
<style>
body {{ font-family: sans-serif; margin: 16px; }}
table {{ border-collapse: collapse; }}
th, td {{ border: 1px solid #ccc; padding: 4px; vertical-align: top; text-align: center; }}
th {{ position: sticky; top: 0; background: #f4f4f4; }}
img {{ width: 180px; display: block; }}
.missing {{ color: #999; width: 180px; }}
</style>
</head>
<body>
<h1>Localization screenshots</h1>
<p>{caption}</p>
<table>
<tr><th>Screen</th>{locale_headers}</tr>
{rows}
</table>
</body>
</html>
"""


def ordered_locales(root, requested):
    found = {d.name for d in root.iterdir() if d.is_dir()}
    listed = list(dict.fromkeys(requested))
    return listed + sorted(found - set(listed))


def cell(root, locale, screen):
    path = f"{locale}/{screen}.png"
    if not (root / path).exists():
        return '<td class="missing">missing</td>'
    src = html.escape(path)
    return f'<td><a href="{src}"><img src="{src}" alt="{html.escape(screen)} in {html.escape(locale)}"></a></td>'


def write_contact_sheet(root, requested, caption):
    locales = ordered_locales(root, requested)
    screens = sorted({png.stem for png in root.glob("*/*.png")})
    locale_headers = "".join(f"<th>{html.escape(locale)}</th>" for locale in locales)
    rows = "\n".join(
        f"<tr><th>{html.escape(screen)}</th>{''.join(cell(root, locale, screen) for locale in locales)}</tr>"
        for screen in screens
    )
    page = PAGE.format(caption=html.escape(caption), locale_headers=locale_headers, rows=rows)
    (root / "index.html").write_text(page, encoding="utf-8")
    return len(screens), len(locales)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: l10n_contact_sheet.py <screenshots dir> [comma-separated locales] [caption]")
    screenshots_dir = Path(sys.argv[1])
    locale_order = [x.strip() for x in (sys.argv[2] if len(sys.argv) > 2 else "").split(",") if x.strip()]
    sheet_caption = sys.argv[3] if len(sys.argv) > 3 else ""
    screen_count, locale_count = write_contact_sheet(screenshots_dir, locale_order, sheet_caption)
    print(f"Wrote {screenshots_dir / 'index.html'}: {screen_count} screens x {locale_count} locales")
