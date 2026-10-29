// Generated from actual OpenAPI; edit schema/generator, never this file.
// source SHA256 140b17c47f8931ef334387d7597594d497334c456177c49c8cd5f666ffe97f5c
export type AdmitEvidenceRequest = {
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  contentChecksum: string;
};
export type AdmitPdfEvidenceRequest = {
  artifactId: string;
  originalScanId: string;
  latestScanId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  scanExpiresAt: string;
  policyVersion: 'pdf-text-review-v1';
};
export type AdmitSocialEvidenceRequest = {
  snapshotId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  contentChecksum: string;
  bindingExpiresAt: string;
};
export type AdmitUrlEvidenceRequest = {
  snapshotId: string;
  latestScanId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  scanExpiresAt: string;
  policyVersion: 'url-text-review-v1';
};
export type AiGenerationJob = {
  jobId: string;
  operationId: string;
  kind: 'DECLARED' | 'EVIDENCE' | 'STRATEGY' | 'CONTENT';
  status:
    | 'QUEUED'
    | 'RUNNING'
    | 'RETRY_WAIT'
    | 'SUCCEEDED'
    | 'SUPERSEDED'
    | 'FAILED'
    | 'EXPIRED'
    | 'OUTCOME_UNKNOWN';
  submittedAt: string;
  deadline: string;
  completedAt: string | null;
  resultId: string | null;
  resultUrl: string | null;
  attempts: number;
  errorCode:
    | 'LEASE_EXPIRED_BEFORE_DISPATCH'
    | 'INPUT_UNAVAILABLE'
    | 'SOURCE_OR_ACCESS_CHANGED'
    | 'INVALID_OUTPUT'
    | 'DEADLINE_EXPIRED'
    | 'PROVIDER_OUTCOME_UNKNOWN'
    | 'CAPABILITY_UNAVAILABLE'
    | 'PUBLICATION_REJECTED'
    | 'GENERATION_FAILED'
    | 'BUDGET_EXCEEDED'
    | null;
};
export type AnalysisCitation = { admissionId: string; quote: string; fieldPath?: string | null };
export type AnalysisJobRequest = { expectedVersion: number; mode: 'DECLARED' | 'EVIDENCE' };
export type AnalysisJobResult = {
  palette: ColorPaletteResponse | null;
  fonts: Array<string>;
  voiceUp: Array<string>;
  voiceDown: Array<string>;
  sampleCaption: string | null;
  warnings: Array<'DEMONSTRATION_ONLY' | 'DECLARED_FIELDS_ONLY' | 'HUMAN_REVIEW_REQUIRED'>;
  provider: string;
  modelVersion: string;
  promptVersion: string;
  citations: Array<AnalysisCitation>;
};
export type BrandEvidencePage = {
  content: Array<BrandEvidenceResponse>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
export type BrandEvidenceResponse = {
  id: string;
  brandId: string;
  type: 'URL' | 'TEXT' | 'FILE';
  status: 'PENDING' | 'FETCHED' | 'READY' | 'FAILED';
  sourceReference: string | null;
  checksum: string | null;
  capturedAt: string;
  warning: string | null;
  originalFileName: string | null;
  mediaType: string | null;
  createdAt: string;
};
export type BrandIdentityResponse = {
  id: string;
  brandId: string;
  workspaceId: string;
  version: number;
  lockVersion: number;
  status: 'DRAFT' | 'APPROVED' | 'LOCKED';
  createdAt: string;
  updatedAt: string;
  content: IdentityContentResponse;
  revisedFromId: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  analysisProvenance: IdentityAnalysisProvenance | null;
};
export type Citation = {
  admissionId: string;
  evidenceId: string;
  sourceVersion: number;
  quote: string;
  withdrawn: boolean;
  artifactId: string | null;
  originalScanId: string | null;
  latestScanId: string | null;
  fieldPath?: string | null;
  urlSnapshotId?: string | null;
  socialSnapshotId: string | null;
};
export type ColorPaletteResponse = {
  primary: string | null;
  secondary: string | null;
  accent: string | null;
  neutral: string | null;
  background: string | null;
};
export type CreateBrandIdentityRequest = { content: IdentityContentRequest };
export type EvidenceAdmissionDetails = {
  id: string;
  evidenceId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  reviewedBy: string;
  reviewedAt: string;
  policyVersion: 'text-review-v1' | 'social-text-review-v1';
};
export type EvidenceAdmissionHistory = {
  id: string;
  evidenceId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  reviewedBy: string;
  reviewedAt: string;
  policyVersion:
    'text-review-v1' | 'pdf-text-review-v1' | 'url-text-review-v1' | 'social-text-review-v1';
  artifactId: string | null;
  originalScanId: string | null;
  latestScanId: string | null;
  snapshotId: string | null;
  withdrawnBy: string | null;
  scanExpiresAt: string | null;
  bindingExpiresAt: string | null;
  withdrawnAt: string | null;
  withdrawn: boolean;
};
export type EvidenceAdmissionHistoryPage = {
  content: Array<EvidenceAdmissionHistory>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
export type EvidenceAdmissionSnapshot = { admission: EvidenceAdmissionDetails; text: string };
export type EvidenceIdentityFromAnalysisRequest = { analysisId: string; expectedVersion: number };
export type EvidenceRegistrationResponse = {
  evidence: BrandEvidenceResponse;
  jobId: string;
  jobStatus: 'PENDING' | 'RUNNING' | 'FAILED' | 'SUCCEEDED' | 'DEAD';
};
export type EvidenceReview = {
  evidenceId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  text: string;
  policyVersion: 'text-review-v1';
};
export type EvidenceWithdrawalDetails = {
  id: string;
  admissionId: string;
  withdrawnBy: string;
  withdrawnAt: string;
};
export type IdentityAnalysisProvenance = {
  analysisId: string;
  provider: string;
  modelVersion: string;
  promptVersion: string;
  warnings: string;
  initialOutputChecksum: string;
  generatedAt: string;
};
export type IdentityContentRequest = {
  palette: IdentityPaletteRequest;
  fonts: Array<string>;
  voiceUp: Array<string>;
  voiceDown: Array<string>;
  sampleCaption: string;
};
export type IdentityContentResponse = {
  palette: IdentityPalette;
  fonts: Array<string>;
  voiceUp: Array<string>;
  voiceDown: Array<string>;
  sampleCaption: string;
};
export type IdentityEvidenceProvenance = {
  identityId: string;
  originIdentityId: string | null;
  analysisId: string | null;
  copiedBy: string | null;
  sourceChecksum: string | null;
  citations: Array<Citation>;
};
export type IdentityPage = {
  content: Array<BrandIdentityResponse>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
export type IdentityPalette = {
  primary: string;
  secondary: string;
  accent: string;
  neutral: string;
  background: string;
};
export type IdentityPaletteRequest = {
  primary: string;
  secondary: string;
  accent: string;
  neutral: string;
  background: string;
};
export type JobResponse = {
  id: string;
  status: 'PENDING' | 'RUNNING' | 'FAILED' | 'SUCCEEDED' | 'DEAD';
  attempts: number;
  maxAttempts: number;
  nextAttemptAt: string | null;
  completedAt: string | null;
};
export type PdfAdmissionDetails = {
  id: string;
  evidenceId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  reviewedBy: string;
  reviewedAt: string;
  policyVersion: 'pdf-text-review-v1';
  artifactId: string;
  originalScanId: string;
  latestScanId: string;
  rendererVersion: string;
  scanExpiresAt: string;
  aiConsumptionEnabled: true;
};
export type PdfAdmissionSnapshot = { admission: PdfAdmissionDetails; text: string };
export type PdfReviewPreview = {
  evidenceId: string;
  artifactId: string;
  originalScanId: string;
  latestScanId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  text: string;
  rendererVersion: string;
  policyVersion: 'pdf-text-review-v1';
  scanExpiresAt: string;
  checkedAt: string;
  requiresHumanReview: true;
  admittedToAi: false;
};
export type RegisterEvidenceRequest = { type: 'URL' | 'TEXT' | 'FILE'; value: string };
export type RegisterSocialEvidenceRequest = { provider: 'INSTAGRAM' | 'TIKTOK' };
export type SocialAdmissionDetails = {
  review: EvidenceAdmissionDetails;
  snapshotId: string;
  capturedAt: string;
  bindingExpiresAt: string;
};
export type SocialAdmissionSnapshot = { admission: SocialAdmissionDetails; text: string };
export type SocialReviewPreview = {
  snapshotId: string;
  evidenceId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  text: string;
  provider: 'INSTAGRAM' | 'TIKTOK';
  capturedAt: string;
  bindingExpiresAt: string;
  policyVersion: 'social-text-review-v1';
  warnings: string;
};
export type UpdateBrandIdentityRequest = {
  expectedVersion: number;
  content: IdentityContentRequest;
};
export type UrlAdmissionDetails = {
  id: string;
  evidenceId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  reviewedBy: string;
  reviewedAt: string;
  policyVersion: 'url-text-review-v1';
  snapshotId: string;
  originalScanId: string;
  latestScanId: string;
  scanExpiresAt: string;
  aiConsumptionEnabled: true;
};
export type UrlAdmissionSnapshot = { admission: UrlAdmissionDetails; text: string };
export type UrlReviewPreview = {
  evidenceId: string;
  snapshotId: string;
  latestScanId: string;
  sourceVersion: number;
  brandVersion: number;
  workspaceVersion: number;
  sourceChecksum: string;
  contentChecksum: string;
  text: string;
  policyVersion: 'url-text-review-v1';
  scanExpiresAt: string;
  checkedAt: string;
  requiresHumanReview: true;
  admittedToAi: false;
  metadata: WebsiteMetadata;
  fetchJobId: string;
  fetchAttempt: number;
  fetchedChecksum: string;
  fetchedAt: string;
  extractedAt: string;
};
export type VersionRequest = { expectedVersion: number };
export type WebsiteMetadata = {
  title: string;
  description: string;
  text: string;
  warnings: string;
  version: string;
};
export type ContractTypes = {
  AdmitEvidenceRequest: AdmitEvidenceRequest;
  AdmitPdfEvidenceRequest: AdmitPdfEvidenceRequest;
  AdmitSocialEvidenceRequest: AdmitSocialEvidenceRequest;
  AdmitUrlEvidenceRequest: AdmitUrlEvidenceRequest;
  AiGenerationJob: AiGenerationJob;
  AnalysisCitation: AnalysisCitation;
  AnalysisJobRequest: AnalysisJobRequest;
  AnalysisJobResult: AnalysisJobResult;
  BrandEvidencePage: BrandEvidencePage;
  BrandEvidenceResponse: BrandEvidenceResponse;
  BrandIdentityResponse: BrandIdentityResponse;
  Citation: Citation;
  ColorPaletteResponse: ColorPaletteResponse;
  CreateBrandIdentityRequest: CreateBrandIdentityRequest;
  EvidenceAdmissionDetails: EvidenceAdmissionDetails;
  EvidenceAdmissionHistory: EvidenceAdmissionHistory;
  EvidenceAdmissionHistoryPage: EvidenceAdmissionHistoryPage;
  EvidenceAdmissionSnapshot: EvidenceAdmissionSnapshot;
  EvidenceIdentityFromAnalysisRequest: EvidenceIdentityFromAnalysisRequest;
  EvidenceRegistrationResponse: EvidenceRegistrationResponse;
  EvidenceReview: EvidenceReview;
  EvidenceWithdrawalDetails: EvidenceWithdrawalDetails;
  IdentityAnalysisProvenance: IdentityAnalysisProvenance;
  IdentityContentRequest: IdentityContentRequest;
  IdentityContentResponse: IdentityContentResponse;
  IdentityEvidenceProvenance: IdentityEvidenceProvenance;
  IdentityPage: IdentityPage;
  IdentityPalette: IdentityPalette;
  IdentityPaletteRequest: IdentityPaletteRequest;
  JobResponse: JobResponse;
  PdfAdmissionDetails: PdfAdmissionDetails;
  PdfAdmissionSnapshot: PdfAdmissionSnapshot;
  PdfReviewPreview: PdfReviewPreview;
  RegisterEvidenceRequest: RegisterEvidenceRequest;
  RegisterSocialEvidenceRequest: RegisterSocialEvidenceRequest;
  SocialAdmissionDetails: SocialAdmissionDetails;
  SocialAdmissionSnapshot: SocialAdmissionSnapshot;
  SocialReviewPreview: SocialReviewPreview;
  UpdateBrandIdentityRequest: UpdateBrandIdentityRequest;
  UrlAdmissionDetails: UrlAdmissionDetails;
  UrlAdmissionSnapshot: UrlAdmissionSnapshot;
  UrlReviewPreview: UrlReviewPreview;
  VersionRequest: VersionRequest;
  WebsiteMetadata: WebsiteMetadata;
};
export const schemas = {
  BrandEvidencePage: {
    type: 'object',
    additionalProperties: false,
    properties: {
      content: { type: 'array', items: { $ref: '#/components/schemas/BrandEvidenceResponse' } },
      page: { type: 'integer', minimum: 0 },
      size: { type: 'integer', minimum: 1, maximum: 100 },
      totalElements: { type: 'integer', minimum: 0 },
      totalPages: { type: 'integer', minimum: 0 },
      first: { type: 'boolean' },
      last: { type: 'boolean' },
    },
    required: ['content', 'page', 'size', 'totalElements', 'totalPages', 'first', 'last'],
  },
  BrandEvidenceResponse: {
    type: 'object',
    properties: {
      id: { type: 'string', format: 'uuid' },
      brandId: { type: 'string', format: 'uuid' },
      type: { type: 'string', enum: ['URL', 'TEXT', 'FILE'] },
      status: { type: 'string', enum: ['PENDING', 'FETCHED', 'READY', 'FAILED'] },
      sourceReference: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      checksum: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      capturedAt: {
        type: 'string',
        format: 'date-time',
        description: 'UTC instant, serialized with Z suffix',
      },
      warning: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      originalFileName: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      mediaType: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      createdAt: {
        type: 'string',
        format: 'date-time',
        description: 'UTC instant, serialized with Z suffix',
      },
    },
    required: [
      'id',
      'brandId',
      'type',
      'status',
      'sourceReference',
      'checksum',
      'capturedAt',
      'warning',
      'originalFileName',
      'mediaType',
      'createdAt',
    ],
    additionalProperties: false,
  },
  EvidenceRegistrationResponse: {
    type: 'object',
    properties: {
      evidence: { $ref: '#/components/schemas/BrandEvidenceResponse' },
      jobId: { type: 'string', format: 'uuid' },
      jobStatus: { type: 'string', enum: ['PENDING', 'RUNNING', 'FAILED', 'SUCCEEDED', 'DEAD'] },
    },
    required: ['evidence', 'jobId', 'jobStatus'],
    additionalProperties: false,
  },
  RegisterEvidenceRequest: {
    type: 'object',
    properties: {
      type: { type: 'string', enum: ['URL', 'TEXT', 'FILE'] },
      value: { type: 'string', minLength: 1, pattern: '\\S', maxLength: 20000 },
    },
    required: ['type', 'value'],
    additionalProperties: true,
    description:
      'Only declared DTO fields affect state. Unknown input fields are ignored; internal fields cannot be assigned.',
  },
  RegisterSocialEvidenceRequest: {
    type: 'object',
    properties: { provider: { type: 'string', enum: ['INSTAGRAM', 'TIKTOK'] } },
    required: ['provider'],
    additionalProperties: true,
    description:
      'Only declared DTO fields affect state. Unknown input fields are ignored; internal fields cannot be assigned.',
  },
  JobResponse: {
    type: 'object',
    properties: {
      id: { type: 'string', format: 'uuid' },
      status: { type: 'string', enum: ['PENDING', 'RUNNING', 'FAILED', 'SUCCEEDED', 'DEAD'] },
      attempts: { type: 'integer', format: 'int32' },
      maxAttempts: { type: 'integer', format: 'int32' },
      nextAttemptAt: {
        anyOf: [
          {
            type: 'string',
            format: 'date-time',
            description: 'UTC instant, serialized with Z suffix',
          },
          { type: 'null' },
        ],
      },
      completedAt: {
        anyOf: [
          {
            type: 'string',
            format: 'date-time',
            description: 'UTC instant, serialized with Z suffix',
          },
          { type: 'null' },
        ],
      },
    },
    required: ['id', 'status', 'attempts', 'maxAttempts', 'nextAttemptAt', 'completedAt'],
    additionalProperties: false,
  },
  EvidenceReview: {
    type: 'object',
    additionalProperties: false,
    required: [
      'evidenceId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'text',
      'policyVersion',
    ],
    properties: {
      evidenceId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', minimum: 0 },
      brandVersion: { type: 'integer', minimum: 0 },
      workspaceVersion: { type: 'integer', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      text: { type: 'string', minLength: 1, maxLength: 20000 },
      policyVersion: { type: 'string', const: 'text-review-v1' },
    },
  },
  PdfReviewPreview: {
    type: 'object',
    additionalProperties: false,
    required: [
      'evidenceId',
      'artifactId',
      'originalScanId',
      'latestScanId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'text',
      'rendererVersion',
      'policyVersion',
      'scanExpiresAt',
      'checkedAt',
      'requiresHumanReview',
      'admittedToAi',
    ],
    properties: {
      evidenceId: { type: 'string', format: 'uuid' },
      artifactId: { type: 'string', format: 'uuid' },
      originalScanId: { type: 'string', format: 'uuid' },
      latestScanId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      brandVersion: { type: 'integer', format: 'int64', minimum: 0 },
      workspaceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      text: { type: 'string', minLength: 1, maxLength: 16000 },
      rendererVersion: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$' },
      policyVersion: { type: 'string', const: 'pdf-text-review-v1' },
      scanExpiresAt: { type: 'string', format: 'date-time' },
      checkedAt: { type: 'string', format: 'date-time' },
      requiresHumanReview: { type: 'boolean', const: true },
      admittedToAi: { type: 'boolean', const: false },
    },
  },
  UrlReviewPreview: {
    type: 'object',
    additionalProperties: false,
    required: [
      'evidenceId',
      'snapshotId',
      'latestScanId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'text',
      'policyVersion',
      'scanExpiresAt',
      'checkedAt',
      'requiresHumanReview',
      'admittedToAi',
      'metadata',
      'fetchJobId',
      'fetchAttempt',
      'fetchedChecksum',
      'fetchedAt',
      'extractedAt',
    ],
    properties: {
      evidenceId: { type: 'string', format: 'uuid' },
      snapshotId: { type: 'string', format: 'uuid' },
      latestScanId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      brandVersion: { type: 'integer', format: 'int64', minimum: 0 },
      workspaceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      text: { type: 'string', minLength: 1, maxLength: 20000 },
      policyVersion: { type: 'string', const: 'url-text-review-v1' },
      scanExpiresAt: { type: 'string', format: 'date-time' },
      checkedAt: { type: 'string', format: 'date-time' },
      requiresHumanReview: { type: 'boolean', const: true },
      admittedToAi: { type: 'boolean', const: false },
      metadata: { $ref: '#/components/schemas/WebsiteMetadata' },
      fetchJobId: { type: 'string', format: 'uuid' },
      fetchAttempt: { type: 'integer', minimum: 1 },
      fetchedChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      fetchedAt: { type: 'string', format: 'date-time' },
      extractedAt: { type: 'string', format: 'date-time' },
    },
  },
  WebsiteMetadata: {
    type: 'object',
    additionalProperties: false,
    required: ['title', 'description', 'text', 'warnings', 'version'],
    properties: {
      title: { type: 'string', maxLength: 300 },
      description: { type: 'string', maxLength: 2000 },
      text: { type: 'string', maxLength: 16000 },
      warnings: { type: 'string', maxLength: 500 },
      version: { type: 'string', maxLength: 32 },
    },
  },
  SocialReviewPreview: {
    type: 'object',
    additionalProperties: false,
    required: [
      'snapshotId',
      'evidenceId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'text',
      'provider',
      'capturedAt',
      'bindingExpiresAt',
      'policyVersion',
      'warnings',
    ],
    properties: {
      snapshotId: { type: 'string', format: 'uuid' },
      evidenceId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      brandVersion: { type: 'integer', format: 'int64', minimum: 0 },
      workspaceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      text: { type: 'string', maxLength: 20000 },
      provider: { type: 'string', enum: ['INSTAGRAM', 'TIKTOK'] },
      capturedAt: { type: 'string', format: 'date-time' },
      bindingExpiresAt: { type: 'string', format: 'date-time' },
      policyVersion: { type: 'string', enum: ['social-text-review-v1'] },
      warnings: { type: 'string' },
    },
  },
  AdmitEvidenceRequest: {
    type: 'object',
    additionalProperties: true,
    required: ['sourceVersion', 'brandVersion', 'workspaceVersion', 'contentChecksum'],
    properties: {
      sourceVersion: { type: 'integer', minimum: 0 },
      brandVersion: { type: 'integer', minimum: 0 },
      workspaceVersion: { type: 'integer', minimum: 0 },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
    },
  },
  AdmitPdfEvidenceRequest: {
    type: 'object',
    additionalProperties: false,
    required: [
      'artifactId',
      'originalScanId',
      'latestScanId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'scanExpiresAt',
      'policyVersion',
    ],
    properties: {
      artifactId: { type: 'string', format: 'uuid' },
      originalScanId: { type: 'string', format: 'uuid' },
      latestScanId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      brandVersion: { type: 'integer', format: 'int64', minimum: 0 },
      workspaceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      scanExpiresAt: { type: 'string', format: 'date-time' },
      policyVersion: { type: 'string', enum: ['pdf-text-review-v1'] },
    },
  },
  AdmitUrlEvidenceRequest: {
    type: 'object',
    additionalProperties: false,
    required: [
      'snapshotId',
      'latestScanId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'scanExpiresAt',
      'policyVersion',
    ],
    properties: {
      snapshotId: { type: 'string', format: 'uuid' },
      latestScanId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      brandVersion: { type: 'integer', format: 'int64', minimum: 0 },
      workspaceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      scanExpiresAt: { type: 'string', format: 'date-time' },
      policyVersion: { type: 'string', enum: ['url-text-review-v1'] },
    },
  },
  AdmitSocialEvidenceRequest: {
    type: 'object',
    additionalProperties: false,
    required: [
      'snapshotId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'contentChecksum',
      'bindingExpiresAt',
    ],
    properties: {
      snapshotId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      brandVersion: { type: 'integer', format: 'int64', minimum: 0 },
      workspaceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      bindingExpiresAt: { type: 'string', format: 'date-time' },
    },
  },
  EvidenceAdmissionSnapshot: {
    type: 'object',
    required: ['admission', 'text'],
    properties: {
      admission: { $ref: '#/components/schemas/EvidenceAdmissionDetails' },
      text: { type: 'string', minLength: 1, maxLength: 20000 },
    },
  },
  EvidenceAdmissionDetails: {
    type: 'object',
    additionalProperties: false,
    required: [
      'id',
      'evidenceId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'reviewedBy',
      'reviewedAt',
      'policyVersion',
    ],
    properties: {
      id: { type: 'string', format: 'uuid' },
      evidenceId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', minimum: 0 },
      brandVersion: { type: 'integer', minimum: 0 },
      workspaceVersion: { type: 'integer', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      reviewedBy: { type: 'string', format: 'uuid' },
      reviewedAt: { type: 'string', format: 'date-time' },
      policyVersion: { type: 'string', enum: ['text-review-v1', 'social-text-review-v1'] },
    },
  },
  PdfAdmissionSnapshot: {
    type: 'object',
    additionalProperties: false,
    required: ['admission', 'text'],
    properties: {
      admission: { $ref: '#/components/schemas/PdfAdmissionDetails' },
      text: { type: 'string', minLength: 1, maxLength: 16000 },
    },
  },
  PdfAdmissionDetails: {
    type: 'object',
    additionalProperties: false,
    required: [
      'id',
      'evidenceId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'reviewedBy',
      'reviewedAt',
      'policyVersion',
      'artifactId',
      'originalScanId',
      'latestScanId',
      'rendererVersion',
      'scanExpiresAt',
      'aiConsumptionEnabled',
    ],
    properties: {
      id: { type: 'string', format: 'uuid' },
      evidenceId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', minimum: 0 },
      brandVersion: { type: 'integer', minimum: 0 },
      workspaceVersion: { type: 'integer', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      reviewedBy: { type: 'string', format: 'uuid' },
      reviewedAt: { type: 'string', format: 'date-time' },
      policyVersion: { type: 'string', enum: ['pdf-text-review-v1'] },
      artifactId: { type: 'string', format: 'uuid' },
      originalScanId: { type: 'string', format: 'uuid' },
      latestScanId: { type: 'string', format: 'uuid' },
      rendererVersion: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$' },
      scanExpiresAt: { type: 'string', format: 'date-time' },
      aiConsumptionEnabled: { type: 'boolean', enum: [true] },
    },
  },
  UrlAdmissionSnapshot: {
    type: 'object',
    additionalProperties: false,
    required: ['admission', 'text'],
    properties: {
      admission: { $ref: '#/components/schemas/UrlAdmissionDetails' },
      text: { type: 'string', minLength: 1, maxLength: 20000 },
    },
  },
  UrlAdmissionDetails: {
    type: 'object',
    additionalProperties: false,
    required: [
      'id',
      'evidenceId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'reviewedBy',
      'reviewedAt',
      'policyVersion',
      'snapshotId',
      'originalScanId',
      'latestScanId',
      'scanExpiresAt',
      'aiConsumptionEnabled',
    ],
    properties: {
      id: { type: 'string', format: 'uuid' },
      evidenceId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', minimum: 0 },
      brandVersion: { type: 'integer', minimum: 0 },
      workspaceVersion: { type: 'integer', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      reviewedBy: { type: 'string', format: 'uuid' },
      reviewedAt: { type: 'string', format: 'date-time' },
      policyVersion: { type: 'string', enum: ['url-text-review-v1'] },
      snapshotId: { type: 'string', format: 'uuid' },
      originalScanId: { type: 'string', format: 'uuid' },
      latestScanId: { type: 'string', format: 'uuid' },
      scanExpiresAt: { type: 'string', format: 'date-time' },
      aiConsumptionEnabled: {
        type: 'boolean',
        enum: [true],
        description:
          'URL textual consumer supported; live provider activation is gated separately.',
      },
    },
  },
  SocialAdmissionSnapshot: {
    type: 'object',
    additionalProperties: false,
    required: ['admission', 'text'],
    properties: {
      admission: { $ref: '#/components/schemas/SocialAdmissionDetails' },
      text: { type: 'string', maxLength: 20000 },
    },
  },
  SocialAdmissionDetails: {
    type: 'object',
    additionalProperties: false,
    required: ['review', 'snapshotId', 'capturedAt', 'bindingExpiresAt'],
    properties: {
      review: { $ref: '#/components/schemas/EvidenceAdmissionDetails' },
      snapshotId: { type: 'string', format: 'uuid' },
      capturedAt: { type: 'string', format: 'date-time' },
      bindingExpiresAt: { type: 'string', format: 'date-time' },
    },
  },
  EvidenceAdmissionHistoryPage: {
    type: 'object',
    additionalProperties: false,
    properties: {
      content: {
        type: 'array',
        items: { $ref: '#/components/schemas/EvidenceAdmissionHistory' },
        maxItems: 20,
      },
      page: { type: 'integer', minimum: 0 },
      size: { type: 'integer', minimum: 1, maximum: 100 },
      totalElements: { type: 'integer', minimum: 0 },
      totalPages: { type: 'integer', minimum: 0 },
      first: { type: 'boolean' },
      last: { type: 'boolean' },
    },
    required: ['content', 'page', 'size', 'totalElements', 'totalPages', 'first', 'last'],
  },
  EvidenceAdmissionHistory: {
    type: 'object',
    additionalProperties: false,
    required: [
      'id',
      'evidenceId',
      'sourceVersion',
      'brandVersion',
      'workspaceVersion',
      'sourceChecksum',
      'contentChecksum',
      'reviewedBy',
      'reviewedAt',
      'policyVersion',
      'artifactId',
      'originalScanId',
      'latestScanId',
      'snapshotId',
      'withdrawnBy',
      'scanExpiresAt',
      'bindingExpiresAt',
      'withdrawnAt',
      'withdrawn',
    ],
    properties: {
      id: { type: 'string', format: 'uuid' },
      evidenceId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', minimum: 0 },
      brandVersion: { type: 'integer', minimum: 0 },
      workspaceVersion: { type: 'integer', minimum: 0 },
      sourceChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      contentChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$', minLength: 64, maxLength: 64 },
      reviewedBy: { type: 'string', format: 'uuid' },
      reviewedAt: { type: 'string', format: 'date-time' },
      policyVersion: {
        type: 'string',
        enum: [
          'text-review-v1',
          'pdf-text-review-v1',
          'url-text-review-v1',
          'social-text-review-v1',
        ],
      },
      artifactId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      originalScanId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      latestScanId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      snapshotId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      withdrawnBy: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      scanExpiresAt: { anyOf: [{ type: 'string', format: 'date-time' }, { type: 'null' }] },
      bindingExpiresAt: { anyOf: [{ type: 'string', format: 'date-time' }, { type: 'null' }] },
      withdrawnAt: { anyOf: [{ type: 'string', format: 'date-time' }, { type: 'null' }] },
      withdrawn: { type: 'boolean' },
    },
    description:
      'Immutable receipt metadata. No historical text/storage keys. Withdrawn applies to the entire sourceVersion; history is not current eligibility.',
  },
  EvidenceWithdrawalDetails: {
    type: 'object',
    required: ['id', 'admissionId', 'withdrawnBy', 'withdrawnAt'],
    properties: {
      id: { type: 'string', format: 'uuid' },
      admissionId: { type: 'string', format: 'uuid' },
      withdrawnBy: { type: 'string', format: 'uuid' },
      withdrawnAt: { type: 'string', format: 'date-time' },
    },
  },
  BrandIdentityResponse: {
    type: 'object',
    properties: {
      id: { type: 'string', format: 'uuid' },
      brandId: { type: 'string', format: 'uuid' },
      workspaceId: { type: 'string', format: 'uuid' },
      version: { type: 'integer', minimum: 1, maximum: 1000 },
      lockVersion: { type: 'integer', minimum: 0, format: 'int64' },
      status: {
        type: 'string',
        enum: ['DRAFT', 'APPROVED', 'LOCKED'],
        description: 'DRAFT is editable; APPROVED/LOCKED edits create a new DRAFT revision.',
      },
      createdAt: { type: 'string', format: 'date-time' },
      updatedAt: { type: 'string', format: 'date-time' },
      content: { $ref: '#/components/schemas/IdentityContentResponse' },
      revisedFromId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      approvedBy: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      approvedAt: { anyOf: [{ type: 'string', format: 'date-time' }, { type: 'null' }] },
      analysisProvenance: {
        anyOf: [{ $ref: '#/components/schemas/IdentityAnalysisProvenance' }, { type: 'null' }],
        description:
          'Original generation receipt; later manual edits are not asserted to be provider output. Null for manual drafts/branches.',
      },
    },
    required: [
      'id',
      'brandId',
      'workspaceId',
      'version',
      'lockVersion',
      'status',
      'createdAt',
      'updatedAt',
      'content',
      'revisedFromId',
      'approvedBy',
      'approvedAt',
      'analysisProvenance',
    ],
    additionalProperties: false,
    description:
      'Versioned Identity with separate approval, immutable approved history, inherited evidence provenance and approved Identity source guards.',
  },
  IdentityContentResponse: {
    type: 'object',
    properties: {
      palette: { $ref: '#/components/schemas/IdentityPalette' },
      fonts: {
        type: 'array',
        minItems: 1,
        maxItems: 5,
        items: { type: 'string', minLength: 1, maxLength: 100 },
        uniqueItems: true,
      },
      voiceUp: {
        type: 'array',
        minItems: 1,
        maxItems: 10,
        items: { type: 'string', minLength: 1, maxLength: 100 },
        uniqueItems: true,
      },
      voiceDown: {
        type: 'array',
        minItems: 1,
        maxItems: 10,
        items: { type: 'string', minLength: 1, maxLength: 100 },
        uniqueItems: true,
      },
      sampleCaption: { type: 'string', minLength: 1, maxLength: 1000 },
    },
    required: ['palette', 'fonts', 'voiceUp', 'voiceDown', 'sampleCaption'],
    additionalProperties: false,
    description:
      'Trimmed text without ISO controls; list values unique ignoring case. Complete replacement content.',
  },
  IdentityPalette: {
    type: 'object',
    properties: {
      primary: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
      secondary: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
      accent: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
      neutral: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
      background: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
    },
    required: ['primary', 'secondary', 'accent', 'neutral', 'background'],
    additionalProperties: false,
  },
  IdentityAnalysisProvenance: {
    type: 'object',
    required: [
      'analysisId',
      'provider',
      'modelVersion',
      'promptVersion',
      'warnings',
      'initialOutputChecksum',
      'generatedAt',
    ],
    properties: {
      analysisId: { type: 'string', format: 'uuid' },
      provider: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$' },
      modelVersion: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$' },
      promptVersion: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$' },
      warnings: { type: 'string', maxLength: 512 },
      initialOutputChecksum: { type: 'string', pattern: '^[0-9a-f]{64}$' },
      generatedAt: { type: 'string', format: 'date-time' },
    },
  },
  IdentityPage: {
    type: 'object',
    additionalProperties: false,
    properties: {
      content: { type: 'array', items: { $ref: '#/components/schemas/BrandIdentityResponse' } },
      page: { type: 'integer', minimum: 0 },
      size: { type: 'integer', minimum: 1, maximum: 100 },
      totalElements: { type: 'integer', minimum: 0 },
      totalPages: { type: 'integer', minimum: 0 },
      first: { type: 'boolean' },
      last: { type: 'boolean' },
    },
    required: ['content', 'page', 'size', 'totalElements', 'totalPages', 'first', 'last'],
  },
  IdentityEvidenceProvenance: {
    type: 'object',
    additionalProperties: false,
    required: [
      'identityId',
      'originIdentityId',
      'analysisId',
      'copiedBy',
      'sourceChecksum',
      'citations',
    ],
    properties: {
      identityId: { type: 'string', format: 'uuid' },
      originIdentityId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      analysisId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      copiedBy: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      sourceChecksum: { anyOf: [{ type: 'string', pattern: '^[0-9a-f]{64}$' }, { type: 'null' }] },
      citations: { type: 'array', maxItems: 10, items: { $ref: '#/components/schemas/Citation' } },
    },
  },
  Citation: {
    type: 'object',
    additionalProperties: false,
    required: [
      'admissionId',
      'evidenceId',
      'sourceVersion',
      'quote',
      'withdrawn',
      'artifactId',
      'originalScanId',
      'latestScanId',
      'socialSnapshotId',
    ],
    properties: {
      admissionId: { type: 'string', format: 'uuid' },
      evidenceId: { type: 'string', format: 'uuid' },
      sourceVersion: { type: 'integer', format: 'int64', minimum: 0 },
      quote: { type: 'string', minLength: 1, maxLength: 500 },
      withdrawn: { type: 'boolean' },
      artifactId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      originalScanId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      latestScanId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      fieldPath: {
        anyOf: [
          {
            type: 'string',
            pattern:
              '^(title|description|text|accountName|biography|posts\\[([0-9]|1[0-9])\\]\\.(text|title))$',
          },
          { type: 'null' },
        ],
        description:
          'Exact URL projection field; null preserves legacy TEXT/PDF substring citations.',
      },
      urlSnapshotId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      socialSnapshotId: { anyOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
    },
  },
  CreateBrandIdentityRequest: {
    type: 'object',
    properties: { content: { $ref: '#/components/schemas/IdentityContentRequest' } },
    required: ['content'],
    additionalProperties: true,
    description:
      'Manual DRAFT only. Extra fields cannot assign status, identity, version, audit, provider or citations.',
  },
  IdentityContentRequest: {
    type: 'object',
    properties: {
      palette: { $ref: '#/components/schemas/IdentityPaletteRequest' },
      fonts: {
        type: 'array',
        minItems: 1,
        maxItems: 5,
        items: { type: 'string', minLength: 1, maxLength: 100 },
        uniqueItems: true,
      },
      voiceUp: {
        type: 'array',
        minItems: 1,
        maxItems: 10,
        items: { type: 'string', minLength: 1, maxLength: 100 },
        uniqueItems: true,
      },
      voiceDown: {
        type: 'array',
        minItems: 1,
        maxItems: 10,
        items: { type: 'string', minLength: 1, maxLength: 100 },
        uniqueItems: true,
      },
      sampleCaption: { type: 'string', minLength: 1, maxLength: 1000 },
    },
    required: ['palette', 'fonts', 'voiceUp', 'voiceDown', 'sampleCaption'],
    additionalProperties: true,
    description:
      'Trimmed text without ISO controls; list values unique ignoring case. Complete replacement content.',
  },
  IdentityPaletteRequest: {
    type: 'object',
    properties: {
      primary: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
      secondary: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
      accent: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
      neutral: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
      background: { type: 'string', pattern: '^#[0-9A-Fa-f]{6}$' },
    },
    required: ['primary', 'secondary', 'accent', 'neutral', 'background'],
    additionalProperties: true,
  },
  UpdateBrandIdentityRequest: {
    type: 'object',
    properties: {
      expectedVersion: { type: 'integer', minimum: 0 },
      content: { $ref: '#/components/schemas/IdentityContentRequest' },
    },
    required: ['expectedVersion', 'content'],
    additionalProperties: true,
  },
  VersionRequest: {
    type: 'object',
    properties: { expectedVersion: { type: 'integer', format: 'int64', minimum: 0 } },
    required: ['expectedVersion'],
    additionalProperties: true,
    description:
      'Only declared DTO fields affect state. Unknown input fields are ignored; internal fields cannot be assigned.',
  },
  EvidenceIdentityFromAnalysisRequest: {
    type: 'object',
    required: ['analysisId', 'expectedVersion'],
    properties: {
      analysisId: { type: 'string', format: 'uuid' },
      expectedVersion: { type: 'integer', format: 'int64', minimum: 0 },
    },
  },
  AiGenerationJob: {
    type: 'object',
    additionalProperties: false,
    required: [
      'jobId',
      'operationId',
      'kind',
      'status',
      'submittedAt',
      'deadline',
      'completedAt',
      'resultId',
      'resultUrl',
      'attempts',
      'errorCode',
    ],
    properties: {
      jobId: { type: 'string', format: 'uuid' },
      operationId: { type: 'string', format: 'uuid' },
      kind: { type: 'string', enum: ['DECLARED', 'EVIDENCE', 'STRATEGY', 'CONTENT'] },
      status: {
        type: 'string',
        enum: [
          'QUEUED',
          'RUNNING',
          'RETRY_WAIT',
          'SUCCEEDED',
          'SUPERSEDED',
          'FAILED',
          'EXPIRED',
          'OUTCOME_UNKNOWN',
        ],
      },
      submittedAt: { type: 'string', format: 'date-time' },
      deadline: { type: 'string', format: 'date-time' },
      completedAt: { oneOf: [{ type: 'string', format: 'date-time' }, { type: 'null' }] },
      resultId: { oneOf: [{ type: 'string', format: 'uuid' }, { type: 'null' }] },
      resultUrl: {
        oneOf: [
          {
            type: 'string',
            pattern: '^/api/v1/(strategies|analysis-results|content-results)/[0-9a-f-]+$',
          },
          { type: 'null' },
        ],
      },
      attempts: { type: 'integer', minimum: 0, maximum: 3 },
      errorCode: {
        oneOf: [
          {
            type: 'string',
            enum: [
              'LEASE_EXPIRED_BEFORE_DISPATCH',
              'INPUT_UNAVAILABLE',
              'SOURCE_OR_ACCESS_CHANGED',
              'INVALID_OUTPUT',
              'DEADLINE_EXPIRED',
              'PROVIDER_OUTCOME_UNKNOWN',
              'CAPABILITY_UNAVAILABLE',
              'PUBLICATION_REJECTED',
              'GENERATION_FAILED',
              'BUDGET_EXCEEDED',
            ],
          },
          { type: 'null' },
        ],
        description: 'Controlled diagnostic code; never raw provider or source content.',
      },
    },
  },
  AnalysisJobResult: {
    type: 'object',
    properties: {
      palette: { anyOf: [{ $ref: '#/components/schemas/ColorPaletteResponse' }, { type: 'null' }] },
      fonts: { type: 'array', items: { type: 'string' } },
      voiceUp: { type: 'array', items: { type: 'string' } },
      voiceDown: { type: 'array', items: { type: 'string' } },
      sampleCaption: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      warnings: {
        type: 'array',
        maxItems: 10,
        items: {
          type: 'string',
          enum: ['DEMONSTRATION_ONLY', 'DECLARED_FIELDS_ONLY', 'HUMAN_REVIEW_REQUIRED'],
        },
      },
      provider: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$' },
      modelVersion: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$' },
      promptVersion: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$' },
      citations: {
        type: 'array',
        minItems: 0,
        maxItems: 10,
        items: { $ref: '#/components/schemas/AnalysisCitation' },
      },
    },
    required: [
      'palette',
      'fonts',
      'voiceUp',
      'voiceDown',
      'sampleCaption',
      'warnings',
      'provider',
      'modelVersion',
      'promptVersion',
      'citations',
    ],
    additionalProperties: false,
  },
  ColorPaletteResponse: {
    type: 'object',
    properties: {
      primary: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      secondary: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      accent: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      neutral: { anyOf: [{ type: 'string' }, { type: 'null' }] },
      background: { anyOf: [{ type: 'string' }, { type: 'null' }] },
    },
    required: ['primary', 'secondary', 'accent', 'neutral', 'background'],
    additionalProperties: false,
  },
  AnalysisCitation: {
    type: 'object',
    additionalProperties: false,
    required: ['admissionId', 'quote'],
    properties: {
      admissionId: { type: 'string', format: 'uuid' },
      quote: { type: 'string', minLength: 1, maxLength: 500 },
      fieldPath: {
        anyOf: [
          {
            type: 'string',
            pattern:
              '^(title|description|text|accountName|biography|posts\\[([0-9]|1[0-9])\\]\\.(text|title))$',
          },
          { type: 'null' },
        ],
        description:
          'Exact URL projection field; null preserves legacy TEXT/PDF substring citations.',
      },
    },
  },
  AnalysisJobRequest: {
    type: 'object',
    additionalProperties: false,
    required: ['expectedVersion', 'mode'],
    properties: {
      expectedVersion: { type: 'integer', minimum: 0 },
      mode: { type: 'string', enum: ['DECLARED', 'EVIDENCE'] },
    },
  },
} as const;
