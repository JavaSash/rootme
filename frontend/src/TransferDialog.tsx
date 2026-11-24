import { useRef, useState } from 'react'
import { api, errorText, exportGedcom } from './api'
import { Dialog } from './Editor'
import type { GenealogyMdbPreview, ImportPreview, ImportSource } from './types'

const fields = {
  firstName: 'Имя', lastName: 'Фамилия', patronymic: 'Отчество', gender: 'Пол',
  birthDate: 'Дата рождения', deathDate: 'Дата смерти', birthSurname: 'Фамилия при рождении',
  birthCountry: 'Страна рождения', birthRegion: 'Регион рождения', birthCity: 'Город рождения',
  deathCountry: 'Страна смерти', deathRegion: 'Регион смерти', deathCity: 'Город смерти',
  occupation: 'Род занятий', burialPlace: 'Место захоронения', biography: 'Биография', notes: 'Заметки',
}

const statuses = { READY: 'Будет добавлена', DUPLICATE: 'Возможный дубликат', INVALID: 'Ошибка' }
const warnings: Record<string, string> = {
  PHOTOS_EXCLUDED: 'Фотографии не входят в GEDCOM. Для них используйте ZIP-копию RootMe.',
  UNION_STATUS_IN_NOTES: 'Статус и дата окончания союза переданы в заметках.',
  MULTIPLE_PARENTS_SPLIT: 'Группы из более чем двух родителей разделены на отдельные семейные записи.',
  MULTIPLE_UNIONS_CHILDREN_SEPARATE: 'При повторных союзах дети переданы отдельно, поскольку их принадлежность к конкретному союзу неизвестна.',
  STEP_USES_PEDI_PHRASE: 'Связи STEP переданы как «другое родство» с пояснением. Некоторые программы могут его не прочитать.',
}

const genealogyWarnings: Record<string, string> = {
  UNSUPPORTED_UNION_DATES: 'Некоторые даты союзов имеют уточнения, которые RootMe не поддерживает.',
  UNSUPPORTED_DATE_PRECISION: 'Некоторые даты с уточнениями не попадут в поля дат. Исходное описание дат персон сохранится в заметках.',
  DUPLICATE_PARENT_CHILD_LINKS: 'Повторяющиеся связи между теми же родителем и ребёнком одного типа будут объединены.',
  UNNAMED_PERSONS: 'В источнике есть персоны без имени.',
  UNSUPPORTED_SOURCE_DATA: 'Фотографии, ссылки на документы, отдельные записи альтернативных имён, дополнительные события, заметки о союзах и настройки дерева не переносятся.',
}

export function TransferDialog({ onClose, onImported, onTreeImported }: {
  onClose: () => void
  onImported: () => void
  onTreeImported: (rootId: string) => void
}) {
  const [file, setFile] = useState<File>()
  const [genealogyFile, setGenealogyFile] = useState<File>()
  const [genealogy, setGenealogy] = useState<GenealogyMdbPreview>()
  const [allowUnnamed, setAllowUnnamed] = useState(false)
  const [sources, setSources] = useState<ImportSource[]>([])
  const [selected, setSelected] = useState('')
  const [mapping, setMapping] = useState<Record<string, number>>({})
  const [preview, setPreview] = useState<ImportPreview>()
  const [notice, setNotice] = useState('')
  const [exportWarnings, setExportWarnings] = useState<string[]>()
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const locked = useRef(false)
  const source = sources.find(item => item.name === selected)

  async function run(action: () => Promise<void>) {
    if (locked.current) return
    locked.current = true
    setBusy(true)
    setError(undefined)
    setNotice('')
    try { await action() } catch (error) { setError(error) }
    finally { locked.current = false; setBusy(false) }
  }

  function mappingField(field: string, label: string) {
    return <label key={field}>{label}{field === 'firstName' ? ' *' : ''}
      <select value={mapping[field] ?? ''} onChange={event => {
        const column = event.target.value
        setMapping(previous => {
          const next = { ...previous }
          if (column === '') delete next[field]
          else next[field] = Number(column)
          return next
        })
        setPreview(undefined)
      }}>
        <option value="">Пропустить</option>
        {source?.columns.map(column => <option key={column.index} value={column.index}
          disabled={mapping[field] !== column.index && Object.values(mapping).includes(column.index)}>
          {column.index + 1}. {column.label}
        </option>)}
      </select>
    </label>
  }

  return <Dialog title="Импорт и экспорт" close={() => { if (!locked.current) onClose() }}>
    <section className="backup-section">
      <h3>Импорт генеалогического дерева из Access</h3>
      <p>Переносит персон, родительские связи и союзы в пустую базу RootMe.</p>
      <label>Генеалогический файл MDB<input type="file" accept=".mdb" disabled={busy} onChange={event => {
        setGenealogyFile(event.target.files?.[0])
        setGenealogy(undefined)
        setAllowUnnamed(false)
        setError(undefined)
      }} /></label>
      <button type="button" disabled={busy || !genealogyFile} onClick={() => {
        if (!genealogyFile) return
        setGenealogy(undefined)
        setAllowUnnamed(false)
        void run(async () => { setGenealogy(await api.previewGenealogyMdb(genealogyFile)) })
      }}>Проверить файл и показать предпросмотр</button>
      {genealogy && <div className="import-preview">
        <p role="status">Формат — {genealogy.format === 'ROOTME_GENEALOGY_MDB_V1' ? 'генеалогический MDB' : genealogy.format}</p>
        <dl>
          <div><dt>Персоны</dt><dd>{genealogy.persons}</dd></div>
          <div><dt>Связи родителей и детей</dt><dd>{genealogy.parentChildRelationships}</dd></div>
          <div><dt>Союзы</dt><dd>{genealogy.partnerships}</dd></div>
          <div><dt>Записи с ошибками</dt><dd>{genealogy.invalidRecords}</dd></div>
          <div><dt>Неразрешённые ссылки</dt><dd>{genealogy.unresolvedReferences}</dd></div>
        </dl>
        {!genealogy.targetEmpty && <p className="error-box" role="alert">В текущей базе уже есть персоны. Для импорта нужна пустая база RootMe.</p>}
        {genealogy.errors.length > 0 && <ul className="error-box">{genealogy.errors.map(message => <li key={message}>{message}</li>)}</ul>}
        {genealogy.warnings.length > 0 && <ul>{genealogy.warnings.map(code => <li key={code}>{genealogyWarnings[code] ?? code}</li>)}</ul>}
        {genealogy.missingGivenNames > 0 && <label className="checkbox">
          <input type="checkbox" checked={allowUnnamed} disabled={busy} onChange={event => setAllowUnnamed(event.target.checked)} />
          Использовать подпись «Имя неизвестно» для записей без имени ({genealogy.missingGivenNames})
        </label>}
        <button type="button" className="primary" disabled={
          busy || !genealogy.targetEmpty || genealogy.invalidRecords > 0 || genealogy.persons === 0 ||
          (genealogy.missingGivenNames > 0 && !allowUnnamed)
        } onClick={() => {
          if (!genealogyFile || !window.confirm('Импортировать показанные данные в текущую БД RootMe?')) return
          void run(async () => {
            const result = await api.applyGenealogyMdb(genealogyFile, {
              fingerprint: genealogy.fingerprint,
              allowUnnamedPersons: allowUnnamed,
            })
            onTreeImported(result.rootPersonId)
          })
        }}>Импортировать и открыть дерево</button>
      </div>}
    </section>
    <section className="backup-section">
      <h3>Импорт персон из Excel или Access</h3>
      <p>Для Excel первая заполненная строка считается заголовком. Импорт добавляет новых персон, а возможные дубликаты пропускает.</p>
      <label>Файл Excel или Access<input type="file" accept=".xls,.xlsx,.mdb,.accdb" disabled={busy} onChange={event => {
        const chosen = event.target.files?.[0]
        setFile(chosen)
        setSources([])
        setSelected('')
        setMapping({})
        setPreview(undefined)
        if (chosen) void run(async () => {
          const result = await api.importSources(chosen)
          setSources(result)
          setSelected(result.find(item => item.columns.length)?.name ?? '')
          if (result.length === 0) setNotice('В файле нет доступных листов или таблиц.')
        })
      }} /></label>
      {sources.length > 0 && <fieldset disabled={busy}>
        <label>Лист Excel или таблица Access<select value={selected} onChange={event => {
          setSelected(event.target.value)
          setMapping({})
          setPreview(undefined)
        }}>
          <option value="">Выберите источник</option>
          {sources.map(item => <option key={item.name} value={item.name} disabled={!item.columns.length}>{item.name}</option>)}
        </select></label>
        {source && <>
          <div className="form-grid">{Object.entries(fields).slice(0, 7).map(([field, label]) => mappingField(field, label))}</div>
          <details><summary>Остальные поля профиля</summary>
            <div className="form-grid">{Object.entries(fields).slice(7).map(([field, label]) => mappingField(field, label))}</div>
          </details>
          <button type="button" disabled={!file || mapping.firstName == null} onClick={() => {
            setPreview(undefined)
            if (file) void run(async () => { setPreview(await api.previewImport(file, { source: selected, mapping })) })
          }}>Предпросмотр</button>
        </>}
      </fieldset>}
      {preview && <div className="import-preview">
        <p role="status">Новые — {preview.ready} · Дубликаты — {preview.duplicates} · Ошибки — {preview.invalid}</p>
        {preview.total > preview.rows.length && <p>Показано {preview.rows.length} из {preview.total} строк.</p>}
        <table><thead><tr><th>Строка</th><th>Имя</th><th>Результат</th></tr></thead>
          <tbody>{preview.rows.map(row => <tr key={row.row}><td>{row.row}</td><td>{row.name || '—'}</td>
            <td>{statuses[row.status]}{row.message && <small>{row.message}</small>}</td></tr>)}</tbody>
        </table>
        <button type="button" className="primary" disabled={busy || preview.invalid > 0 || preview.ready === 0} onClick={() => {
          if (!file || !window.confirm(`Добавить новых персон: ${preview.ready}?`)) return
          void run(async () => {
            const result = await api.applyImport(file, { source: selected, mapping, fingerprint: preview.fingerprint })
            setPreview(undefined)
            setNotice(`Добавлено — ${result.created} · Пропущено дубликатов — ${result.skippedDuplicates}`)
            onImported()
          })
        }}>Импортировать</button>
      </div>}
      {notice && <p role="status">{notice}</p>}
    </section>
    <section className="backup-section">
      <h3>Экспорт GEDCOM 7</h3>
      <button type="button" disabled={busy} onClick={() => {
        setExportWarnings(undefined)
        void run(async () => {
          const result = await exportGedcom()
          const url = URL.createObjectURL(result.file)
          try {
            const link = document.createElement('a')
            link.href = url
            link.download = 'rootme.ged'
            link.click()
          } finally {
            setTimeout(() => URL.revokeObjectURL(url), 10_000)
          }
          setExportWarnings(result.warnings)
        })
      }}>Скачать GEDCOM</button>
      {exportWarnings && <div role="status">
        <p>GEDCOM подготовлен.</p>
        {exportWarnings.length > 0 && <ul>{exportWarnings.map(code => <li key={code}>{warnings[code] ?? code}</li>)}</ul>}
      </div>}
    </section>
    {busy && <p role="status">Обрабатываем данные…</p>}
    {error !== undefined && <p className="error-box" role="alert">{errorText(error)}</p>}
  </Dialog>
}
