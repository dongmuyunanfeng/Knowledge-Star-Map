// Agent会话
export interface AgentSessionDTO {
  sessionId: string
  userId: number
  query: string
  rounds?: AgentRoundDTO[]
  thoughtList?: string[]
  toolCalls?: ToolCallDTO[]
  toolObservations?: ToolObservationDTO[]
  roundNo?: number
  isPinned: number
  status: string
  finalAnswer?: string
  createTime: string
  historyMessages?: Array<{ role: string; content: string }>
}

export interface AgentRoundDTO {
  roundNo: number
  agentThought: string
  toolCalls: ToolCallDTO[]
  toolObservations: ToolObservationDTO[]
}

export interface ToolCallSummaryDTO {
  toolName: string
  success: boolean
  callCount: number
  errorMessage?: string | null
}

export interface ToolCallDTO {
  toolName: string
  parameters: Record<string, any>
  success: boolean
  errorMessage?: string
}

export interface ToolObservationDTO {
  toolName: string
  result: any
  success: boolean
}

export interface AgentSessionCreateParams {
  query: string
}

export interface AgentSessionContinueParams {
  query: string
}

// 知识点
export interface KnowledgeInfoVO {
  id: number
  knowledgeName: string
  knowledgeDomain?: string
  knowledgeTag: string
  projectId?: number
  projectName?: string
  knowledgeContent: string
  completeContent?: string
  masteryLevel: number
  masteryScore: number
  isCompleted: number
  sourceType: number
  contentSegment?: string
  fileSources?: FileKnowledgeVO[]
  suggestions?: KnowledgeSuggestionVO[]
}

export interface FileKnowledgeVO {
  knowledgeId?: number
  knowledgeName?: string
  fileId: number
  fileName: string
  filePath: string
  contentSegment?: string
  segmentStart?: number
  segmentEnd?: number
}

// 知识补全建议
export interface KnowledgeSuggestionVO {
  id: number
  knowledgeId: number
  suggestionType: number
  suggestionTitle: string
  suggestionContent: string
  suggestionReason?: string
  status: number
  createTime: string
}

// 星图
export interface KnowledgeStarMapVO {
  id: number
  domainName: string
  knowledgeCount: number
  masteryScore: number
  weakFlag: number
  xCoordinate: number
  yCoordinate: number
  weightFactor: number
}

export interface StarMapStatsVO {
  totalKnowledgeCount: number
  averageMasteryScore: number
  domains: KnowledgeStarMapVO[]
}

export interface ProjectStarMapVO {
  id: number
  projectName: string
  knowledgeCount: number
  masteryScore: number
  weakFlag: number
  xCoordinate: number
  yCoordinate: number
}

// 学习规划
export interface StudyPlanVO {
  id: number
  planTitle: string
  planType: number
  planDesc: string
  targetNeed: string
  waitKnowledge: WaitKnowledgeVO[]
  priority: number
  finishStatus: number
  progressRate: number
  createTime: string
}

export interface WaitKnowledgeVO {
  id: number
  name: string
  tag: string
  priority: number
  schedule: string
  learnContent: string
}

export interface StudyPlanCreateDTO {
  planTitle: string
  planType: number
  planDesc?: string
  targetNeed: string
  priority?: number
}

export interface StudyPlanUpdateDTO {
  planTitle?: string
  planDesc?: string
  priority?: number
}

// 简历素材
export interface ResumeMaterialVO {
  techStack: string
  skillDesc: string
  projectHighlights: string
  resumeSummary: string
}

export interface ResumeGenerateDTO {
  forceRegenerate?: boolean
}

// 检索历史
export interface SearchHistoryVO {
  id: number
  keyword: string
  resultCount: number
  createTime: string
}

// 用户
export interface UserVO {
  id: number
  username: string
  nickname: string
  avatar: string
  studyDirection: string
  jobTarget: string
  email?: string
}

export interface UserConfigVO {
  enableAutoKnowledgeSuggestion: boolean
}

export interface UserConfigDTO {
  enableAutoKnowledgeSuggestion: boolean
}

export interface UserLoginDTO {
  username: string
  password: string
}

export interface UserRegisterDTO {
  username: string
  password: string
  nickname?: string
  email?: string
  studyDirection?: string
  jobTarget?: string
}

export interface AuthResponse {
  token: string
}

// 项目信息
export interface ProjectInfoVO {
  id: number
  projectName: string
  projectDesc: string
  projectTechStack: string
  projectRole: string
  projectHighlights: string
  projectSourceFiles: string
  createTime: string
  updateTime: string
}

export interface ProjectCreateDTO {
  projectName: string
  projectDesc: string
  projectTechStack: string
  projectRole: string
  projectHighlights: string
  sourceFileIds: string
}

export interface ProjectUpdateDTO {
  projectName?: string
  projectDesc?: string
  projectTechStack?: string
  projectRole?: string
  projectHighlights?: string
}

// 项目经历
export interface ProjectExperienceVO {
  projectId: number
  projectName: string
  projectDesc: string
  projectTechStack: string
  responsibilities: string
  generateStatus: number
  updateTime: string
}

// 领域
export interface DomainVO {
  domainName: string
}

export interface CreateDomainDTO {
  domainName: string
}

// 查询参数
export interface KnowledgeQueryDTO {
  keyword?: string
  tag?: string
  masteryLevel?: number
  page?: number
  pageSize?: number
}

export interface KnowledgeQueryParam {
  keyword?: string
  domain?: string
  tag?: string
  projectId?: number
  masteryLevel?: number
  page?: number
  pageSize?: number
}

export interface FileUploadDTO {
  file: File
}

// 通用分页
export interface PageResult<T> {
  total: number
  pages: number
  page: number
  pageSize: number
  list: T[]
}

// 统一响应
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// 文件
export interface FileResourceVO {
  id: number
  userId: number
  fileName: string
  fileSuffix: string
  fileSize: number
  fileCategory: string
  parseStatus: number
  parseMessage?: string
  createTime: string
}

export interface KnowledgeSuggestionGenerateDTO {
  knowledgeId: number
  suggestionType: number
}
