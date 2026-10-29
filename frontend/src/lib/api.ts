import { ApiError } from './problem';
export { ApiError, parseProblem, type ProblemDetails } from './problem';
import { ApiTransport } from './transport';
import { EvidenceIdentityApi } from './identity-api';
export type Workspace = Readonly<{
  id: string;
  name: string;
  industry: string;
  status: 'ACTIVE' | 'ARCHIVED';
  lockVersion: number;
}>;
export type Brand = Readonly<{
  id: string;
  workspaceId: string;
  name: string;
  websiteUrl: string | null;
  instagramHandle: string | null;
  tiktokHandle: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  lockVersion: number;
}>;
export type Page<T> = Readonly<{
  content: T[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
}>;
export type CreateWorkspace = { name: string; industry: string };
export type CreateBrand = {
  name: string;
  websiteUrl: string | null;
  instagramHandle: string | null;
  tiktokHandle: string | null;
};
type Json = Record<string, unknown>;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const fail = (): never => {
  throw new ApiError({ status: 0, code: 'invalid_response', retryable: false, errors: [] });
};
function object(v: unknown): Json {
  return v !== null && typeof v === 'object' && !Array.isArray(v) ? (v as Json) : fail();
}
function str(v: unknown, max: number): string {
  return typeof v === 'string' && v.length <= max ? v : fail();
}
function number(v: unknown): number {
  return typeof v === 'number' && Number.isSafeInteger(v) && v >= 0 ? v : fail();
}
function id(v: unknown): string {
  return typeof v === 'string' && UUID.test(v) ? v : fail();
}
function status(v: unknown): 'ACTIVE' | 'ARCHIVED' {
  return v === 'ACTIVE' || v === 'ARCHIVED' ? v : fail();
}
function nullable(v: unknown, max: number): string | null {
  return v === null ? null : str(v, max);
}
function workspace(v: unknown): Workspace {
  const o = object(v);
  return {
    id: id(o.id),
    name: str(o.name, 255),
    industry: str(o.industry, 255),
    status: status(o.status),
    lockVersion: number(o.lockVersion),
  };
}
function brand(v: unknown): Brand {
  const o = object(v);
  return {
    id: id(o.id),
    workspaceId: id(o.workspaceId),
    name: str(o.name, 255),
    status: status(o.status),
    lockVersion: number(o.lockVersion),
    websiteUrl: nullable(o.websiteUrl, 2048),
    instagramHandle: nullable(o.instagramHandle, 255),
    tiktokHandle: nullable(o.tiktokHandle, 255),
  };
}
function page<T>(v: unknown, parse: (x: unknown) => T, expected: number): Page<T> {
  const o = object(v);
  if (
    !Array.isArray(o.content) ||
    o.content.length > 20 ||
    o.page !== expected ||
    o.size !== 20 ||
    typeof o.first !== 'boolean' ||
    typeof o.last !== 'boolean'
  )
    fail();
  return {
    content: (o.content as unknown[]).map(parse),
    page: number(o.page),
    size: number(o.size),
    totalPages: number(o.totalPages),
    totalElements: number(o.totalElements),
    first: o.first as boolean,
    last: o.last as boolean,
  };
}
export class CadenceApi {
  readonly identity: EvidenceIdentityApi;
  private readonly http: ApiTransport;
  constructor(
    base: string,
    getToken: () => Promise<string | null>,
    transport: typeof fetch = (input, init) => fetch(input, init),
  ) {
    this.http = new ApiTransport(base, getToken, transport);
    this.identity = new EvidenceIdentityApi(this.http);
  }
  private request(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
    return this.http.json(path, signal, { body });
  }
  async workspaces(pageIndex: number, signal: AbortSignal): Promise<Page<Workspace>> {
    this.pageBound(pageIndex);
    return page(
      await this.request(`/workspaces?page=${pageIndex}&size=20`, signal),
      workspace,
      pageIndex,
    );
  }
  async createWorkspace(input: CreateWorkspace, signal: AbortSignal): Promise<Workspace> {
    return workspace(
      await this.request('/workspaces', signal, { name: input.name, industry: input.industry }),
    );
  }
  async brands(workspaceId: string, pageIndex: number, signal: AbortSignal): Promise<Page<Brand>> {
    id(workspaceId);
    this.pageBound(pageIndex);
    const result = page(
      await this.request(`/workspaces/${workspaceId}/brands?page=${pageIndex}&size=20`, signal),
      brand,
      pageIndex,
    );
    if (result.content.some((b) => b.workspaceId !== workspaceId)) fail();
    return result;
  }
  async createBrand(workspaceId: string, input: CreateBrand, signal: AbortSignal): Promise<Brand> {
    id(workspaceId);
    const result = brand(
      await this.request(`/workspaces/${workspaceId}/brands`, signal, {
        name: input.name,
        websiteUrl: input.websiteUrl,
        instagramHandle: input.instagramHandle,
        tiktokHandle: input.tiktokHandle,
      }),
    );
    if (result.workspaceId !== workspaceId) fail();
    return result;
  }
  async brand(workspaceId: string, brandId: string, signal: AbortSignal): Promise<Brand> {
    id(workspaceId);
    id(brandId);
    const result = brand(await this.request(`/brands/${brandId}`, signal));
    if (result.id !== brandId || result.workspaceId !== workspaceId) fail();
    return result;
  }
  private pageBound(value: number) {
    if (!Number.isInteger(value) || value < 0 || value > 10_000) fail();
  }
}
