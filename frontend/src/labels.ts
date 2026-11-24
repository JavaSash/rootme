import type { Gender, ParentChildType, PartnershipStatus, PartnershipType, PersonSummary } from './types'

export const genderLabels: Record<Gender, string> = { MALE: 'Мужской', FEMALE: 'Женский', UNKNOWN: 'Неизвестен' }
export const parentChildLabels: Record<ParentChildType, string> = {
  BIOLOGICAL: 'Биологическое', ADOPTIVE: 'Приёмное', STEP: 'Неродное',
}
export const partnershipLabels: Record<PartnershipType, string> = { MARRIAGE: 'Брак', PARTNERSHIP: 'Партнёрство' }
export const statusLabels: Record<PartnershipStatus, string> = {
  ACTIVE: 'Текущий союз', ENDED: 'Бывший союз', UNKNOWN: 'Статус неизвестен',
}

export function fullName(person: PersonSummary) {
  return [person.lastName, person.firstName, person.patronymic].filter(Boolean).join(' ')
}

export function lifeYears(person: PersonSummary) {
  const birth = person.birthDate?.slice(0, 4)
  const death = person.deathDate?.slice(0, 4)
  return birth && death ? `${birth} — ${death}` : birth ? `р. ${birth}` : death ? `ум. ${death}` : ''
}
