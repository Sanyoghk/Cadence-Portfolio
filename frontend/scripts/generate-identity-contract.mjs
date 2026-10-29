import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import * as prettier from 'prettier';

const input = await readFile('../src/main/resources/openapi/cadence.json', 'utf8');
const document = JSON.parse(input);
const roots = [
  'BrandEvidencePage',
  'EvidenceRegistrationResponse',
  'RegisterEvidenceRequest',
  'RegisterSocialEvidenceRequest',
  'JobResponse',
  'EvidenceReview',
  'PdfReviewPreview',
  'UrlReviewPreview',
  'SocialReviewPreview',
  'AdmitEvidenceRequest',
  'AdmitPdfEvidenceRequest',
  'AdmitUrlEvidenceRequest',
  'AdmitSocialEvidenceRequest',
  'EvidenceAdmissionSnapshot',
  'PdfAdmissionSnapshot',
  'UrlAdmissionSnapshot',
  'SocialAdmissionSnapshot',
  'EvidenceAdmissionHistoryPage',
  'EvidenceWithdrawalDetails',
  'BrandIdentityResponse',
  'IdentityPage',
  'IdentityEvidenceProvenance',
  'CreateBrandIdentityRequest',
  'UpdateBrandIdentityRequest',
  'VersionRequest',
  'EvidenceIdentityFromAnalysisRequest',
  'AiGenerationJob',
  'AnalysisJobResult',
  'AnalysisJobRequest',
];
const schemas = {};
function collect(name) {
  if (schemas[name]) return;
  const schema = document.components.schemas[name];
  if (!schema) throw Error(`Missing OpenAPI schema ${name}`);
  schemas[name] = schema;
  const visit = (v) => {
    if (!v || typeof v !== 'object') return;
    if (v.$ref) collect(v.$ref.split('/').at(-1));
    Object.values(v).forEach(visit);
  };
  visit(schema);
}
roots.forEach(collect);
function type(s) {
  if (s.$ref) return s.$ref.split('/').at(-1);
  if (s.const !== undefined) return JSON.stringify(s.const);
  if (s.enum) return s.enum.map((v) => JSON.stringify(v)).join(' | ');
  if (s.oneOf || s.anyOf) return (s.oneOf || s.anyOf).map(type).join(' | ');
  if (s.type === 'object')
    return (
      '{' +
      Object.entries(s.properties ?? {})
        .map(([k, v]) => `${JSON.stringify(k)}${s.required?.includes(k) ? '' : '?'}: ${type(v)};`)
        .join('') +
      '}'
    );
  if (s.type === 'array') return `Array<${type(s.items)}>`;
  if (s.type === 'integer' || s.type === 'number') return 'number';
  if (['string', 'boolean', 'null'].includes(s.type)) return s.type;
  throw Error('Unsupported schema in reviewed subset');
}
const names = Object.keys(schemas).sort();
let source = `// Generated from actual OpenAPI; edit schema/generator, never this file.\n// source SHA256 ${createHash('sha256').update(input).digest('hex')}\n`;
for (const n of names) source += `export type ${n} = ${type(schemas[n])};\n`;
source += 'export type ContractTypes = {' + names.map((n) => `${n}: ${n};`).join('') + '};\n';
source += 'export const schemas = ' + JSON.stringify(schemas) + ' as const;\n';
const target = 'src/lib/identity-contract.generated.ts';
source = await prettier.format(source, {
  ...(await prettier.resolveConfig(target)),
  parser: 'typescript',
});
if (process.argv.includes('--check')) {
  if ((await readFile(target, 'utf8')) !== source)
    throw Error('Identity OpenAPI contract drift; run pnpm contracts:generate');
  console.log(`PASS: ${names.length} actual OpenAPI schemas/types in sync`);
} else {
  await writeFile(target, source);
  console.log(`Generated ${names.length} reviewed OpenAPI schemas/types`);
}
