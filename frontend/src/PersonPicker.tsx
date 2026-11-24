import { useCallback, useState } from 'react'
import { api, errorText } from './api'
import { fullName, lifeYears } from './labels'
import type { PersonSummary } from './types'
import { useQuery } from './useQuery'

interface Props {
  onSelect: (person: PersonSummary) => void
  excludeId?: string
  label?: string
}

export function PersonPicker({ excludeId, onSelect, label = 'Имя, фамилия или отчество' }: Props) {
  const [input, setInput] = useState('')
  const [retry, setRetry] = useState(0)
  const query = input.trim()
  const load = useCallback((signal: AbortSignal) => api.people(query, signal), [query, retry])
  const result = useQuery(query, load)
  const people = result.data?.filter(person => person.id !== excludeId)

  return (
    <div className="person-picker">
      <label>{label}
        <input type="search" value={input} onChange={event => setInput(event.target.value)}
          placeholder="Например, Анна" autoComplete="off" />
      </label>
      {result.loading && <p role="status">Ищем персон…</p>}
      {result.error !== undefined && <p role="alert">{errorText(result.error)}{' '}
        <button type="button" onClick={() => setRetry(value => value + 1)}>Повторить</button>
      </p>}
      {people?.length === 0 && <p>Персоны не найдены.</p>}
      {!!people?.length && (
        <ul className="picker-results">
          {people.map(person => <li key={person.id}>
            <button type="button" onClick={() => onSelect(person)}>
              {fullName(person)} {lifeYears(person) && <small>{lifeYears(person)}</small>}
            </button>
          </li>)}
        </ul>
      )}
    </div>
  )
}
