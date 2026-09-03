import { expect, test, type Page } from '@playwright/test'

const readyResume = {
  id: 1,
  originalFilename: 'chinese-java-two-page.pdf',
  fileType: 'PDF',
  fileSize: 2 * 1024 * 1024,
  uploadStatus: 'SUCCESS',
  parseStatus: 'SUCCESS',
  qualityStatus: 'READY',
  canonicalReady: true,
  parseErrorMessage: null,
  createdAt: '2026-01-02T09:30:00Z',
}

const response = (data: unknown) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: 'success', data }),
})

const reviewResume = {
  ...readyResume,
  id: 2,
  originalFilename: 'product-analytics-review.docx',
  fileType: 'DOCX',
  qualityStatus: 'NEEDS_REVIEW',
}

async function mockShell(page: Page, resumes: unknown[]) {
  await page.addInitScript(() => {
    window.localStorage.setItem('ai-resume-token', 'resume-library-test-token')
  })
  await page.route('**/api/users/me', (route) => route.fulfill(response({
    id: 1,
    username: 'resume-test',
    email: 'resume@example.invalid',
    nickname: '简历库测试用户',
    createdAt: '2026-01-01T00:00:00Z',
  })))
  await page.route('**/api/resumes', (route) => route.fulfill(response(resumes)))
}

test.describe('Resume Library', () => {
  test('loads a full-width ledger with readable file, status and date columns', async ({ page }) => {
    await mockShell(page, [readyResume, { ...readyResume, id: 2, originalFilename: 'backend-general.docx', fileType: 'DOCX' }])
    await page.goto('/resumes')

    await expect(page.getByRole('heading', { name: '我的简历' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '简历库' })).toBeVisible()
    await expect(page.locator('.resume-library-summary')).toHaveText('2 份简历 · 2 份可用于岗位分析')
    await expect(page.locator('.resume-library-row')).toHaveCount(2)
    await expect(page.locator('.resume-library-row').first()).toContainText('chinese-java-two-page.pdf')
    await expect(page.locator('.resume-library-row').first()).toContainText('可用于岗位分析')
    await expect(page.locator('.resume-library-row').first()).toContainText('2026年01月02日')

    const layout = await page.locator('.resume-library-shell').evaluate((element) => {
      const shell = element.getBoundingClientRect()
      const main = element.querySelector('.resume-library-main')?.getBoundingClientRect()
      return { display: getComputedStyle(element).display, shellWidth: shell.width, mainWidth: main?.width ?? 0 }
    })
    expect(layout.display).toBe('block')
    expect(layout.mainWidth).toBeCloseTo(layout.shellWidth, 0)
  })

  test('opens the upload composer with secondary cancellation and selected file summary', async ({ page }) => {
    await mockShell(page, [readyResume])
    await page.goto('/resumes')

    const pageUpload = page.getByRole('button', { name: '上传简历', exact: true })
    await pageUpload.click()
    await expect(page.getByRole('heading', { name: '上传一份真实简历' })).toBeVisible()
    await expect(page.locator('.resume-upload-cancel')).toBeVisible()
    await expect(page.locator('.resume-upload-cancel')).not.toHaveClass(/el-button--primary/)
    await expect(page.getByRole('button', { name: '上传并准备' })).toBeDisabled()

    await page.locator('.resume-file-picker input').setInputFiles({
      name: 'candidate-long-name.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.alloc(2 * 1024 * 1024),
    })
    await expect(page.locator('.resume-file-picker strong')).toHaveText('candidate-long-name.pdf')
    await expect(page.locator('.resume-file-picker small')).toHaveText('PDF · 2.00 MB')
    await expect(page.getByRole('button', { name: '上传并准备' })).toBeEnabled()

    await page.getByRole('button', { name: '取消上传', exact: true }).last().click()
    await expect(page.getByRole('heading', { name: '上传一份真实简历' })).toBeHidden()
  })

  test('shows review, reprepare, failed and waiting states with their next actions', async ({ page }) => {
    await mockShell(page, [
      readyResume,
      reviewResume,
      { ...readyResume, id: 3, originalFilename: 'stale.pdf', canonicalReady: false },
      { ...readyResume, id: 4, originalFilename: 'broken.pdf', parseStatus: 'FAILED', parseErrorMessage: '文件无法读取' },
      { ...readyResume, id: 5, originalFilename: 'waiting.pdf', parseStatus: 'PROCESSING', qualityStatus: null },
    ])
    await page.goto('/resumes')

    await expect(page.locator('.resume-library-summary')).toHaveText('5 份简历 · 1 份可用于岗位分析 · 4 份需要处理')
    await expect(page.locator('.resume-library-row').nth(1)).toContainText('需要确认')
    await expect(page.locator('.resume-library-row').nth(1).getByRole('button', { name: /确认 product-analytics-review/ })).toBeVisible()
    await expect(page.locator('.resume-library-row').nth(2)).toContainText('需要重新准备')
    await expect(page.locator('.resume-library-row').nth(2).getByRole('button', { name: /重新准备 stale/ })).toBeVisible()
    await expect(page.locator('.resume-library-row').nth(3)).toContainText('准备失败')
    await expect(page.locator('.resume-library-row').nth(3)).toContainText('文件无法读取')
    await expect(page.locator('.resume-library-row').nth(3).getByRole('button', { name: /重试 broken/ })).toBeVisible()
    await expect(page.locator('.resume-library-row').nth(4)).toContainText('等待准备')
    await expect(page.locator('.resume-library-row').nth(4)).toContainText('尚未完成内容准备')
  })

  test('opens and closes the existing review inspector without leaving an empty rail', async ({ page }) => {
    await mockShell(page, [readyResume, reviewResume])
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response({
      resumeId: 2,
      qualityStatus: 'NEEDS_REVIEW',
      qualityIssues: null,
      unresolvedItems: '[]',
      canonicalDocument: '{"basics":{"name":"测试用户"}}',
    })))
    await page.goto('/resumes')

    const shell = page.locator('.resume-library-shell')
    await expect(shell).not.toHaveClass(/has-review-inspector/)
    await page.locator('.resume-library-row').nth(1).getByRole('button', { name: /确认 product-analytics-review/ }).click()
    await expect(shell).toHaveClass(/has-review-inspector/)
    await expect(page.locator('.resume-review-inspector')).toBeVisible()
    await expect(page.locator('.resume-library-row').nth(1)).toHaveAttribute('aria-current', 'true')
    await expect(page.locator('.resume-library-row').nth(1)).toContainText('正在确认')
    await expect(page.locator('.resume-library-row').nth(1)).not.toContainText('收起确认')

    await page.locator('.resume-review-close').click()
    await expect(shell).not.toHaveClass(/has-review-inspector/)
    await expect(page.locator('.resume-review-inspector')).toBeHidden()
  })

  test('keeps deletion behind an accessible menu and preserves the real impact warning', async ({ page }) => {
    await mockShell(page, [readyResume])
    await page.goto('/resumes')

    await page.locator('.resume-item-more summary').click()
    await page.getByRole('button', { name: '删除简历' }).click()
    const dialog = page.locator('.el-message-box')
    await expect(dialog).toContainText('相关分析结果也会删除')
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    await expect(dialog).toBeHidden()
  })

  test('keeps active preparation visible, blocks delete, and shows no fake percentage', async ({ page }) => {
    await mockShell(page, [{ ...readyResume, id: 8, originalFilename: 'pending.pdf', parseStatus: 'PENDING', qualityStatus: 'PENDING' }])
    await page.route('**/api/resumes/8/preparation', (route) => route.fulfill(response({
      taskId: 808,
      taskType: 'RESUME_PREPARATION',
      status: 'RUNNING',
      progress: 17,
      message: '正在读取工作经历',
    })))
    await page.route('**/api/tasks/808', (route) => route.fulfill(response({
      taskId: 808,
      taskType: 'RESUME_PREPARATION',
      status: 'RUNNING',
      progress: 17,
      message: '正在读取工作经历',
    })))
    await page.goto('/resumes')

    const row = page.locator('.resume-library-row')
    await expect(row).toContainText('正在准备')
    await expect(row).toContainText('正在读取工作经历')
    await expect(row).not.toContainText('17%')
    await row.locator('summary').click()
    await expect(row.getByRole('button', { name: '删除简历' })).toBeDisabled()
  })

  test('separates load failure from empty state and supports retry', async ({ page }) => {
    let attempts = 0
    await mockShell(page, [readyResume])
    await page.route('**/api/resumes', async (route) => {
      attempts += 1
      if (attempts === 1) {
        await route.abort('failed')
        return
      }
      await route.fulfill(response([readyResume]))
    })
    await page.goto('/resumes')

    await expect(page.getByRole('heading', { name: '我的简历' })).toBeVisible()
    await expect(page.getByText('简历列表加载失败')).toBeVisible()
    await expect(page.getByText('还没有简历')).toBeHidden()
    await page.getByRole('button', { name: '重新加载' }).click()
    await expect(page.locator('.resume-library-row')).toHaveCount(1)
  })

  test('shows a dedicated empty state and opens the upload composer', async ({ page }) => {
    await mockShell(page, [])
    await page.goto('/resumes')

    await expect(page.getByText('简历库', { exact: true })).toBeVisible()
    await expect(page.getByText('还没有简历', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '上传第一份简历' })).toBeVisible()
    await page.getByRole('button', { name: '上传第一份简历' }).click()
    await expect(page.getByRole('heading', { name: '上传一份真实简历' })).toBeVisible()
  })

  test('keeps review ACCEPT and DELETE payloads unchanged', async ({ page }) => {
    await mockShell(page, [reviewResume])
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response({
      resumeId: 2,
      qualityStatus: 'NEEDS_REVIEW',
      qualityIssues: null,
      unresolvedItems: JSON.stringify([
        { id: 'fragment-1', kind: 'TEXT_FRAGMENT', canonicalDraft: JSON.stringify({ text: '待确认内容' }), reason: '请确认内容' },
        { id: 'fragment-2', kind: 'TEXT_FRAGMENT', canonicalDraft: JSON.stringify({ text: '待删除内容' }), reason: '请确认内容' },
      ]),
      canonicalDocument: '{"basics":{"name":"测试用户"}}',
    })))
    const payloads: Array<Record<string, unknown>> = []
    await page.route('**/api/resumes/2/review/resolve', async (route) => {
      payloads.push(route.request().postDataJSON() as Record<string, unknown>)
      const remaining = payloads.length === 1
        ? [{ id: 'fragment-1', kind: 'TEXT_FRAGMENT', canonicalDraft: JSON.stringify({ text: '待确认内容' }), reason: '请确认内容' }]
        : []
      await route.fulfill(response({
        resumeId: 2,
        qualityStatus: remaining.length ? 'NEEDS_REVIEW' : 'READY',
        qualityIssues: null,
        unresolvedItems: remaining.length ? JSON.stringify(remaining) : '[]',
        canonicalDocument: '{"basics":{"name":"测试用户"}}',
      }))
    })
    await page.goto('/resumes')
    await page.getByRole('button', { name: /确认 product-analytics-review/ }).click()
    await page.locator('.resume-review-progress-item').nth(1).click()
    await page.getByRole('button', { name: '不加入简历' }).click()
    await expect.poll(() => payloads).toHaveLength(1)
    await page.getByRole('button', { name: '保留这段内容' }).click()
    await expect.poll(() => payloads).toHaveLength(2)
    expect(payloads).toEqual([
      { itemId: 'fragment-2', action: 'DELETE', name: '测试用户' },
      { itemId: 'fragment-1', action: 'ACCEPT', text: '待确认内容', name: '测试用户' },
    ])
  })

  test('keeps the list while a background refresh is in progress', async ({ page }) => {
    await mockShell(page, [readyResume])
    await page.goto('/resumes')
    await expect(page.locator('.resume-library-row')).toHaveCount(1)

    await page.route('**/api/resumes', async (route) => {
      if (route.request().method() === 'GET') {
        await new Promise((resolve) => setTimeout(resolve, 500))
        await route.fulfill(response([readyResume]))
        return
      }
      await route.fulfill(response({ ...readyResume, id: 9, originalFilename: 'uploaded.pdf', preparationTaskId: null }))
    })
    await page.getByRole('button', { name: '上传简历', exact: true }).click()
    await page.locator('.resume-file-picker input').setInputFiles({
      name: 'uploaded.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('resume'),
    })
    await page.getByRole('button', { name: '上传并准备' }).click()
    await expect(page.locator('.resume-library-row')).toHaveCount(1)
    await expect(page.locator('.resume-library-refreshing')).toContainText('正在更新简历库')
  })
})
