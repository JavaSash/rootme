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
  biography: string | null
  deathCountry: string | null
  deathRegion: string | null
  deathCity: string | null
  occupation: string | null
  burialPlace: string | null
}

export interface Person extends PersonFields {
  id: string
}

export type PersonSummary = Pick<Person, 'id' | 'firstName' | 'lastName' | 'patronymic' | 'gender' | 'birthDate' | 'deathDate'
  | 'birthSurname' | 'birthCountry' | 'birthRegion' | 'birthCity'> & { primaryPhoto?: PhotoSummary | null }

export interface PhotoSummary {
  id: string
  thumbnailUrl: string
}

export interface PhotoFields {
  caption: string | null
  takenDate: string | null
}

export interface PersonPhoto extends PhotoSummary, PhotoFields {
  originalFileName: string
  originalUrl: string
  isPrimary: boolean
}

export type AlternativeNameType = 'FORMER_NAME' | 'NICKNAME' | 'ALTERNATIVE_SPELLING' | 'OTHER'
export interface AlternativeNameFields { name: string; type: AlternativeNameType }
export interface AlternativeName extends AlternativeNameFields { id: string }
export interface PersonPage { items: PersonSummary[]; page: number; size: number; total: number }

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
  alternativeNames: AlternativeName[]
  primaryPhoto: PhotoSummary | null
}

export interface HiddenBranches {
  parents: boolean
  children: boolean
  stepParents: boolean
  stepChildren: boolean
  partners: boolean
  nextAncestorDepth: number | null
  nextDescendantDepth: number | null
}

export interface Tree {
  rootPersonId: string
  nodes: PersonSummary[]
  parentChildEdges: ParentChild[]
  partnerships: Partnership[]
  hiddenBranches: Record<string, HiddenBranches | undefined>
  maxDepth: number
  ancestorDepth: number
  descendantDepth: number
}

export interface Depth {
  ancestors: number
  descendants: number
}
