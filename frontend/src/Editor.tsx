import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { ApiError, errorText } from './api'
import { fullName, genderLabels, parentChildLabels, partnershipLabels, statusLabels } from './labels'
import { PersonPicker } from './PersonPicker'
import type {
  Gender, ParentChildType, Partnership, PartnershipFields, PartnershipStatus,
  PartnershipType, Person, PersonFields, PersonSummary, RelativeRequest, RelativeRole,
} from './types'

export const relativeTitles: Record<RelativeRole, string> = {
  PARENT: 'Добавить родителя', CHILD: 'Добавить ребёнка', PARTNER: 'Добавить партнёра',
}

export type EditorMode =
  | { kind: 'person'; person?: Person }
  | { kind: 'relative'; role: RelativeRole; person: Person; sharedParents?: PersonSummary[] }
  | { kind: 'partnership'; partnership: Partnership }
  | { kind: 'search' }

export type EditorSubmission =
  | { kind: 'person'; fields: PersonFields }
  | { kind: 'person'; id: string; fields: Partial<PersonFields> }
  | { kind: 'relative'; id: string; request: RelativeRequest }
  | { kind: 'partnership'; id: string; fields: PartnershipFields }

interface Props {
  mode: EditorMode
  busy: boolean
  error: unknown
  onClose: () => void
  onSubmit: (submission: EditorSubmission) => void
  onChooseRoot: (id: string) => void
  onOpenPerson: (id: string) => void
}

const textFields = [
  ['firstName', 'Имя'],
  ['lastName', 'Фамилия'],
  ['patronymic', 'Отчество'],
  ['birthSurname', 'Фамилия при рождении'],
  ['birthCountry', 'Страна рождения'],
  ['birthRegion', 'Регион рождения'],
  ['birthCity', 'Город рождения'],
  ['deathCountry', 'Страна смерти'],
  ['deathRegion', 'Регион смерти'],
  ['deathCity', 'Город смерти'],
  ['occupation', 'Род занятий'],
  ['burialPlace', 'Место захоронения'],
] as const

function DateField({ name, label, value }: { name: string; label: string; value?: string | null }) {
  return <label>{label}
    <input name={name} placeholder="ГГГГ, ГГГГ-ММ или ГГГГ-ММ-ДД"
      pattern="[0-9]{4}(-[0-9]{2}(-[0-9]{2})?)?" maxLength={10} defaultValue={value ?? ''} />
  </label>
}

function PersonInputs({ person }: { person?: Person }) {
  return (
    <div className="form-grid">
      {textFields.slice(0, 4).map(([name, label]) => (
        <label key={name}>{label}
          <input name={name} required={name === 'firstName'} maxLength={255} defaultValue={person?.[name] ?? ''} />
        </label>
      ))}
      <label>Пол
        <select name="gender" defaultValue={person?.gender ?? 'UNKNOWN'}>
          {Object.entries(genderLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <div />
      <DateField name="birthDate" label="Дата рождения" value={person?.birthDate} />
      <DateField name="deathDate" label="Дата смерти" value={person?.deathDate} />
      {textFields.slice(4).map(([name, label]) => (
        <label key={name}>{label}<input name={name} maxLength={255} defaultValue={person?.[name] ?? ''} /></label>
      ))}
      <label className="full-row">Заметки<textarea name="notes" rows={3} maxLength={10_000} defaultValue={person?.notes ?? ''} /></label>
    </div>
  )
}

function PartnershipInputs({ partnership, duplicate }: { partnership?: Partnership; duplicate: boolean }) {
  return (
    <div className="form-grid">
      <label>Вид союза
        <select name="partnershipType" defaultValue={partnership?.type ?? 'MARRIAGE'}>
          {Object.entries(partnershipLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <label>Статус
        <select name="partnershipStatus" defaultValue={partnership?.status ?? 'UNKNOWN'}>
          {Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <DateField name="startDate" label="Начало союза" value={partnership?.startDate} />
      <DateField name="endDate" label="Окончание союза" value={partnership?.endDate} />
      {duplicate && <label className="checkbox full-row">
        <input name="allowRepeat" type="checkbox" /> Подтверждаю, что это отдельный исторический союз
      </label>}
    </div>
  )
}

function nullable(form: FormData, key: string) {
  return String(form.get(key) ?? '').trim() || null
}

function getPersonFields(form: FormData): PersonFields {
  return {
    firstName: nullable(form, 'firstName') ?? '',
    lastName: nullable(form, 'lastName'),
    patronymic: nullable(form, 'patronymic'),
    gender: form.get('gender') as Gender,
    birthDate: nullable(form, 'birthDate'),
    deathDate: nullable(form, 'deathDate'),
    birthCountry: nullable(form, 'birthCountry'),
    birthRegion: nullable(form, 'birthRegion'),
    birthCity: nullable(form, 'birthCity'),
    birthSurname: nullable(form, 'birthSurname'),
    notes: String(form.get('notes') ?? '') || null,
    biography: null,
    deathCountry: nullable(form, 'deathCountry'),
    deathRegion: nullable(form, 'deathRegion'),
    deathCity: nullable(form, 'deathCity'),
    occupation: nullable(form, 'occupation'),
    burialPlace: nullable(form, 'burialPlace'),
  }
}

function getPartnershipFields(form: FormData): PartnershipFields {
  return {
    type: form.get('partnershipType') as PartnershipType,
    status: form.get('partnershipStatus') as PartnershipStatus,
    startDate: nullable(form, 'startDate'),
    endDate: nullable(form, 'endDate'),
    allowRepeat: form.has('allowRepeat'),
  }
}

export function Dialog({ title, close, children }: { title: string; close: () => void; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = dialog.current!
    element.showModal()
    return () => element.close()
  }, [])
  return (
    <dialog ref={dialog} aria-labelledby="editor-title" onCancel={event => { event.preventDefault(); close() }}>
      <div className="dialog-heading">
        <h2 id="editor-title">{title}</h2>
        <button type="button" className="icon-button" aria-label="Закрыть" onClick={close}>×</button>
      </div>
      {children}
    </dialog>
  )
}

export function Editor({ mode, busy, error, onClose, onSubmit, onChooseRoot, onOpenPerson }: Props) {
  const [linkExisting, setLinkExisting] = useState(false)
  const [existing, setExisting] = useState<PersonSummary>()
  const title = mode.kind === 'search' ? 'Справочник персон'
    : mode.kind === 'person' ? (mode.person ? 'Редактировать персону' : 'Новая персона')
    : mode.kind === 'partnership' ? 'Редактировать союз'
    : mode.sharedParents ? 'Добавить брата или сестру' : relativeTitles[mode.role]

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || mode.kind === 'search') return
    const form = new FormData(event.currentTarget)
    if (mode.kind === 'person') {
      const fields = getPersonFields(form)
      const person = mode.person
      if (person) {
        const changes = Object.fromEntries(Object.entries(fields).filter(([key]) => {
          if (!form.has(key)) return false
          const previous = person[key as keyof PersonFields] ?? ''
          return previous.replace(/\r\n?/g, '\n') !== String(form.get(key) ?? '')
        }))
        onSubmit({ kind: 'person', id: person.id, fields: changes })
      } else {
        onSubmit({ kind: 'person', fields })
      }
    } else if (mode.kind === 'partnership') {
      onSubmit({ kind: 'partnership', id: mode.partnership.id, fields: getPartnershipFields(form) })
    } else {
      const id = mode.sharedParents ? String(form.get('sharedParent') ?? '') : mode.person.id
      if (!id || (linkExisting && !existing)) return
      const relative = linkExisting ? { existingPersonId: existing!.id } : { person: getPersonFields(form) }
      const request: RelativeRequest = mode.role === 'PARTNER'
        ? { role: 'PARTNER', partnership: getPartnershipFields(form), ...relative }
        : { role: mode.role, parentChildType: form.get('parentChildType') as ParentChildType, ...relative }
      onSubmit({ kind: 'relative', id, request })
    }
  }

  const duplicate = error instanceof ApiError &&
    error.problem.code === 'PARTNERSHIP_REPEAT_CONFIRMATION_REQUIRED'

  return (
    <Dialog title={title} close={() => { if (!busy) onClose() }}>
      {mode.kind === 'search' ? <PersonPicker onSelect={person => onOpenPerson(person.id)}
        onRoot={person => onChooseRoot(person.id)} /> : (
        <form onSubmit={submit}>
          <fieldset disabled={busy}>
            {mode.kind === 'person' && <PersonInputs person={mode.person} />}
            {mode.kind === 'relative' && (
              <>
                {mode.sharedParents && (
                  <>
                    <label>Общий родитель
                      <select name="sharedParent" defaultValue="" required>
                        <option value="" disabled>Выберите родителя</option>
                        {mode.sharedParents.map(person => <option key={person.id} value={person.id}>{fullName(person)}</option>)}
                      </select>
                    </label>
                    <label className="checkbox">
                      <input type="checkbox" required /> Подтверждаю, что выбранная персона — общий родитель
                    </label>
                  </>
                )}
                <label className="checkbox">
                  <input type="checkbox" checked={linkExisting} onChange={event => setLinkExisting(event.target.checked)} />
                  Привязать существующую персону
                </label>
                {linkExisting ? (
                  existing ? <div className="picked-person">
                    <strong>{fullName(existing)}</strong>
                    <button type="button" onClick={() => setExisting(undefined)}>Выбрать другую</button>
                  </div> : <PersonPicker excludeId={mode.person.id} onSelect={setExisting} />
                ) : <PersonInputs />}
                {mode.role === 'PARTNER' ? <PartnershipInputs duplicate={duplicate} /> : (
                  <label>Тип родства
                    <select name="parentChildType" defaultValue="" required>
                      <option value="" disabled>Выберите тип</option>
                      {Object.entries(parentChildLabels)
                        .filter(([value]) => !mode.sharedParents || value !== 'STEP')
                        .map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                  </label>
                )}
              </>
            )}
            {mode.kind === 'partnership' && <PartnershipInputs partnership={mode.partnership} duplicate={duplicate} />}
          </fieldset>
          {error !== undefined && (
            <div className="error-box" role="alert">
              {errorText(error)}
              {error instanceof ApiError && error.problem.errors && (
                <ul>{Object.entries(error.problem.errors).map(([field, message]) => <li key={field}>{field} — {message}</li>)}</ul>
              )}
            </div>
          )}
          <div className="dialog-actions">
            <button type="button" onClick={onClose} disabled={busy}>Отмена</button>
            <button type="submit" className="primary" disabled={busy || (mode.kind === 'relative' && linkExisting && !existing)}>
              {busy ? 'Сохраняем…' : 'Сохранить'}
            </button>
          </div>
        </form>
      )}
    </Dialog>
  )
}
