# Cadence — инструкции для продолжения работы

Эти правила восстановлены из доступных чатов и пользовательского handoff 2026-10-08.
Новые явные инструкции пользователя имеют приоритет. Исторические отчёты не заменяют
проверку текущего состояния repository.

## Граница публичной копии

Этот repository содержит проверенный portfolio snapshot с независимой Git-историей.
Не добавляй private development remote, не merge/rebase старые private commits.
Обновления переноси только как проверенные изменения файлов с новым privacy/secret scan.
Исторические scope/status записи ниже не означают свежую production qualification.

## Контекст и источники

- Работай как Senior Software Engineer, Software Architect, Application Security Engineer
  и Technical Mentor. Пользователь — студент второго курса Software Engineering.
- Все планы, updates, review и отчёты — на русском. Идентификаторы, команды и технические
  термины сохраняй; сложный термин при первом упоминании кратко объясняй.
- Начинай с фактических branch, HEAD, git status и существующих изменений.
- Основная рабочая ветка публичной копии — `main`; не переключай её и не сбрасывай изменения без необходимости.
- Для восстановления контекста прочитай [recovery document](docs/project-recovery-2026-10-08.md),
  [product scope](docs/product-scope.md), DEVELOPMENT_PLAN.md, DEVELOPMENT_PLAN_RU.md,
  применимые docs/adr и актуальные GitHub issues #107 и текущей задачи.
- Recovery document — снимок состояния, а не бессрочное указание следующего issue.
- На2026-10-21 пользователь предварительно предпочитает Sol вместо Astra для нового плана.
  Версия Sol и reasoning effort отдельно не выбраны; прежний Medium не является новой
  командой настроить модель. Фактический режим приложения отдельно не подтверждён.

## Product и архитектура

- Cadence — self-service SaaS для creators и individual entrepreneurs.
- Актуальное ТЗ2026-10-20: Creator → Workspace → Brand Identity → Market research →
  Strategy Playbook → ready photo/video или raw-video editing → review/approval →
  Instagram/TikTok publishing → Performance/regular reports/reminders → Recommendations.
  [Product scope](docs/product-scope.md) и [промпты V2](docs/astra-medium-mvp-prompts-v2.md)
  приоритетны; [inventory](docs/scope-v2-inventory.md) сохраняет все старые остатки.
  Prototype — UX reference; agency CRM и iframe не являются требованиями.
- Java 21 / Spring Boot modular monolith; планируемый frontend Next.js / React / TypeScript.
  Сохраняй presentation → application → domain → repositories/infrastructure.
- Tenant boundary: CadenceUser → Workspace → Brand. MVP business operations OWNER-only;
  EDITOR/VIEWER зарезервированы и не получают доступ автоматически.
- Новое требование: **один Workspace на аккаунт, несколько Brands внутри него**.
  После onboarding Workspace ровно один; server/DB enforcement и миграция ещё pending
  в V2-01/[ADR0042](docs/adr/0042-single-workspace-per-account.md). Текущий multi-workspace
  код не является целевым product scope. Новый UI выбирает Brand, без Workspace manager.
  Не удаляй/объединяй legacy tenants автоматически; сохраняй account и Brand isolation.
- Clerk — authentication; backend и актуальная membership — authorization.
- REST под `/api/v1`, отдельные request/response DTO, safe Problem Details, UTC,
  bounded pagination и optimistic locking.
- Human review обязателен. APPROVED/LOCKED history immutable; изменение создаёт новый DRAFT.
  Не выдумывай legacy approval, citations или generation provenance.
- Direct publishing после approval/explicit consent теперь входит в целевой MVP.
  CSV сохраняется; billing и paid ads вне scope. Пользователь выбрал органическое продвижение.
  £20 — demo AI calls, hosting/rendering/market data/training оцениваются отдельно.
- Real AI provider/model не выбраны. Согласовано: validated ports + production fail-closed,
  real adapters отдельным шагом (#120). Fake допустим только в изолированных local/test.
- Durable AI Jobs/recovery, synthetic budgets и Playbook v2 уже qualified в прежних scopes;
  не реализуй их повторно. Live monetary accounting, новый evidence-rich Playbook,
  real media/research/publishing требуют расширений. Demo assets не real generation.
- Сначала backend/API readiness, затем полный новый frontend; финальный platform audit
  с consent UX после UI. Готовый код не означает разрешённую public публикацию.
- Claude frontend-only handoff: [настройки и границы](docs/frontend-v2-claude-handoff.md).
  Второй frontend разрабатывается отдельно; основной backend и первый frontend/ не меняются.
  Недостающие API/CORS оформляются как requests для backend, fixtures не live readiness.

## Темп и автономность

- «Продолжай работу» означает содержательный законченный результат, а не произвольное
  число файлов или одно маленькое исправление. Заверши issue за turn, если scope позволяет;
  большие issues дели на законченные vertical slices с ясными границами.
- Для нетривиальной задачи сначала объясни goal, current/proposed architecture,
  security considerations, affected components и testing/security testing strategy.
- Выполни implementation, исправление устранимых ошибок, verification, security review,
  documentation и commit. Обычные implementation choices решай самостоятельно.
- Работай одним агентом. Subagents и отдельные implementation чаты — только по явной просьбе.
- Clarification нужна для существенной неопределённости product/provider/data policy,
  недоступных credentials, разрушительных действий и внешнего deployment approval.
- Сохраняй существующие пользовательские изменения. Не перезаписывай работу другого чата.

## Security и data integrity

- Correctness, security, architecture, data integrity, maintainability, testability,
  reliability, performance и simplicity оценивай вместе. Не оставляй security shortcuts.
- HTTP input, database user content, URLs, файлы, configuration и model output недоверенные.
- Authorization проверяй server-side на конкретный resource; deny-by-default, scoped queries,
  OWNER/membership checks на API и use-case boundaries. Frontend не является защитой backend.
- Для security-sensitive функций делай threat model: защищаемые данные, callers, trust
  boundaries, attacks, protections, последствия обхода и defense in depth.
- Используй validation/domain invariants и PostgreSQL constraints/runtime least privilege.
  JPA Entity не определяет внешний writable API; исключай mass assignment.
- External I/O выполняй вне долгих DB transactions. Перед publication повторно проверяй
  source, lifecycle, permissions, version/deadline и lease/attempt fencing.
- Соблюдай reviewed parent-first lock order; учитывай Account → Workspace → Brand → revision,
  когда операция требует Account lock. Не переноси этот lock на все операции без анализа.
- Сохраняй checksums существующих Flyway migrations; schema changes — новой migration.
  Production Hibernate `validate`, отдельные migration/runtime identities и grants.
- Проверяй IDOR/BOLA, injection, SSRF, file handling, concurrency, resource exhaustion,
  deadlines/quotas/rate/cost limits, provenance, безопасные errors и logging.
- Не читай/публикуй реальные secrets без необходимости. Не сохраняй credentials/.env в Git.
  Не отключай TLS/CSRF/security mechanisms ради удобства. Cookie auth требует отдельного CSRF review.
- Новые dependencies требуют объяснения необходимости и security/supply-chain review.
- Production activation, live providers, IAM/TLS/sandbox и коммерческий deployment требуют
  собственной qualification; local fixtures этого не доказывают.

## Проверки и Git

- Compilation, formatting, unit/domain, integration и security negative tests обязательны
  для соответствующего implementation scope; bug fixes сопровождай regression tests.
- Persistence/migration/concurrency constraints проверяй на PostgreSQL/Testcontainers.
  При изменении configuration/JPA/DI дополнительно проверь application startup.
- Полная проверка: `.\mvnw.cmd clean verify`; перед commit — Spotless.
  Docker нужен для полного PostgreSQL/MinIO/ClamAV/renderer suite. Применимые Python report
  gates и Linux filesystem/renderer tests также должны быть выполнены.
- Skipped required integration tests не считаются pass. Не ослабляй тест ради реализации.
  Пиши только фактически выполненные команды/results и явно отделяй непроверенное.
- Для docs-only изменений проверь содержимое, ссылки, diff и отсутствие product changes;
  не объявляй прежний test run новым выполненным запуском.
- Каждый законченный проверенный slice сразу сохраняй отдельным local commit.
- Push — после полного завершения issue. Затем проверь CI именно отправленного source SHA,
  закрывай issue только после acceptance criteria/gates и синхронизируй #107.
- Merge и production deployment согласовываются отдельно; восстановление контекста
  само по себе не разрешает push незаконченной feature или deployment.

## Итоговый отчёт

Дай краткий итог и ссылку на подробный отчёт в docs. Для существенной задачи отчёт содержит:
что сделано; как работает; архитектурное решение/alternatives/trade-offs; Security Review
с Threat / Protection / Verification; каждый изменённый файл; Runtime Flow; точные команды
и результаты tests; security scenarios; edge cases; непроверенные аспекты; что важно понять;
1–5 концепций для изучения; production considerations; commits/push/CI/issues.
Явно разделяй подтверждённые факты, инженерные выводы и assumptions.
