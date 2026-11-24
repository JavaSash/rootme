export async function debounceSearch<T>(
  signal: AbortSignal, delayMs: number, request: (signal: AbortSignal) => Promise<T>,
): Promise<T> {
  signal.throwIfAborted()
  if (delayMs > 0) {
    await new Promise<void>((resolve, reject) => {
      const cancel = () => {
        clearTimeout(timer)
        reject(signal.reason)
      }
      const timer = setTimeout(() => {
        signal.removeEventListener('abort', cancel)
        resolve()
      }, delayMs)
      signal.addEventListener('abort', cancel, { once: true })
    })
  }
  signal.throwIfAborted()
  return request(signal)
}
