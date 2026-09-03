import { expect, test, type Page } from '@playwright/test'

const response = (data: unknown) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: 'success', data }),
})

const document = {
  schemaVersion: 'RESUME_DOCUMENT_V1',
  basics: {
    name: 'Alex Chen',
    jobIntention: 'Java 后端工程师',
    highestEducation: '硕士',
    contacts: [{ id: 'email-1', type: 'EMAIL', label: null, value: 'alex@example.com' }],
  },
  sections: [
    {
      id: 'experience',
      kind: 'EXPERIENCE',
      title: '工作经历',
      entries: [{
        id: 'exp-1', organization: '某科技公司', role: '后端工程师', school: null, degree: null,
        major: null, startDate: '2021', endDate: '2024', location: null, group: null, skillItems: null,
        bullets: Array.from({ length: 12 }, (_, index) => ({ id: `bullet-${index}`, text: `负责 Java 后端服务与 Redis 缓存优化工作 ${index + 1}` })),
      }],
    },
  ],
}

const requirement = (id: number, level: string) => ({
  evidenceRequirementId: id,
  requirementText: id === 3 ? '具备 Redis 缓存设计经验' : `岗位要求 ${id}`,
  importance: 'REQUIRED',
  matchLevel: level,
  conclusion: level === 'PARTIAL_EVIDENCE' ? '存在相关材料，但当前表达还不完整。' : '当前材料已有支持。',
  suggestion: '建议核对真实使用场景并完善表达。',
  evidences: [{
    requirementEvidenceId: id * 10,
    sectionLabel: '工作经历',
    evidenceText: `简历中的证据 ${id}`,
    supportLevel: level === 'MATCHED' ? 'SUFFICIENT' : 'PARTIAL',
  }],
})

const analysis = {
  optimizationTaskId: 42,
  sourceResumeVersionId: 1,
  targetResumeVersionId: 2,
  jobTargetId: 3,
  status: 'SUCCESS',
  jobTitle: 'Java 后端工程师',
  resumeName: 'chinese-java-two-page.pdf',
  analysisMode: 'EVIDENCE',
  evidenceAnalysis: {
    evidenceAnalysisId: 7,
    matchedCount: 7,
    partialEvidenceCount: 1,
    noEvidenceCount: 1,
    requirements: Array.from({ length: 9 }, (_, index) => requirement(index + 1, index === 2 ? 'PARTIAL_EVIDENCE' : 'MATCHED')),
  },
  legacyAnalysis: null,
}

async function mockWorkspace(page: Page, options: { failSaveOnce?: boolean } = {}) {
  await page.addInitScript(() => localStorage.setItem('ai-resume-token', 'workspace-frame-token'))
  await page.route('**/api/users/me', (route) => route.fulfill(response({
    id: 1, username: 'workspace', email: 'workspace@example.invalid', nickname: '工作区测试用户', createdAt: '2026-01-01T00:00:00Z',
  })))
  let saveAttempts = 0
  await page.route('**/api/workspace/42/content', (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill(response({ optimizationTaskId: 42, revision: 3, document }))
    }
    saveAttempts += 1
    if (options.failSaveOnce && saveAttempts === 1) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, message: '保存服务暂时不可用', data: null }) })
    }
    return route.fulfill(response({ saved: true, conflict: false, revision: 3 + saveAttempts, document: null }))
  })
  await page.route('**/api/optimization-tasks/42/analysis-result', (route) => route.fulfill(response(analysis)))
  await page.route('**/api/workspace/42/artifacts', (route) => route.fulfill(response([])))
}

const issues = new WeakMap<Page, string[]>()
test.beforeEach(({ page }) => {
  const messages: string[] = []
  issues.set(page, messages)
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') messages.push(`${message.type()}: ${message.text()}`)
  })
  page.on('pageerror', (error) => messages.push(`pageerror: ${error.message}`))
})
test.afterEach(({ page }) => expect(issues.get(page) ?? []).toEqual([]))

test.describe('Workspace editor frame', () => {
  for (const viewport of [{ width: 1366, height: 768 }, { width: 1920, height: 1080 }]) {
    test(`uses a fixed 20/60/20 editor-first layout at ${viewport.width}x${viewport.height}`, async ({ page }) => {
      await page.setViewportSize(viewport)
      await mockWorkspace(page)
      await page.goto('/workspace/42?requirement=3')
      await expect(page.locator('.workspace-layout')).toBeVisible()
      await expect(page.getByText('岗位定向编辑', { exact: true })).toBeVisible()
      await expect(page.getByText('当前优化：具备 Redis 缓存设计经验', { exact: true })).toBeVisible()
      await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible()

      const metrics = await page.evaluate(() => {
        const app = document.querySelector<HTMLElement>('.app-page')!
        const layout = document.querySelector<HTMLElement>('.workspace-layout')!
        const columns = Array.from(layout.children).map((child) => child.getBoundingClientRect().width)
        return { appScrollHeight: app.scrollHeight, appClientHeight: app.clientHeight, appScrollTop: app.scrollTop, columns }
      })
      expect(metrics.appScrollHeight).toBeLessThanOrEqual(metrics.appClientHeight + 1)
      expect(metrics.appScrollTop).toBe(0)
      expect(metrics.columns[1]).toBeGreaterThan(metrics.columns[0]! * 2.5)
      expect(metrics.columns[1]).toBeGreaterThan(metrics.columns[2]! * 2.5)
    })
  }

  test('keeps requirements, document and context in independent scroll regions', async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 768 })
    await mockWorkspace(page)
    await page.goto('/workspace/42?requirement=3')
    const app = page.locator('.app-page')
    const requirements = page.locator('.requirement-list')
    const editor = page.locator('.resume-stage-scroll')
    const context = page.locator('.inspector-scroll')
    await requirements.hover()
    await page.mouse.wheel(0, 600)
    await expect.poll(() => requirements.evaluate((element) => element.scrollTop)).toBeGreaterThan(0)
    await editor.hover()
    await page.mouse.wheel(0, 600)
    await expect.poll(() => editor.evaluate((element) => element.scrollTop)).toBeGreaterThan(0)
    await context.hover()
    await page.mouse.wheel(0, 300)
    await expect.poll(() => context.evaluate((element) => element.scrollTop)).toBeGreaterThan(0)
    expect(await app.evaluate((element) => element.scrollTop)).toBe(0)
  })

  test('shows save failure recovery without changing the save API', async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 768 })
    await mockWorkspace(page, { failSaveOnce: true })
    await page.goto('/workspace/42?requirement=3')
    const name = page.locator('.editor-basics input').first()
    await name.fill('Alex Chen Updated')
    await expect(page.getByText('保存失败 · 重新保存', { exact: true })).toBeVisible({ timeout: 5_000 })
    await expect(page.getByRole('button', { name: '重新保存' })).toBeVisible()
    await page.getByRole('button', { name: '重新保存' }).click()
    await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 5_000 })
  })

  test('opens narrow workspace in the editable document and keeps the outer page fixed', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await mockWorkspace(page)
    await page.goto('/workspace/42?requirement=3')
    await expect(page.getByRole('tab', { name: '编辑简历' })).toHaveAttribute('aria-selected', 'true')
    await expect(page.locator('.resume-stage')).toBeVisible()
    await expect(page.locator('.workspace-inspector')).toBeHidden()
    await expect.poll(() => page.evaluate(() => {
      const list = document.querySelector('.requirement-list')?.getBoundingClientRect()
      const selected = document.querySelector('.requirement-item.is-selected')?.getBoundingClientRect()
      return Boolean(list && selected && selected.left >= list.left - 1 && selected.right <= list.right + 1)
    })).toBe(true)
    const appMetrics = await page.locator('.app-page').evaluate((element) => ({ scrollHeight: element.scrollHeight, clientHeight: element.clientHeight, scrollTop: element.scrollTop, scrollWidth: element.scrollWidth, clientWidth: element.clientWidth }))
    expect(appMetrics.scrollHeight).toBeLessThanOrEqual(appMetrics.clientHeight + 1)
    expect(appMetrics.scrollWidth).toBeLessThanOrEqual(appMetrics.clientWidth + 1)
    expect(appMetrics.scrollTop).toBe(0)
    await page.getByRole('tab', { name: /优化建议/ }).click()
    await expect(page.locator('.workspace-inspector')).toBeVisible()
    await expect(page.getByText('当前修改上下文 · 03', { exact: true })).toBeVisible()
  })
})
