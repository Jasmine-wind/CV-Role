import { expect, test, type Page } from '@playwright/test'

const readyResume = {
  id: 1,
  originalFilename: '林然-产品分析简历-2026.pdf',
  fileType: 'PDF',
  fileSize: 120000,
  uploadStatus: 'SUCCESS',
  parseStatus: 'SUCCESS',
  qualityStatus: 'READY',
  canonicalReady: true,
  parseErrorMessage: null,
  createdAt: '2026-01-01T00:00:00Z',
}

const reviewResume = {
  ...readyResume,
  id: 2,
  originalFilename: 'resume-product-analytics-long-name.docx',
  fileType: 'DOCX',
  qualityStatus: 'NEEDS_REVIEW',
}

const response = (data: unknown) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: 'success', data }),
})

async function mockHome(
  page: Page,
  resumes = [readyResume, reviewResume],
  insights: unknown = { cohorts: [] },
) {
  await page.addInitScript(() => {
    window.localStorage.setItem('ai-resume-token', 'home-composer-test-token')
  })
  await page.route('**/api/users/me', (route) => route.fulfill(response({
    id: 1,
    username: 'home-test',
    email: 'home@example.invalid',
    nickname: '首页测试用户',
    createdAt: '2026-01-01T00:00:00Z',
  })))
  await page.route('**/api/resumes', (route) => route.fulfill(response(resumes)))
  await page.route('**/api/job-direction-insights', (route) => route.fulfill(response(insights)))
}

test.describe('Job Target Composer', () => {
  test('makes selection, JD input and blocking reason explicit', async ({ page }) => {
    await mockHome(page)
    await page.goto('/app')

    await expect(page.getByRole('heading', { name: '开始一次岗位定向' })).toBeVisible()
    await expect(page.locator('.ui-page-eyebrow')).toHaveText('新建岗位任务')
    await expect(page.getByRole('radio', { name: /林然-产品分析简历/ })).toBeChecked()
    await expect(page.getByTestId('home-start-analysis')).toBeDisabled()
    await expect(page.locator('.home-action-summary strong')).toHaveText('请粘贴完整的目标岗位描述。')

    await page.locator('label.home-resume-option').nth(1).click()
    await expect(page.getByRole('radio', { name: /resume-product-analytics/ })).toBeChecked()
    await expect(page.locator('.home-resume-state')).toContainText('需要确认')
    await expect(page.locator('.home-inline-link')).toContainText('前往确认')

    await page.locator('#home-jd').fill('负责数据分析与跨团队协作。')
    await expect(page.locator('.el-input__count')).toHaveText('13 / 10000')
    await expect(page.locator('.home-resume-option.is-selected strong')).toContainText('resume-product-analytics-long-name.docx')
    await expect(page.getByTestId('home-start-analysis')).toBeDisabled()
    await expect(page.locator('.home-action-summary strong')).toHaveText('这份简历有内容需要确认。')

    await page.locator('label.home-resume-option').first().click()
    await expect(page.getByTestId('home-start-analysis')).toBeEnabled()
    await expect(page.locator('.home-action-label')).toHaveText('本次核对')
  })

  test('starts one background analysis and locks the composer against duplicates', async ({ page }) => {
    await mockHome(page, [readyResume])
    let startCount = 0
    await page.route('**/api/job-analyses', (route) => {
      startCount += 1
      return route.fulfill(response({
        taskId: 123,
        optimizationTaskId: 456,
        sourceResumeVersionId: 11,
        targetResumeVersionId: 12,
        jobTargetId: 13,
      }))
    })
    await page.route('**/api/tasks/123', (route) => route.fulfill(response({
      taskId: 123,
      taskType: 'JOB_ANALYSIS',
      status: 'RUNNING',
      progress: 0,
      message: '正在拆解岗位要求',
    })))

    await page.goto('/app')
    await page.locator('#home-jd').fill('负责数据分析与跨团队协作。')
    const startButton = page.getByTestId('home-start-analysis')
    await expect(startButton).toBeEnabled()
    await startButton.click()
    await expect(page.locator('.home-analysis-state')).toContainText('岗位分析正在后台进行')
    await expect(page.locator('.home-analysis-state')).toContainText('正在拆解岗位要求')
    await expect(page.locator('#home-jd')).toBeDisabled()
    await expect(startButton).toBeDisabled()
    expect(startCount).toBe(1)
  })

  test('restores the active task and JD input directly after refresh', async ({ page }) => {
    await mockHome(page, [readyResume])
    await page.addInitScript(() => {
      window.sessionStorage.setItem('cv-role:active-job-analysis', JSON.stringify({
        taskId: 123,
        optimizationTaskId: 456,
        sourceResumeVersionId: 11,
        targetResumeVersionId: 12,
        jobTargetId: 13,
        resumeId: 1,
        jobDescription: '恢复后的岗位描述',
        startedAt: '2026-01-01T00:00:00Z',
      }))
    })
    await page.route('**/api/tasks/123', (route) => route.fulfill(response({
      taskId: 123,
      taskType: 'JOB_ANALYSIS',
      status: 'RUNNING',
      progress: 0,
      message: '正在恢复岗位分析',
    })))

    await page.goto('/app')
    await expect(page.locator('.home-analysis-state')).toContainText('岗位分析正在后台进行')
    await expect(page.locator('#home-jd')).toHaveValue('恢复后的岗位描述')
    await expect(page.locator('#home-jd')).toBeDisabled()
  })

  test('shows a useful empty state and keeps the JD field available', async ({ page }) => {
    await mockHome(page, [])
    await page.goto('/app')

    await expect(page.locator('.home-empty-source')).toContainText('还没有可用简历')
    await expect(page.getByRole('button', { name: '上传第一份简历' })).toBeVisible()
    await expect(page.locator('.home-inline-upload')).toContainText('10 MB')
    await expect(page.locator('#home-jd')).toBeVisible()
    await expect(page.locator('.home-action-summary strong')).toHaveText('请选择一份可以用于分析的简历。')
  })

  test('keeps the primary task and CTA within the first desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 768 })
    await mockHome(page)
    await page.goto('/app')
    await expect(page.locator('.home-task-bar')).toBeVisible()

    const taskBarBottom = await page.locator('.home-task-bar').evaluate((element) => element.getBoundingClientRect().bottom)
    expect(taskBarBottom).toBeLessThanOrEqual(768)
    await expect(page.getByRole('heading', { name: '粘贴完整岗位描述' })).toBeVisible()
    await expect(page.locator('label.home-sr-only')).toHaveText('目标岗位 JD')
  })

  test('shows the optional insight link only when the server reports a cohort', async ({ page }) => {
    await mockHome(page, [readyResume], { cohorts: [{}] })
    await page.goto('/app')
    await expect(page.getByRole('link', { name: /查看洞察/ })).toBeVisible()
  })

  test('uses a single-column layout without horizontal overflow on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await mockHome(page)
    await page.goto('/app')
    await expect(page.locator('.home-target-column')).toBeVisible()

    const layout = await page.evaluate(() => {
      const source = document.querySelector('.home-source-column')?.getBoundingClientRect()
      const target = document.querySelector('.home-target-column')?.getBoundingClientRect()
      return {
        sourceTop: source?.top ?? 0,
        targetTop: target?.top ?? 0,
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }
    })
    expect(layout.targetTop).toBeGreaterThan(layout.sourceTop)
    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth)
  })
})
