import { describe, expect, it, vi } from 'vitest'
import type { ParentChild, ParentChildType, Partnership, PersonSummary, Tree } from '../src/types'
import { branchKey, cardHeight, cardWidth, layoutTree, visibleTree } from '../src/treeLayout'

vi.mock('elkjs/lib/elk-api.js', async () => {
  const { default: ELK } = await import('elkjs/lib/elk.bundled.js')
  return { default: function NodeElk() { return new ELK() } }
})
vi.mock('elkjs/lib/elk-worker.min.js?worker', () => ({ default: class UnusedBrowserWorker {} }))

function edge(parentId: string, childId: string, type: ParentChildType = 'BIOLOGICAL'): ParentChild {
  return { id: `${parentId}-${childId}-${type}`, parentId, childId, type, createdAt: '' }
}

function tree(ids: string[], parentChildEdges: ParentChild[], partnerships: Partnership[] = []): Tree {
  const nodes: PersonSummary[] = ids.map(id => ({
    id, firstName: id, lastName: null, patronymic: null, gender: 'UNKNOWN',
    birthDate: null, deathDate: null, birthSurname: null,
    birthCountry: null, birthRegion: null, birthCity: null,
  }))
  return {
    rootPersonId: 'root', nodes, parentChildEdges, partnerships,
    maxDepth: 10, ancestorDepth: 5, descendantDepth: 5, hiddenBranches: {},
  }
}

const union = (id: string, person2Id: string): Partnership => ({
  id, person1Id: 'root', person2Id, type: 'MARRIAGE', status: 'ENDED', startDate: null, endDate: null,
})

describe('tree layout', () => {
  it('lays out multiple unions, a shared ancestor and STEP context without overlapping cards', async () => {
    const ancestry = [
      edge('shared', 'left'), edge('shared', 'right'), edge('left', 'root'), edge('right', 'root', 'ADOPTIVE'),
      edge('root', 'child-a'), edge('root', 'child-b'), edge('partner-a', 'child-a'), edge('partner-b', 'child-b'),
    ]
    const nodes = ['root', 'shared', 'left', 'right', 'partner-a', 'partner-b', 'child-a', 'child-b', 'step-child', 'step-parent']
    const graph = tree(nodes, [...ancestry, edge('root', 'step-child', 'STEP'), edge('step-parent', 'root', 'STEP')],
      [union('union-a', 'partner-a'), union('union-b', 'partner-b'), union('old-union', 'partner-a')])
    const positions = await layoutTree(graph)
    expect([...positions.keys()].sort()).toEqual([...nodes].sort())
    expect(await layoutTree(graph)).toEqual(positions)
    for (const { parentId, childId } of ancestry) {
      expect(positions.get(parentId)!.y).toBeLessThan(positions.get(childId)!.y)
    }
    expect(positions.get('root')!.y).toBe(positions.get('partner-a')!.y)
    expect(positions.get('root')!.y).toBe(positions.get('partner-b')!.y)
    expect(positions.get('step-parent')!.y).toBeLessThan(positions.get('root')!.y)
    expect(positions.get('step-child')!.y).toBeGreaterThan(positions.get('root')!.y)
    for (const [id, position] of positions) {
      for (const [otherId, other] of positions) {
        if (id !== otherId) {
          expect(Math.abs(position.x - other.x) >= cardWidth || Math.abs(position.y - other.y) >= cardHeight).toBe(true)
        }
      }
    }
  })

  it('places a single known parent over a child', async () => {
    const positions = await layoutTree(tree(['root', 'child'], [edge('root', 'child')]))
    expect(positions.get('root')!.y).toBeLessThan(positions.get('child')!.y)
  })
})

describe('collapsed tree branches', () => {
  it('hides a closed branch, preserves relatives reachable by another path, and can be expanded again', () => {
    const graph = tree(['root', 'left', 'right', 'shared', 'left-only'], [
      edge('left', 'root'), edge('right', 'root'), edge('shared', 'left'),
      edge('shared', 'right'), edge('left-only', 'left'),
    ])
    const collapsed = visibleTree(graph, new Set([branchKey('left', 'parents')]))
    expect(collapsed.nodes.map(node => node.id)).toEqual(['root', 'left', 'right', 'shared'])
    expect(collapsed.parentChildEdges).toEqual([
      edge('left', 'root'), edge('right', 'root'), edge('shared', 'right'),
    ])
    expect(visibleTree(graph, new Set())).toEqual(graph)
  })

  it('hides a subtree without removing a visible partner', () => {
    const graph = tree(['root', 'child', 'grandchild', 'partner'], [edge('root', 'child'), edge('child', 'grandchild')],
      [union('union', 'partner')])
    const collapsed = visibleTree(graph, new Set([branchKey('root', 'children')]))
    expect(collapsed.nodes.map(node => node.id)).toEqual(['root', 'partner'])
    expect(collapsed.parentChildEdges).toEqual([])
    expect(collapsed.partnerships).toEqual(graph.partnerships)
  })
})
