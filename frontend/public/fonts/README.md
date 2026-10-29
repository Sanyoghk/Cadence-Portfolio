# Local prototype typography

Only these three unmodified WOFF2 files are reused from the user-supplied
`Cadence Prototype.html` (SHA256
`2f15d3a196af190b0d9732a32cb6223e1d05cf285a9d01b6a33a4d20a016a068`).
No Google Fonts request/preconnect, runtime font download, vendor JS, images or media are reused.

| File                                  | Original manifest ID                 | Bytes | SHA256                                                           |
| ------------------------------------- | ------------------------------------ | ----- | ---------------------------------------------------------------- |
| cormorant-garamond-latin-normal.woff2 | 2d05e7a6-167a-49ec-93fa-f4fb5897f9b5 | 37776 | 5d618c462b7a5b74f442e1548880086af71764d9cc7d35c16ab45353da934621 |
| cormorant-garamond-latin-italic.woff2 | 627c8840-4135-471d-a890-4c004fce27c1 | 39304 | e6d6d1d73858aa9b66f3a3539f9dec22faab2276e61ad1d813bc04dd59c9c122 |
| dm-sans-latin.woff2                   | 8a5ad374-d968-4e55-a287-85a72bb3519a | 62556 | aa530716b0d351866af7dbfa3eee4120fb36f2d071baff8c234185141865c7ff |

The prototype's CSS identifies family/style/weight and Latin subset. WOFF2 signature,
declared file length and bounded size checked; Chromium loads the fonts in browser gates.
This is not a claim of exhaustive font-parser security analysis. Unsupported glyphs use
system fallback; no new script/decoder dependency is added.

Licenses checked against the official Google Fonts repository on 2026-10-18:
[Cormorant Garamond OFL](https://github.com/google/fonts/blob/main/ofl/cormorantgaramond/OFL.txt),
[DM Sans OFL](https://github.com/google/fonts/blob/main/ofl/dmsans/OFL.txt).
Full copyright notices and SIL Open Font License 1.1 are retained here as
[Cormorant-Garamond-OFL.txt](Cormorant-Garamond-OFL.txt) and [DM-Sans-OFL.txt](DM-Sans-OFL.txt).
They permit embedding/redistribution under their conditions; these fonts are not sold
separately or renamed/modified. The source HTML itself is not distributed as an app asset.

The prototype's JetBrains Mono reference is served by a system monospace fallback;
its font bytes are not copied. No icon package: fixed inline geometric SVG paths and the
Cadence C mark replace agency EF branding. No external image/media license is assumed.
