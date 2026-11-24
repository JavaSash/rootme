import { useRef, useState } from 'react'
import { api, errorText } from './api'
import { Dialog } from './Editor'
import type { BackupVerification, CreatedBackup } from './types'

const labels = {
  persons: 'Персоны',
  parent_child_relationships: 'Родительские связи',
  partnerships: 'Партнёрства',
  person_alternative_names: 'Альтернативные имена',
  person_photos: 'Фотографии',
} as const

export function BackupDialog({ onClose, canRestore, onRestored }: {
  onClose: () => void; canRestore: boolean; onRestored: () => void
}) {
  const [busy, setBusy] = useState<'create' | 'verify' | 'restore' | null>(null)
  const [error, setError] = useState<unknown>()
  const [created, setCreated] = useState<CreatedBackup>()
  const [verified, setVerified] = useState<{ file: File; summary: BackupVerification }>()
  const [restored, setRestored] = useState(false)
  const locked = useRef(false)

  async function run(kind: NonNullable<typeof busy>, action: () => Promise<void>) {
    if (locked.current) return
    locked.current = true
    setBusy(kind)
    setError(undefined)
    try { await action() } catch (error) { setError(error) }
    finally { locked.current = false; setBusy(null) }
  }

  return <Dialog title="Резервные копии" close={() => { if (!locked.current) onClose() }}>
    <section className="backup-section">
      <h3>Создать ZIP-копию</h3>
      <button type="button" className="primary" disabled={!!busy} onClick={() => {
        setCreated(undefined)
        void run('create', async () => { setCreated(await api.createBackup()) })
      }}>{busy === 'create' ? 'Создаём копию…' : 'Создать копию'}</button>
      {created && <div role="status">
        <a href={created.downloadUrl} download>Скачать ZIP</a>
        <p>{created.verification.counts.persons} персон · {created.verification.counts.person_photos} фото</p>
      </div>}
    </section>
    <section className="backup-section">
      <h3>Проверить и восстановить ZIP-копию</h3>
      <form onSubmit={event => {
        event.preventDefault()
        const file = new FormData(event.currentTarget).get('archive')
        if (!(file instanceof File) || !file.size) return
        setVerified(undefined)
        setRestored(false)
        void run('verify', async () => { setVerified({ file, summary: await api.verifyBackup(file) }) })
      }}>
        <fieldset disabled={!!busy}>
          <label>Архив RootMe<input type="file" name="archive" accept=".zip,application/zip" required
            onChange={() => { setVerified(undefined); setRestored(false); setError(undefined) }} /></label>
          <div><button type="submit">{busy === 'verify' ? 'Проверяем…' : 'Проверить целостность'}</button></div>
        </fieldset>
      </form>
      {verified && <div role="status">
        <p>Архив прошёл проверку.</p>
        <dl>{Object.entries(labels).map(([key, label]) => <div key={key}>
          <dt>{label}</dt><dd>{verified.summary.counts[key]}</dd>
        </div>)}</dl>
      </div>}
      {verified && (canRestore ? (
        <button type="button" disabled={!!busy} onClick={() => {
          if (!window.confirm('Восстановить данные из проверенного архива в пустую базу?')) return
          void run('restore', async () => {
            await api.restoreBackup(verified.file, verified.summary.archiveSha256)
            setVerified(undefined)
            setRestored(true)
            onRestored()
          })
        }}>{busy === 'restore' ? 'Восстанавливаем…' : 'Восстановить в пустую базу'}</button>
      ) : <p className="muted">Для восстановления подключите пустую базу RootMe.</p>)}
      {restored && <p role="status">Данные восстановлены.</p>}
    </section>
    {error !== undefined && <p className="error-box" role="alert">{errorText(error)}</p>}
  </Dialog>
}
