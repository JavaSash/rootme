import { useCallback, useEffect, useRef, useState } from 'react'
import { ReactFlowProvider } from '@xyflow/react'
import { api, errorText } from './api'
import { Editor } from './Editor'
import type { EditorMode, EditorSubmission } from './Editor'
import FamilyTree from './FamilyTree'
import { fullName } from './labels'
import { PersonDetailsPanel } from './PersonDetailsPanel'
import { branchKey, depthLimit } from './treeLayout'
import type { Branch, Depth, PersonSummary } from './types'
import { useQuery } from './useQuery'

const rootPreference = 'rootme.rootPersonId'
const depths = Array.from({ length: depthLimit + 1 }, (_, index) => index)
const defaultDepth: Depth = { ancestors: 5, descendants: 5 }

function initialRoot() {
  try { return localStorage.getItem(rootPreference) } catch { return null }
}

function TreeToolbar({
  root, depth, changeDepth, onSearch, onCreate, busy,
}: {
  root?: PersonSummary
  depth: Depth
  changeDepth: (key: keyof Depth, value: number) => void
  onSearch: () => void
  onCreate: () => void
  busy: boolean
}) {
  return (
    <header className="toolbar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
        <div><span className="brand-name">RootMe</span><h1>Семейное дерево</h1></div>
      </div>
      <div className="toolbar-controls">
        {root && (
          <>
            <label>Предки<select value={depth.ancestors} onChange={event => changeDepth('ancestors', +event.target.value)}>
              {depths.map(value => <option key={value} value={value}>{value}</option>)}
            </select></label>
            <label>Потомки<select value={depth.descendants} onChange={event => changeDepth('descendants', +event.target.value)}>
              {depths.map(value => <option key={value} value={value}>{value}</option>)}
            </select></label>
            <button type="button" className="toolbar-search" onClick={onSearch} disabled={busy}>Найти персону</button>
          </>
        )}
        <button type="button" className="primary" onClick={onCreate} disabled={busy}>Добавить персону</button>
      </div>
    </header>
  )
}

function LoadError({ error, retry }: { error: unknown; retry: () => void }) {
  return (
    <div className="error-box" role="alert">{errorText(error)}{' '}
      <button type="button" onClick={retry}>Повторить</button>
    </div>
  )
}

export default function App() {
  const [preferredRoot, setPreferredRoot] = useState(initialRoot)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [depth, setDepth] = useState<Depth>(defaultDepth)
  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(new Set())
  const [editor, setEditor] = useState<EditorMode | null>(null)
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)
  const [mutationError, setMutationError] = useState<unknown>()
  const [formError, setFormError] = useState<unknown>()
  const [revision, setRevision] = useState(0)
  const reload = () => setRevision(value => value + 1)

  const loadPeople = useCallback((signal: AbortSignal) => api.people('', signal), [revision])
  const people = useQuery('people', loadPeople)
  const root = people.data?.find(person => person.id === preferredRoot) ?? people.data?.[0]
  const rootId = root?.id
  const loadTree = useCallback((signal: AbortSignal) => api.tree(rootId!, depth, signal), [rootId, depth, revision])
  const graph = useQuery(rootId ?? null, loadTree)
  const loadDetails = useCallback((signal: AbortSignal) => api.person(selectedId!, signal), [selectedId, revision])
  const details = useQuery(selectedId, loadDetails)

  useEffect(() => {
    if (people.data && !people.loading) {
      try {
        if (rootId) localStorage.setItem(rootPreference, rootId)
        else localStorage.removeItem(rootPreference)
      } catch { /* localStorage is optional */ }
    }
  }, [people.data, people.loading, rootId])

  function openEditor(mode: EditorMode) {
    setFormError(undefined)
    setEditor(mode)
  }

  function chooseRoot(id: string) {
    if (id !== rootId) setCollapsed(new Set())
    setPreferredRoot(id)
    setSelectedId(id)
    setEditor(null)
    if (!people.data?.some(person => person.id === id)) reload()
  }

  async function mutate(action: () => Promise<void>, fromEditor = false) {
    if (saving.current) return
    saving.current = true
    setBusy(true)
    setMutationError(undefined)
    setFormError(undefined)
    try {
      await action()
      if (fromEditor) setEditor(null)
      reload()
    } catch (error) {
      if (fromEditor) setFormError(error)
      else setMutationError(error)
    } finally {
      setBusy(false)
      saving.current = false
    }
  }

  function submit(submission: EditorSubmission) {
    void mutate(async () => {
      if (submission.kind === 'person') {
        const person = 'id' in submission
          ? await api.updatePerson(submission.id, submission.fields)
          : await api.createPerson(submission.fields)
        setSelectedId(person.id)
        if (!('id' in submission)) {
          setPreferredRoot(person.id)
          setCollapsed(new Set())
        }
      } else if (submission.kind === 'relative') {
        await api.addRelative(submission.id, submission.request)
      } else {
        await api.updatePartnership(submission.id, submission.fields)
      }
    }, true)
  }

  function removePerson(person: PersonSummary) {
    if (!window.confirm(`Удалить персону «${fullName(person)}» и все её связи?`)) return
    void mutate(async () => {
      await api.deletePerson(person.id)
      setSelectedId(null)
      if (person.id === rootId) {
        setPreferredRoot(people.data?.find(other => other.id !== person.id)?.id ?? null)
        setCollapsed(new Set())
      }
    })
  }

  function toggleBranch(branch: Branch) {
    if (!selectedId) return
    const key = branchKey(selectedId, branch)
    setCollapsed(previous => {
      const next = new Set(previous)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  function branchState(branch: Branch) {
    const field = branch === 'parents' ? 'childId' : 'parentId'
    const depthKey = branch === 'parents' ? 'ancestors' : 'descendants'
    return {
      available: graph.data?.parentChildEdges.some(edge => edge[field] === selectedId) ?? false,
      collapsed: !!selectedId && collapsed.has(branchKey(selectedId, branch)),
      canExpand: depth[depthKey] < depthLimit,
    }
  }

  return (
    <div className="app">
      <TreeToolbar root={root} depth={depth} changeDepth={(key, value) => setDepth(current => ({ ...current, [key]: value }))}
        onSearch={() => openEditor({ kind: 'search' })} onCreate={() => openEditor({ kind: 'person' })} busy={busy} />
      {mutationError !== undefined && (
        <div className="error-box mutation-error" role="alert">{errorText(mutationError)}
          <button type="button" className="icon-button" aria-label="Закрыть сообщение" onClick={() => setMutationError(undefined)}>×</button>
        </div>
      )}
      <main className="workspace">
        <section className="tree-workspace" aria-label="Дерево">
          {!!root && (
            <div className="graph-toolbar">
              <span>Корень <strong>{fullName(root)}</strong></span>
              {collapsed.size > 0 && <button type="button" className="text-button" onClick={() => setCollapsed(new Set())}>Развернуть всё</button>}
              {graph.loading && graph.data && <span className="muted" role="status">Обновляем…</span>}
            </div>
          )}
          {people.error !== undefined && <LoadError error={people.error} retry={reload} />}
          {graph.error !== undefined && <LoadError error={graph.error} retry={reload} />}
          {(people.loading && !people.data || graph.loading && !graph.data) && <p className="workspace-status" role="status">Загружаем дерево…</p>}
          {people.data?.length === 0 && !people.loading && (
            <div className="empty-state">
              <span className="empty-mark" aria-hidden="true">◇</span>
              <h2>Начните семейное дерево</h2>
              <p>Добавьте первую персону, затем укажите её родственников.</p>
              <button type="button" className="primary" onClick={() => openEditor({ kind: 'person' })}>Добавить первую персону</button>
            </div>
          )}
          {graph.data && (
            <ReactFlowProvider key={graph.data.rootPersonId}>
              <FamilyTree tree={graph.data} selectedId={selectedId} collapsed={collapsed} onSelect={setSelectedId} />
            </ReactFlowProvider>
          )}
        </section>
        {selectedId && details.data && (
          <PersonDetailsPanel details={details.data} rootId={rootId} busy={busy}
            hidden={graph.data?.hiddenBranches[selectedId]}
            branches={{ parents: branchState('parents'), children: branchState('children') }}
            onClose={() => setSelectedId(null)} onSelect={setSelectedId} onRoot={chooseRoot} onEditor={openEditor}
            onToggleBranch={toggleBranch}
            onExpandDepth={branch => {
              const key = branch === 'parents' ? 'ancestors' : 'descendants'
              setDepth(current => ({ ...current, [key]: Math.min(depthLimit, current[key] + 1) }))
            }}
            onDeletePerson={() => removePerson(details.data!.person)}
            onDeleteParentChild={edge => {
              if (window.confirm('Удалить эту родительскую связь?')) void mutate(() => api.deleteParentChild(edge.id))
            }}
            onDeletePartnership={union => {
              if (window.confirm('Удалить этот союз?')) void mutate(() => api.deletePartnership(union.id))
            }}
          />
        )}
        {selectedId && !details.data && (
          <aside className="details-panel">
            <div className="details-heading"><h2>Персона</h2>
              <button type="button" className="icon-button" aria-label="Закрыть карточку" onClick={() => setSelectedId(null)}>×</button>
            </div>
            {details.loading && <p role="status">Загружаем карточку…</p>}
            {details.error !== undefined && <LoadError error={details.error} retry={reload} />}
          </aside>
        )}
      </main>
      {editor && <Editor mode={editor} busy={busy} error={formError} onClose={() => setEditor(null)}
        onSubmit={submit} onChooseRoot={chooseRoot} />}
    </div>
  )
}
