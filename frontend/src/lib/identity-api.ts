import { contract, invalid, pageIndex, requestContract, uuid } from './contract';
import type * as C from './identity-contract.generated';
import { ApiTransport } from './transport';

export type ReviewKind = 'TEXT' | 'PDF' | 'URL' | 'SOCIAL';
export type Review =
  | { kind: 'TEXT'; value: C.EvidenceReview }
  | { kind: 'PDF'; value: C.PdfReviewPreview }
  | { kind: 'URL'; value: C.UrlReviewPreview }
  | { kind: 'SOCIAL'; value: C.SocialReviewPreview };
export type Admission = C.EvidenceAdmissionDetails | C.PdfAdmissionDetails | C.UrlAdmissionDetails;
export type Scope = { workspaceId: string; brandId: string };
const roots = {
  TEXT: 'ai-admission',
  PDF: 'document-review',
  URL: 'url-review',
  SOCIAL: 'social-review',
} as const;
const reviewSchemas = {
  TEXT: 'EvidenceReview',
  PDF: 'PdfReviewPreview',
  URL: 'UrlReviewPreview',
  SOCIAL: 'SocialReviewPreview',
} as const;
const admissionSchemas = {
  TEXT: 'EvidenceAdmissionDetails',
  PDF: 'PdfAdmissionDetails',
  URL: 'UrlAdmissionDetails',
  SOCIAL: 'SocialAdmissionDetails',
} as const;
const snapshotSchemas = {
  TEXT: 'EvidenceAdmissionSnapshot',
  PDF: 'PdfAdmissionSnapshot',
  URL: 'UrlAdmissionSnapshot',
  SOCIAL: 'SocialAdmissionSnapshot',
} as const;
const postSchemas = {
  TEXT: 'AdmitEvidenceRequest',
  PDF: 'AdmitPdfEvidenceRequest',
  URL: 'AdmitUrlEvidenceRequest',
  SOCIAL: 'AdmitSocialEvidenceRequest',
} as const;

function brandPath(scope: Scope) {
  uuid(scope.workspaceId);
  return `/brands/${uuid(scope.brandId)}`;
}
function sourcePath(id: string) {
  return `/evidence/${uuid(id)}`;
}
function samePage<T extends { page: number; size: number; content: unknown[] }>(
  value: T,
  page: number,
): T {
  if (value.page !== page || value.size !== 20 || value.content.length > 20) invalid();
  return value;
}
function sameIdentity(value: C.BrandIdentityResponse, scope: Scope, id?: string) {
  if (
    value.brandId !== scope.brandId ||
    value.workspaceId !== scope.workspaceId ||
    (id && id !== value.id)
  )
    invalid();
  return value;
}
function draft(value: C.BrandIdentityResponse) {
  if (value.status !== 'DRAFT' || value.approvedAt !== null || value.approvedBy !== null) invalid();
  return value;
}
function generationReceipt(value: C.AiGenerationJob) {
  const succeeded = value.status === 'SUCCEEDED';
  const terminal = !['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(value.status);
  if (
    value.kind !== 'EVIDENCE' ||
    succeeded !== (value.resultId !== null) ||
    terminal !== (value.completedAt !== null) ||
    (succeeded && value.resultUrl !== `/api/v1/analysis-results/${value.resultId}`) ||
    (!succeeded && value.resultUrl !== null)
  )
    invalid();
  return value;
}
async function exactText(text: string, checksum: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  const hex = Array.from(new Uint8Array(digest), (x) => x.toString(16).padStart(2, '0')).join('');
  if (hex !== checksum) invalid();
}
export function reviewKind(policy: string): ReviewKind {
  switch (policy) {
    case 'text-review-v1':
      return 'TEXT';
    case 'pdf-text-review-v1':
      return 'PDF';
    case 'url-text-review-v1':
      return 'URL';
    case 'social-text-review-v1':
      return 'SOCIAL';
    default:
      return invalid();
  }
}

/** Fixed scoped builders + validators from actual OpenAPI; never fetch server/model URLs. */
export class EvidenceIdentityApi {
  constructor(private readonly http: ApiTransport) {}
  async sources(scope: Scope, page: number, signal: AbortSignal) {
    const result = samePage(
      contract(
        'BrandEvidencePage',
        await this.http.json(
          `${brandPath(scope)}/evidence?page=${pageIndex(page)}&size=20`,
          signal,
          { maxBytes: 4194304 },
        ),
      ),
      page,
    );
    if (result.content.some((v) => v.brandId !== scope.brandId)) invalid();
    return result;
  }
  async register(scope: Scope, type: 'TEXT' | 'URL', value: string, signal: AbortSignal) {
    if (type === 'URL' && value.length > 2048) invalid();
    return this.registration(
      scope,
      await this.http.json(`${brandPath(scope)}/evidence`, signal, {
        body: requestContract('RegisterEvidenceRequest', { type, value }),
      }),
    );
  }
  async social(scope: Scope, provider: 'INSTAGRAM' | 'TIKTOK', signal: AbortSignal) {
    return this.registration(
      scope,
      await this.http.json(`${brandPath(scope)}/evidence/social`, signal, {
        body: requestContract('RegisterSocialEvidenceRequest', { provider }),
      }),
    );
  }
  async upload(scope: Scope, file: File, signal: AbortSignal) {
    if (
      !['application/pdf', 'image/png', 'image/jpeg'].includes(file.type) ||
      file.size < 1 ||
      file.size > 10 * 1024 * 1024
    )
      invalid();
    const body = new FormData();
    body.append('file', file);
    return this.registration(
      scope,
      await this.http.json(`${brandPath(scope)}/evidence`, signal, { body }),
    );
  }
  private registration(scope: Scope, value: unknown) {
    const r = contract('EvidenceRegistrationResponse', value);
    if (r.evidence.brandId !== scope.brandId) invalid();
    return r;
  }
  async ingestJob(id: string, signal: AbortSignal) {
    const result = contract('JobResponse', await this.http.json(`/jobs/${uuid(id)}`, signal));
    if (result.id !== id) invalid();
    return result;
  }
  async preview(kind: ReviewKind, id: string, signal: AbortSignal): Promise<Review> {
    const value = contract(
      reviewSchemas[kind],
      await this.http.json(`${sourcePath(id)}/${roots[kind]}`, signal),
    );
    if (value.evidenceId !== id) invalid();
    await exactText(value.text, value.contentChecksum);
    signal.throwIfAborted();
    return Object.freeze({ kind, value: Object.freeze(value) }) as Review;
  }
  async admit(review: Review, signal: AbortSignal): Promise<Admission> {
    // Pick ONLY the selectors in the request schema from the exact displayed preview.
    const body = contract(postSchemas[review.kind], review.value);
    const value = contract(
      admissionSchemas[review.kind],
      await this.http.json(
        `${sourcePath(review.value.evidenceId)}/${roots[review.kind]}${review.kind === 'TEXT' ? '' : '/admissions'}`,
        signal,
        { body },
      ),
    );
    const receipt = 'review' in value ? value.review : value;
    for (const field of [
      'evidenceId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'contentChecksum',
      'sourceChecksum',
    ] as const)
      if (receipt[field] !== review.value[field]) invalid();
    if (reviewKind(receipt.policyVersion) !== review.kind) invalid();
    const returned = { ...receipt, ...value } as Record<string, unknown>;
    const captured = review.value as unknown as Record<string, unknown>;
    for (const field of [
      'artifactId',
      'originalScanId',
      'latestScanId',
      'snapshotId',
      'scanExpiresAt',
      'bindingExpiresAt',
    ])
      if (field in captured && captured[field] !== returned[field]) invalid();
    return receipt;
  }
  async history(id: string, page: number, signal: AbortSignal) {
    const result = samePage(
      contract(
        'EvidenceAdmissionHistoryPage',
        await this.http.json(
          `${sourcePath(id)}/ai-admission/history?page=${pageIndex(page)}&size=20`,
          signal,
        ),
      ),
      page,
    );
    if (result.content.some((v) => v.evidenceId !== id)) invalid();
    return result;
  }
  async snapshot(kind: ReviewKind, id: string, admissionId: string, signal: AbortSignal) {
    const route =
      kind === 'TEXT'
        ? `ai-admission/${uuid(admissionId)}/snapshot`
        : `${roots[kind]}/admissions/${uuid(admissionId)}/snapshot`;
    const r = contract(
      snapshotSchemas[kind],
      await this.http.json(`${sourcePath(id)}/${route}`, signal),
    );
    const a = 'review' in r.admission ? r.admission.review : r.admission;
    if (a.evidenceId !== id || a.id !== admissionId || reviewKind(a.policyVersion) !== kind)
      invalid();
    await exactText(r.text, a.contentChecksum);
    signal.throwIfAborted();
    return { admission: a, text: r.text };
  }
  async withdraw(id: string, admissionId: string, signal: AbortSignal) {
    const r = contract(
      'EvidenceWithdrawalDetails',
      await this.http.json(
        `${sourcePath(id)}/ai-admission/${uuid(admissionId)}/withdrawals`,
        signal,
        { method: 'POST' },
      ),
    );
    if (r.admissionId !== admissionId) invalid();
    return r;
  }
  async identities(scope: Scope, page: number, signal: AbortSignal) {
    const result = samePage(
      contract(
        'IdentityPage',
        await this.http.json(
          `${brandPath(scope)}/identities?page=${pageIndex(page)}&size=20&sort=version&direction=desc`,
          signal,
        ),
      ),
      page,
    );
    result.content.forEach((v) => sameIdentity(v, scope));
    return result;
  }
  async readIdentity(scope: Scope, id: string, signal: AbortSignal) {
    return sameIdentity(
      contract('BrandIdentityResponse', await this.http.json(`/identities/${uuid(id)}`, signal)),
      scope,
      id,
    );
  }
  async createIdentity(
    scope: Scope,
    content: C.CreateBrandIdentityRequest['content'],
    signal: AbortSignal,
  ) {
    return draft(
      sameIdentity(
        contract(
          'BrandIdentityResponse',
          await this.http.json(`${brandPath(scope)}/identities`, signal, {
            body: requestContract('CreateBrandIdentityRequest', { content }),
          }),
        ),
        scope,
      ),
    );
  }
  async editIdentity(
    scope: Scope,
    identity: C.BrandIdentityResponse,
    content: C.CreateBrandIdentityRequest['content'],
    signal: AbortSignal,
  ) {
    sameIdentity(identity, scope);
    const r = sameIdentity(
      contract(
        'BrandIdentityResponse',
        await this.http.json(`/identities/${uuid(identity.id)}`, signal, {
          method: 'PATCH',
          body: requestContract('UpdateBrandIdentityRequest', {
            expectedVersion: identity.lockVersion,
            content,
          }),
        }),
      ),
      scope,
    );
    if (
      identity.status === 'DRAFT'
        ? r.id !== identity.id
        : r.id === identity.id || r.revisedFromId !== identity.id
    )
      invalid();
    return draft(r);
  }
  async approve(scope: Scope, identity: C.BrandIdentityResponse, signal: AbortSignal) {
    sameIdentity(identity, scope);
    const r = sameIdentity(
      contract(
        'BrandIdentityResponse',
        await this.http.json(`/identities/${uuid(identity.id)}/approval`, signal, {
          body: requestContract('VersionRequest', { expectedVersion: identity.lockVersion }),
        }),
      ),
      scope,
      identity.id,
    );
    if (r.status !== 'APPROVED' || !r.approvedAt || !r.approvedBy) invalid();
    return r;
  }
  async provenance(scope: Scope, id: string, signal: AbortSignal) {
    brandPath(scope);
    const r = contract(
      'IdentityEvidenceProvenance',
      await this.http.json(`/identities/${uuid(id)}/evidence-provenance`, signal),
    );
    if (r.identityId !== id) invalid();
    return r;
  }
  async submit(scope: Scope, expectedVersion: number, key: string, signal: AbortSignal) {
    const r = contract(
      'AiGenerationJob',
      await this.http.json(`${brandPath(scope)}/analysis-jobs`, signal, {
        key,
        body: requestContract('AnalysisJobRequest', { expectedVersion, mode: 'EVIDENCE' }),
      }),
    );
    return generationReceipt(r);
  }
  async generationJob(id: string, signal: AbortSignal) {
    const r = contract('AiGenerationJob', await this.http.json(`/jobs/${uuid(id)}`, signal));
    if (r.jobId !== id) invalid();
    return generationReceipt(r);
  }
  async analysisResult(id: string, signal: AbortSignal) {
    return contract(
      'AnalysisJobResult',
      await this.http.json(`/analysis-results/${uuid(id)}`, signal),
    );
  }
  async copyAnalysis(scope: Scope, analysisId: string, brandVersion: number, signal: AbortSignal) {
    const r = draft(
      sameIdentity(
        contract(
          'BrandIdentityResponse',
          await this.http.json(`${brandPath(scope)}/identities/from-evidence-analysis`, signal, {
            body: requestContract('EvidenceIdentityFromAnalysisRequest', {
              analysisId: uuid(analysisId),
              expectedVersion: brandVersion,
            }),
          }),
        ),
        scope,
      ),
    );
    if (r.analysisProvenance?.analysisId !== analysisId) invalid();
    return r;
  }
}
