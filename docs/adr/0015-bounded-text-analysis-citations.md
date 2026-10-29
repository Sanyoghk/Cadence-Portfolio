# 0015 — Bounded TEXT analysis и проверяемые цитаты

Статус: принято; TEXT analysis slice квалифицирован локально 2026-10-12.

## Решение

Отдельное действие `analyze-evidence` использует только актуальные human-reviewed TEXT
admissions. Сервер выбирает источники, максимум 5 и суммарно 20000 UTF-8 bytes.
Превышение лимита возвращает conflict: молчаливое усечение исказило бы вход анализа.
Declared-only action остаётся совместимым. Website/file ingest ещё не допускается.

Attempt сохраняет actor, declared input, immutable ссылки на admissions и input digest.
Внешний adapter вызывается вне DB transaction. Publication заново проверяет actual
OWNER/membership, lifecycle, versions, withdrawal и deadline под parent-first locks.
Цитаты (1–10, до 500 UTF-16 units / 1000 UTF-8 bytes) обязаны ссылаться на выбранный
admission и содержать точную подстроку reviewed text. PostgreSQL дополнительно
защищает immutable receipts, bounded input и selected-source binding.

## Границы доказательства

Exact quote доказывает наличие текста в конкретном reviewed snapshot, но не истинность
источника и не смысловую обоснованность выводов модели. Источники и model output остаются
недоверенными; human review обязателен. Fake только local/test, production fail-closed.
Fake выдаёт демонстрационный результат и не является реальным evidence reasoning.

## Alternatives и trade-offs

Расширить старое действие автоматически проще, но это молча меняло бы input contract.
Client-selected sources потребовали бы отдельного product/API решения. Поэтому выбран
явный bounded server-selected action. Immutable admission links сохраняют provenance
без дублирования текста; новая migration не придумывает actor/citations для legacy history.

Evidence analysis → Identity copy ещё требует отдельной provenance/withdrawal integration.
Legacy copy явно отклоняет evidence-mode receipts в domain и PostgreSQL до её qualification.
Live provider, privacy policy и non-TEXT admission не входят в этот slice.
