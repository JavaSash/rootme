import type { Branch, Depth, HiddenBranches, Tree } from './types'

export function treeAtDepth(tree: Tree | undefined, depth: Depth) {
  return tree?.ancestorDepth === depth.ancestors && tree.descendantDepth === depth.descendants ? tree : undefined
}

export function hiddenBranchAction(
  hidden: HiddenBranches | undefined, branch: Branch, collapsed: boolean, isRoot: boolean,
) {
  if (collapsed) return null
  const parents = branch === 'parents'
  const hasHidden = parents ? hidden?.parents || hidden?.stepParents : hidden?.children || hidden?.stepChildren
  if (!hasHidden) return null

  const depth = parents ? hidden?.nextAncestorDepth : hidden?.nextDescendantDepth
  if (depth != null) return { kind: 'depth', depth } as const
  return { kind: isRoot ? 'notice' : 'root' } as const
}
