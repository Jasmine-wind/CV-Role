/**
 * 确定性词级 Diff：由代码根据 originalText / suggestedText 生成，不依赖 AI。
 *
 * 分词规则：汉字逐字、拉丁/数字连续串按词、空白按段、其余符号逐字。
 * 使用 LCS 回溯生成 added / removed / equal 段；超长输入退化为整体替换展示，
 * 保证任何输入都有确定结果。
 */

export type DiffSegmentType = 'equal' | 'added' | 'removed'

export interface DiffSegment {
  type: DiffSegmentType
  text: string
}

/** 拉丁/数字按词，空白按段，其余逐字（汉字逐字以提升中文 Diff 精度）。 */
const TOKENIZER = /[A-Za-z0-9+#.-]+|\s+|[\s\S]/g

const MAX_LCS_PRODUCT = 400_000

export function tokenizeForDiff(text: string): string[] {
  return text.match(TOKENIZER) ?? []
}

export function diffText(original: string, next: string): DiffSegment[] {
  if (original === next) {
    return original ? [{ type: 'equal', text: original }] : []
  }
  if (!original) {
    return [{ type: 'added', text: next }]
  }
  if (!next) {
    return [{ type: 'removed', text: original }]
  }

  const left = tokenizeForDiff(original)
  const right = tokenizeForDiff(next)

  if (left.length * right.length > MAX_LCS_PRODUCT) {
    return [
      { type: 'removed', text: original },
      { type: 'added', text: next },
    ]
  }

  const cols = right.length + 1
  const table = new Int32Array((left.length + 1) * cols)
  const score = (row: number, col: number) => table[row * cols + col] ?? 0
  for (let i = left.length - 1; i >= 0; i -= 1) {
    for (let j = right.length - 1; j >= 0; j -= 1) {
      table[i * cols + j] =
        left[i] === right[j]
          ? score(i + 1, j + 1) + 1
          : Math.max(score(i + 1, j), score(i, j + 1))
    }
  }

  const segments: DiffSegment[] = []
  const push = (type: DiffSegmentType, text: string) => {
    const last = segments[segments.length - 1]
    if (last && last.type === type) {
      last.text += text
      return
    }
    segments.push({ type, text })
  }

  let i = 0
  let j = 0
  while (i < left.length && j < right.length) {
    const leftToken = left[i] ?? ''
    const rightToken = right[j] ?? ''
    if (leftToken === rightToken) {
      push('equal', leftToken)
      i += 1
      j += 1
    } else if (score(i + 1, j) >= score(i, j + 1)) {
      push('removed', leftToken)
      i += 1
    } else {
      push('added', rightToken)
      j += 1
    }
  }
  while (i < left.length) {
    push('removed', left[i] ?? '')
    i += 1
  }
  while (j < right.length) {
    push('added', right[j] ?? '')
    j += 1
  }
  return segments
}
