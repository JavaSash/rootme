import { useCallback, useState } from 'react'
import { api, errorText } from './api'
import { fullName, lifeYears } from './labels'
import { PhotoPreview } from './PhotoPreview'
import { debounceSearch } from './searchDebounce'
import type { PersonSummary } from './types'
import { useQuery } from './useQuery'

interface Props {
  onSelect: (person: PersonSummary) => void
  onRoot?: (person: PersonSummary) => void
  excludeId?: string
  label?: string
}

export function PersonPicker({ excludeId, onSelect, onRoot, label = 'Имя, фамилия или альтернативное имя' }: Props) {
  const [input, setInput] = useState('')
  const [year, setYear] = useState('')
  const [page, setPage] = useState(0)
  const [retry, setRetry] = useState(0)
  const query = input.trim()
  const validYear = year === '' || (/^[0-9]{4}$/.test(year) && Number(year) > 0)
  const load = useCallback((signal: AbortSignal) => debounceSearch(signal, query || year ? 300 : 0, current =>
    api.people({ query, birthYear: year ? Number(year) : undefined, page }, current),
  ), [query, year, page, retry])
  const result = useQuery(validYear ? JSON.stringify([query, year, page]) : null, load)
  const people = result.data?.items.filter(person => person.id !== excludeId)

  return (
    <div className="person-picker">
      <div className="directory-filters">
        <label>{label}
          <input type="search" value={input} onChange={event => { setInput(event.target.value); setPage(0) }}
            placeholder="Например, Анна Северова" autoComplete="off" maxLength={255} />
        </label>
        <label>Год рождения
          <input inputMode="numeric" placeholder="ГГГГ" maxLength={4} value={year} aria-invalid={!validYear}
            onChange={event => { setYear(event.target.value); setPage(0) }} />
        </label>
      </div>
      {!validYear && <p role="status">Введите четырёхзначный год рождения.</p>}
      {result.loading && <p role="status">Ищем персон…</p>}
      {result.error !== undefined && <p role="alert">{errorText(result.error)}{' '}
        <button type="button" onClick={() => setRetry(value => value + 1)}>Повторить</button>
      </p>}
      {people?.length === 0 && <p>На этой странице нет подходящих персон.</p>}
      {!!people?.length && (
        <ul className="picker-results">
          {people.map(person => {
            const birthplace = [person.birthCity, person.birthRegion, person.birthCountry].filter(Boolean).join(', ')
            const description = [lifeYears(person), person.birthSurname && `при рождении ${person.birthSurname}`, birthplace].filter(Boolean).join(' · ')
            return <li key={person.id}>
              <button type="button" className="directory-person" onClick={() => onSelect(person)}>
                <PhotoPreview src={person.primaryPhoto?.thumbnailUrl} />
                <span><strong>{fullName(person)}</strong>{description && <small>{description}</small>}</span>
              </button>
              {onRoot && <button type="button" className="directory-root" aria-label={`Сделать корнем: ${fullName(person)}`}
                onClick={() => onRoot(person)}>Сделать корнем</button>}
            </li>
          })}
        </ul>
      )}
      {(result.data || page > 0) && <div className="pagination">
        <button type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Назад</button>
        <span>{result.data ? `Страница ${page + 1} из ${Math.max(1, Math.ceil(result.data.total / result.data.size))}` : `Страница ${page + 1}`}</span>
        <button type="button" disabled={!result.data || (page + 1) * result.data.size >= result.data.total}
          onClick={() => setPage(value => value + 1)}>Далее</button>
      </div>}
    </div>
  )
}
