const isPlainObject = (value: unknown): value is Record<string, unknown> => {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

export const hasDisplayValue = (value: unknown): boolean => {
  if (value === null || value === undefined) {
    return false
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    return normalized !== '' && normalized !== '-'
  }

  if (typeof value === 'number') {
    return Number.isFinite(value)
  }

  if (typeof value === 'boolean') {
    return true
  }

  if (Array.isArray(value)) {
    return value.some((item) => hasDisplayValue(item))
  }

  if (isPlainObject(value)) {
    return Object.values(value).some((item) => hasDisplayValue(item))
  }

  return false
}

export const nonEmptyArray = <T>(value: unknown): T[] => {
  if (!Array.isArray(value)) {
    return []
  }

  return value.filter((item) => hasDisplayValue(item)) as T[]
}

export const getFirstValidText = (...values: unknown[]): string => {
  for (const value of values) {
    if (!hasDisplayValue(value)) {
      continue
    }

    if (typeof value === 'string') {
      return value.trim()
    }

    return String(value)
  }

  return ''
}

export const truncateText = (text: string | null | undefined, length = 120) => {
  const value = text?.trim().replace(/\s+/g, ' ') || ''
  if (!value) {
    return ''
  }

  if (value.length <= length) {
    return value
  }

  return `${value.slice(0, length)}...`
}

export const splitTextSegments = (text: string | null | undefined) => {
  const value = text?.trim() || ''
  if (!value) {
    return []
  }

  return value
    .split(/[\n。！？!?；;]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

export const parseJsonValue = <T>(value: unknown): T | null => {
  if (!hasDisplayValue(value)) {
    return null
  }

  if (isPlainObject(value) || Array.isArray(value)) {
    return value as T
  }

  if (typeof value !== 'string') {
    return null
  }

  try {
    return JSON.parse(value) as T
  } catch {
    return null
  }
}

export const toTextArray = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value
      .map((item) => (typeof item === 'string' ? item.trim() : hasDisplayValue(item) ? String(item).trim() : ''))
      .filter((item) => item.length > 0)
  }

  if (typeof value === 'string') {
    const normalized = value.trim()
    if (normalized.startsWith('[') || normalized.startsWith('{')) {
      const parsed = parseJsonValue<unknown>(normalized)
      if (Array.isArray(parsed)) {
        return toTextArray(parsed)
      }
    }

    return splitTextSegments(normalized)
  }

  if (hasDisplayValue(value)) {
    return [String(value)]
  }

  return []
}

export const toRecordArray = (value: unknown): Record<string, unknown>[] => {
  if (Array.isArray(value)) {
    return value.filter((item): item is Record<string, unknown> => isPlainObject(item))
  }

  if (typeof value === 'string') {
    const parsed = parseJsonValue<unknown>(value.trim())
    if (Array.isArray(parsed)) {
      return parsed.filter((item): item is Record<string, unknown> => isPlainObject(item))
    }
  }

  return []
}
