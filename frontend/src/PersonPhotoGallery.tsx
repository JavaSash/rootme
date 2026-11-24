import { useCallback, useEffect, useRef, useState } from 'react'
import { api, errorText, uploadPhoto } from './api'
import { PhotoPreview } from './PhotoPreview'
import type { PersonPhoto, PhotoFields } from './types'
import { useProfileSave } from './useProfileSave'
import { useQuery } from './useQuery'

function fields(form: HTMLFormElement): PhotoFields {
  const data = new FormData(form)
  return { caption: String(data.get('caption') ?? '') || null, takenDate: String(data.get('takenDate') ?? '').trim() || null }
}

function PhotoInputs({ photo }: { photo?: PersonPhoto }) {
  return <>
    <label>Подпись<textarea name="caption" rows={2} maxLength={1000} defaultValue={photo?.caption ?? ''} /></label>
    <label>Дата съёмки<input name="takenDate" maxLength={10} pattern="[0-9]{4}(-[0-9]{2}(-[0-9]{2})?)?"
      placeholder="ГГГГ, ГГГГ-ММ или ГГГГ-ММ-ДД" defaultValue={photo?.takenDate ?? ''} /></label>
  </>
}

function PhotoViewer({ photo, onClose }: { photo: PersonPhoto; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [missing, setMissing] = useState(false)
  useEffect(() => {
    const element = dialog.current!
    element.showModal()
    return () => element.close()
  }, [])
  return <dialog ref={dialog} className="photo-viewer" aria-label="Просмотр фотографии" onCancel={event => {
    event.preventDefault()
    onClose()
  }}>
    <div className="dialog-heading"><h2>Фотография</h2>
      <button type="button" className="icon-button" aria-label="Закрыть просмотр" onClick={onClose}>×</button>
    </div>
    {missing ? <p role="alert">Файл фотографии недоступен.</p>
      : <img src={photo.originalUrl} alt={photo.caption || photo.originalFileName} onError={() => setMissing(true)} />}
    {(photo.caption || photo.takenDate) && <p className="photo-caption">{[photo.caption, photo.takenDate].filter(Boolean).join(' · ')}</p>}
  </dialog>
}

export function PersonPhotoGallery({ personId, active, onChange }: {
  personId: string; active: boolean; onChange: () => void
}) {
  const [revision, setRevision] = useState(0)
  const [opened, setOpened] = useState<PersonPhoto>()
  const [editing, setEditing] = useState<PersonPhoto | 'upload' | null>(null)
  const [progress, setProgress] = useState(0)
  const load = useCallback((signal: AbortSignal) => api.photos(personId, signal), [personId, revision])
  const photos = useQuery(active ? personId : null, load)
  const save = useProfileSave(() => { setRevision(value => value + 1); onChange() })

  function openEditor(photo: PersonPhoto | 'upload') {
    save.clearError()
    setProgress(0)
    setEditing(photo)
  }

  return <section className="photo-gallery" aria-label="Фотографии персоны">
    {!editing && <button type="button" disabled={save.busy} onClick={() => openEditor('upload')}>Добавить фотографию</button>}
    {editing && <form className="profile-form" key={editing === 'upload' ? 'upload' : editing.id} onSubmit={event => {
      event.preventDefault()
      const form = event.currentTarget
      const metadata = fields(form)
      if (editing === 'upload') {
        const file = new FormData(form).get('file')
        if (!(file instanceof File)) return
        setProgress(0)
        void save.run(() => uploadPhoto(personId, file, metadata, setProgress)).then(ok => { if (ok) setEditing(null) })
      } else {
        void save.run(() => api.updatePhoto(editing.id, metadata)).then(ok => { if (ok) setEditing(null) })
      }
    }}>
      <fieldset disabled={save.busy}>
        {editing === 'upload' && <label>Файл JPEG или PNG
          <input type="file" name="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" required />
        </label>}
        <PhotoInputs photo={editing === 'upload' ? undefined : editing} />
        <div className="inline-actions">
          <button type="submit" className="primary">{editing === 'upload' ? 'Загрузить' : 'Сохранить сведения'}</button>
          <button type="button" onClick={() => { save.clearError(); setEditing(null) }}>Отмена</button>
        </div>
      </fieldset>
      {editing === 'upload' && save.busy && <label>{progress < 100 ? `Загрузка, ${progress}%` : 'Проверяем изображение…'}
        <progress max={100} value={progress} />
      </label>}
    </form>}
    {save.error !== undefined && <p className="error-box" role="alert">{errorText(save.error)}</p>}
    {photos.loading && !photos.data && <p role="status">Загружаем фотографии…</p>}
    {photos.error !== undefined && <p className="error-box" role="alert">{errorText(photos.error)}{' '}
      <button type="button" onClick={() => setRevision(value => value + 1)}>Повторить</button>
    </p>}
    {photos.data?.length === 0 && <p className="muted">Фотографий пока нет.</p>}
    {!!photos.data?.length && <ul className="photo-list">{photos.data.map(photo => <li key={photo.id}>
      <button type="button" className="photo-open" onClick={() => setOpened(photo)} aria-label={`Открыть фото: ${photo.caption || photo.originalFileName}`}>
        <PhotoPreview src={photo.thumbnailUrl} />
        <span>{photo.caption || photo.originalFileName}</span>
      </button>
      <div className="photo-meta">
        {photo.takenDate && <small>{photo.takenDate}</small>}
        {photo.isPrimary && <span className="root-tag">Основное фото</span>}
      </div>
      <div className="inline-actions">
        {!photo.isPrimary && <button type="button" className="text-button" disabled={save.busy || !!editing}
          onClick={() => { void save.run(() => api.primaryPhoto(personId, photo.id)) }}>Сделать основным</button>}
        <button type="button" className="text-button" disabled={save.busy || !!editing} onClick={() => openEditor(photo)}>Изменить</button>
        <button type="button" className="text-button danger" disabled={save.busy || !!editing}
          onClick={() => { if (window.confirm('Удалить фотографию?')) void save.run(() => api.deletePhoto(photo.id)) }}>Удалить</button>
      </div>
    </li>)}</ul>}
    {opened && <PhotoViewer photo={opened} onClose={() => setOpened(undefined)} />}
  </section>
}
