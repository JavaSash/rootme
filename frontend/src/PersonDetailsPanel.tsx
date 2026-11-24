import { relativeTitles } from './Editor'
import type { EditorMode } from './Editor'
import { fullName, genderLabels, parentChildLabels, partnershipLabels, statusLabels } from './labels'
import type {
  Branch, HiddenBranches, ParentChild, Partnership, PersonDetails, PersonSummary, RelativeRole,
} from './types'

interface Props {
  details: PersonDetails
  rootId?: string
  busy: boolean
  hidden?: HiddenBranches
  branches: Record<Branch, { available: boolean; collapsed: boolean; canExpand: boolean }>
  onClose: () => void
  onSelect: (id: string) => void
  onRoot: (id: string) => void
  onEditor: (mode: EditorMode) => void
  onToggleBranch: (branch: Branch) => void
  onExpandDepth: (branch: Branch) => void
  onDeletePerson: () => void
  onDeleteParentChild: (edge: ParentChild) => void
  onDeletePartnership: (partnership: Partnership) => void
}

function RelativeName({ person, onSelect }: { person?: PersonSummary; onSelect: (id: string) => void }) {
  return person
    ? <button type="button" className="text-button relative-name" onClick={() => onSelect(person.id)}>{fullName(person)}</button>
    : <span>Персона</span>
}

export function PersonDetailsPanel({
  details, rootId, busy, hidden, branches, onClose, onSelect, onRoot, onEditor,
  onToggleBranch, onExpandDepth, onDeletePerson, onDeleteParentChild, onDeletePartnership,
}: Props) {
  const { person, relatives, parentChildEdges, partnerships } = details
  const people = new Map(relatives.map(relative => [relative.id, relative]))
  const parents = parentChildEdges.filter(edge => edge.childId === person.id)
  const children = parentChildEdges.filter(edge => edge.parentId === person.id)
  const sharedParents = [...new Set(parents.filter(edge => edge.type !== 'STEP').map(edge => edge.parentId))]
    .flatMap(id => people.get(id) ?? [])
  const facts = [
    ['Пол', genderLabels[person.gender]],
    ['Дата рождения', person.birthDate],
    ['Дата смерти', person.deathDate],
    ['Место рождения', [person.birthCity, person.birthRegion, person.birthCountry].filter(Boolean).join(', ')],
    ['Фамилия при рождении', person.birthSurname],
    ['Заметки', person.notes],
  ]

  function parentChildSection(branch: Branch, edges: ParentChild[]) {
    const isParents = branch === 'parents'
    const hasHidden = isParents ? hidden?.parents || hidden?.stepParents : hidden?.children || hidden?.stepChildren
    const { available, collapsed, canExpand } = branches[branch]
    return (
      <section className="detail-section">
        <div className="section-heading">
          <h3>{isParents ? 'Родители' : 'Дети'}</h3>
          {(available || collapsed) && <button type="button" className="text-button"
            aria-expanded={!collapsed} onClick={() => onToggleBranch(branch)}>
            {collapsed ? 'Развернуть ветку' : 'Свернуть ветку'}
          </button>}
        </div>
        {!edges.length && <p className="muted">{isParents ? 'Родители не указаны.' : 'Дети не указаны.'}</p>}
        {edges.length > 0 && <ul className="relative-list">{edges.map(edge => {
          const other = people.get(isParents ? edge.parentId : edge.childId)
          return <li key={edge.id}>
            <div><RelativeName person={other} onSelect={onSelect} /><small>{parentChildLabels[edge.type]}</small></div>
            <button type="button" className="icon-button remove" disabled={busy}
              aria-label={other ? `Удалить связь с персоной ${fullName(other)}` : 'Удалить родительскую связь'}
              title="Удалить связь" onClick={() => onDeleteParentChild(edge)}>×</button>
          </li>
        })}</ul>}
        {hasHidden && (
          <p className="hidden-note">Часть ветки за пределами дерева.{' '}
            {canExpand && <button type="button" className="text-button" onClick={() => onExpandDepth(branch)}>Увеличить глубину</button>}
          </p>
        )}
      </section>
    )
  }

  return (
    <aside className="details-panel" aria-labelledby="details-title">
      <div className="details-heading">
        <div><h2 id="details-title">{fullName(person)}</h2>{rootId === person.id && <span className="root-tag">Корень дерева</span>}</div>
        <button type="button" className="icon-button" aria-label="Закрыть карточку" onClick={onClose}>×</button>
      </div>
      <div className="panel-actions">
        <button type="button" disabled={busy} onClick={() => onEditor({ kind: 'person', person })}>Редактировать</button>
        {rootId !== person.id && <button type="button" onClick={() => onRoot(person.id)}>Сделать корнем</button>}
      </div>
      <dl className="person-facts">{facts.filter(([, value]) => value).map(([label, value]) =>
        <div key={label}><dt>{label}</dt><dd>{value}</dd></div>,
      )}</dl>
      <div className="panel-actions relatives-actions">
        {Object.entries(relativeTitles).map(([role, title]) => <button key={role} type="button" disabled={busy}
          onClick={() => onEditor({ kind: 'relative', role: role as RelativeRole, person })}>{title}</button>)}
        {sharedParents.length > 0 && <button type="button" disabled={busy}
          onClick={() => onEditor({ kind: 'relative', role: 'CHILD', person, sharedParents })}>Добавить брата или сестру</button>}
      </div>
      {parentChildSection('parents', parents)}
      {parentChildSection('children', children)}
      <section className="detail-section">
        <h3>Партнёры</h3>
        {!partnerships.length && <p className="muted">Партнёры не указаны.</p>}
        {partnerships.length > 0 && <ul className="relative-list">{partnerships.map(partnership => {
          const other = people.get(partnership.person1Id === person.id ? partnership.person2Id : partnership.person1Id)
          return <li key={partnership.id}>
            <div>
              <RelativeName person={other} onSelect={onSelect} />
              <small>{partnershipLabels[partnership.type]} · {statusLabels[partnership.status]}</small>
              {partnership.startDate && <small>Начало {partnership.startDate}</small>}
              {partnership.endDate && <small>Окончание {partnership.endDate}</small>}
              <button type="button" className="text-button" disabled={busy}
                onClick={() => onEditor({ kind: 'partnership', partnership })}>Изменить союз</button>
            </div>
            <button type="button" className="icon-button remove" aria-label="Удалить союз" title="Удалить союз" disabled={busy}
              onClick={() => onDeletePartnership(partnership)}>×</button>
          </li>
        })}</ul>}
        {hidden?.partners && <p className="hidden-note">Часть партнёров за пределами дерева.</p>}
      </section>
      <button type="button" className="danger delete-person" disabled={busy} onClick={onDeletePerson}>Удалить персону</button>
    </aside>
  )
}
