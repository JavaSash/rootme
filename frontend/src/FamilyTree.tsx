import { useEffect, useMemo, useState } from 'react'
import {
  Background, BaseEdge, Controls, EdgeLabelRenderer, Handle, Position, ReactFlow,
  getSmoothStepPath, useReactFlow,
} from '@xyflow/react'
import type { Edge, EdgeProps, Node, NodeProps, OnSelectionChangeParams, XYPosition } from '@xyflow/react'
import { fullName, genderLabels, lifeYears, parentChildLabels, statusLabels } from './labels'
import { cardHeight, cardWidth, layoutTree, visibleTree } from './treeLayout'
import type { HiddenBranches, PersonSummary, Tree } from './types'

type PersonGraphNode = Node<{
  person: PersonSummary
  root: boolean
  hidden?: HiddenBranches
}, 'person'>
type RelationshipGraphEdge = Edge<{ offset: number; sideways: boolean }, 'relationship'>

function PersonNode({ data, selected }: NodeProps<PersonGraphNode>) {
  const { person, root, hidden } = data
  const years = lifeYears(person)
  return (
    <div className={`person-node ${selected ? 'is-selected' : ''}`}>
      <Handle type="target" id="top" position={Position.Top} />
      <Handle type="source" id="bottom" position={Position.Bottom} />
      <Handle type="source" id="left-out" position={Position.Left} />
      <Handle type="target" id="left-in" position={Position.Left} />
      <Handle type="source" id="right-out" position={Position.Right} />
      <Handle type="target" id="right-in" position={Position.Right} />
      <div className="node-meta">
        <span className={`gender-dot gender-${person.gender.toLowerCase()}`} title={genderLabels[person.gender]}>
          <span className="visually-hidden">{genderLabels[person.gender]}</span>
        </span>
        {root && <span className="root-tag">Корень</span>}
        {hidden && <span className="hidden-marker" title="Есть родственники за пределами дерева" aria-label="Есть скрытые ветки">…</span>}
      </div>
      <strong title={fullName(person)}>{fullName(person)}</strong>
      {years && <span className="node-years">{years}</span>}
    </div>
  )
}

function RelationshipEdge(props: EdgeProps<RelationshipGraphEdge>) {
  const { id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data, style, label } = props
  const lane = data?.offset ?? 0
  const [step, stepX, stepY] = getSmoothStepPath({
    sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition,
    centerY: (sourceY + targetY) / 2 + lane,
  })
  const side = data?.sideways
  const aroundCards = side && Math.abs(sourceY - targetY) < 2 && Math.abs(targetX - sourceX) > 100
  const direction = Math.sign(targetX - sourceX)
  const sameRow = Math.abs(sourceY - targetY) < 2
  const curve = sameRow ? (cardHeight / 2 + 13 + lane) * 4 / 3 : 24 + lane
  const corridor = sourceY + cardHeight / 2 + 18 + lane
  const path = aroundCards
    ? `M ${sourceX} ${sourceY} H ${sourceX + direction * 15} V ${corridor} H ${targetX - direction * 15} V ${targetY} H ${targetX}`
    : side
      ? `M ${sourceX} ${sourceY} C ${sourceX + direction * 34} ${sourceY + curve}, ${targetX - direction * 34} ${targetY + curve}, ${targetX} ${targetY}`
      : step
  return (
    <>
      <BaseEdge id={id} path={path} style={style} />
      {label && <EdgeLabelRenderer>
        <span className="relationship-label" style={{
          left: side ? (sourceX + targetX) / 2 : stepX,
          top: aroundCards ? corridor : side ? (sourceY + targetY) / 2 + curve * 0.75 : stepY,
        }}>{label}</span>
      </EdgeLabelRenderer>}
    </>
  )
}

const nodeTypes = { person: PersonNode }
const edgeTypes = { relationship: RelationshipEdge }

const parentStroke = { BIOLOGICAL: undefined, ADOPTIVE: '8 5', STEP: '2 5' }
const partnershipStroke = { ACTIVE: undefined, ENDED: '9 4 2 4', UNKNOWN: '3 5' }

function graphEdges(tree: Tree, positions: Map<string, XYPosition>): RelationshipGraphEdge[] {
  const lanes = new Map<string, number>()
  const targetsByRow = new Map<number, Set<string>>()
  for (const relation of tree.parentChildEdges) {
    const y = positions.get(relation.childId)!.y
    const targets = targetsByRow.get(y) ?? new Set()
    targets.add(relation.childId)
    targetsByRow.set(y, targets)
  }
  const targetOffsets = new Map<string, number>()
  for (const targets of targetsByRow.values()) {
    const ordered = [...targets].sort((a, b) => positions.get(a)!.x - positions.get(b)!.x)
    const spacing = Math.min(22, 62 / Math.max(1, ordered.length - 1))
    ordered.forEach((id, index) => targetOffsets.set(id, (index - (ordered.length - 1) / 2) * spacing))
  }

  function edge(
    id: string, source: string, target: string, color: string,
    pattern: string | undefined, label: string | undefined, forceSide = false,
  ): RelationshipGraphEdge {
    const from = positions.get(source)!
    const to = positions.get(target)!
    const sideways = forceSide || from.y >= to.y
    const key = sideways ? [source, target].sort().join(':') : `${source}:${target}`
    const laneIndex = lanes.get(key) ?? 0
    lanes.set(key, laneIndex + 1)
    const offset = sideways ? laneIndex * 21 : (targetOffsets.get(target) ?? 0) + laneIndex * 14
    return {
      id, source, target, label, type: 'relationship',
      sourceHandle: sideways ? (from.x <= to.x ? 'right-out' : 'left-out') : 'bottom',
      targetHandle: sideways ? (from.x <= to.x ? 'left-in' : 'right-in') : 'top',
      data: { offset, sideways },
      style: { stroke: color, strokeWidth: 2, strokeDasharray: pattern },
    }
  }
  return [
    ...tree.parentChildEdges.map(relation => edge(
      relation.id, relation.parentId, relation.childId, '#546f66', parentStroke[relation.type],
      relation.type === 'BIOLOGICAL' ? undefined : parentChildLabels[relation.type],
    )),
    ...tree.partnerships.map(relation => edge(
      relation.id, relation.person1Id, relation.person2Id, '#976246', partnershipStroke[relation.status],
      statusLabels[relation.status], true,
    )),
  ]
}

interface Props {
  tree: Tree
  selectedId: string | null
  collapsed: ReadonlySet<string>
  onSelect: (id: string) => void
}

export default function FamilyTree({ tree, selectedId, collapsed, onSelect }: Props) {
  const visible = useMemo(() => visibleTree(tree, collapsed), [tree, collapsed])
  const [layout, setLayout] = useState<{
    tree: Tree
    positions?: Map<string, XYPosition>
    failed?: boolean
  }>()
  const [retry, setRetry] = useState(0)
  const { fitView, setCenter } = useReactFlow()
  const [fittedRoot, setFittedRoot] = useState<string>()
  useEffect(() => {
    let active = true
    layoutTree(visible).then(
      positions => { if (active) setLayout({ tree: visible, positions }) },
      () => { if (active) setLayout({ tree: visible, failed: true }) },
    )
    return () => { active = false }
  }, [visible, retry])

  const positions = layout?.positions
  const displayed = layout?.tree
  const nodes: PersonGraphNode[] = positions && displayed ? displayed.nodes.map(person => ({
    id: person.id, type: 'person', position: positions.get(person.id)!,
    width: cardWidth, height: cardHeight, selected: person.id === selectedId,
    ariaLabel: fullName(person),
    data: { person, root: person.id === displayed.rootPersonId, hidden: displayed.hiddenBranches[person.id] },
  })) : []
  const edges = useMemo(() => positions && displayed ? graphEdges(displayed, positions) : [], [positions, displayed])

  useEffect(() => {
    if (!positions || displayed?.rootPersonId === fittedRoot || displayed?.rootPersonId !== tree.rootPersonId) return
    const frame = requestAnimationFrame(() => {
      void fitView({ padding: 0.2, maxZoom: 1 })
      setFittedRoot(tree.rootPersonId)
    })
    return () => cancelAnimationFrame(frame)
  }, [displayed, fitView, fittedRoot, positions, tree.rootPersonId])

  function selectNode({ nodes: selected }: OnSelectionChangeParams) {
    const node = selected[0]
    if (node && node.id !== selectedId) onSelect(node.id)
  }

  function resetView() {
    const root = positions?.get(tree.rootPersonId)
    if (root) void setCenter(root.x + cardWidth / 2, root.y + cardHeight / 2, { zoom: 1, duration: 250 })
  }

  const layingOut = displayed !== visible
  return (
    <div className="graph" aria-label="Семейное дерево" aria-busy={layingOut}>
      <ReactFlow
        nodes={nodes} edges={edges} nodeTypes={nodeTypes} edgeTypes={edgeTypes}
        onNodeClick={(_, node) => onSelect(node.id)}
        onSelectionChange={selectNode}
        nodesDraggable={false} nodesConnectable={false} edgesFocusable={false}
        elementsSelectable panOnDrag zoomOnScroll minZoom={0.02} maxZoom={2}
        deleteKeyCode={null} multiSelectionKeyCode={null}
        ariaLabelConfig={{
          'controls.ariaLabel': 'Управление деревом',
          'controls.zoomIn.ariaLabel': 'Приблизить',
          'controls.zoomOut.ariaLabel': 'Отдалить',
          'controls.fitView.ariaLabel': 'Показать всё дерево',
        }}
      >
        <Background gap={24} color="#b8c9bd" />
        <Controls showInteractive={false} fitViewOptions={{ padding: 0.2, maxZoom: 1 }} />
      </ReactFlow>
      <button type="button" className="reset-view" onClick={resetView} disabled={!positions?.has(tree.rootPersonId)}>
        К корню
      </button>
      {edges.length > 0 && <details className="graph-legend">
        <summary>Обозначения связей</summary>
        <ul>
          <li><i className="legend-biological" />Биологическое родство</li>
          <li><i className="legend-adoptive" />Приёмное родство</li>
          <li><i className="legend-step" />Неродное родство</li>
          <li><i className="legend-active" />Текущий союз</li>
          <li><i className="legend-ended" />Бывший союз</li>
          <li><i className="legend-unknown" />Статус союза неизвестен</li>
        </ul>
      </details>}
      {layingOut && <div className="graph-status" role="status">Раскладываем дерево…</div>}
      {!layingOut && layout?.failed && (
        <div className="graph-status" role="alert">
          Не удалось разложить дерево. <button type="button" onClick={() => { setLayout(undefined); setRetry(value => value + 1) }}>Повторить</button>
        </div>
      )}
    </div>
  )
}
