import request from '@/api/request'
import type { OptimizationTask } from '@/types/optimization-task'
export const getRecentOptimizationTasks = (limit = 5) => request.get<OptimizationTask[]>('/api/optimization-tasks/recent', { params: { limit } })
