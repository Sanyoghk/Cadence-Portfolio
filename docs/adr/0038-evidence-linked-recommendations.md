# ADR0038 — bounded evidence-linked recommendation / apply

Дата:2026-10-17. Baseline dabc367, clean mvp. Engineering contract задания21/#100.
Actual #100/#105/#107 прочитаны. Reports из #105 остаются заданием22; closure не заявляется.

## Supported scope и честная uncertainty

Provider-independent HEURISTIC: сравнить engagement/reach двух известных pillars одной
конкретной latest APPROVED full Strategy, одной platform и одного exact PERIOD window
ADR0037. Каждый cohort: минимум5 distinct Posts,100 reach, complete/current/non-stale
pairs, reviewed historical attribution. Не смешивать окна, Strategy revisions или platforms.
Разница минимум2 percentage points; предложить перенести5 points pillar weight от нижнего
к верхнему (donor>=5,receiver<=95). Это гипотеза для human review, не ожидаемый causal lift.
Outline/cadence/другие поля не меняются; будущий человек проверяет согласованность draft.
Неподходящая/недостаточная выборка409, не fabricated advice. Максимум100 supporting rows;
более широкая выборка отвергается, не обрезается незаметно. Retained1000 recommendations/Brand.

Origin HEURISTIC, versioned rule, confidence UNCALIBRATED, calibrated
probability/expected lift/cost/moderation NULL. Provider-neutral port принимает только typed
proposal/citation IDs; backend сам формирует bounded HYPOTHESIS explanation. Arbitrary model
prose/claims/tools/URLs не являются исполняемым или фактическим output contract.
Live AI adapter отсутствует. Его activation требует отдельной qualification durable Jobs,
budget/usage/privacy/provider policy; синхронный bounded rule не означает ready live AI.
Demo/model attribution нельзя подменить rule metadata. Legacy Recommendation остаётся legacy.

## Capture / publication / apply

Actual Account→Workspace→Brand→Strategy locks, current OWNER/membership/active parents.
Capture exact request, parent versions, source Strategy ID/version/lock/checksum, inherited
Identity source и normalized contribution IDs/versions/checksums/source/audit times.
Latest corrections/annotations/snapshots reselected before publication/apply; изменившаяся
selected evidence, scan/withdrawal/expiry/source/parent version даёт safe отказ. Существующая
IdentitySourceVerification проверяет retained PDF/URL bytes вне transactions.

Model/rule call вне DB locks,2 instance slots, bounded executor/3s wait,30s publication
deadline; поздний output не публикуется. В production implementation только pure rule,
не external transmission. Failures не создают partial recommendations и не меняют history.

Apply — отдельная human operation: exact proposed5-point patch на копии source, новый DRAFT
с revisedFromId, actor/time и immutable recommendation application receipt/source metrics.
Source approval/content не меняется. Latest source требуется до первого apply; concurrent
edit/approval/lock/archive/revocation/metric correction сериализуются или отклоняются.
Applied draft остаётся обычным editable/approvable Playbook, subsequent changes не меняют
original application checksum/audit. Dismiss сохраняет отдельную immutable resolution.

## Idempotency / history

Operation-specific extension ADR0030: actor+Workspace+operation+SHA256(key), request digest
включает Brand/source/expectedVersion/query или recommendation/action. Same request replay
после actual authorization возвращает existing immutable receipt; different payload409.
Generation publication под parent locks создаёт только одну запись; concurrent pure rule
computation может повториться, но external paid calls этим contract не разрешены.
Apply/dismiss mutually exclusive, one resolution/recommendation. Same key replay не создаёт
новую Strategy и не откатывает её последующее редактирование. Existing history доступна
с original eligibility time; GET не обещает current eligibility, новый apply повторяет guards.
Immutable admission key binding фиксируется до rule call и сохраняется при failure/timeout;
different payload на failed key тоже409. Повторный pure-rule computation с тем же payload
допустим; это не retry-safe обещание для будущего external provider. Operation ID сохраняется
в detail/resolution receipts. Retained key cap10000/Brand, recommendations1000/Brand.
Keys/history без выдуманного TTL; рост закрыт retained quota. Future cleanup отдельной policy.

## Defense in depth / qualification

Защищаем tenant metrics, immutable approved content и human decisions. HTTP/usecase/DB
границы недоверенные. Scoped queries/DTO allowlist/current membership предотвращают BOLA
и mass assignment; citations только captured current source IDs, SQL FK/guards/append-only
history защищают от обхода application validation. Parameterized queries, input/query/
output/concurrency caps ограничивают injection и DoS. No prompts/private bytes/secrets в logs.
Обход API validation не разрешает SQL foreign evidence/history mutation или second apply.

Alternatives: uncalibrated numeric confidence создаёт ложную точность; chosen explicit label.
Free-form model action сложно проверить; chosen fixed patch. Current-source inference
переписывала бы metrics history; chosen exact immutable attribution. In-place apply нарушил
бы approval; chosen existing Strategy revision mechanism. No new dependencies.

Required tests: arithmetic/sample/citation validation; HTTP scope/roles/unknown fields;
stale/corrected sources, revocation и late completion; PostgreSQL concurrent generation/apply,
direct SQL/runtime tampering, populated migration; full/Python/Linux/renderer/startup gates.
