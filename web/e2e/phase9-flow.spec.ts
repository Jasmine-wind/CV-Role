import { expect, test, type Page } from '@playwright/test'

const fixture = new URL('./fixtures/synthetic-java-resume.pdf', import.meta.url).pathname

const unique = (prefix: string) => `${prefix.slice(0, 3)}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`

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
}

async function uploadAndStartAnalysis(page: Page, jobDescription: string) {
  await page.getByTestId('home-resume-upload').setInputFiles(fixture)
  await page.getByRole('button', { name: '上传简历', exact: true }).click()
  await page.locator('#home-jd').fill(jobDescription)
  await expect(page.getByTestId('home-start-analysis')).toBeEnabled({ timeout: 30_000 })
  await page.getByTestId('home-start-analysis').click()
}

async function waitForAnalysis(page: Page) {
  await expect(page).toHaveURL(/\/job-analysis\/\d+/, { timeout: 45_000 })
  await expect(page.getByText('已有优势', { exact: true })).toBeVisible({ timeout: 20_000 })
}

async function openWorkspaceWithSavedDraft(page: Page) {
  await page.getByRole('button', { name: '修改简历', exact: true }).first().click()
  await expect(page).toHaveURL(/\/workspace\/\d+/, { timeout: 15_000 })
  const bullet = page.locator('textarea').filter({ hasText: '' }).last()
  await expect(bullet).toBeVisible({ timeout: 15_000 })
  await bullet.fill('负责 Java 后端服务开发')
  await expect(page.getByText('已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
  return bullet
}

test('happy path: upload, analysis, workspace, deterministic suggestion, preview and export', async ({ page }) => {
  await registerAndLogin(page)
  await uploadAndStartAnalysis(page, 'Java 后端开发工程师，要求熟悉 Java。')
  await waitForAnalysis(page)
  // A direct result route remains usable after a browser refresh.
  await page.reload()
  await expect(page.getByText('已有优势', { exact: true })).toBeVisible({ timeout: 20_000 })

  const bullet = await openWorkspaceWithSavedDraft(page)
  const bulletLine = bullet.locator('xpath=ancestor::div[contains(@class, "bullet-line")]')
  await bulletLine.getByRole('button', { name: '优化', exact: true }).click()
  await page.locator('[role="menuitem"]:visible', { hasText: '精简' }).click()
  await expect(page.getByText('优化建议', { exact: true })).toBeVisible({ timeout: 15_000 })
  await page.getByRole('button', { name: '采纳', exact: true }).click()
  await expect(page.getByText('已保存', { exact: true })).toBeVisible({ timeout: 15_000 })

  await page.getByRole('button', { name: '预览 / 导出 PDF', exact: true }).click()
  await page.getByRole('button', { name: '预览 PDF', exact: true }).click()
  await expect(page.getByText('导出前检查', { exact: true })).toBeVisible({ timeout: 45_000 })
  await page.keyboard.press('Escape')
  await expect(page.getByTitle('简历 PDF 预览')).toHaveCount(0)

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 PDF', exact: true }).click()
  await (await download).cancel()
  await expect(page.getByText('已导出文件', { exact: true })).toBeVisible()
})

test('analysis failure recovers by retrying the same retained task without re-upload', async ({ page }) => {
  await registerAndLogin(page)
  await uploadAndStartAnalysis(
    page,
    `Java 后端开发工程师，要求熟悉 Java。[[FAKE_PROVIDER_FAIL_ONCE]] ${unique('retry')}`,
  )

  await expect(page.getByText('岗位分析没有完成', { exact: true })).toBeVisible({ timeout: 45_000 })
  // Home restores the retained task after refresh, so retry does not re-upload input.
  await page.reload()
  await expect(page.getByText('岗位分析没有完成', { exact: true })).toBeVisible({ timeout: 20_000 })
  await page.getByRole('button', { name: '重试分析', exact: true }).click()
  await waitForAnalysis(page)
})

test('workspace conflict preserves the local draft; stale Preview and Suggest cannot overwrite it', async ({ page: first, context }) => {
  await registerAndLogin(first)
  await uploadAndStartAnalysis(first, 'Java 后端开发工程师，要求熟悉 Java。')
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
  await expect(first.getByText('已保存', { exact: true })).toBeVisible({ timeout: 15_000 })

  const secondBullet = sameContextSecond.locator('textarea').last()
  await secondBullet.fill('负责 Java 后端服务开发 - local conflicting draft')
  await expect(sameContextSecond.getByText('本地草稿未保存，请解决冲突', { exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(secondBullet).toHaveValue('负责 Java 后端服务开发 - local conflicting draft')

  await sameContextSecond.getByRole('button', { name: '使用最新版本', exact: true }).click()
  await expect(sameContextSecond.getByText('已保存', { exact: true })).toBeVisible({ timeout: 15_000 })

  let previewRequestStarted!: () => void
  let releasePreviewResponse!: () => void
  const previewStarted = new Promise<void>((resolve) => { previewRequestStarted = resolve })
  const previewRelease = new Promise<void>((resolve) => { releasePreviewResponse = resolve })
  await first.route('**/api/workspace/*/preview.pdf*', async (route) => {
    previewRequestStarted()
    const response = await route.fetch()
    await previewRelease
    await route.fulfill({ response })
  })
  await first.getByRole('button', { name: '预览 / 导出 PDF', exact: true }).click()
  await first.getByRole('button', { name: '预览 PDF', exact: true }).click()
  await previewStarted
  await firstBullet.fill('负责 Java 后端服务开发 - changed while preview was pending', { force: true })
  await expect(first.getByText('修改未保存', { exact: true })).toBeVisible()
  releasePreviewResponse()
  await expect(first.getByTitle('简历 PDF 预览')).toHaveCount(0)
  await expect(first.getByText('已保存', { exact: true })).toBeVisible({ timeout: 15_000 })
  await first.keyboard.press('Escape')

  await first.route('**/api/workspace/*/bullet-suggestion', async (route) => {
    const response = await route.fetch()
    await new Promise((resolve) => setTimeout(resolve, 600))
    await route.fulfill({ response })
  })
  const refreshedBullet = first.locator('textarea').last()
  const bulletLine = refreshedBullet.locator('xpath=ancestor::div[contains(@class, "bullet-line")]')
  await bulletLine.getByRole('button', { name: '优化', exact: true }).click()
  await first.locator('[role="menuitem"]:visible', { hasText: '精简' }).click()
  await refreshedBullet.fill('负责 Java 后端服务开发 - edited while suggestion was pending')
  await expect(first.getByText('内容或版本已变化，这条建议已失效，不能采纳。可以重新生成或关闭。')).toBeVisible({ timeout: 15_000 })
  await expect(first.getByRole('button', { name: '采纳', exact: true })).toHaveCount(0)

  await sameContextSecond.close()
  await first.close()
})
