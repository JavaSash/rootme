import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { createRoot } from 'react-dom/client'
import './style.css'

const fields = [
  ['firstName', 'Имя', 'text'],
  ['lastName', 'Фамилия', 'text'],
  ['patronymic', 'Отчество', 'text'],
  ['birthDate', 'Дата рождения', 'date'],
  ['deathDate', 'Дата смерти', 'date'],
  ['birthCountry', 'Страна рождения', 'text'],
  ['birthRegion', 'Регион рождения', 'text'],
  ['birthCity', 'Город рождения', 'text'],
] as const

type PersonFields = Record<(typeof fields)[number][0], string | null>
type Person = PersonFields & { id: string; gender: 'FEMALE' | 'MALE'; isRoot: boolean }

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch('/api/v1/person/' + path, init)
  if (!response.ok) throw new Error(`Запрос не выполнен (HTTP ${response.status}).`)
  if (init?.method === 'DELETE') return undefined as T
  return response.json()
}

function fullName(person: Person) {
  return [person.firstName, person.patronymic, person.lastName].filter(Boolean).join(' ')
}

function App() {
  const [people, setPeople] = useState<Person[]>([])
  const [editor, setEditor] = useState<Person | 'new' | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    let active = true
    request<Person[]>('get')
      .then(list => { if (active) setPeople(list) })
      .catch(() => { if (active) setError('Не удалось загрузить список персон.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [loadAttempt])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const body = Object.fromEntries(fields.map(([name]) => [name, String(form.get(name) ?? '').trim() || null]))
    const id = editor && editor !== 'new' ? editor.id : null
    setBusy(true)
    setError('')
    try {
      const person = await request<Person>(id ? `edit/${id}` : 'create', {
        method: id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...body, gender: form.get('gender') }),
      })
      setPeople(list => id ? list.map(item => item.id === id ? person : item) : [...list, person])
      setEditor(null)
    } catch {
      setError('Не удалось сохранить персону. Проверьте поля и попробуйте снова.')
    } finally {
      setBusy(false)
    }
  }

  async function remove(person: Person) {
    if (!window.confirm(`Удалить персону «${fullName(person)}»?`)) return
    setBusy(true)
    setError('')
    try {
      await request<void>(`delete/${person.id}`, { method: 'DELETE' })
      setPeople(list => list.filter(item => item.id !== person.id))
      if (editor === person) setEditor(null)
    } catch {
      setError('Не удалось удалить персону.')
    } finally {
      setBusy(false)
    }
  }

  const selected = editor && editor !== 'new' ? editor : null
  const root = people.find(person => person.isRoot) ?? people[0]

  return (
    <main>
      <header>
        <div><span className="brand">RootMe</span><h1>Персоны</h1></div>
        <button className="primary" disabled={busy || loading} onClick={() => { setError(''); setEditor('new') }}>
          Добавить персону
        </button>
      </header>
      {error && <p className="error" role="alert">{error}</p>}
      {loading && <p role="status">Загрузка…</p>}
      {!loading && error && people.length === 0 && !editor && (
        <button onClick={() => { setError(''); setLoading(true); setLoadAttempt(value => value + 1) }}>Обновить список</button>
      )}
      {!loading && !error && people.length === 0 && !editor && <p>Добавьте первую персону.</p>}
      <div className="workspace">
        <section className="cards" aria-label="Список персон">
          {people.map(person => (
            <article key={person.id}>
              {person.id === root?.id && <p className="root-label">Корневая персона</p>}
              <h2>{fullName(person)}</h2>
              <dl>
                <div><dt>Пол</dt><dd>{person.gender === 'FEMALE' ? 'Женский' : 'Мужской'}</dd></div>
                {fields.slice(3).map(([name, label]) => person[name] && (
                  <div key={name}><dt>{label}</dt><dd>{person[name]}</dd></div>
                ))}
              </dl>
              <div className="actions">
                <button disabled={busy} onClick={() => { setError(''); setEditor(person) }}>Редактировать</button>
                <button className="danger" disabled={busy} onClick={() => void remove(person)}>Удалить</button>
              </div>
            </article>
          ))}
        </section>
        {editor && (
          <aside aria-label={selected ? 'Редактирование персоны' : 'Добавление персоны'}>
            <h2>{selected ? 'Редактирование' : 'Новая персона'}</h2>
            <form key={selected?.id ?? 'new'} onSubmit={event => void save(event)}>
              <fieldset disabled={busy}>
                {fields.map(([name, label, type]) => (
                  <label key={name}>{label}
                    <input name={name} type={type} required={name === 'firstName'} maxLength={255}
                      defaultValue={selected?.[name] ?? ''} />
                  </label>
                ))}
                <label>Пол
                  <select name="gender" required defaultValue={selected?.gender ?? ''}>
                    <option value="" disabled>Выберите</option>
                    <option value="FEMALE">Женский</option>
                    <option value="MALE">Мужской</option>
                  </select>
                </label>
                <div className="actions">
                  <button className="primary" type="submit">Сохранить</button>
                  <button type="button" onClick={() => setEditor(null)}>Отмена</button>
                </div>
              </fieldset>
            </form>
          </aside>
        )}
      </div>
    </main>
  )
}

createRoot(document.getElementById('root')!).render(<App />)
