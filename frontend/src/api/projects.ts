import {apiFetch} from './client'

/** Expense amounts are integers in minor units: the displayed value is `amount / 10 ** minorUnits`. */
export interface Currency {
  /** ISO 4217, e.g. PLN; shown as-is next to amounts */
  code: string
  minorUnits: number
}

export interface Project {
  id: string
  name: string
  ownerId: string
  currency: Currency
}

export interface ProjectMember {
  memberId: string
  name: string
}

export interface Invitation {
  projectId: string
  token: string
}

export interface InvitationPreview {
  projectId: string
  projectName: string
}

export const listProjects = () => apiFetch<Project[]>('/projects')

export const createProject = (name: string, currency: string) =>
    apiFetch<Project>('/projects', {method: 'POST', body: JSON.stringify({name, currency})})

export const listMembers = (projectId: string) => apiFetch<ProjectMember[]>(`/projects/${projectId}`)

/** Only the project owner may create invitations. Each token can be accepted once. */
export const createInvitation = (projectId: string) =>
    apiFetch<Invitation>(`/projects/${projectId}/invitations`, {method: 'POST'})

const invitationQuery = ({projectId, token}: Invitation) => new URLSearchParams({projectId, token}).toString()

export const previewInvitation = (invitation: Invitation) =>
    apiFetch<InvitationPreview>(`/invitations?${invitationQuery(invitation)}`)

export const acceptInvitation = (invitation: Invitation) =>
    apiFetch<void>(`/invitations?${invitationQuery(invitation)}`, {method: 'POST'})

/** Frontend URL that lands an invitee on the join page. */
export const invitationUrl = ({projectId, token}: Invitation) =>
    `${window.location.origin}/join/${projectId}/${token}`
