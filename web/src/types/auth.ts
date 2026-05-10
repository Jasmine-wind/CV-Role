export interface ApiResult<T> {
  code: number
  message: string
  data: T
  path?: string | null
  timestamp?: string | null
}

export interface LoginRequest {
  account: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  nickname?: string
}

export interface RegisterResponse {
  userId: number
}

export interface LoginResponse {
  userId: number
  username: string
  email: string
  nickname: string | null
  token: string
  tokenType: string
  expiresIn: number
}

export interface CurrentUser {
  id: number
  username: string
  email: string
  nickname: string | null
  createdAt: string
}
