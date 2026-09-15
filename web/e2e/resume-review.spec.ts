import { readFileSync } from 'node:fs'
import { expect, test, type Page } from '@playwright/test'

const reviewResume = {
  id: 2,
  originalFilename: 'product-analytics-review.docx',
  fileType: 'DOCX',
  fileSize: 456789,
  uploadStatus: 'SUCCESS',
  parseStatus: 'SUCCESS',
  qualityStatus: 'NEEDS_REVIEW',
  canonicalReady: true,
  parseErrorMessage: null,
  createdAt: '2025-12-18T12:00:00Z',
}

const readyResume = {
  ...reviewResume,
  qualityStatus: 'READY',
  originalFilename: 'product-analytics-ready.docx',
}

const response = (data: unknown) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: 'success', data }),
})

const contactItem = {
  id: 'contact-1',
  kind: 'CONTACT_CANDIDATE',
  canonicalDraft: JSON.stringify({ type: 'EMAIL', label: '邮箱', value: 'candidate@example.com' }),
  reason: '邮箱格式需要你核对。',
}

const requiredContactItem = {
  id: 'required-contact-1',
  kind: 'REQUIRED_CONTACT_CANDIDATE',
  canonicalDraft: JSON.stringify({ type: 'PHONE', label: '电话', value: '13800138000' }),
  reason: null,
}

const nameItem = {
  id: 'name-1',
  kind: 'NAME_CANDIDATE',
  canonicalDraft: JSON.stringify({ text: '林然' }),
  reason: null,
}

const fragmentItem = {
  id: 'fragment-1',
  kind: 'TEXT_FRAGMENT',
  canonicalDraft: JSON.stringify({ text: '负责跨团队项目推进' }),
  reason: null,
}

const experienceItem = {
  id: 'experience-1',
  kind: 'ENTRY_CANDIDATE',
  canonicalDraft: JSON.stringify({
    kind: 'EXPERIENCE',
    organization: '示例科技',
    role: '后端工程师',
    startDate: '2022',
    endDate: '至今',
    bullets: [
      { id: 'bullet-1', text: '负责服务端接口开发与维护' },
      { id: 'bullet-2', text: '推动跨团队协作交付' },
    ],
  }),
  reason: '请核对这段工作经历的归属。',
}

const educationItem = {
  id: 'education-1',
  kind: 'ENTRY_CANDIDATE',
  canonicalDraft: JSON.stringify({
    kind: 'EDUCATION',
    school: '示例大学',
    degree: '本科',
    major: '计算机科学与技术',
    startDate: '2018',
    endDate: '2022',
    bullets: [{ id: 'education-bullet', text: '主修软件工程' }],
  }),
  reason: '请核对这段教育经历。',
}

const projectItem = {
  id: 'project-1',
  kind: 'ENTRY_CANDIDATE',
  canonicalDraft: JSON.stringify({
    kind: 'PROJECT',
    organization: '推荐系统项目',
    role: '后端负责人',
    startDate: '2023',
    endDate: '2024',
    bullets: [{ id: 'project-bullet', text: '负责推荐服务开发' }],
  }),
  reason: '请核对这段项目经历。',
}

const confirmedDocument = JSON.stringify({
  schemaVersion: 'RESUME_DOCUMENT_V1',
  basics: {
    name: '林然',
    contacts: [{ id: 'confirmed-email', type: 'EMAIL', label: null, value: 'linran@example.com' }],
  },
  sections: [{
    id: 'confirmed-experience',
    kind: 'EXPERIENCE',
    title: '工作经历',
    entries: [{
      id: 'confirmed-entry',
      organization: '示例科技',
      role: '后端工程师',
      school: null,
      degree: null,
      major: null,
      startDate: '2022',
      endDate: '至今',
      location: null,
      group: null,
      skillItems: null,
      bullets: [{ id: 'confirmed-bullet', text: '负责服务端接口开发与维护' }],
    }],
  }],
})

const reviewPayload = (
  items: unknown[],
  qualityStatus = 'NEEDS_REVIEW',
  canonicalDocument = '{"basics":{"name":"测试用户"}}',
) => ({
  resumeId: 2,
  qualityStatus,
  qualityIssues: null,
  unresolvedItems: JSON.stringify(items),
  canonicalDocument,
})

const longReviewItems = [
  nameItem,
  requiredContactItem,
  contactItem,
  experienceItem,
  projectItem,
  educationItem,
  ...Array.from({ length: 28 }, (_, index) => ({
    id: `fragment-long-${index + 1}`,
    kind: 'TEXT_FRAGMENT',
    canonicalDraft: JSON.stringify({ text: `一段较长的未归类内容 ${index + 1}，需要你确认它是否属于简历正文。` }),
    reason: null,
  })),
]

async function mockReviewShell(page: Page, resumes: unknown[] = [reviewResume]) {
  await page.addInitScript(() => {
    window.localStorage.setItem('ai-resume-token', 'resume-review-test-token')
  })
  await page.route('**/api/users/me', (route) => route.fulfill(response({
    id: 1,
    username: 'review-test',
    email: 'review@example.invalid',
    nickname: '确认测试用户',
    createdAt: '2026-01-01T00:00:00Z',
  })))
  await page.route('**/api/resumes', (route) => route.fulfill(response(resumes)))
  await page.route('**/api/settings/ai-provider', (route) =>
    route.fulfill(response({
      providerType: 'OPENAI_COMPATIBLE',
      baseUrl: 'https://api.example.invalid/v1',
      model: 'e2e-model',
      config: {},
      status: 'ACTIVE',
      configured: true,
      apiKeyConfigured: true,
      maskedApiKey: '••••••••',
      credentialStorageAvailable: true,
    })),
  )
  await page.route('**/api/job-direction-insights', (route) => route.fulfill(response({ cohorts: [] })))
  await page.route('**/api/optimization-tasks/recent*', (route) => route.fulfill(response([])))
}

async function openReview(page: Page) {
  await page.goto('/resumes')
  await page.getByRole('button', { name: /确认 product-analytics-review/ }).click()
  await expect(page.getByRole('heading', { name: '内容确认' })).toBeVisible()
}

const browserIssues = new WeakMap<Page, string[]>()

test.beforeEach(({ page }) => {
  const issues: string[] = []
  browserIssues.set(page, issues)
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      issues.push(`${message.type()}: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) => issues.push(`pageerror: ${error.message}`))
})

test.afterEach(({ page }) => {
  expect(browserIssues.get(page) ?? []).toEqual([])
})

test.describe('Resume Review Workspace', () => {
  test('opens a Chinese task workspace with progress and contact labels', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([
      contactItem,
      fragmentItem,
      experienceItem,
    ]))))
    await openReview(page)

    await expect(page.locator('.resume-review-label')).toHaveText('内容审阅')
    await expect(page.getByRole('heading', { name: '内容确认' })).toBeVisible()
    await expect(page.locator('.resume-review-header-description')).toContainText('无法安全判断')
    await expect(page.locator('.resume-review-progress-meta')).toContainText('第 1 项')
    await expect(page.locator('.resume-review-progress-meta')).toContainText('当前还有 3 项待确认')
    await page.getByText('查看全部待确认内容', { exact: true }).click()
    await expect(page.locator('.resume-review-index-item')).toHaveCount(3)
    await expect(page.getByText('联系方式类型', { exact: true })).toBeVisible()
    await expect(page.getByText('联系方式内容', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '保留这项联系方式' })).toBeVisible()
    await expect(page.getByRole('button', { name: '不加入简历' })).toBeVisible()
    await expect(page.locator('.resume-review-inspector')).not.toContainText('CONTEXTUAL REVIEW')
  })

  test('navigates by stable progress item id and renders education fields comfortably', async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 768 })
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([
      contactItem,
      educationItem,
    ]))))
    await openReview(page)

    const reviewWorkspace = page.locator('.resume-review-workspace')
    await reviewWorkspace.focus()
    await page.keyboard.press('ArrowRight')
    await expect(page.getByRole('heading', { name: '教育经历' })).toBeVisible()
    await page.keyboard.press('ArrowLeft')
    await expect(page.getByRole('heading', { name: '联系方式' })).toBeVisible()
    await page.getByText('查看全部待确认内容', { exact: true }).click()
    await page.getByRole('button', { name: /教育经历/ }).click()
    await expect(page.getByRole('heading', { name: '教育经历' })).toBeVisible()
    await expect(page.getByText('学校', { exact: true })).toBeVisible()
    await expect(page.getByText('学历', { exact: true })).toBeVisible()
    await expect(page.getByText('专业', { exact: true })).toBeVisible()
    await expect(page.getByText('开始时间', { exact: true })).toBeVisible()
    await expect(page.getByText('结束时间', { exact: true })).toBeVisible()
    await expect(page.getByText('内容 1', { exact: true })).toBeVisible()
    await expect(page.locator('.resume-review-field-pair')).toHaveCount(2)
  })

  test('keeps a 34-item review scannable without changing linear resolve order', async ({ page }) => {
    let remaining = [...longReviewItems]
    const payloads: Array<Record<string, unknown>> = []
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload(remaining))))
    await page.route('**/api/resumes/2/review/resolve', async (route) => {
      const payload = route.request().postDataJSON() as Record<string, unknown>
      payloads.push(payload)
      remaining = remaining.filter((item) => item.id !== payload.itemId)
      await route.fulfill(response(reviewPayload(remaining)))
    })
    await openReview(page)

    await expect(page.locator('.resume-review-progress-meta')).toContainText('第 1 项')
    await expect(page.locator('.resume-review-progress-meta')).toContainText('当前还有 34 项待确认')
    await page.getByText('查看全部待确认内容', { exact: true }).click()
    await expect(page.locator('.resume-review-index-item')).toHaveCount(34)
    await expect(page.getByRole('heading', { name: '基本信息' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '经历内容' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '其他内容' })).toBeVisible()

    const projectNavigation = page.getByRole('button', { name: /项目经历.*推荐系统项目/ })
    await projectNavigation.click()
    await expect(page.getByRole('heading', { name: '项目经历' })).toBeVisible()
    await expect(projectNavigation).toHaveAttribute('aria-current', 'step')
    await page.getByRole('button', { name: '保留这段项目经历' }).click()
    await expect(page.getByRole('heading', { name: '教育经历' })).toBeVisible()
    await expect(page.getByRole('button', { name: /教育经历.*示例大学/ })).toHaveAttribute('aria-current', 'step')
    await expect(page.locator('.resume-review-progress-meta')).toContainText('当前还有 33 项待确认')

    const lastFragment = page.getByRole('button', { name: /一段较长的未归类内容 28/ })
    await lastFragment.click()
    await expect(page.getByRole('heading', { name: '未归类内容' })).toBeVisible()
    await page.getByRole('button', { name: '不加入简历' }).click()
    await expect(page.getByRole('heading', { name: '未归类内容' })).toBeVisible()
    await expect(page.getByRole('button', { name: /一段较长的未归类内容 27/ })).toHaveAttribute('aria-current', 'step')
    expect(payloads.map((payload) => payload.itemId)).toEqual(['project-1', 'fragment-long-28'])
  })

  test('keeps the long review index usable on narrow screens', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload(longReviewItems))))

    for (const viewport of [{ width: 390, height: 844 }, { width: 430, height: 932 }]) {
      await page.setViewportSize(viewport)
      await page.goto('/resumes')
      await page.getByRole('button', { name: /确认 product-analytics-review/ }).click()
      await expect(page.getByRole('heading', { name: '内容确认' })).toBeVisible()
      await page.getByText('查看全部待确认内容', { exact: true }).click()
      await expect(page.locator('.resume-review-index-items')).toBeVisible()
      await expect(page.getByRole('button', { name: /一段较长的未归类内容 28/ })).toBeVisible()
      const layout = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }))
      expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth)
      await page.getByRole('button', { name: /一段较长的未归类内容 28/ }).click()
      await expect(page.getByRole('heading', { name: '未归类内容' })).toBeVisible()
    }
  })

  test('keeps reject and accept payloads, then moves to the next item and completes', async ({ page }) => {
    let listReady = false
    await mockReviewShell(page, [reviewResume])
    await page.unroute('**/api/resumes')
    await page.route('**/api/resumes', (route) => route.fulfill(response(listReady ? [readyResume] : [reviewResume])))
    let latestReview = reviewPayload([contactItem, fragmentItem])
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(latestReview)))
    const payloads: Array<Record<string, unknown>> = []
    await page.route('**/api/resumes/2/review/resolve', async (route) => {
      const payload = route.request().postDataJSON() as Record<string, unknown>
      payloads.push(payload)
      if (payload.action === 'DELETE') {
        await route.fulfill(response(reviewPayload([fragmentItem])))
      } else {
        listReady = true
        latestReview = reviewPayload([], 'READY', confirmedDocument)
        await route.fulfill(response(latestReview))
      }
    })
    await openReview(page)

    await page.getByRole('button', { name: '不加入简历' }).click()
    await expect(page.getByRole('heading', { name: '未归类内容' })).toBeVisible()
    await expect(page.locator('.resume-review-progress-meta')).toContainText('第 1 项')
    await expect(page.locator('.resume-review-progress-meta')).toContainText('当前还有 1 项待确认')
    await page.getByRole('button', { name: '保留这段内容' }).click()
    await expect(page.locator('.resume-review-workspace')).toBeHidden()
    await expect(page.locator('.resume-library-row')).toContainText('可用于岗位分析')
    expect(await page.evaluate(() => document.activeElement?.getAttribute('data-resume-row'))).toBe('2')
    await page.locator('.resume-source-trigger').filter({ hasText: 'product-analytics-ready' }).click()
    await expect(page.getByRole('heading', { name: '林然' })).toBeVisible()
    await expect(page.locator('.resume-source-preview')).toContainText('示例科技')
    await expect(page.locator('.resume-source-preview')).toContainText('负责服务端接口开发与维护')
    await page.getByRole('button', { name: /开始优化这份简历/ }).click()
    await expect(page).toHaveURL(/\/app\?resumeId=2$/)
    await expect(page.getByRole('radio', { name: /product-analytics-ready/ })).toBeChecked()
    expect(payloads).toEqual([
      { itemId: 'contact-1', action: 'DELETE', name: '测试用户' },
      { itemId: 'fragment-1', action: 'ACCEPT', text: '负责跨团队项目推进', name: '测试用户' },
    ])
  })

  test('uses constrained contact options and does not offer reject for name candidates', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([
      requiredContactItem,
      nameItem,
    ]))))
    await openReview(page)

    await expect(page.locator('#resume-contact-type-required-contact-1 option')).toHaveCount(2)
    await expect(page.getByRole('button', { name: '保存这项联系方式' })).toBeVisible()
    await expect(page.getByRole('button', { name: '不加入简历' })).toBeHidden()
    await page.getByText('查看全部待确认内容', { exact: true }).click()
    await page.getByRole('button', { name: /姓名/ }).click()
    await expect(page.getByRole('heading', { name: '姓名' })).toBeVisible()
    await expect(page.getByRole('button', { name: '使用这个姓名' })).toBeVisible()
    await expect(page.getByRole('button', { name: '不加入简历' })).toBeHidden()
  })

  test('returns focus to the triggering row after closing the workspace', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([contactItem]))))
    await page.goto('/resumes')
    const trigger = page.getByRole('button', { name: /确认 product-analytics-review/ })
    await trigger.click()
    await page.getByRole('heading', { name: '内容确认' }).waitFor()
    await page.getByRole('button', { name: '收起' }).click()
    await expect.poll(() => page.evaluate(() => document.activeElement?.getAttribute('data-resume-row'))).toBe('2')
  })

  test('preserves a dirty candidate while a different candidate resolves', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([fragmentItem, contactItem]))))
    await page.route('**/api/resumes/2/review/resolve', async (route) => {
      await route.fulfill(response(reviewPayload([fragmentItem])))
    })
    await openReview(page)

    const fragmentEditor = page.locator('#resume-fragment-fragment-1')
    await fragmentEditor.fill('用户修改后的候选 A')
    await page.getByText('查看全部待确认内容', { exact: true }).click()
    await page.getByRole('button', { name: /邮箱/ }).click()
    await page.getByRole('button', { name: '保留这项联系方式' }).click()
    await expect(page.getByRole('heading', { name: '未归类内容' })).toBeVisible()
    await expect(fragmentEditor).toHaveValue('用户修改后的候选 A')
  })

  test('keeps the review open when a dirty draft leaves through the close guard', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([fragmentItem]))))
    await openReview(page)
    await page.locator('#resume-fragment-fragment-1').fill('尚未确认的内容')
    await page.getByRole('button', { name: '收起' }).click()
    await expect(page.getByText('还有未确认的修改，离开后这些修改不会保留。')).toBeVisible()
    await page.getByRole('button', { name: '继续确认' }).click()
    await expect(page.locator('.resume-review-workspace')).toBeVisible()
    await expect(page.locator('#resume-fragment-fragment-1')).toHaveValue('尚未确认的内容')
    await page.getByRole('button', { name: '收起' }).click()
    await page.getByRole('button', { name: '放弃修改' }).click()
    await expect(page.locator('.resume-review-workspace')).toBeHidden()
  })

  test('does not open source preview when dirty review exit is cancelled', async ({ page }) => {
    await mockReviewShell(page, [reviewResume, readyResume])
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([fragmentItem]))))
    await page.route('**/api/resumes/1/review', (route) => route.fulfill(response(reviewPayload([], 'READY', confirmedDocument))))
    await openReview(page)
    await page.locator('#resume-fragment-fragment-1').fill('尚未确认的内容')
    await page.locator('.resume-source-trigger').filter({ hasText: 'product-analytics-ready' }).click()
    await expect(page.getByText('还有未确认的修改，离开后这些修改不会保留。')).toBeVisible()
    await page.getByRole('button', { name: '继续确认' }).click()
    await expect(page.locator('.resume-review-workspace')).toBeVisible()
    await expect(page.locator('.resume-source-preview')).toBeHidden()
    await expect(page.locator('#resume-fragment-fragment-1')).toHaveValue('尚未确认的内容')
  })

  test('clears a dirty review name when the final server response is READY', async ({ page }) => {
    await mockReviewShell(page)
    let listReady = false
    await page.unroute('**/api/resumes')
    await page.route('**/api/resumes', (route) => route.fulfill(response(listReady ? [readyResume] : [reviewResume])))
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([fragmentItem], 'NEEDS_REVIEW', '{"basics":{"name":""}}'))))
    await page.route('**/api/resumes/2/review/resolve', async (route) => {
      listReady = true
      await route.fulfill(response(reviewPayload([], 'READY', JSON.stringify({ schemaVersion: 'RESUME_DOCUMENT_V1', basics: { name: '用户改名', contacts: [] }, sections: [] }))))
    })
    await openReview(page)

    await page.locator('#resume-review-name-fragment-1').fill('用户改名')
    await page.locator('#resume-fragment-fragment-1').fill('确认后的内容')
    await page.getByRole('button', { name: '保留这段内容' }).click()
    await expect(page.locator('.resume-review-workspace')).toBeHidden()
    await expect(page.locator('.resume-library-row')).toContainText('可用于岗位分析')
  })

  test('keeps edited candidate content after resolve failure and offers retry', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([fragmentItem]))))
    let attempts = 0
    await page.route('**/api/resumes/2/review/resolve', async (route) => {
      attempts += 1
      if (attempts === 1) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, message: '保存服务暂时不可用', data: null }) })
        return
      }
      await route.fulfill(response(reviewPayload([], 'READY')))
    })
    await openReview(page)

    const editor = page.locator('#resume-fragment-fragment-1')
    await editor.fill('用户核对后的内容')
    await page.getByRole('button', { name: '保留这段内容' }).click()
    await expect(page.locator('.resume-review-action-error')).toContainText('本次确认没有保存')
    await expect(editor).toHaveValue('用户核对后的内容')
    await page.getByRole('button', { name: '重试当前操作' }).click()
    await expect(page.locator('.resume-review-workspace')).toBeHidden()
  })

  test('keeps load failure inside the workspace and supports rereading', async ({ page }) => {
    await mockReviewShell(page)
    let attempts = 0
    await page.route('**/api/resumes/2/review', async (route) => {
      attempts += 1
      if (attempts === 1) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, message: '读取服务暂时不可用', data: null }) })
        return
      }
      await route.fulfill(response(reviewPayload([contactItem])))
    })
    await openReview(page)

    await expect(page.getByText('暂时无法读取待确认内容。', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '重新读取' })).toBeVisible()
    await page.getByRole('button', { name: '重新读取' }).click()
    await expect(page.getByText('联系方式类型', { exact: true })).toBeVisible()
  })

  test('shows a state-sync fallback when candidates are empty but status is still NEEDS_REVIEW', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([]))))
    await openReview(page)

    await expect(page.getByText('状态同步异常', { exact: true })).toBeVisible()
    await expect(page.locator('.resume-review-workspace')).toContainText(
      '简历内容已经生成，但状态同步异常，请刷新后重试。',
    )
    // 普通结构问题不能再引导用户“上传更清晰的版本”。
    await expect(page.locator('.resume-review-workspace')).not.toContainText('上传更清晰的版本')
    await expect(page.locator('.resume-review-workspace')).not.toContainText('当前没有可安全确认的候选内容')
    await page.getByRole('button', { name: '返回简历库' }).click()
    await expect(page.locator('.resume-review-workspace')).toBeHidden()
  })

  test('offers re-upload only when the resume truly failed to form usable content', async ({ page }) => {
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([], 'FAILED'))))
    await openReview(page)

    await expect(page.getByText('这份简历未能形成可用内容', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '重新上传简历' }).click()
    await expect(page.getByRole('heading', { name: '上传一份真实简历' })).toBeVisible()
    await expect(page.locator('.resume-review-workspace')).toBeHidden()
  })

  test('moves the mobile review workspace into view without horizontal overflow', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await mockReviewShell(page)
    await page.route('**/api/resumes/2/review', (route) => route.fulfill(response(reviewPayload([experienceItem]))))
    await openReview(page)

    const layout = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
      reviewTop: document.querySelector('.resume-review-workspace')?.getBoundingClientRect().top ?? 0,
    }))
    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth)
    expect(layout.reviewTop).toBeLessThanOrEqual(90)
    await expect(page.getByRole('heading', { name: '工作经历' })).toBeVisible()
    await expect(page.getByRole('button', { name: '保留这段工作经历' })).toBeVisible()
  })

  test('a low-quality parse keeps the main flow usable from the last confirmation to Preview', async ({ page }) => {
    // 解析质量一般的简历：canonical 文档已生成、有候选、qualityIssues 仍保留结构问题。
    // 确认最后一个候选后不要求重新上传，可以开始岗位分析、进入 Workspace 并正常预览。
    const lowQualityResume = {
      ...reviewResume,
      id: 9,
      originalFilename: 'low-quality-parse.pdf',
      displayName: '低质量解析 · 流程回归',
    }
    const structuralQualityIssues = JSON.stringify([
      { code: 'CROSS_SECTION_DUPLICATE', severity: 'BLOCKER', message: '同一内容出现在多个章节' },
      { code: 'ENTRY_MISSING_TITLE_WITH_MANY_BULLETS', severity: 'BLOCKER', message: '存在缺少项目名的条目' },
    ])
    const workspaceDocument = {
      schemaVersion: 'RESUME_DOCUMENT_V1',
      basics: {
        name: '林然',
        contacts: [{ id: 'email-1', type: 'EMAIL', label: null, value: 'linran@example.com' }],
      },
      sections: [{
        id: 'experience',
        kind: 'EXPERIENCE',
        title: '工作经历',
        entries: [{
          id: 'entry-1',
          organization: '示例科技',
          role: '后端工程师',
          school: null,
          degree: null,
          major: null,
          startDate: '2022',
          endDate: '至今',
          location: null,
          group: null,
          skillItems: null,
          bullets: [{ id: 'bullet-1', text: '负责服务端接口开发与维护' }],
        }],
      }],
    }
    const analysisResult = {
      optimizationTaskId: 42,
      sourceResumeVersionId: 11,
      targetResumeVersionId: 12,
      jobTargetId: 13,
      status: 'SUCCESS',
      jobTitle: 'Java 后端工程师',
      resumeName: '低质量解析 · 流程回归',
      analysisMode: 'EVIDENCE',
      sourceCanonicalDocument: JSON.stringify(workspaceDocument),
      evidenceAnalysis: {
        evidenceAnalysisId: 7,
        matchedCount: 1,
        partialEvidenceCount: 0,
        noEvidenceCount: 0,
        requirements: [{
          evidenceRequirementId: 1,
          requirementText: '岗位要求 1：具备 Java 后端服务开发能力',
          importance: 'REQUIRED',
          matchLevel: 'MATCHED',
          conclusion: '当前材料存在相关证据。',
          suggestion: '核对已有表达。',
          evidences: [{
            requirementEvidenceId: 10,
            sectionLabel: '工作经历',
            evidenceText: '负责服务端接口开发与维护',
            supportLevel: 'SUFFICIENT',
          }],
        }],
      },
      legacyAnalysis: null,
    }
    const previewPdfBytes = readFileSync(
      new URL('./fixtures/synthetic-java-resume.pdf', import.meta.url).pathname,
    )

    let confirmed = false
    await mockReviewShell(page, [lowQualityResume])
    await page.unroute('**/api/resumes')
    await page.route('**/api/resumes', (route) =>
      route.fulfill(response([{ ...lowQualityResume, qualityStatus: confirmed ? 'READY' : 'NEEDS_REVIEW' }])),
    )
    await page.route('**/api/resumes/9/review', (route) => route.fulfill(response({
      resumeId: 9,
      qualityStatus: 'NEEDS_REVIEW',
      qualityIssues: structuralQualityIssues,
      unresolvedItems: JSON.stringify([fragmentItem]),
      canonicalDocument: confirmedDocument,
    })))
    await page.route('**/api/resumes/9/review/resolve', (route) => {
      confirmed = true
      // 最后一个候选确认完成：状态回到 READY；qualityIssues 中的结构问题仍然保留。
      return route.fulfill(response({
        resumeId: 9,
        qualityStatus: 'READY',
        qualityIssues: structuralQualityIssues,
        unresolvedItems: '[]',
        canonicalDocument: confirmedDocument,
      }))
    })

    await page.goto('/resumes')
    const row = page.locator('.resume-library-row')
    await expect(row).toContainText('有内容待确认')
    await expect(row).not.toContainText('需要重新准备')
    await row.getByRole('button', { name: /确认 low-quality-parse/ }).click()
    await expect(page.getByRole('heading', { name: '内容确认' })).toBeVisible()
    await expect(page.locator('.resume-review-workspace')).toContainText('不影响先继续优化')
    await page.getByRole('button', { name: '保留这段内容' }).click()

    // 确认最后一个候选项：自动退出确认面板，列表变为可用于岗位分析，不要求重新上传。
    await expect(page.locator('.resume-review-workspace')).toBeHidden()
    await expect(row).toContainText('可用于岗位分析')
    await expect(row).not.toContainText('需要重新准备')
    await expect(row).not.toContainText('有内容待确认')

    // 可以开始岗位分析。
    await page.goto('/app?resumeId=9')
    await expect(page.getByRole('radio', { name: /low-quality-parse/ })).toBeChecked()
    await page.locator('#home-jd').fill('负责后端服务开发与维护。')
    await page.route('**/api/job-analyses', (route) => route.fulfill(response({
      taskId: 900,
      optimizationTaskId: 42,
      sourceResumeVersionId: 11,
      targetResumeVersionId: 12,
      jobTargetId: 13,
    })))
    await page.route('**/api/tasks/900', (route) => route.fulfill(response({
      taskId: 900,
      taskType: 'JOB_ANALYSIS',
      status: 'SUCCESS',
      progress: 100,
      message: '分析完成',
    })))
    await page.route('**/api/optimization-tasks/42/analysis-result', (route) =>
      route.fulfill(response(analysisResult)),
    )
    const startButton = page.getByTestId('home-start-analysis')
    await expect(startButton).toBeEnabled()
    await startButton.click()
    await expect(page).toHaveURL(/\/job-analysis\/42$/, { timeout: 15_000 })

    // 进入 Workspace。
    await page.route('**/api/workspace/42/source-reference', (route) => route.fulfill(response({
      optimizationTaskId: 42,
      sourceResumeVersionId: 11,
      targetResumeVersionId: 12,
      targetRevision: 3,
      sourceFilename: 'low-quality-parse.pdf',
      sourcePdfAvailable: false,
      sourceBlocks: [{
        id: 'occ-bullet-1',
        order: 0,
        text: '负责服务端接口开发与维护',
        occurrenceIds: ['occ-bullet-1'],
        targetNodeIds: ['section:experience/entry:entry-1/bullet:bullet-1'],
        status: 'EXACT',
        reliable: true,
      }],
      mappings: [{
        targetNodeId: 'section:experience/entry:entry-1/bullet:bullet-1',
        nodeType: 'BULLET',
        sectionId: 'experience',
        entryId: 'entry-1',
        bulletId: 'bullet-1',
        targetText: '负责服务端接口开发与维护',
        sourceOccurrenceIds: ['occ-bullet-1'],
        status: 'EXACT',
        reliable: true,
        textChanged: false,
      }],
      fidelityIssues: [],
      statusCounts: { EXACT: 1, MERGED: 0, SPLIT: 0, UNMAPPED: 0, AMBIGUOUS: 0 },
      exportBlocked: false,
    })))
    await page.route('**/api/workspace/42/content', async (route) => {
      if (route.request().method() === 'GET') {
        return route.fulfill(response({ optimizationTaskId: 42, revision: 3, document: workspaceDocument }))
      }
      return route.fulfill(response({ saved: true, conflict: false, revision: 4, document: workspaceDocument }))
    })
    await page.route('**/api/workspace/42/artifacts', (route) => route.fulfill(response([])))
    await page.getByRole('button', { name: '修改简历', exact: true }).first().click()
    await expect(page).toHaveURL(/\/workspace\/42/, { timeout: 15_000 })
    await expect(page.locator('textarea').first()).toBeVisible({ timeout: 15_000 })

    // Preview 正常：仍有内容需要确认只提示，不阻止查看 PDF；导出保持严格。
    await page.route('**/api/workspace/42/preview.pdf*', (route) => {
      const expectedRevision =
        new URL(route.request().url()).searchParams.get('expectedRevision') ?? '3'
      return route.fulfill({
      status: 200,
      headers: {
        'content-type': 'application/pdf',
        'access-control-allow-origin': '*',
        'access-control-expose-headers': 'x-content-revision,x-target-resume-version,x-template-version,x-renderer-version,x-preview-receipt,x-resume-page-count,x-resume-missing-contact,x-resume-page-limit-exceeded,x-resume-overflow-detected,x-resume-orphan-final-page,x-resume-readability-too-small,x-resume-needs-review',
        'x-content-revision': expectedRevision,
        'x-target-resume-version': '12',
        'x-template-version': 'classic-v1',
        'x-renderer-version': 'typst-e2e',
        'x-preview-receipt': 'receipt-e2e',
        'x-resume-page-count': '1',
        'x-resume-missing-contact': 'false',
        'x-resume-page-limit-exceeded': 'false',
        'x-resume-overflow-detected': 'false',
        'x-resume-orphan-final-page': 'false',
        'x-resume-readability-too-small': 'false',
        'x-resume-needs-review': 'true',
      },
      body: previewPdfBytes,
      })
    })
    await page.getByRole('button', { name: '预览 →', exact: true }).click()
    await expect(page.getByTitle('简历 PDF 预览')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.preflight-section')).toContainText('还有内容需要确认')
    await expect(page.getByRole('button', { name: /需要先处理/ })).toBeDisabled()
  })
})
