import { schemas, type ContractTypes } from './identity-contract.generated';
import { ApiError } from './problem';

type Schema = {
  $ref?: string;
  type?: string;
  const?: unknown;
  enum?: readonly unknown[];
  oneOf?: readonly Schema[];
  anyOf?: readonly Schema[];
  properties?: Record<string, Schema>;
  required?: readonly string[];
  items?: Schema;
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
  minItems?: number;
  maxItems?: number;
  pattern?: string;
  format?: string;
};
export const invalid = (): never => {
  throw new ApiError({ status: 0, code: 'invalid_response', retryable: false, errors: [] });
};
export function uuid(value: string): string {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
    ? value
    : invalid();
}
export function pageIndex(value: number): number {
  return Number.isInteger(value) && value >= 0 && value <= 10000 ? value : invalid();
}

function parse(schema: Schema, value: unknown, depth = 0): unknown {
  if (depth > 16) invalid();
  if (schema.$ref) {
    const key = schema.$ref.split('/').at(-1)!;
    return parse((schemas as unknown as Record<string, Schema>)[key], value, depth + 1);
  }
  if (schema.oneOf || schema.anyOf) {
    const matches: unknown[] = [];
    for (const s of schema.oneOf ?? schema.anyOf ?? []) {
      try {
        const parsed = parse(s, value, depth + 1);
        if (schema.anyOf) return parsed;
        matches.push(parsed);
      } catch (e) {
        if (!(e instanceof ApiError)) throw e;
      }
    }
    return matches.length === 1 ? matches[0] : invalid();
  }
  if (schema.const !== undefined && value !== schema.const) invalid();
  if (schema.enum && !schema.enum.includes(value)) invalid();
  if (schema.type === 'null') return value === null ? null : invalid();
  if (schema.type === 'boolean') return typeof value === 'boolean' ? value : invalid();
  if (schema.type === 'integer' || schema.type === 'number')
    return typeof value === 'number' &&
      Number.isSafeInteger(value) &&
      value >= (schema.minimum ?? 0) &&
      value <= (schema.maximum ?? Number.MAX_SAFE_INTEGER)
      ? value
      : invalid();
  if (schema.type === 'string') {
    if (
      typeof value !== 'string' ||
      value.length < (schema.minLength ?? 0) ||
      value.length > (schema.maxLength ?? 20000)
    )
      invalid();
    const text = value as string;
    if (new TextDecoder().decode(new TextEncoder().encode(text)) !== text) invalid();
    if (schema.pattern && !new RegExp(schema.pattern).test(text)) invalid();
    if (schema.format === 'uuid') uuid(text);
    if (
      schema.format === 'date-time' &&
      (!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z$/.test(text) ||
        !Number.isFinite(Date.parse(text)))
    )
      invalid();
    return text;
  }
  if (schema.type === 'array') {
    if (
      !Array.isArray(value) ||
      value.length < (schema.minItems ?? 0) ||
      value.length > (schema.maxItems ?? 100)
    )
      invalid();
    return (value as unknown[]).map((v) => parse(schema.items!, v, depth + 1));
  }
  if (schema.type === 'object') {
    if (
      value === null ||
      typeof value !== 'object' ||
      Array.isArray(value) ||
      Object.keys(value).length > 80
    )
      invalid();
    const input = value as Record<string, unknown>;
    const result: Record<string, unknown> = {};
    for (const [name, s] of Object.entries(schema.properties ?? {})) {
      if (!(name in input)) {
        if (schema.required?.includes(name)) invalid();
        continue;
      }
      result[name] = parse(s, input[name], depth + 1);
    }
    return result;
  }
  return invalid();
}
export function contract<K extends keyof ContractTypes>(name: K, value: unknown): ContractTypes[K] {
  return parse(schemas[name] as unknown as Schema, value) as ContractTypes[K];
}
export function requestContract<K extends keyof ContractTypes>(
  name: K,
  value: unknown,
): ContractTypes[K] {
  try {
    return contract(name, value);
  } catch (e) {
    if (!(e instanceof ApiError)) throw e;
    throw new ApiError({ status: 400, code: 'invalid_request', retryable: false, errors: [] });
  }
}
