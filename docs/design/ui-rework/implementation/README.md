# Cream Orbit — рабочий UI23–24

Capture2026-10-20, исходный HEAD `e35d4e1`, slice1 `edf2d76`, final source manifest рядом.
Только synthetic test API/session data; эти скриншоты не доказывают live Clerk/AI.

| Поверхность | Desktop | Tablet / mobile |
| --- | --- | --- |
| Before actual landing | [1440px](before-landing-desktop.jpg) | Historical baseline, не новый UI |
| Public landing | [1440px](landing-desktop.jpg) | [768px](landing-tablet.jpg), [390px](landing-mobile.jpg) |
| Actual exact review UI | [1440px](identity-desktop.png) | [390px](identity-mobile.png) |
| Controlled404/error | [1440px](error-desktop.jpg) | [390px](error-mobile.jpg) |

[Reference fingerprint](reference-fingerprint.json):21 unchanged src/public/package inputs от
accepted `aa240ae`; composite SHA256 compact JSON `f426739cfad635f104caff5b2aee2705043a7ef6c4f416d42727585adff56bac`.
[Qualified inputs](qualified-source-manifest.json) фиксируют exact71 frontend/OpenAPI files,
без env и node_modules. Build/test runtime использует unchanged pinned lockfile.
[Browser stats](browser-summary.json):66 expected,0 unexpected/skipped/flaky.

## Design inspector и review

Raw [landing measurements](landing-inspector.json):5 FAIL/7 WARN по generic house rules;
[app error-state measurements](error-inspector.json):0 FAIL/1 WARN;
[source design lint](source-design-lint.txt):1 FAIL/19 WARN. Это отдельные design heuristics,
не ESLint/axe/security scanner. `pnpm lint` и axe проходят; raw findings не скрывались.

| Before | After | Why |
| --- | --- | --- |
| Card-with-shadow baseline | Flat Cream Orbit с fixed rails, serif logo/orbit | Выбранная пользователем композиция, никаких дополнительных random directions |
| Workflow strip над public hero | Отдельные public/app scales и responsive menu | На public видны продукт и реальный next step; app title28/24px |
| Sidebar/forms рядом с длинным Identity workflow | Manager и Identity — отдельные views, bounded Brand selector | Не смешивать onboarding form и exact review; context всегда явно виден |
| Theme background transition | Atomic theme colors, pointer-only120ms press | Подтверждённый transient contrast defect исправлен, keyboard/reduced motion без scale |
| Fixed assumed header height | Scoped ResizeObserver/cleanup + bounded Workspace label | Wrap/viewport/account control не закрывают heading/focus targets |

Оставшиеся generic flags обоснованы, а не объявлены зелёными:

- `hairline`, `outlined-box`, `palette-cream`: fixed rails/topbar/curtain и palette прямо
  утверждены в reference. Их удаление нарушило бы user-selected composition.
- `few-pictures`, `no-footage`, `still-page`: reference без фотографий/видео; новая motion
  polishing отложена пользователем. Сохранены только feedback/footer с pause/reduced motion.
- `eyebrow` footer: decorative CADENCE `aria-hidden`, это footer mark, не вторая semantic h1.
  App source labels — source/stage/provenance information, а не invented metrics.
- `body-size`, `grey-body`, `size-count`:14px только supporting disclosure/meta; основные
  paragraphs/controls16px, display/plan scale fluid по согласованному reference; axe contrast0.
- `measure`: disclaimer65ch; эвристика считает символы всей строки, не итоговую ширину glyphs.
- `name-only-title`: mobile h1 CADENCE сопровождается описанием продукта16px и real CTA;
  эвристика требует18px, reference прямо задаёт16px на mobile.
- Source `placeholder-data`: `https://example.com` только native URL input placeholder,
  не source observation/customer/proof. Source `mono` применяется к IDs/JSON, `shadow` только
  overlay menu; padStart в digest алгоритме не decorative numbering.

Визуально проверены screenshots1440/768/390, Identity320/768/390/1440 и Manager1280/375.
Grid: two equal columns +24px gap = full inner canvas; ниже1100px — одна колонка, никаких
подставных chart/data blocks. На телефоне сначала source/review, затем draft; все действия
доступны обычным scroll/keyboard. Physical Safari/iPhone/Android/GPU60fps не квалифицированы.

Полный [implementation/security/test report](../../../ui-rework23-24-report.md).
