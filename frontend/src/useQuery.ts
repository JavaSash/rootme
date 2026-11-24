import { useEffect, useState } from 'react'

interface Result<T> {
  key: string
  data?: T
  error?: unknown
  loading: boolean
}

export function useQuery<T>(
  key: string | null, load: (signal: AbortSignal) => Promise<T>,
): Omit<Result<T>, 'key'> {
  const [result, setResult] = useState<Result<T>>({ key: '', loading: false })
  useEffect(() => {
    if (key == null) return
    const controller = new AbortController()
    setResult(previous => ({
      key, data: previous.key === key ? previous.data : undefined, loading: true,
    }))
    load(controller.signal).then(
      data => { if (!controller.signal.aborted) setResult({ key, data, loading: false }) },
      error => {
        if (!controller.signal.aborted) setResult(previous => ({ ...previous, key, error, loading: false }))
      },
    )
    return () => controller.abort()
  }, [key, load])
  if (key == null) return { loading: false }
  return result.key === key ? result : { loading: true }
}
