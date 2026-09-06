import request from '@/utils/request'
import type {
  AgentSessionCreateParams,
  AgentSessionContinueParams,
  AgentSessionDTO,
  ToolCallSummaryDTO,
  KnowledgeQueryDTO,
  KnowledgeSuggestionGenerateDTO,
  StudyPlanCreateDTO,
  StudyPlanUpdateDTO,
  ResumeGenerateDTO,
  CreateDomainDTO,
  KnowledgeQueryParam,
  StarMapStatsVO,
  UserVO,
  UserConfigVO,
  UserConfigDTO,
  UserLoginDTO,
  UserRegisterDTO,
  ProjectInfoVO,
  ProjectCreateDTO,
  ProjectUpdateDTO,
  ProjectExperienceVO,
  FileResourceVO,
  PageResult
} from '@/types'

// Agent会话
export function createAgentSession(data: AgentSessionCreateParams) {
  return request.post<{ data: { sessionId: string; finalAnswer: string; roundCount: number; toolCallSummary: ToolCallSummaryDTO[] } }>('/agent/sessions', data)
}

export function continueAgentSession(sessionId: string, data: AgentSessionContinueParams) {
  return request.post<{ data: { sessionId: string; finalAnswer: string; roundCount: number; toolCallSummary: ToolCallSummaryDTO[] } }>(`/agent/sessions/${sessionId}/continue`, data)
}

export function deleteAgentSession(sessionId: string) {
  return request.delete<{ data: null }>(`/agent/sessions/${sessionId}`)
}

export function pinAgentSession(sessionId: string) {
  return request.post<{ data: null }>(`/agent/sessions/${sessionId}/pin`)
}

export function unpinAgentSession(sessionId: string) {
  return request.delete<{ data: null }>(`/agent/sessions/${sessionId}/pin`)
}

export function getAgentSession(sessionId: string) {
  return request.get<{ data: AgentSessionDTO }>(`/agent/sessions/${sessionId}`)
}

export function listAgentSessions(page = 1, pageSize = 10) {
  return request.get<{ data: PageResult<AgentSessionDTO> }>(`/agent/sessions?page=${page}&pageSize=${pageSize}`)
}

// 文件
export const fileApi = {
  upload: (formData: FormData) => request.post<{ data: FileResourceVO }>('/files/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  list: (params: any) => request.get<{ data: PageResult<FileResourceVO> }>('/files', { params }),
  get: (id: number) => request.get<{ data: FileResourceVO }>(`/files/${id}`),
  getKnowledge: (id: number) => request.get<{ data: any[] }>(`/files/${id}/knowledge`),
  download: (id: number) => request.get<{ data: any }>(`/files/${id}/download`, { responseType: 'blob' as any }),
  delete: (id: number) => request.delete<{ data: null }>(`/files/${id}`),
  parse: (id: number) => request.post<{ data: any }>(`/files/${id}/parse`),
  updateCategory: (id: number, fileCategory: string) => request.put<{ data: FileResourceVO }>(`/files/${id}/category`, { fileCategory }),
}

// 知识点
export const knowledgeApi = {
  list: (params: any) => request.get<{ data: PageResult<any> }>('/knowledge', { params }),
  get: (id: number) => request.get<{ data: any }>(`/knowledge/${id}`),
  create: (data: any) => request.post<{ data: any }>('/knowledge', data),
  update: (id: number, data: any) => request.put<{ data: any }>(`/knowledge/${id}`, data),
  delete: (id: number) => request.delete<{ data: null }>(`/knowledge/${id}`),
  triggerSuggestion: (id: number, suggestionType?: number) => request.post<{ data: any }>(`/knowledge/${id}/suggestions`, { knowledgeId: id, suggestionType }),
  tags: () => request.get<{ data: string[] }>('/knowledge/tags'),
  domains: () => request.get<{ data: string[] }>('/knowledge/domains'),
}

// 知识检索
export const searchApi = {
  knowledge: (params: any) => request.get<{ data: PageResult<any> }>('/search/knowledge', { params }),
}

// 检索历史
export const searchHistoryApi = {
  list: (params?: any) => request.get<{ data: PageResult<any> }>('/search/history', { params }),
  delete: (id: number) => request.delete<{ data: null }>(`/search/history/${id}`),
}

// 补全建议
export const suggestionApi = {
  list: (knowledgeId: number, page = 1, pageSize = 20) =>
    request.get<{ data: PageResult<any> }>(`/knowledge/suggestions?knowledgeId=${knowledgeId}&page=${page}&pageSize=${pageSize}`),
  approve: (id: number) => request.post<{ data: null }>(`/knowledge/suggestions/${id}/approve`),
  reject: (id: number) => request.post<{ data: null }>(`/knowledge/suggestions/${id}/reject`),
}

// 星图
export const starMapApi = {
  listDomains: () => request.get<{ data: any[] }>('/star-map/domains'),
  listProjects: () => request.get<{ data: any[] }>('/star-map/projects'),
  getStats: () => request.get<{ data: StarMapStatsVO }>('/star-map/stats'),
  recalculate: () => request.post<{ data: null }>('/star-map/recalculate'),
}

// 领域
export const domainApi = {
  list: () => request.get<{ data: { domainNames: string[] } }>('/domains'),
  create: (domainName: string) => request.post<{ data: null }>('/domains', { domainName }),
}

// 学习规划
export const studyPlanApi = {
  list: () => request.get<{ data: PageResult<any> }>('/study-plans'),
  get: (id: number) => request.get<{ data: any }>(`/study-plans/${id}`),
  create: (data: StudyPlanCreateDTO) => request.post<{ data: any }>('/study-plans', data),
  update: (id: number, data: StudyPlanUpdateDTO) => request.put<{ data: any }>(`/study-plans/${id}`, data),
  delete: (id: number) => request.delete<{ data: null }>(`/study-plans/${id}`),
  refreshProgress: (id: number) => request.post<{ data: null }>(`/study-plans/${id}/progress`),
  complete: (id: number) => request.post<{ data: null }>(`/study-plans/${id}/complete`),
}

// 简历
export const resumeApi = {
  get: () => request.get<{ data: any }>('/resume'),
  generate: (forceRegenerate = false) => request.post<{ data: any }>('/resume/generate', { forceRegenerate }),
  listTemplates: () => request.get<{ data: any[] }>('/resume/templates'),
  saveTemplate: (data: any) => request.post<{ data: any }>('/resume/templates', data),
  deleteTemplate: (id: number) => request.delete<{ data: null }>(`/resume/templates/${id}`),
}

// 项目
export const projectApi = {
  list: (params: any) => request.get<{ data: PageResult<ProjectInfoVO> }>('/projects', { params }),
  get: (id: number) => request.get<{ data: ProjectInfoVO }>(`/projects/${id}`),
  getKnowledge: (id: number) => request.get<{ data: any[] }>(`/projects/${id}/knowledge`),
  create: (data: ProjectCreateDTO) => request.post<{ data: ProjectInfoVO }>('/projects', data),
  update: (id: number, data: ProjectUpdateDTO) => request.put<{ data: ProjectInfoVO }>(`/projects/${id}`, data),
  delete: (id: number) => request.delete<{ data: null }>(`/projects/${id}`),
}

// 项目经历
export const projectExperienceApi = {
  list: () => request.get<{ data: ProjectExperienceVO[] }>('/projects/experiences'),
  regenerate: (projectId: number) => request.post<{ data: ProjectExperienceVO }>(`/projects/${projectId}/experience`),
}

// 用户认证
export const userApi = {
  login: (data: UserLoginDTO) => request.post<{ data: string }>('/auth/login', data),
  register: (data: UserRegisterDTO) => request.post<{ data: string }>('/auth/register', data),
  getMe: () => request.get<{ data: UserVO }>('/auth/me'),
  updateMe: (data: any) => request.patch<{ data: UserVO }>('/auth/me', data),
  getConfig: () => request.get<{ data: UserConfigVO }>('/user/config'),
  updateConfig: (data: UserConfigDTO) => request.post<{ data: null }>('/user/config', data),
}
