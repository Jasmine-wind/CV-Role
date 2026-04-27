export const AUTH_TOKEN_STORAGE_KEY = 'ai-resume-token'

export const readAuthToken = () => localStorage.getItem(AUTH_TOKEN_STORAGE_KEY) ?? ''

export const writeAuthToken = (token: string) => {
  localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token)
}

export const clearAuthToken = () => {
  localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
}
