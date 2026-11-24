import type {
  AlternativeName, AlternativeNameFields, BackupVerification, CreatedBackup, Depth, Partnership, PartnershipFields, Person, PersonDetails,
  PersonFields, PersonPage, PersonPhoto, PhotoFields, RelativeRequest, Tree,
  ImportMapping, ImportPreview, ImportResult, ImportSource,
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
  if (!response.ok) throw await apiFailure(response)
  if (response.status === 204) return undefined as T
  return response.json()
}

async function apiFailure(response: Response) {
  const problem = response.headers.get('content-type')?.includes('json')
    ? await response.json().catch(() => undefined) as Problem | undefined : undefined
  return new ApiError(response.status, problem)
}

function importFile(file: File, options?: object): RequestInit {
  const data = new FormData()
  data.append('file', file)
  if (options) data.append('options', new Blob([JSON.stringify(options)], { type: 'application/json' }))
  return { method: 'POST', body: data }
}

export async function exportGedcom() {
  const response = await fetch('/api/v1/exports/gedcom')
  if (!response.ok) throw await apiFailure(response)
  return {
    file: await response.blob(),
    warnings: (response.headers.get('X-RootMe-Gedcom-Warnings') ?? '').split(',').filter(Boolean),
  }
}

function json(method: 'POST' | 'PATCH' | 'PUT', body: object): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export const api = {
  importSources: (file: File) => request<ImportSource[]>('/imports/persons/sources', importFile(file)),
  previewImport: (file: File, options: ImportMapping) =>
    request<ImportPreview>('/imports/persons/preview', importFile(file, options)),
  applyImport: (file: File, options: ImportMapping & { fingerprint: string }) =>
    request<ImportResult>('/imports/persons/apply', importFile(file, options)),
  createBackup: () => request<CreatedBackup>('/backups', { method: 'POST' }),
  verifyBackup: (file: File) => request<BackupVerification>('/backups/verify', {
    method: 'POST', headers: { 'Content-Type': 'application/zip' }, body: file,
  }),
  restoreBackup: (file: File, sha256: string) => request<BackupVerification>('/backups/restore', {
    method: 'POST',
    headers: { 'Content-Type': 'application/zip', 'X-RootMe-Confirm-Restore': 'true', 'X-RootMe-Backup-SHA256': sha256 },
    body: file,
  }),
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

const localizedErrors: Record<string, string> = {
  IMPORT_FORMAT_UNSUPPORTED: 'Выберите XLS, XLSX, MDB или ACCDB.',
  IMPORT_FILE_INVALID: 'Файл повреждён, защищён или имеет неподдерживаемый формат.',
  IMPORT_PREVIEW_CHANGED: 'Файл, сопоставление или список дубликатов изменились. Повторите предпросмотр.',
  INVALID_BACKUP: 'Проверка копии не пройдена.',
  BACKUP_TOO_LARGE: 'Архив или его содержимое превышает допустимый размер.',
  BACKUP_STORAGE_ERROR: 'Хранилище резервных копий недоступно.',
  BACKUP_PHOTO_MISSING: 'Копия не создана: отсутствует оригинал одной из фотографий.',
  BACKUP_PHOTO_MISMATCH: 'Копия не создана: один из оригиналов не совпадает с записанной контрольной суммой.',
  BACKUP_TARGET_NOT_EMPTY: 'Восстановление доступно только в пустой базе.',
  BACKUP_PHOTO_CONFLICT: 'В хранилище уже есть папка одной из фотографий архива или ожидается её очистка.',
  BACKUP_RESTORE_CONFIRMATION_REQUIRED: 'Подтвердите восстановление.',
  BACKUP_PREVIEW_CHANGED: 'Архив изменился после проверки. Проверьте его снова.',
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
    ? localizedErrors[error.problem.code ?? ''] ?? error.message
    : 'Не удалось связаться с сервером. Проверьте, что RootMe запущен.'
}
