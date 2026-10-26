#!/usr/bin/env python3

"""
Write an index.html into a screenshots folder: one row per screen, one column per locale
(or per locale and screen size), so the screens can be compared at a glance.
Images are scaled to their screen's dp width, so smaller devices look smaller.
"""

import html
import re
import sys
from pathlib import Path

DEFAULT_WIDTH_DP = 360
PX_PER_DP = 0.5

PAGE = """<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>{title}</title>
<style>
body {{ font-family: sans-serif; margin: 16px; }}
table {{ border-collapse: collapse; }}
th, td {{ border: 1px solid #ccc; padding: 4px; vertical-align: top; text-align: center; }}
th {{ position: sticky; top: 0; background: #f4f4f4; }}
img {{ display: block; }}
.missing {{ color: #999; }}
</style>
</head>
<body>
<h1>{title}</h1>
<p>{caption}</p>
<table>
<tr><th>Screen</th>{column_headers}</tr>
{rows}
</table>
</body>
</html>
"""


def ordered(found, requested):
    listed = [x for x in dict.fromkeys(requested) if x in found]
    return listed + sorted(set(found) - set(listed))


def ordered_columns(root, requested_locales, requested_sizes):
    folders = {png.parent.relative_to(root).as_posix() for png in root.glob("**/*.png")}
    locales = ordered({folder.split("/")[0] for folder in folders}, requested_locales)
    columns = []
    for locale in locales:
        sizes = [folder.split("/", 1)[1] for folder in folders if folder.startswith(f"{locale}/")]
        if locale in folders:
            columns.append(locale)
        columns += [f"{locale}/{size}" for size in ordered(sizes, requested_sizes)]
    return columns


def image_width(column):
    match = re.match(r"w(\d+)dp", column.split("/")[-1])
    return round(int(match.group(1) if match else DEFAULT_WIDTH_DP) * PX_PER_DP)


def cell(root, column, screen):
    width = image_width(column)
    path = f"{column}/{screen}.png"
    if not (root / path).exists():
        return f'<td class="missing" style="width: {width}px">missing</td>'
    src = html.escape(path)
    alt = html.escape(f"{screen} in {column}")
    return f'<td><a href="{src}"><img src="{src}" style="width: {width}px" alt="{alt}"></a></td>'


def write_contact_sheet(root, requested_locales, caption, requested_sizes=(), title="Localization screenshots"):
    columns = ordered_columns(root, requested_locales, requested_sizes)
    screens = sorted({png.stem for png in root.glob("**/*.png")})
    column_headers = "".join(f"<th>{html.escape(column.replace('/', ' '))}</th>" for column in columns)
    rows = "\n".join(
        f"<tr><th>{html.escape(screen)}</th>{''.join(cell(root, column, screen) for column in columns)}</tr>"
        for screen in screens
    )
    page = PAGE.format(
        title=html.escape(title),
        caption=html.escape(caption),
        column_headers=column_headers,
        rows=rows,
    )
    (root / "index.html").write_text(page, encoding="utf-8")
    return len(screens), len(columns)


def comma_separated(index):
    return [x.strip() for x in (sys.argv[index] if len(sys.argv) > index else "").split(",") if x.strip()]


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: l10n_contact_sheet.py <screenshots dir> [locales] [caption] [sizes] [title]")
    screenshots_dir = Path(sys.argv[1])
    sheet_caption = sys.argv[3] if len(sys.argv) > 3 else ""
    sheet_title = sys.argv[5] if len(sys.argv) > 5 and sys.argv[5] else "Localization screenshots"
    screen_count, column_count = write_contact_sheet(
        screenshots_dir, comma_separated(2), sheet_caption, comma_separated(4), sheet_title
    )
    print(f"Wrote {screenshots_dir / 'index.html'}: {screen_count} screens x {column_count} columns")
