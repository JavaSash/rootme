import { useRef, useState } from 'react'

export function useProfileSave(onChange: () => void) {
  const locked = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  async function run(action: () => Promise<unknown>): Promise<boolean> {
    if (locked.current) return false
    locked.current = true
    setBusy(true)
    setError(undefined)
    try {
      await action()
      onChange()
      return true
    } catch (error) {
      setError(error)
      return false
    } finally {
      locked.current = false
      setBusy(false)
    }
  }
  return { busy, error, run, clearError: () => setError(undefined) }
}
