import { afterEach, describe, expect, it, vi } from 'vitest'
import { hiddenBranchAction, treeAtDepth } from '../src/branchNavigation'
import { debounceSearch } from '../src/searchDebounce'
import type { HiddenBranches, Tree } from '../src/types'

const hidden: HiddenBranches = {
  parents: true, children: false, stepParents: false, stepChildren: true, partners: false,
  nextAncestorDepth: 6, nextDescendantDepth: null,
}

describe('hidden branch navigation', () => {
  it('offers a depth increase only when the server supplied a reachable frontier', () => {
    expect(hiddenBranchAction(hidden, 'parents', false, true)).toEqual({ kind: 'depth', depth: 6 })
    expect(hiddenBranchAction(hidden, 'children', false, false)).toEqual({ kind: 'root' })
    expect(hiddenBranchAction(hidden, 'children', false, true)).toEqual({ kind: 'notice' })
  })

  it('does not offer a server navigation action for a collapsed branch or a stale response', () => {
    const tree: Tree = {
      rootPersonId: 'root', nodes: [], parentChildEdges: [], partnerships: [], hiddenBranches: {},
      maxDepth: 8, ancestorDepth: 5, descendantDepth: 5,
    }
    expect(hiddenBranchAction(hidden, 'parents', true, false)).toBeNull()
    expect(hiddenBranchAction(undefined, 'parents', false, false)).toBeNull()
    expect(treeAtDepth(tree, { ancestors: 6, descendants: 5 })).toBeUndefined()
    expect(treeAtDepth(tree, { ancestors: 5, descendants: 5 })).toBe(tree)
  })
})

describe('search debounce', () => {
  afterEach(() => vi.useRealTimers())

  it('cancels outdated input before calling the server and only loads the latest query', async () => {
    vi.useFakeTimers()
    const outdated = new AbortController()
    const current = new AbortController()
    const loadOld = vi.fn(async () => 'old')
    const loadNew = vi.fn(async () => 'new')
    const resultOld = debounceSearch(outdated.signal, 300, loadOld)
    const cancelled = expect(resultOld).rejects.toMatchObject({ name: 'AbortError' })
    await vi.advanceTimersByTimeAsync(100)
    outdated.abort()
    const resultNew = debounceSearch(current.signal, 300, loadNew)
    await vi.advanceTimersByTimeAsync(299)
    expect(loadOld).not.toHaveBeenCalled()
    expect(loadNew).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    await cancelled
    await expect(resultNew).resolves.toBe('new')
    expect(loadNew).toHaveBeenCalledWith(current.signal)
  })

  it('passes the cancellation signal to an already started request', async () => {
    const controller = new AbortController()
    const request = vi.fn((signal: AbortSignal) => new Promise<string>((_, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    }))
    const result = debounceSearch(controller.signal, 0, request)
    const cancellation = expect(result).rejects.toMatchObject({ name: 'AbortError' })
    expect(request).toHaveBeenCalledWith(controller.signal)
    controller.abort()
    await cancellation
  })
})
