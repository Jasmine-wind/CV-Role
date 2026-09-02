import { expect, test } from '@playwright/test'

test.describe('landing page presentation', () => {
  test('uses one scene per wheel burst and keeps the demo mounted', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /为每一个岗位/ })).toBeVisible()
    await expect(page.getByRole('button', { name: '查看示例' })).toBeVisible()
    await expect(page.evaluate(() => ({ scrollY: window.scrollY, body: document.body.style.overflow }))).resolves.toEqual({
      scrollY: 0,
      body: 'hidden',
    })

    await page.mouse.move(300, 300)
    await page.mouse.wheel(0, 100)
    await expect(page.locator('.product-scene')).toHaveClass(/is-active/)
    await expect(page.locator('.product-flow-demo')).toHaveCount(1)

    // The rest of this burst, including synthetic trackpad inertia, cannot skip
    // from the first product step to another scene.
    await page.evaluate(() => {
      const viewport = document.querySelector<HTMLElement>('.scene-viewport')
      if (!viewport) throw new Error('scene viewport is missing')
      for (let index = 0; index < 20; index += 1) {
        viewport.dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY: 12 }))
      }
    })
    await expect(page.locator('.product-scene')).toHaveClass(/is-active/)
    await page.waitForTimeout(700)

    await page.keyboard.press('ArrowDown')
    await page.waitForTimeout(700)
    await page.keyboard.press('ArrowDown')
    await page.waitForTimeout(700)
    await page.keyboard.press('ArrowDown')
    await expect(page.locator('.product-scene')).toHaveClass(/is-active/)
    await expect(page.locator('.landing-review-workspace')).toBeVisible()
    await expect(page.locator('.landing-review-margin')).toContainText('建议')

    await page.waitForTimeout(700)
    await page.keyboard.press('ArrowDown')
    await expect(page.locator('.product-scene')).toHaveClass(/is-active/)
    await expect(page.locator('.landing-review-workspace')).toBeVisible()
    await expect(page.locator('.landing-review-margin')).toContainText('可以导出 PDF')
  })

  test('reaches principles and final artifact with keyboard and restores scrolling on exit', async ({ page }) => {
    await page.goto('/')
    await page.locator('.scene-viewport').focus()
    await page.keyboard.press('ArrowDown')
    await expect(page.locator('.product-scene')).toHaveClass(/is-active/)
    await page.waitForTimeout(700)
    await page.keyboard.press('End')
    await expect(page.locator('.final-scene')).toHaveClass(/is-active/)
    await expect(page.locator('.final-resume-artifact')).toBeVisible()
    await expect(page.locator('.landing-result-summary')).toHaveCount(0)
    await expect(page.locator('.scene-panel.is-active')).toHaveAttribute('aria-hidden', 'false')
    await page.waitForTimeout(700)

    await page.keyboard.press('ArrowUp')
    await expect(page.locator('.principles-scene')).toHaveClass(/is-active/)
    await expect(page.locator('#principles-title')).toBeVisible()

    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(/\/login/)
    await expect(page.evaluate(() => ({ body: document.body.style.overflow, html: document.documentElement.style.overflow }))).resolves.toEqual({
      body: '',
      html: '',
    })
  })
})
