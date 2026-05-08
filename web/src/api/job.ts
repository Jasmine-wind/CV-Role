import request from '@/api/request'
import type { JobDetail, JobListItem, JobMatchResult } from '@/types/job'

export const getJobList = () => {
  return request.get<JobListItem[]>('/api/jobs')
}

export const getJobDetail = (id: number) => {
  return request.get<JobDetail>(`/api/jobs/${id}`)
}

export const matchResumeToJob = (resumeId: number, jobId: number) => {
  return request.post<JobMatchResult>(`/api/resumes/${resumeId}/job-matches`, { jobId })
}

export const getResumeJobMatches = (resumeId: number) => {
  return request.get<JobMatchResult[]>(`/api/resumes/${resumeId}/job-matches`)
}
