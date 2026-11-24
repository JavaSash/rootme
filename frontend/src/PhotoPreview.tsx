import { useState } from 'react'

export function PhotoPreview({ src, label, className = '' }: { src?: string | null; label?: string; className?: string }) {
  const [failed, setFailed] = useState<string>()
  return <span className={`photo-preview ${className}`}>
    {src && failed !== src
      ? <img src={src} loading="lazy" alt={label ?? ''} onError={() => setFailed(src)} />
      : <span className="photo-placeholder" title={src ? 'Файл фотографии недоступен' : 'Фотографии нет'}
        aria-label={src ? 'Файл фотографии недоступен' : undefined} role={src ? 'img' : undefined} />}
  </span>
}
