import { createHash } from 'node:crypto';
import type * as C from '../../src/lib/identity-contract.generated';
export const id = (n: number) => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`;
export const w = id(1),
  b = id(2),
  e = id(3),
  a = id(4),
  j = id(5),
  r = id(6),
  i = id(7);
export const time = '2026-10-18T10:00:00Z';
export const expiry = '2026-10-19T10:00:00Z';
export const text = 'Our voice <img src=x onerror="alert(1)"> 🪴 is thoughtful.';
export const hash = (s: string) => createHash('sha256').update(s).digest('hex');
export const pageOf = (content: unknown[]) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
  first: true,
  last: true,
});
export const workspace = {
  id: w,
  name: 'Studio',
  industry: 'Design',
  status: 'ACTIVE',
  lockVersion: 0,
};
export const brand = {
  id: b,
  workspaceId: w,
  name: 'North',
  status: 'ACTIVE',
  lockVersion: 0,
  websiteUrl: null,
  instagramHandle: null,
  tiktokHandle: null,
};
export const evidence: C.BrandEvidenceResponse = {
  id: e,
  brandId: b,
  type: 'TEXT',
  status: 'READY',
  sourceReference: text,
  checksum: hash(text),
  capturedAt: time,
  warning: null,
  originalFileName: null,
  mediaType: null,
  createdAt: time,
};
export const review: C.EvidenceReview = {
  evidenceId: e,
  sourceVersion: 0,
  brandVersion: 0,
  workspaceVersion: 0,
  sourceChecksum: hash(text),
  contentChecksum: hash(text),
  text,
  policyVersion: 'text-review-v1',
};
export const admission: C.EvidenceAdmissionDetails = {
  id: a,
  evidenceId: e,
  sourceVersion: 0,
  brandVersion: 0,
  workspaceVersion: 0,
  sourceChecksum: hash(text),
  contentChecksum: hash(text),
  reviewedBy: id(8),
  reviewedAt: time,
  policyVersion: 'text-review-v1',
};
export const receipt: C.EvidenceAdmissionHistory = {
  ...admission,
  artifactId: null,
  originalScanId: null,
  latestScanId: null,
  snapshotId: null,
  scanExpiresAt: null,
  bindingExpiresAt: null,
  withdrawn: false,
  withdrawnBy: null,
  withdrawnAt: null,
};
export const job: C.AiGenerationJob = {
  jobId: j,
  operationId: id(9),
  kind: 'EVIDENCE',
  status: 'SUCCEEDED',
  submittedAt: time,
  deadline: expiry,
  completedAt: time,
  resultId: r,
  resultUrl: `/api/v1/analysis-results/${r}`,
  attempts: 1,
  errorCode: null,
};
export const content: C.IdentityContentRequest = {
  palette: {
    primary: '#112233',
    secondary: '#445566',
    accent: '#778899',
    neutral: '#AAAAAA',
    background: '#FFFFFF',
  },
  fonts: ['DM Sans'],
  voiceUp: ['Thoughtful'],
  voiceDown: ['Pushy'],
  sampleCaption: 'A reviewed caption.',
};
export const result: C.AnalysisJobResult = {
  ...content,
  warnings: ['DEMONSTRATION_ONLY', 'HUMAN_REVIEW_REQUIRED'],
  provider: 'fake-local',
  modelVersion: 'deterministic-v1',
  promptVersion: 'identity-evidence-v1',
  citations: [{ admissionId: a, quote: text }],
};
export const identity: C.BrandIdentityResponse = {
  id: i,
  brandId: b,
  workspaceId: w,
  version: 1,
  lockVersion: 0,
  status: 'DRAFT',
  createdAt: time,
  updatedAt: time,
  content,
  revisedFromId: null,
  approvedBy: null,
  approvedAt: null,
  analysisProvenance: {
    analysisId: r,
    provider: 'fake-local',
    modelVersion: 'deterministic-v1',
    promptVersion: 'identity-evidence-v1',
    warnings: 'DEMONSTRATION_ONLY,HUMAN_REVIEW_REQUIRED',
    initialOutputChecksum: hash(JSON.stringify(content)),
    generatedAt: time,
  },
};
export const provenance: C.IdentityEvidenceProvenance = {
  identityId: i,
  originIdentityId: i,
  analysisId: r,
  copiedBy: id(8),
  sourceChecksum: hash(text),
  citations: [
    {
      admissionId: a,
      evidenceId: e,
      sourceVersion: 0,
      quote: text,
      withdrawn: false,
      artifactId: null,
      originalScanId: null,
      latestScanId: null,
      urlSnapshotId: null,
      socialSnapshotId: null,
    },
  ],
};
export const pdf: C.PdfReviewPreview = {
  ...review,
  policyVersion: 'pdf-text-review-v1',
  artifactId: id(10),
  originalScanId: id(11),
  latestScanId: id(12),
  rendererVersion: 'renderer-v1',
  scanExpiresAt: expiry,
  checkedAt: time,
  requiresHumanReview: true,
  admittedToAi: false,
};
export const url: C.UrlReviewPreview = {
  ...review,
  policyVersion: 'url-text-review-v1',
  snapshotId: id(10),
  latestScanId: id(12),
  scanExpiresAt: expiry,
  checkedAt: time,
  requiresHumanReview: true,
  admittedToAi: false,
  metadata: { title: 'North', description: 'Creator', text: 'Words', warnings: '', version: 'v1' },
  fetchJobId: id(13),
  fetchAttempt: 1,
  fetchedChecksum: hash('bytes'),
  fetchedAt: time,
  extractedAt: time,
};
export const social: C.SocialReviewPreview = {
  ...review,
  policyVersion: 'social-text-review-v1',
  snapshotId: id(10),
  provider: 'INSTAGRAM',
  capturedAt: time,
  bindingExpiresAt: expiry,
  warnings: 'HISTORICAL_SNAPSHOT',
};
