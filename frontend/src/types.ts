export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN'
export type ParentChildType = 'BIOLOGICAL' | 'ADOPTIVE' | 'STEP'
export type PartnershipType = 'MARRIAGE' | 'PARTNERSHIP'
export type PartnershipStatus = 'ACTIVE' | 'ENDED' | 'UNKNOWN'
export type RelativeRole = 'PARENT' | 'CHILD' | 'PARTNER'
export type Branch = 'parents' | 'children'

export interface PersonFields {
  firstName: string
  lastName: string | null
  patronymic: string | null
  gender: Gender
  birthDate: string | null
  deathDate: string | null
  birthCountry: string | null
  birthRegion: string | null
  birthCity: string | null
  birthSurname: string | null
  notes: string | null
}

export interface Person extends PersonFields {
  id: string
}

export type PersonSummary = Pick<Person, 'id' | 'firstName' | 'lastName' | 'patronymic' | 'gender' | 'birthDate' | 'deathDate'>

export interface ParentChild {
  id: string
  parentId: string
  childId: string
  type: ParentChildType
  createdAt: string
}

export interface PartnershipFields {
  type: PartnershipType
  status: PartnershipStatus
  startDate: string | null
  endDate: string | null
  allowRepeat?: boolean
}

export interface Partnership extends Omit<PartnershipFields, 'allowRepeat'> {
  id: string
  person1Id: string
  person2Id: string
}

export type RelativeRequest = (
  | { role: 'PARENT' | 'CHILD'; parentChildType: ParentChildType }
  | { role: 'PARTNER'; partnership: PartnershipFields }
) & ({ person: PersonFields } | { existingPersonId: string })

export interface PersonDetails {
  person: Person
  relatives: PersonSummary[]
  parentChildEdges: ParentChild[]
  partnerships: Partnership[]
}

export interface HiddenBranches {
  parents: boolean
  children: boolean
  stepParents: boolean
  stepChildren: boolean
  partners: boolean
}

export interface Tree {
  rootPersonId: string
  nodes: PersonSummary[]
  parentChildEdges: ParentChild[]
  partnerships: Partnership[]
  hiddenBranches: Record<string, HiddenBranches | undefined>
}

export interface Depth {
  ancestors: number
  descendants: number
}
