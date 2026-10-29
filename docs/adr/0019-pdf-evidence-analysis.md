# ADR0019 — Reviewed PDF text in bounded evidence analysis

Дата: 2026-10-14. Scope: PDF/TEXT generation и publication; PDF Identity copy/approval отдельно.

Явный `analyze-evidence` расширяется reviewed PDF_TEXT admissions. Server выбирает latest
reviewedAt/id consent каждого evidence в current source/Brand/Workspace context, сортирует
по evidence ID, не принимает client IDs. Лимиты прежние:1–5 sources,20000 UTF-8 bytes total.
Latest выбранный consent должен быть eligible: без fallback к старому scan/expiry receipt.

Текущий AnalysisTextSource передаёт reviewed text, не source bytes/key. Format input digest
`text-evidence-v1` сохраняется: это textual snapshot format; immutable admission ID теперь может
ссылаться на TEXT или PDF_TEXT policy. Old receipts/digests не меняются; source kind и scan
provenance остаются в immutable admission, не выдумываются для legacy данных.
Internal snapshot отдельно фиксирует exact PDF admission ID set; preflight/publication проверяют
его соответствие persisted source links, чтобы нельзя было пропустить physical verification.

Workflow: capture/commit RUNNING + source links → physical PDF receipt checks вне transactions →
fresh all-source/account preflight → model port → fresh metadata/access preflight → повторные physical PDF checks → fresh
publication transaction. Each physical batch deadline30s; overall attempt deadline сохраняется.
Shared PDF read bulkhead и новый two-slot analysis bulkhead ограничивают concurrent work.
Physical storage не блокируется DB locks: rogue deletion после witness остаётся operational risk.

V37 расширяет SQL analysis admission guard для PDF: exact artifact/original/latest CLEAN, expiry,
SUCCEEDED extraction, referenced scoped ledger, checksums/text/renderer. Publication Brand+1
учитывается общим analysis guard. SQL не видит bytes; application orchestration обязателен.
TXT checks и bounded deferred input seal неизменны; V1–V36 сохраняются.

PDF evidence analysis пока нельзя copy/approve в Identity: Java/V35 guards остаются TEXT-only.
No silent legacy copy/manual origin fabrication. Signature/attempt/citations publication —
законченная vertical slice; full #121 требует PDF Identity fences и прочих оставшихся scopes.
Production model port остаётся fail-closed; fake только local/test, human review обязательно.

Alternative сразу расширить Identity отвергнута для этого slice: copy/approval после publication
требуют отдельного physical/read lifetime workflow и inherited source guards. Client-selected
source IDs или raw PDF input не утверждены. Бounded server selection сохраняет существующий API.

Threat model: private text/provenance защищены от BOLA, stale/withdrawn/expired scans, changed bytes,
access/archive/source races и forged citations. Guards: actual OWNER/member, Account→Workspace→
Brand→Evidence→Quarantine ordering, metadata/physical checks до egress и publication, bounded
commands, exact quotes и immutable SQL links. Model text остаётся data, не instructions/tools.
No dependencies или live credentials; distributed quotas/provider/retention qualification отдельно.

## Дополнение 2026-10-14 — PDF Identity

Ограничение TEXT-only Identity этого исторического slice заменено [ADR0020](0020-pdf-identity-consumption.md)
и V38. PDF copy/direct+inherited approval теперь проверяют original publication lineage,
physical bytes вне transactions и fresh locked metadata/access. Остальной contract сохраняется.
