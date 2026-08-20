import { describe, expect, it } from 'vitest'
import { diffText, tokenizeForDiff } from '@/utils/diffText'

describe('diffText', () => {
  it('returns equal segment for identical text', () => {
    expect(diffText('负责订单服务开发', '负责订单服务开发')).toEqual([
      { type: 'equal', text: '负责订单服务开发' },
    ])
  })

  it('shows removed and added segments for rewording', () => {
    const segments = diffText('负责后台接口开发', '基于 Spring Boot 完成核心业务接口开发')
    const removed = segments.filter((segment) => segment.type === 'removed').map((s) => s.text).join('')
    const added = segments.filter((segment) => segment.type === 'added').map((s) => s.text).join('')
    const equal = segments.filter((segment) => segment.type === 'equal').map((s) => s.text).join('')

    expect(equal).toContain('接口开发')
    expect(removed.length).toBeGreaterThan(0)
    expect(added).toContain('Spring Boot')
  })

  it('keeps latin words as whole tokens and splits han chars', () => {
    expect(tokenizeForDiff('使用 Redis 缓存')).toEqual(['使', '用', ' ', 'Redis', ' ', '缓', '存'])
  })

  it('handles empty sides deterministically', () => {
    expect(diffText('', '新增')).toEqual([{ type: 'added', text: '新增' }])
    expect(diffText('删除', '')).toEqual([{ type: 'removed', text: '删除' }])
    expect(diffText('', '')).toEqual([])
  })

  it('falls back to whole-block diff for oversized input', () => {
    const original = '甲'.repeat(1000)
    const next = '乙'.repeat(1000)
    const segments = diffText(original, next)

    expect(segments).toEqual([
      { type: 'removed', text: original },
      { type: 'added', text: next },
    ])
  })

  it('is deterministic across repeated calls', () => {
    const first = diffText('参与订单模块开发，使用 Redis', '使用 Redis 参与订单模块开发')
    const second = diffText('参与订单模块开发，使用 Redis', '使用 Redis 参与订单模块开发')
    expect(first).toEqual(second)
  })
})
