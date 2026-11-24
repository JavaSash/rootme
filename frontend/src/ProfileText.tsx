import { useState } from 'react'
import { api, errorText } from './api'
import type { AlternativeName, AlternativeNameType, Person } from './types'
import { useProfileSave } from './useProfileSave'

const nameTypes: Record<AlternativeNameType, string> = {
  FORMER_NAME: 'Прежнее имя', NICKNAME: 'Прозвище',
  ALTERNATIVE_SPELLING: 'Вариант написания', OTHER: 'Другое',
}

export function AlternativeNamesEditor({ personId, names, onChange }: {
  personId: string; names: AlternativeName[]; onChange: () => void
}) {
  const [editing, setEditing] = useState<AlternativeName | 'new' | null>(null)
  const save = useProfileSave(onChange)
  return <section className="detail-section">
    <div className="section-heading">
      <h3>Альтернативные имена</h3>
      {!editing && <button type="button" className="text-button" onClick={() => { save.clearError(); setEditing('new') }}>Добавить имя</button>}
    </div>
    {names.length > 0 && <ul className="relative-list">{names.map(name => <li key={name.id}>
      <div><strong>{name.name}</strong><small>{nameTypes[name.type]}</small></div>
      <div className="inline-actions">
        <button type="button" className="text-button" disabled={save.busy} onClick={() => { save.clearError(); setEditing(name) }}>Изменить</button>
        <button type="button" className="icon-button remove" disabled={save.busy} aria-label={`Удалить имя ${name.name}`}
          onClick={() => {
            if (window.confirm(`Удалить имя «${name.name}»?`)) void save.run(() => api.deleteAlternativeName(personId, name.id)).then(ok => {
              if (ok && editing === name) setEditing(null)
            })
          }}>×</button>
      </div>
    </li>)}</ul>}
    {editing && <form key={editing === 'new' ? 'new' : editing.id} className="profile-form" onSubmit={event => {
      event.preventDefault()
      const data = new FormData(event.currentTarget)
      const fields = { name: String(data.get('name') ?? '').trim(), type: data.get('type') as AlternativeNameType }
      void save.run(() => editing === 'new' ? api.addAlternativeName(personId, fields)
        : api.updateAlternativeName(personId, editing.id, fields)).then(ok => { if (ok) setEditing(null) })
    }}>
      <fieldset disabled={save.busy}>
        <label>Альтернативное имя<input name="name" required maxLength={255} defaultValue={editing === 'new' ? '' : editing.name} /></label>
        <label>Тип имени<select name="type" defaultValue={editing === 'new' ? 'OTHER' : editing.type}>
          {Object.entries(nameTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>
        <div className="inline-actions">
          <button type="submit" className="primary">Сохранить имя</button>
          <button type="button" onClick={() => { save.clearError(); setEditing(null) }}>Отмена</button>
        </div>
      </fieldset>
    </form>}
    {save.error !== undefined && <p className="error-box" role="alert">{errorText(save.error)}</p>}
  </section>
}

export function BiographyEditor({ person, onChange }: { person: Person; onChange: () => void }) {
  const [draft, setDraft] = useState<string | null>(null)
  const save = useProfileSave(onChange)
  return <>
    {draft === null ? <>
      {person.biography && <p className="biography-text" tabIndex={0}>{person.biography}</p>}
      <button type="button" onClick={() => setDraft(person.biography ?? '')}>
        {person.biography ? 'Редактировать биографию' : 'Добавить биографию'}
      </button>
    </> : <form className="profile-form" onSubmit={event => {
      event.preventDefault()
      void save.run(() => api.updatePerson(person.id, { biography: draft || null }))
        .then(ok => { if (ok) setDraft(null) })
    }}>
      <fieldset disabled={save.busy}>
        <label>Биография<textarea rows={10} maxLength={100_000} value={draft} onChange={event => setDraft(event.target.value)} /></label>
        <div className="inline-actions">
          <button type="submit" className="primary">Сохранить биографию</button>
          <button type="button" onClick={() => { save.clearError(); setDraft(null) }}>Отмена</button>
        </div>
      </fieldset>
    </form>}
    {save.error !== undefined && <p className="error-box" role="alert">{errorText(save.error)}</p>}
  </>
}
