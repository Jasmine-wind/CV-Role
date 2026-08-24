import request from '@/api/request'
import type { JobDirectionInsights } from '@/types/job-direction-insight'

/** Retained formal Evidence is aggregated server-side; this endpoint never writes user data. */
export const getJobDirectionInsights = () =>
  request.get<JobDirectionInsights>('/api/job-direction-insights')
