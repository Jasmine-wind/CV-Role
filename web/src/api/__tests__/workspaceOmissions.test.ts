import { beforeEach, describe, expect, it, vi } from 'vitest'

const { postMock } = vi.hoisted(() => ({ postMock: vi.fn() }))

vi.mock('@/api/request', () => ({
  default: {
    get: vi.fn(),
    post: postMock,
    put: vi.fn(),
    delete: vi.fn(),
  },
  downloadBlob: vi.fn(),
  downloadPdfResponse: vi.fn(),
}))

import { confirmWorkspaceSourceOmissions, unconfirmWorkspaceSourceOmissions } from '@/api/workspace'

const request = {
  expectedRevision: 7,
  sourceOccurrenceIds: ['occ-project-1', 'occ-project-2'],
}

describe('workspace source omission API', () => {
  beforeEach(() => postMock.mockReset())

  it('posts an exact occurrence set to the confirm CAS endpoint', async () => {
    const result = { saved: true, conflict: false, revision: 8, document: null }
    postMock.mockResolvedValueOnce(result)

    await expect(confirmWorkspaceSourceOmissions(42, request)).resolves.toBe(result)
    expect(postMock).toHaveBeenCalledWith('/api/workspace/42/source-omissions/confirm', request)
  })

  it('posts the same CAS shape to the unconfirm endpoint', async () => {
    const result = { saved: true, conflict: false, revision: 8, document: null }
    postMock.mockResolvedValueOnce(result)

    await expect(unconfirmWorkspaceSourceOmissions(42, request)).resolves.toBe(result)
    expect(postMock).toHaveBeenCalledWith('/api/workspace/42/source-omissions/unconfirm', request)
  })
})
