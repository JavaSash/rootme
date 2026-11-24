import type {
  Depth, Partnership, PartnershipFields, Person, PersonDetails, PersonFields,
  PersonSummary, RelativeRequest, Tree,
} from './types'

interface Problem {
  detail?: string
  code?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  constructor(readonly status: number, readonly problem: Problem = {}) {
    super(problem.detail || `Запрос не выполнен (HTTP ${status}).`)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch('/api/v1' + path, init)
  if (!response.ok) {
    const problem = response.headers.get('content-type')?.includes('json')
      ? await response.json().catch(() => undefined) as Problem | undefined
      : undefined
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) return undefined as T
  return response.json()
}

function json(method: 'POST' | 'PATCH', body: object): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export const api = {
  people: (query = '', signal?: AbortSignal) =>
    request<PersonSummary[]>('/persons?' + new URLSearchParams({ query }), { signal }),
  person: (id: string, signal?: AbortSignal) =>
    request<PersonDetails>(`/persons/${id}`, { signal }),
  tree: (rootPersonId: string, depth: Depth, signal?: AbortSignal) =>
    request<Tree>('/tree?' + new URLSearchParams({
      rootPersonId, ancestors: String(depth.ancestors), descendants: String(depth.descendants),
    }), { signal }),
  createPerson: (fields: PersonFields) => request<Person>('/persons', json('POST', fields)),
  updatePerson: (id: string, fields: Partial<PersonFields>) =>
    request<Person>(`/persons/${id}`, json('PATCH', fields)),
  deletePerson: (id: string) => request<void>(`/persons/${id}`, { method: 'DELETE' }),
  addRelative: (id: string, fields: RelativeRequest) =>
    request<{ person: Person }>(`/persons/${id}/relatives`, json('POST', fields)),
  deleteParentChild: (id: string) =>
    request<void>(`/relationships/parent-child/${id}`, { method: 'DELETE' }),
  updatePartnership: (id: string, fields: PartnershipFields) =>
    request<Partnership>(`/partnerships/${id}`, json('PATCH', fields)),
  deletePartnership: (id: string) => request<void>(`/partnerships/${id}`, { method: 'DELETE' }),
}

export function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Не удалось связаться с сервером. Проверьте, что RootMe запущен.'
}
