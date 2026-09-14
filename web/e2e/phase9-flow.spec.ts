import { readFileSync } from 'node:fs'
import { expect, test, type Page, type TestInfo } from '@playwright/test'

const standardFixture = new URL('./fixtures/synthetic-java-resume.pdf', import.meta.url).pathname
const twoPageFixture = new URL('./fixtures/chinese-java-two-page.pdf', import.meta.url).pathname
const mixedFixture = new URL('./fixtures/mixed-backend-platform.pdf', import.meta.url).pathname

const chineseJavaJobDescription = readFileSync(
  new URL('./fixtures/jds/java-backend-platform.txt', import.meta.url),
  'utf8',
).trim()
const englishPlatformJobDescription = readFileSync(
  new URL('./fixtures/jds/backend-platform-en.txt', import.meta.url),
  'utf8',
).trim()

const unique = (prefix: string) =>
  `${prefix.slice(0, 3)}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`

async function registerAndLogin(page: Page) {
  const username = unique('phase9')
  const email = `${username}@example.invalid`
  const password = 'phase9-safe-password'

  await page.goto('/register')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入邮箱').fill(email)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)

  await page.getByPlaceholder('请输入用户名或邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/app/)

  // The phase9 fake Provider is still exercised through the production BYOK
  // contract; configure a synthetic user credential before starting a task.
  await page.goto('/settings/ai-provider')
  await page.getByLabel('连接地址').fill('https://example.com/v1')
  await page.getByLabel('API 密钥').fill('phase9-user-key')
  await page.getByLabel('模型').fill('phase9-fake')
  await page.getByRole('button', { name: '测试连接', exact: true }).click()
  await expect(
    page.getByLabel('使用自己的 API').getByText('连接测试成功', { exact: true }),
  ).toBeVisible({ timeout: 15_000 })
  await page.getByRole('button', { name: '保存配置', exact: true }).click()
  await expect(page.getByText('配置已保存，尚未启用', { exact: true })).toBeVisible()
  await page
    .getByLabel('你的 API 已保存，尚未启用')
    .getByRole('button', { name: '启用', exact: true })
    .click()
  await expect(
    page.getByLabel('当前状态').getByText('你的 API 已启用', { exact: true }),
  ).toBeVisible()
  await page.goto('/app')
}

async function uploadAndStartAnalysis(page: Page, jobDescription: string, fixture = mixedFixture) {
  await page.getByTestId('home-resume-upload').setInputFiles(fixture)
  const uploadFinished = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith('/api/resumes'),
  )
  await page.getByRole('button', { name: '上传简历', exact: true }).click()
  await uploadFinished
  await expect(page.locator('.home-resume-option').filter({ hasText: '可用于分析' })).toBeVisible({
    timeout: 30_000,
  })
  await page.locator('#home-jd').fill(jobDescription)
  await expect(page.getByTestId('home-start-analysis')).toBeEnabled({ timeout: 30_000 })
  await page.getByTestId('home-start-analysis').click()
}

async function waitForAnalysis(page: Page) {
  await expect(page).toHaveURL(/\/job-analysis\/\d+/, { timeout: 45_000 })
  await expect(page.getByRole('region', { name: '岗位要求与证据审阅' })).toBeVisible({
    timeout: 20_000,
  })
}

async function openWorkspaceWithSavedDraft(page: Page) {
  await page.getByRole('button', { name: '修改简历', exact: true }).first().click()
  await expect(page).toHaveURL(/\/workspace\/\d+/, { timeout: 15_000 })
  const bullet = page.locator('textarea').filter({ hasText: '' }).last()
  await expect(bullet).toBeVisible({ timeout: 15_000 })
  await bullet.fill('负责 Java 后端服务开发')
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
  return bullet
}

async function openWorkspaceWithoutEditing(page: Page) {
  await page.getByRole('button', { name: '修改简历', exact: true }).first().click()
  await expect(page).toHaveURL(/\/workspace\/\d+/, { timeout: 15_000 })
  await expect(page.locator('textarea').first()).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
}

async function previewAndExportAll(
  page: Page,
  testInfo: TestInfo,
  prefix: string,
  assertDesktopSticky = false,
) {
  const previewButton = page.getByRole('button', { name: '预览 →', exact: true })
  if (await previewButton.isVisible()) await previewButton.click()
  await expect(
    page.locator('.preflight-section').getByText('可以导出', { exact: true }),
  ).toBeVisible({ timeout: 45_000 })
  await expect(page.getByTitle('简历 PDF 预览')).toBeVisible({ timeout: 45_000 })
  const previewInspector = page.getByRole('complementary', { name: '导出检查器' })
  await expect(page.locator('.preview-document-toolbar strong')).toHaveText(/PDF 预览 · \d+ 页/)
  await expect(page.locator('.preview-document-toolbar')).not.toContainText('最终文档')
  await expect(page.getByRole('link', { name: '在新窗口打开完整 PDF', exact: true })).toBeVisible()
  await expect(previewInspector.locator('.preview-template-section h2')).toHaveText('模板')
  await expect(previewInspector.locator('.preview-template-section p')).toHaveText(
    '切换模板后需重新预览。',
  )
  await expect(previewInspector.locator('.preview-status-section')).toContainText(/已生成 · \d+ 页/)
  await expect(previewInspector.locator('.preview-state-row')).toHaveCount(0)
  await expect(previewInspector.getByText('导出前检查', { exact: true })).toBeVisible()
  if (assertDesktopSticky) {
    await expect(page.getByRole('button', { name: '导出 PDF', exact: true })).toBeInViewport()
  }

  for (const template of ['classic', 'modern', 'minimal']) {
    if (template !== 'classic') {
      await page.getByTestId(`preview-template-${template}`).click()
      await page.locator('.preview-status-section').getByRole('button', { name: /预览/ }).click()
      await expect(page.getByTitle('简历 PDF 预览')).toBeVisible({ timeout: 45_000 })
    }
    await expect(
      page.locator('.preflight-section').getByText('可以导出', { exact: true }),
    ).toBeVisible({ timeout: 45_000 })
    const download = page.waitForEvent('download')
    await page.getByRole('button', { name: '导出 PDF', exact: true }).click()
    const downloaded = await download
    expect(await downloaded.path()).toBeTruthy()
    await downloaded.saveAs(testInfo.outputPath(`${prefix}-${template}.pdf`))
  }
  const exportHistory = page.locator('details.export-history')
  await exportHistory.locator('summary').click()
  await expect(exportHistory).toHaveJSProperty('open', true)
  await expect(exportHistory.locator('.artifact-list li')).toHaveCount(3)
  if (assertDesktopSticky) {
    const inspectorMetrics = await previewInspector.evaluate((element) => ({
      clientHeight: element.clientHeight,
      scrollHeight: element.scrollHeight,
    }))
    expect(inspectorMetrics.scrollHeight).toBeGreaterThan(inspectorMetrics.clientHeight)
    await previewInspector.evaluate((element) => {
      element.scrollTop = element.scrollHeight
    })
    await expect(page.getByRole('button', { name: '导出 PDF', exact: true })).toBeInViewport()
    await expect(exportHistory.locator('.artifact-list li').last()).toBeInViewport()
  }
}

test('happy path: upload, analysis, workspace, deterministic suggestion, preview and export', async ({
  page,
}, testInfo) => {
  await registerAndLogin(page)
  await uploadAndStartAnalysis(page, englishPlatformJobDescription, mixedFixture)
  await waitForAnalysis(page)
  // A direct result route remains usable after a browser refresh.
  await page.reload()
  await expect(page.getByRole('region', { name: '岗位要求与证据审阅' })).toBeVisible({
    timeout: 20_000,
  })

  const bullet = await openWorkspaceWithSavedDraft(page)
  const bulletLine = bullet.locator('xpath=ancestor::div[contains(@class, "bullet-line")]')
  await bulletLine.getByRole('button', { name: 'AI 优化', exact: true }).click()
  const suggestionFinished = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' && response.url().includes('/bullet-suggestion'),
  )
  await page.locator('[role="menuitem"]:visible', { hasText: '精简' }).click()
  await suggestionFinished
  // Demo Provider intentionally returns the frozen original. The no-op guard must
  // discard it rather than expose a misleading Apply action.
  await expect(page.getByText('正在生成修改建议…', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '采纳', exact: true })).toHaveCount(0)
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })

  await page.getByRole('button', { name: '预览 →', exact: true }).click()
  await expect(
    page.locator('.preflight-section').getByText('可以导出', { exact: true }),
  ).toBeVisible({ timeout: 45_000 })
  await expect(page.getByTitle('简历 PDF 预览')).toBeVisible()
  await page.getByRole('button', { name: '返回编辑', exact: true }).click()
  await expect(page.getByTitle('简历 PDF 预览')).toBeHidden()
  await page.getByRole('button', { name: '预览 →', exact: true }).click()
  await expect(page.getByTitle('简历 PDF 预览')).toBeVisible({ timeout: 45_000 })

  await previewAndExportAll(page, testInfo, 'mixed')
})

test('delayed upload keeps the JD and blocks analysis until the selected resume is ready', async ({
  page,
}) => {
  await registerAndLogin(page)
  await expect(page.locator('#home-jd')).toBeVisible({ timeout: 15_000 })

  let releaseUpload!: () => void
  const uploadRelease = new Promise<void>((resolve) => {
    releaseUpload = resolve
  })
  await page.route('**/api/resumes', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }
    // Hold the multipart request before it reaches the API. Using route.fetch()
    // here would rebuild the multipart stream and can drop the uploaded file.
    await uploadRelease
    await route.continue()
  })

  await page.getByTestId('home-resume-upload').setInputFiles(mixedFixture)
  const uploadClick = page.getByRole('button', { name: '上传简历', exact: true }).click()
  await expect(page.getByTestId('home-start-analysis')).toBeDisabled({ timeout: 15_000 })
  await page.locator('#home-jd').fill(englishPlatformJobDescription)
  await expect(page.getByTestId('home-start-analysis')).toBeDisabled()
  await expect(page.getByText('简历正在上传，请稍候。', { exact: true })).toBeVisible()

  releaseUpload()
  await uploadClick
  await page.unroute('**/api/resumes')
  await expect(page.locator('.home-resume-option.is-selected')).toContainText('可用于分析', {
    timeout: 30_000,
  })
  await expect(page.locator('#home-jd')).toHaveValue(englishPlatformJobDescription)
  await expect(page.getByTestId('home-start-analysis')).toBeEnabled({ timeout: 15_000 })
})

test('returning user can reopen the same optimization from recent tasks', async ({ page }) => {
  await registerAndLogin(page)
  await uploadAndStartAnalysis(page, englishPlatformJobDescription, mixedFixture)
  await waitForAnalysis(page)
  const taskId = new URL(page.url()).pathname.split('/').pop()
  await openWorkspaceWithSavedDraft(page)
  await page.goto('/app')
  await expect(page.getByRole('heading', { name: '最近优化' })).toBeVisible({ timeout: 15_000 })
  const recentTask = page.locator('.recent-task').first()
  await expect(recentTask).toContainText('继续')
  await recentTask.getByRole('button', { name: '继续' }).click()
  await expect(page).toHaveURL(new RegExp(`/job-analysis/${taskId}$`))
})

test('standard and legal two-page fixtures export all templates', async ({
  page,
  browser,
}, testInfo: TestInfo) => {
  await registerAndLogin(page)
  await uploadAndStartAnalysis(page, chineseJavaJobDescription, standardFixture)
  await waitForAnalysis(page)
  await openWorkspaceWithoutEditing(page)
  await previewAndExportAll(page, testInfo, 'standard')

  const twoPageContext = await browser.newContext({
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173',
  })
  const twoPage = await twoPageContext.newPage()
  try {
    await registerAndLogin(twoPage)
    await uploadAndStartAnalysis(twoPage, chineseJavaJobDescription, twoPageFixture)
    await waitForAnalysis(twoPage)
    await openWorkspaceWithoutEditing(twoPage)
    await previewAndExportAll(twoPage, testInfo, 'two-page')
  } finally {
    await twoPageContext.close()
  }
})

test('analysis failure recovers by retrying the same retained task without re-upload', async ({
  page,
}) => {
  await registerAndLogin(page)
  await uploadAndStartAnalysis(
    page,
    `${chineseJavaJobDescription}[[FAKE_PROVIDER_FAIL_ONCE]] ${unique('retry')}`,
    standardFixture,
  )

  await expect(page.getByText('岗位分析没有完成', { exact: true })).toBeVisible({ timeout: 45_000 })
  // Home restores the retained task after refresh, so retry does not re-upload input.
  await page.reload()
  await expect(page.getByText('岗位分析没有完成', { exact: true })).toBeVisible({ timeout: 20_000 })
  await page.getByRole('button', { name: '重试分析', exact: true }).click()
  await waitForAnalysis(page)
})

test('workspace conflict preserves the local draft; stale Preview and Suggest cannot overwrite it', async ({
  page: first,
  context,
}) => {
  await registerAndLogin(first)
  await uploadAndStartAnalysis(first, chineseJavaJobDescription, standardFixture)
  await waitForAnalysis(first)
  const originalUrl = first.url()
  const taskPath = new URL(originalUrl).pathname.replace('/job-analysis/', '/workspace/')
  await first.goto(taskPath)
  await expect(first.locator('textarea').last()).toBeVisible({ timeout: 15_000 })

  const sameContextSecond = await context.newPage()
  await sameContextSecond.goto(taskPath)
  await expect(sameContextSecond.locator('textarea').last()).toBeVisible({ timeout: 15_000 })

  const firstBullet = first.locator('textarea').last()
  await firstBullet.fill('负责 Java 后端服务开发 - first writer')
  await expect(first.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })

  const secondBullet = sameContextSecond.locator('textarea').last()
  await secondBullet.fill('负责 Java 后端服务开发 - local conflicting draft')
  await expect(sameContextSecond.getByText('存在冲突', { exact: true })).toBeVisible({
    timeout: 15_000,
  })
  await expect(secondBullet).toHaveValue('负责 Java 后端服务开发 - local conflicting draft')

  await sameContextSecond.getByRole('button', { name: '使用线上版本', exact: true }).click()
  await expect(sameContextSecond.getByText('✓ 已保存', { exact: true })).toBeVisible({
    timeout: 15_000,
  })

  let previewRequestStarted!: () => void
  let releasePreviewResponse!: () => void
  const previewStarted = new Promise<void>((resolve) => {
    previewRequestStarted = resolve
  })
  const previewRelease = new Promise<void>((resolve) => {
    releasePreviewResponse = resolve
  })
  await first.route('**/api/workspace/*/preview.pdf*', async (route) => {
    previewRequestStarted()
    const response = await route.fetch()
    await previewRelease
    await route.fulfill({ response })
  })
  await first.getByRole('button', { name: '预览 →', exact: true }).click()
  await previewStarted
  // Preview is a focus mode; switch back to the editor while the request is pending
  // to prove the late response cannot replace a new local draft.
  await first.getByRole('button', { name: '返回编辑', exact: true }).click()
  await firstBullet.fill('负责 Java 后端服务开发 - changed while preview was pending', {
    force: true,
  })
  await expect(first.getByText('未保存', { exact: true })).toBeVisible()
  releasePreviewResponse()
  await expect(first.getByTitle('简历 PDF 预览')).toBeHidden()
  await expect(first.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })

  let suggestionRequestStarted!: () => void
  let releaseSuggestionResponse!: () => void
  const suggestionStarted = new Promise<void>((resolve) => {
    suggestionRequestStarted = resolve
  })
  const suggestionRelease = new Promise<void>((resolve) => {
    releaseSuggestionResponse = resolve
  })
  await first.route('**/bullet-suggestion', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }
    suggestionRequestStarted()
    const request = JSON.parse(route.request().postData() ?? '{}') as {
      requestId: string
      bulletId: string
      baseRevision: number
      originalText: string
    }
    const response = await route.fetch()
    await response.body()
    await suggestionRelease
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'access-control-allow-origin':
          response.headers()['access-control-allow-origin'] ?? 'http://127.0.0.1:5175',
        'access-control-allow-credentials': 'true',
      },
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          requestId: request.requestId,
          state: 'READY',
          baseRevision: request.baseRevision,
          bulletId: request.bulletId,
          originalText: request.originalText,
          // Make the test response a visible candidate so the client stale guard
          // is exercised without changing the deterministic backend contract.
          suggestedText: '负责 Java 后端服务开发并持续迭代',
          reason: '测试候选',
          reviewCode: null,
          reviewMessage: null,
          rejectCode: null,
          rejectMessage: null,
          modelName: 'test-model',
        },
      }),
    })
  })
  const refreshedBullet = first.locator('textarea').last()
  const bulletLine = refreshedBullet.locator('xpath=ancestor::div[contains(@class, "bullet-line")]')
  await bulletLine.getByRole('button', { name: 'AI 优化', exact: true }).click()
  await first.locator('[role="menuitem"]:visible', { hasText: '精简' }).click()
  await suggestionStarted
  await refreshedBullet.fill('负责 Java 后端服务开发 - edited while suggestion was pending')
  releaseSuggestionResponse()
  await expect(
    first.getByText('内容或版本已变化，这条建议已失效，不能采纳。可以重新生成或关闭。'),
  ).toBeVisible({ timeout: 15_000 })
  await expect(first.getByRole('button', { name: '采纳', exact: true })).toHaveCount(0)

  await sameContextSecond.close()
  await first.close()
})

test('intentional omission: a deleted source bullet blocks delivery until confirmed and can be unconfirmed', async ({
  page,
}) => {
  await registerAndLogin(page)
  await uploadAndStartAnalysis(page, chineseJavaJobDescription, standardFixture)
  await waitForAnalysis(page)
  await openWorkspaceWithoutEditing(page)

  // Use one bullet from a multi-bullet work entry so deleting it exercises omission
  // without independently triggering the EMPTY_ENTRY document-quality blocker.
  const targetBullet = page
    .getByRole('group', { name: /工作经历，第 \d+ 项/ })
    .locator('.bullet-block')
    .first()
  await expect(targetBullet).toBeVisible({ timeout: 15_000 })
  const targetNodeId = await targetBullet.getAttribute('data-target-node-id')
  expect(targetNodeId).toBeTruthy()
  const mappedSourceBlock = page.locator(
    `.source-block[data-target-node-id="${targetNodeId}"]`,
  )
  await expect(mappedSourceBlock).toHaveCount(1)
  const sourceBlockId = await mappedSourceBlock.getAttribute('data-source-block-id')
  expect(sourceBlockId).toBeTruthy()

  const sourceCard = page.locator(`.source-block[data-source-block-id="${sourceBlockId}"]`)
  const targetText = targetBullet.locator('textarea')
  const changedSave = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      /\/api\/workspace\/\d+\/content$/.test(new URL(response.url()).pathname),
  )
  await targetText.fill(
    '重新组织平台核心服务的故障隔离、容量治理与发布流程，并补充完全不同的交付说明。',
  )
  await changedSave
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(sourceCard.locator('.mapping-state')).toHaveText('已定位 · 内容已修改', {
    timeout: 15_000,
  })

  const deletedSave = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      /\/api\/workspace\/\d+\/content$/.test(new URL(response.url()).pathname),
  )
  await targetBullet.locator('.bullet-delete-action').click()
  await deletedSave
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(sourceCard.locator('.mapping-state')).toHaveText('未映射', { timeout: 15_000 })
  await expect(page.getByText(/结构保真：\d+ 项阻断/)).toBeVisible()

  let previewRequestCount = 0
  const countPreviewRequest = (request: { url: () => string }) => {
    if (request.url().includes('/preview.pdf')) previewRequestCount += 1
  }
  page.on('request', countPreviewRequest)
  const freshFidelityVerdict = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/workspace\/\d+\/source-reference$/.test(new URL(response.url()).pathname),
  )
  await page.getByRole('button', { name: '预览 →', exact: true }).click()
  const fidelityResponse = await freshFidelityVerdict
  expect(fidelityResponse.ok()).toBe(true)
  const fidelityPayload = (await fidelityResponse.json()) as {
    data: { sourceBlocks: Array<{ id: string; occurrenceIds: string[] }> }
  }
  const expectedOrdinaryOccurrenceIds = fidelityPayload.data.sourceBlocks.find(
    (block) => block.id === sourceBlockId,
  )?.occurrenceIds
  expect(expectedOrdinaryOccurrenceIds).toBeTruthy()
  await expect(page.getByText('请先处理冻结原文中的结构保真问题，再预览或导出')).toBeVisible()
  expect(previewRequestCount).toBe(0)
  await expect(page.getByRole('button', { name: '导出 PDF', exact: true })).toHaveCount(0)
  page.off('request', countPreviewRequest)

  const confirmAction = sourceCard.getByRole('button', { name: /^确认省略/ })
  await expect(confirmAction).toBeEnabled()

  // Dirty and saving states must disable omission and issue zero omission POSTs.
  let omissionPostCount = 0
  const countOmissionPost = (request: { method: () => string; url: () => string }) => {
    if (request.method() === 'POST' && request.url().includes('/source-omissions/')) {
      omissionPostCount += 1
    }
  }
  page.on('request', countOmissionPost)
  let releaseSave!: () => void
  let markSaveStarted!: () => void
  const saveStarted = new Promise<void>((resolve) => {
    markSaveStarted = resolve
  })
  const saveRelease = new Promise<void>((resolve) => {
    releaseSave = resolve
  })
  await page.route('**/api/workspace/*/content', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue()
    markSaveStarted()
    await saveRelease
    await route.continue()
  })
  const remainingBullet = page.locator('.bullet-block textarea').first()
  await remainingBullet.fill(`${await remainingBullet.inputValue()} 补充保存门禁验证`)
  await expect(confirmAction).toBeDisabled()
  await confirmAction.click({ force: true })
  expect(omissionPostCount).toBe(0)
  await saveStarted
  await expect(confirmAction).toBeDisabled()
  await confirmAction.click({ force: true })
  expect(omissionPostCount).toBe(0)
  const gateSaveResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      /\/api\/workspace\/\d+\/content$/.test(new URL(response.url()).pathname),
  )
  releaseSave()
  expect((await gateSaveResponse).ok()).toBe(true)
  await page.unroute('**/api/workspace/*/content')
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(confirmAction).toBeEnabled({ timeout: 15_000 })

  // A failed save keeps the omission action disabled and cannot emit an omission POST.
  await page.route('**/api/workspace/*/content', async (route) => {
    if (route.request().method() === 'PUT') await route.abort('failed')
    else await route.continue()
  })
  const failedSaveRequest = page.waitForRequest(
    (request) =>
      request.method() === 'PUT' &&
      /\/api\/workspace\/\d+\/content$/.test(new URL(request.url()).pathname),
  )
  await remainingBullet.fill(`${await remainingBullet.inputValue()} failed-save gate`)
  await failedSaveRequest
  await expect(page.getByRole('button', { name: '重新保存', exact: true })).toBeVisible({
    timeout: 15_000,
  })
  await expect(confirmAction).toBeDisabled()
  await confirmAction.click({ force: true })
  expect(omissionPostCount).toBe(0)
  await page.unroute('**/api/workspace/*/content')
  const retrySaveResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      /\/api\/workspace\/\d+\/content$/.test(new URL(response.url()).pathname),
  )
  await page.getByRole('button', { name: '重新保存', exact: true }).click()
  expect((await retrySaveResponse).ok()).toBe(true)
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(confirmAction).toBeEnabled({ timeout: 15_000 })

  // Keep the real confirm request pending and prove every TARGET mutation surface is locked.
  let releaseConfirm!: () => void
  let markConfirmStarted!: () => void
  const confirmStarted = new Promise<void>((resolve) => {
    markConfirmStarted = resolve
  })
  const confirmRelease = new Promise<void>((resolve) => {
    releaseConfirm = resolve
  })
  await page.route('**/source-omissions/confirm', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    markConfirmStarted()
    await confirmRelease
    await route.continue()
  })
  const confirmResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/source-omissions/confirm'),
  )
  await confirmAction.click()
  await confirmStarted
  await expect(page.locator('.resume-editor')).toHaveAttribute('aria-busy', 'true')
  await expect(page.locator('.resume-editor')).toHaveAttribute('inert', /^(|true)$/)
  await expect(page.getByRole('button', { name: '预览 →', exact: true })).toBeDisabled()
  releaseConfirm()
  const confirmed = await confirmResponse
  await page.unroute('**/source-omissions/confirm')
  page.off('request', countOmissionPost)
  const confirmBody = confirmed.request().postDataJSON() as {
    expectedRevision: number
    sourceOccurrenceIds: string[]
  }
  expect(Number.isInteger(confirmBody.expectedRevision)).toBe(true)
  expect(confirmBody.sourceOccurrenceIds).toEqual(expectedOrdinaryOccurrenceIds)
  expect(new Set(confirmBody.sourceOccurrenceIds).size).toBe(confirmBody.sourceOccurrenceIds.length)
  expect(confirmed.ok()).toBe(true)

  await expect(sourceCard.locator('.mapping-state')).toHaveText('已确认省略', {
    timeout: 15_000,
  })
  await expect(page.getByText('结构保真检查通过', { exact: true })).toBeVisible({
    timeout: 15_000,
  })

  // A failed authoritative source-reference refresh must fail closed and issue no preview request.
  let failedGatePreviewRequests = 0
  const countFailedGatePreview = (request: { url: () => string }) => {
    if (request.url().includes('/preview.pdf')) failedGatePreviewRequests += 1
  }
  page.on('request', countFailedGatePreview)
  await page.route('**/api/workspace/*/source-reference', (route) => route.abort('failed'))
  await page.getByRole('button', { name: '预览 →', exact: true }).click()
  await expect(page.getByText('冻结原文检查尚未完成，请稍后重试')).toBeVisible()
  expect(failedGatePreviewRequests).toBe(0)
  await expect(page.getByTitle('简历 PDF 预览')).toHaveCount(0)
  await page.unroute('**/api/workspace/*/source-reference')
  page.off('request', countFailedGatePreview)

  const successfulFidelityVerdict = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/workspace\/\d+\/source-reference$/.test(new URL(response.url()).pathname),
  )
  await page.getByRole('button', { name: '预览 →', exact: true }).click()
  const successfulFidelityResponse = await successfulFidelityVerdict
  expect(successfulFidelityResponse.ok()).toBe(true)
  const successfulFidelityPayload = (await successfulFidelityResponse.json()) as {
    data: { exportBlocked: boolean }
  }
  expect(successfulFidelityPayload.data.exportBlocked).toBe(false)
  await expect(page.getByTitle('简历 PDF 预览')).toBeVisible({ timeout: 45_000 })
  await expect(
    page.locator('.preflight-section').getByText('可以导出', { exact: true }),
  ).toBeVisible({ timeout: 45_000 })
  await page.getByRole('button', { name: '返回编辑', exact: true }).click()

  const unconfirmAction = sourceCard.getByRole('button', { name: /^取消省略/ })
  await expect(unconfirmAction).toBeEnabled({ timeout: 15_000 })
  const unconfirmResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/source-omissions/unconfirm'),
  )
  await unconfirmAction.click()
  const unconfirmed = await unconfirmResponse
  expect(unconfirmed.ok()).toBe(true)
  const unconfirmBody = unconfirmed.request().postDataJSON() as {
    expectedRevision: number
    sourceOccurrenceIds: string[]
  }
  expect(unconfirmBody.sourceOccurrenceIds).toEqual(expectedOrdinaryOccurrenceIds)
  await expect(sourceCard.locator('.mapping-state')).toHaveText('未映射', { timeout: 15_000 })
  await expect(page.getByText(/结构保真：\d+ 项阻断/)).toBeVisible({ timeout: 15_000 })

  // A second clean page keeps the old revision while the first page wins a save.
  // Its omission POST must lose CAS, report conflict, adopt the server revision, and never claim success.
  const stalePage = await page.context().newPage()
  await stalePage.goto(page.url())
  const staleConfirmAction = stalePage.getByRole('button', { name: /^确认省略/ }).first()
  await expect(staleConfirmAction).toBeEnabled({ timeout: 15_000 })

  const winnerSave = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      /\/api\/workspace\/\d+\/content$/.test(new URL(response.url()).pathname),
  )
  await remainingBullet.fill(`${await remainingBullet.inputValue()} CAS winner`)
  expect((await winnerSave).ok()).toBe(true)
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })

  const staleConfirmResponse = stalePage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/source-omissions/confirm'),
  )
  await staleConfirmAction.click()
  const staleResponse = await staleConfirmResponse
  expect(staleResponse.ok()).toBe(true)
  const stalePayload = (await staleResponse.json()) as {
    data: { saved: boolean; conflict: boolean; revision: number }
  }
  expect(stalePayload.data.saved).toBe(false)
  expect(stalePayload.data.conflict).toBe(true)
  await expect(stalePage.getByText(/当前简历已有更新，本次操作未生效/)).toBeVisible()
  await expect(stalePage.getByText('已确认省略，结构保真状态已更新')).toHaveCount(0)
  await expect(stalePage.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
  await stalePage.close()
})

test('intentional omission: a whole Project entry remains blocked after authoritative confirmation', async ({
  page,
}) => {
  await registerAndLogin(page)
  await uploadAndStartAnalysis(page, chineseJavaJobDescription, standardFixture)
  await waitForAnalysis(page)
  await openWorkspaceWithoutEditing(page)

  const sourceReferenceResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/workspace\/\d+\/source-reference$/.test(new URL(response.url()).pathname),
  )
  await page.reload()
  const sourcePayload = (await (await sourceReferenceResponse).json()) as {
    data: {
      sourceBlocks: Array<{
        id: string
        order: number
        occurrenceIds: string[]
        sourceSectionKind: string | null
        sourceSectionId: string | null
        sourceEntryId: string | null
      }>
    }
  }

  const projectSection = page.getByRole('group', { name: /项目经历，第 \d+ 项/ }).first()
  await expect(projectSection).toBeVisible({ timeout: 15_000 })
  const projectEntry = projectSection.locator('.editor-entry').first()
  const sourceSectionId = await projectSection.getAttribute('data-section-id')
  const sourceEntryId = await projectEntry.getAttribute('data-entry-id')
  expect(sourceSectionId).toBeTruthy()
  expect(sourceEntryId).toBeTruthy()

  const projectBlocks = sourcePayload.data.sourceBlocks
    .filter(
      (block) =>
        block.sourceSectionKind === 'PROJECT' &&
        block.sourceSectionId === sourceSectionId &&
        block.sourceEntryId === sourceEntryId,
    )
    .sort((left, right) => left.order - right.order)
  const expectedProjectOccurrenceIds = [
    ...new Set(projectBlocks.flatMap((block) => block.occurrenceIds)),
  ]
  expect(projectBlocks.length).toBeGreaterThan(1)
  expect(expectedProjectOccurrenceIds.length).toBeGreaterThan(1)

  const deletedSave = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      /\/api\/workspace\/\d+\/content$/.test(new URL(response.url()).pathname),
  )
  await projectEntry.hover()
  await projectEntry.locator('.entry-delete-action').click()
  await page
    .locator('.el-message-box__btns')
    .getByRole('button', { name: '删除', exact: true })
    .click()
  expect((await deletedSave).ok()).toBe(true)
  await expect(page.getByText('✓ 已保存', { exact: true })).toBeVisible({ timeout: 15_000 })

  const projectSourceCard = page.locator(
    `.source-block[data-source-block-id="${projectBlocks[0]!.id}"]`,
  )
  const projectConfirm = projectSourceCard.getByRole('button', {
    name: /^确认省略此项目对应的/,
  })
  await expect(projectConfirm).toBeEnabled({ timeout: 15_000 })
  const confirmResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/source-omissions/confirm'),
  )
  await projectConfirm.click()
  const confirmed = await confirmResponse
  expect(confirmed.ok()).toBe(true)
  const confirmBody = confirmed.request().postDataJSON() as {
    expectedRevision: number
    sourceOccurrenceIds: string[]
  }
  expect(confirmBody.sourceOccurrenceIds).toEqual(expectedProjectOccurrenceIds)
  await expect(page.getByText(/冻结项目条目缺失，省略确认不能解除项目边界阻断/)).toBeVisible({
    timeout: 15_000,
  })
  await expect(page.getByText(/结构保真：\d+ 项阻断/)).toBeVisible()
  let projectPreviewRequests = 0
  const countProjectPreview = (request: { url: () => string }) => {
    if (request.url().includes('/preview.pdf')) projectPreviewRequests += 1
  }
  page.on('request', countProjectPreview)
  const freshProjectFidelityVerdict = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/workspace\/\d+\/source-reference$/.test(new URL(response.url()).pathname),
  )
  await page.getByRole('button', { name: '预览 →', exact: true }).click()
  const projectFidelityResponse = await freshProjectFidelityVerdict
  expect(projectFidelityResponse.ok()).toBe(true)
  const projectFidelityPayload = (await projectFidelityResponse.json()) as {
    data: { exportBlocked: boolean }
  }
  expect(projectFidelityPayload.data.exportBlocked).toBe(true)
  await expect(page.getByText('请先处理冻结原文中的结构保真问题，再预览或导出')).toBeVisible()
  expect(projectPreviewRequests).toBe(0)
  page.off('request', countProjectPreview)

  const projectUnconfirm = projectSourceCard.getByRole('button', {
    name: /^取消省略此项目对应的/,
  })
  await expect(projectUnconfirm).toBeEnabled({ timeout: 15_000 })
  const unconfirmResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/source-omissions/unconfirm'),
  )
  await projectUnconfirm.click()
  const unconfirmed = await unconfirmResponse
  expect(unconfirmed.ok()).toBe(true)
  const unconfirmBody = unconfirmed.request().postDataJSON() as {
    expectedRevision: number
    sourceOccurrenceIds: string[]
  }
  expect(unconfirmBody.sourceOccurrenceIds).toEqual(expectedProjectOccurrenceIds)
  await expect(page.getByText(/结构保真：\d+ 项阻断/)).toBeVisible({ timeout: 15_000 })
})

test.describe('desktop preview viewport', () => {
  test.use({ viewport: { width: 1440, height: 900 } })

  test('keeps the export action visible while the inspector scrolls', async ({
    page,
  }, testInfo) => {
    await registerAndLogin(page)
    await uploadAndStartAnalysis(page, englishPlatformJobDescription, mixedFixture)
    await waitForAnalysis(page)
    await openWorkspaceWithoutEditing(page)
    await page.getByRole('button', { name: '预览 →', exact: true }).click()
    await previewAndExportAll(page, testInfo, 'desktop-sticky', true)
  })
})

test.describe('narrow viewport', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('keeps navigation, workspace and Preview usable without horizontal overflow', async ({
    page,
  }, testInfo) => {
    await registerAndLogin(page)
    await expect(page.getByRole('button', { name: '打开导航菜单' })).toBeVisible()

    const closedShell = await page.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }))
    expect(closedShell.scrollWidth).toBeLessThanOrEqual(closedShell.clientWidth)

    await page.getByRole('button', { name: '打开导航菜单' }).click()
    await expect(page.locator('.app-sidebar.is-open')).toBeVisible()
    await expect(page.getByRole('button', { name: '关闭导航菜单' })).toBeVisible()
    await page.getByRole('button', { name: '关闭导航菜单' }).click()
    await expect(page.locator('.app-sidebar.is-open')).toHaveCount(0)

    await uploadAndStartAnalysis(page, englishPlatformJobDescription, mixedFixture)
    await waitForAnalysis(page)
    await openWorkspaceWithoutEditing(page)
    await page.getByRole('button', { name: '预览 →', exact: true }).click()
    await expect(
      page.locator('.preflight-section').getByText('可以导出', { exact: true }),
    ).toBeVisible({ timeout: 45_000 })
    const frame = page.getByTitle('简历 PDF 预览')
    await expect(frame).toBeVisible({ timeout: 45_000 })
    const openPdf = page.getByRole('link', { name: '在新窗口打开完整 PDF' })
    await expect(openPdf).toBeVisible()
    await expect(openPdf).toHaveAttribute('target', '_blank')
    await expect(openPdf).toHaveAttribute('href', /blob:/)
    await expect(page.locator('.preview-template-section')).toBeInViewport()
    const previewDocument = page.locator('.preview-document')
    const previewDocumentBox = await previewDocument.boundingBox()
    expect(previewDocumentBox?.height ?? 0).toBeGreaterThanOrEqual(180)
    expect(previewDocumentBox?.height ?? 0).toBeLessThan(280)
    const narrowShell = await page.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }))
    expect(narrowShell.scrollWidth).toBeLessThanOrEqual(narrowShell.clientWidth)
    const frameBox = await frame.boundingBox()
    expect(frameBox?.width ?? 0).toBeLessThanOrEqual(narrowShell.clientWidth)
    await page.locator('.preview-template-section').scrollIntoViewIfNeeded()
    await expect(page.locator('.preview-status-section')).toBeVisible()
    await expect(page.locator('.preflight-section')).toBeVisible()
    await expect(page.getByRole('button', { name: '导出 PDF', exact: true })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('narrow-preview.png'), fullPage: true })
  })
})
