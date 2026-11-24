import type {
  AlternativeName, AlternativeNameFields, Depth, Partnership, PartnershipFields, Person, PersonDetails,
  PersonFields, PersonPage, PersonPhoto, PhotoFields, RelativeRequest, Tree,
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

function json(method: 'POST' | 'PATCH' | 'PUT', body: object): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export const api = {
  people: ({ query = '', birthYear, page = 0, size = 30 }: {
    query?: string; birthYear?: number; page?: number; size?: number
  } = {}, signal?: AbortSignal) => {
    const params = new URLSearchParams({ query, page: String(page), size: String(size) })
    if (birthYear != null) params.set('birthYear', String(birthYear))
    return request<PersonPage>('/persons?' + params, { signal })
  },
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
  addAlternativeName: (personId: string, fields: AlternativeNameFields) =>
    request<AlternativeName>(`/persons/${personId}/alternative-names`, json('POST', fields)),
  updateAlternativeName: (personId: string, id: string, fields: AlternativeNameFields) =>
    request<AlternativeName>(`/persons/${personId}/alternative-names/${id}`, json('PATCH', fields)),
  deleteAlternativeName: (personId: string, id: string) =>
    request<void>(`/persons/${personId}/alternative-names/${id}`, { method: 'DELETE' }),
  photos: (personId: string, signal?: AbortSignal) =>
    request<PersonPhoto[]>(`/persons/${personId}/photos`, { signal }),
  updatePhoto: (id: string, fields: PhotoFields) => request<PersonPhoto>(`/photos/${id}`, json('PATCH', fields)),
  primaryPhoto: (personId: string, photoId: string) =>
    request<PersonPhoto>(`/persons/${personId}/primary-photo`, json('PUT', { photoId })),
  deletePhoto: (id: string) => request<void>(`/photos/${id}`, { method: 'DELETE' }),
}

export function uploadPhoto(personId: string, file: File, fields: PhotoFields, progress: (percent: number) => void) {
  const data = new FormData()
  data.append('file', file)
  if (fields.caption != null) data.append('caption', fields.caption)
  if (fields.takenDate != null) data.append('takenDate', fields.takenDate)
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `/api/v1/persons/${personId}/photos`)
    xhr.responseType = 'json'
    xhr.upload.onprogress = event => {
      if (event.lengthComputable) progress(Math.round(event.loaded / event.total * 100))
    }
    xhr.onload = () => {
      if (xhr.status === 201) resolve()
      else reject(new ApiError(xhr.status, xhr.response ?? {}))
    }
    xhr.onerror = () => reject(new Error('Upload failed'))
    xhr.send(data)
  })
}

const photoErrors: Record<string, string> = {
  PHOTO_TOO_LARGE: 'Файл превышает 20 МиБ.',
  UNSUPPORTED_IMAGE_FORMAT: 'Выберите фотографию в формате JPEG или PNG.',
  INVALID_IMAGE: 'Не удалось прочитать изображение. Возможно, оно повреждено.',
  IMAGE_DIMENSIONS_EXCEEDED: 'Разрешение изображения превышает 40 мегапикселей.',
  PHOTO_STORAGE_ERROR: 'Хранилище фотографий недоступно.',
  PHOTO_FILE_MISSING: 'Файл фотографии отсутствует.',
  PHOTO_NOT_FOUND: 'Фотография не найдена.',
}

export function errorText(error: unknown): string {
  return error instanceof ApiError
    ? photoErrors[error.problem.code ?? ''] ?? error.message
    : 'Не удалось связаться с сервером. Проверьте, что RootMe запущен.'
}
