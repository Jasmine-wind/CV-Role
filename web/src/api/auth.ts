import request from '@/api/request'
import type { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse } from '@/types/auth'

export const login = (payload: LoginRequest) => {
  return request.post<LoginResponse>('/api/auth/login', payload)
}

export const register = (payload: RegisterRequest) => {
  return request.post<RegisterResponse>('/api/auth/register', payload)
}
