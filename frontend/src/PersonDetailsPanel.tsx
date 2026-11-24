import { useState } from 'react'
import { hiddenBranchAction } from './branchNavigation'
import { relativeTitles } from './Editor'
import type { EditorMode } from './Editor'
import { fullName, genderLabels, parentChildLabels, partnershipLabels, statusLabels } from './labels'
import { PersonPhotoGallery } from './PersonPhotoGallery'
import { PhotoPreview } from './PhotoPreview'
import { AlternativeNamesEditor, BiographyEditor } from './ProfileText'
import type {
  Branch, HiddenBranches, ParentChild, Partnership, PersonDetails, PersonSummary, RelativeRole,
} from './types'

interface Props {
  details: PersonDetails
  rootId?: string
  busy: boolean
  hidden?: HiddenBranches
  branches: Record<Branch, { available: boolean; collapsed: boolean }>
  onClose: () => void
  onSelect: (id: string) => void
  onRoot: (id: string) => void
  onEditor: (mode: EditorMode) => void
  onToggleBranch: (branch: Branch) => void
  onExpandDepth: (branch: Branch, depth: number) => void
  onDeletePerson: () => void
  onDeleteParentChild: (edge: ParentChild) => void
  onDeletePartnership: (partnership: Partnership) => void
  onProfileChange: () => void
}

const sections = {
  main: 'Основное', relationships: 'Связи', biography: 'Биография', photos: 'Фотографии',
} as const

function RelativeName({ person, onSelect }: { person?: PersonSummary; onSelect: (id: string) => void }) {
  return person
    ? <button type="button" className="text-button relative-name" onClick={() => onSelect(person.id)}>{fullName(person)}</button>
    : <span>Персона</span>
}

export function PersonDetailsPanel({
  details, rootId, busy, hidden, branches, onClose, onSelect, onRoot, onEditor,
  onToggleBranch, onExpandDepth, onDeletePerson, onDeleteParentChild, onDeletePartnership, onProfileChange,
}: Props) {
  const [section, setSection] = useState<keyof typeof sections>('main')
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
    ['Место смерти', [person.deathCity, person.deathRegion, person.deathCountry].filter(Boolean).join(', ')],
    ['Род занятий', person.occupation],
    ['Место захоронения', person.burialPlace],
    ['Заметки', person.notes],
  ]

  function parentChildSection(branch: Branch, edges: ParentChild[]) {
    const isParents = branch === 'parents'
    const { available, collapsed } = branches[branch]
    const action = hiddenBranchAction(hidden, branch, collapsed, rootId === person.id)
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
        {action && (
          <p className="hidden-note">Часть ветки за пределами дерева.{' '}
            {action.kind === 'depth' ? (
              <button type="button" className="text-button" onClick={() => onExpandDepth(branch, action.depth)}>Увеличить глубину</button>
            ) : action.kind === 'root' && (
              <button type="button" className="text-button" onClick={() => onRoot(person.id)}>Посмотреть от этой персоны</button>
            )}
          </p>
        )}
      </section>
    )
  }

  return (
    <aside className="details-panel" aria-labelledby="details-title">
      <div className="details-heading">
        <div className="profile-heading">
          <PhotoPreview src={details.primaryPhoto?.thumbnailUrl} />
          <div><h2 id="details-title">{fullName(person)}</h2>{rootId === person.id && <span className="root-tag">Корень дерева</span>}</div>
        </div>
        <button type="button" className="icon-button" aria-label="Закрыть карточку" onClick={onClose}>×</button>
      </div>
      {rootId !== person.id && <div className="panel-actions">
        <button type="button" onClick={() => onRoot(person.id)}>Сделать корнем</button>
      </div>}
      <nav className="profile-tabs" aria-label="Разделы профиля">
        {(Object.keys(sections) as (keyof typeof sections)[]).map(key => (
          <button key={key} type="button" aria-pressed={section === key} onClick={() => setSection(key)}>{sections[key]}</button>
        ))}
      </nav>
      <div className="detail-tab" hidden={section !== 'main'}>
      <button type="button" disabled={busy} onClick={() => onEditor({ kind: 'person', person })}>Редактировать профиль</button>
      <dl className="person-facts">{facts.filter(([, value]) => value).map(([label, value]) =>
        <div key={label}><dt>{label}</dt><dd>{value}</dd></div>,
      )}</dl>
      <AlternativeNamesEditor personId={person.id} names={details.alternativeNames} onChange={onProfileChange} />
      <button type="button" className="danger delete-person" disabled={busy} onClick={onDeletePerson}>Удалить персону</button>
      </div>
      <div className="detail-tab" hidden={section !== 'relationships'}>
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
        {hidden?.partners && <p className="hidden-note">Часть партнёров за пределами дерева.{' '}
          {rootId !== person.id && (
            <button type="button" className="text-button" onClick={() => onRoot(person.id)}>Посмотреть от этой персоны</button>
          )}
        </p>}
      </section>
      </div>
      <div className="detail-tab" hidden={section !== 'biography'}>
        <BiographyEditor person={person} onChange={onProfileChange} />
      </div>
      <div className="detail-tab" hidden={section !== 'photos'}>
        <PersonPhotoGallery personId={person.id} active={section === 'photos'} onChange={onProfileChange} />
      </div>
    </aside>
  )
}
