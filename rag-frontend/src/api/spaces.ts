import { api } from './client'
import type { SpaceResponse, CreateSpaceRequest, AccessRuleInput } from './types'

export const spacesApi = {
  create: (req: CreateSpaceRequest) => api.post<SpaceResponse>('/spaces', req),
  list: () => api.get<SpaceResponse[]>('/spaces'),
  get: (spaceId: string) => api.get<SpaceResponse>(`/spaces/${spaceId}`),
  updateAccessRules: (spaceId: string, rules: AccessRuleInput[]) =>
    api.put<SpaceResponse>(`/spaces/${spaceId}/access-rules`, { rules }),
}
