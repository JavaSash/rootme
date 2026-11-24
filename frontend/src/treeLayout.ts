import ELK from 'elkjs/lib/elk-api.js'
import ElkWorker from 'elkjs/lib/elk-worker.min.js?worker'
import type { XYPosition } from '@xyflow/react'
import type { Branch, Tree } from './types'

export const cardWidth = 256
export const cardHeight = 104
export const branchKey = (id: string, branch: Branch) => `${id}:${branch}`

export function visibleTree(tree: Tree, collapsed: ReadonlySet<string>): Tree {
  if (!collapsed.size) return tree

  const parentChildEdges = tree.parentChildEdges.filter(edge =>
    !collapsed.has(branchKey(edge.parentId, 'children')) &&
    !collapsed.has(branchKey(edge.childId, 'parents')),
  )
  const adjacent = new Map(tree.nodes.map(node => [node.id, [] as string[]]))
  const connect = (a: string, b: string) => {
    adjacent.get(a)?.push(b)
    adjacent.get(b)?.push(a)
  }
  parentChildEdges.forEach(edge => connect(edge.parentId, edge.childId))
  tree.partnerships.forEach(edge => connect(edge.person1Id, edge.person2Id))

  const visible = new Set([tree.rootPersonId])
  for (const id of visible) adjacent.get(id)?.forEach(other => visible.add(other))
  return {
    ...tree,
    nodes: tree.nodes.filter(person => visible.has(person.id)),
    parentChildEdges: parentChildEdges.filter(edge => visible.has(edge.parentId) && visible.has(edge.childId)),
    partnerships: tree.partnerships.filter(edge => visible.has(edge.person1Id) && visible.has(edge.person2Id)),
  }
}

const elk = new ELK({ workerFactory: () => new ElkWorker() })

export async function layoutTree(tree: Tree): Promise<Map<string, XYPosition>> {
  const ids = tree.nodes.map(person => person.id).sort()
  const ancestry = tree.parentChildEdges.filter(relation => relation.type !== 'STEP')
  const graph = await elk.layout({
    id: 'root',
    layoutOptions: { 'elk.algorithm': 'layered', 'elk.direction': 'DOWN' },
    children: ids.map(id => ({ id, width: cardWidth, height: cardHeight })),
    edges: [...ancestry].sort((a, b) => a.id.localeCompare(b.id)).map(relation => ({
      id: relation.id, sources: [relation.parentId], targets: [relation.childId],
    })),
  })
  const elkX = new Map(graph.children?.map(node => [node.id, node.x ?? 0]))
  const horizontalOrder = (a: string, b: string) => (elkX.get(a) ?? 0) - (elkX.get(b) ?? 0) || a.localeCompare(b)

  // Group partners on a row only when the resulting ancestry graph remains acyclic.
  const groupOf = new Map(ids.map(id => [id, id]))
  const groups = new Map(ids.map(id => [id, [id]]))
  const partners = new Map(ids.map(id => [id, new Set<string>()]))
  tree.partnerships.forEach(union => {
    partners.get(union.person1Id)?.add(union.person2Id)
    partners.get(union.person2Id)?.add(union.person1Id)
  })
  function ancestryByGroup() {
    const outgoing = new Map([...groups.keys()].map(id => [id, new Set<string>()]))
    for (const edge of ancestry) {
      outgoing.get(groupOf.get(edge.parentId)!)!.add(groupOf.get(edge.childId)!)
    }
    return outgoing
  }
  let outgoing = ancestryByGroup()
  function reaches(start: string, target: string) {
    const seen = new Set([start])
    for (const id of seen) {
      if (id === target) return true
      outgoing.get(id)?.forEach(next => seen.add(next))
    }
    return false
  }
  for (const partnership of [...tree.partnerships].sort((a, b) =>
    a.person1Id.localeCompare(b.person1Id) || a.person2Id.localeCompare(b.person2Id),
  )) {
    const left = groupOf.get(partnership.person1Id)!
    const right = groupOf.get(partnership.person2Id)!
    if (left === right || reaches(left, right) || reaches(right, left)) continue
    const people = groups.get(right)!
    groups.get(left)!.push(...people)
    people.forEach(id => groupOf.set(id, left))
    groups.delete(right)
    outgoing = ancestryByGroup()
  }

  const incoming = new Map([...groups.keys()].map(id => [id, 0]))
  outgoing.forEach(next => next.forEach(id => incoming.set(id, incoming.get(id)! + 1)))
  const ranks = new Map([...groups.keys()].map(id => [id, 0]))
  const queue = [...groups.keys()].filter(id => incoming.get(id) === 0)
  for (let index = 0; index < queue.length; index++) {
    const group = queue[index]
    for (const next of outgoing.get(group) ?? []) {
      ranks.set(next, Math.max(ranks.get(next)!, ranks.get(group)! + 1))
      incoming.set(next, incoming.get(next)! - 1)
      if (incoming.get(next) === 0) queue.push(next)
    }
  }

  // Place groups connected only by STEP next to their established generation.
  // STEP never changes the rank of an ancestry group or the selected root.
  const anchored = new Set([groupOf.get(tree.rootPersonId)!])
  for (const [parent, children] of outgoing) {
    if (children.size > 0) {
      anchored.add(parent)
      children.forEach(child => anchored.add(child))
    }
  }
  const stepRanks = new Map<string, Set<number>>()
  function suggestStepRank(group: string, anchor: string, offset: number) {
    if (anchored.has(group) || !anchored.has(anchor)) return
    const suggestions = stepRanks.get(group) ?? new Set<number>()
    suggestions.add(ranks.get(anchor)! + offset)
    stepRanks.set(group, suggestions)
  }
  for (const edge of tree.parentChildEdges) {
    if (edge.type !== 'STEP') continue
    const parent = groupOf.get(edge.parentId)!
    const child = groupOf.get(edge.childId)!
    suggestStepRank(parent, child, -1)
    suggestStepRank(child, parent, 1)
  }
  for (const [group, suggestions] of stepRanks) {
    if (suggestions.size === 1) ranks.set(group, [...suggestions][0])
  }

  const rows = new Map<number, string[][]>()
  for (const [group, people] of groups) {
    const rank = ranks.get(group)!
    const row = rows.get(rank) ?? []
    const ordered = people.sort(horizontalOrder)
    const hub = ordered.reduce((first, next) => partners.get(next)!.size > partners.get(first)!.size ? next : first)
    if (ordered.length > 2 && partners.get(hub)!.size > 1) {
      ordered.splice(ordered.indexOf(hub), 1)
      ordered.splice(Math.ceil(ordered.length / 2), 0, hub)
    }
    row.push(ordered)
    rows.set(rank, row)
  }

  const positions = new Map<string, XYPosition>()
  for (const [rank, row] of rows) {
    row.sort((a, b) => horizontalOrder(a[0], b[0]))
    const cards = row.reduce((count, rowGroup) => count + rowGroup.length, 0)
    const width = cards * cardWidth + (cards - row.length) * 48 + (row.length - 1) * 76
    let x = -width / 2
    for (const people of row) {
      for (const person of people) {
        positions.set(person, { x, y: rank * (cardHeight + 104) })
        x += cardWidth + 48
      }
      x += 28
    }
  }
  return positions
}
