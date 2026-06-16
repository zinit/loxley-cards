import { test, expect, Page } from '@playwright/test'

/**
 * Plays a full game by clicking cards and target rows until match ends.
 * Handles three card targeting modes:
 *   1. Row targeting (UNIT/SPY/ROW) → .board-row-valid-target appears → click it
 *   2. Unit targeting (UNIT_TARGET/decoy) → .board-card-valid-target appears → click it
 *   3. Auto-play (SPECIAL) → card plays immediately on click, no targeting needed
 * Falls back to Pass when no cards can be played.
 */
async function playGameToWin(page: Page): Promise<'victory' | 'defeat' | 'draw'> {
  await page.waitForSelector('[data-testid="hand-card"]', { timeout: 10_000 })

  for (let turn = 0; turn < 300; turn++) {
    // Check if match ended
    const matchEnd = page.locator('[data-testid="match-end-screen"]')
    if (await matchEnd.isVisible()) {
      return await matchEnd.getAttribute('data-result') as 'victory' | 'defeat' | 'draw'
    }

    // Dismiss round overlay if visible
    const roundOverlay = page.locator('.round-overlay')
    if (await roundOverlay.isVisible().catch(() => false)) {
      await roundOverlay.click({ force: true })
      await page.waitForTimeout(500)
      continue
    }

    const handCards = page.locator('[data-testid="hand-card"]')
    const handCount = await handCards.count()
    let played = false

    // Try each card in hand
    for (let i = 0; i < handCount; i++) {
      const card = handCards.nth(i)
      if (!(await card.isVisible().catch(() => false))) continue

      const countBefore = await handCards.count()
      await card.click()
      await page.waitForTimeout(200)

      // Check if hand count decreased — card was auto-played (SPECIAL)
      const countAfter = await handCards.count()
      if (countAfter < countBefore) {
        // Card was auto-played — wait for bot response
        await page.waitForTimeout(800)
        played = true
        break
      }

      // Check for row targeting (UNIT/SPY/ROW kinds)
      const validRow = page.locator('.board-row-valid-target')
      if (await validRow.count() > 0) {
        await validRow.first().evaluate(el => (el as HTMLElement).click())
        await page.waitForTimeout(1000)
        played = true
        break
      }

      // Check for unit targeting (decoy — .board-card-valid-target)
      const validUnit = page.locator('.board-card-valid-target')
      if (await validUnit.count() > 0) {
        await validUnit.first().evaluate(el => (el as HTMLElement).click())
        await page.waitForTimeout(1000)
        played = true
        break
      }

      // Card selected but no valid targets found — deselect and try next card
      await page.keyboard.press('Escape')
      await page.waitForTimeout(100)
    }

    if (played) continue

    // No card could be played — try Pass
    const passButton = page.locator('[data-testid="pass-button"]')
    if (await passButton.isVisible().catch(() => false)) {
      await passButton.click()
      await page.waitForTimeout(1000)
      continue
    }

    // Nothing available — wait for state change
    await page.waitForTimeout(500)
  }

  throw new Error('Game did not end within 300 turns')
}

test('campaign progression persists across sessions', async ({ page }) => {
  test.setTimeout(120_000)

  const username = `test_${Date.now()}`
  const password = 'sherwood1'

  // 1. Navigate to login page
  await page.goto('/login')

  // 2. Switch to register mode and fill form
  await page.click('text=Don\'t have an account? Register')
  await page.fill('input[placeholder="Username"]', username)
  await page.fill('input[placeholder="Password"]', password)
  await page.click('button[type="submit"]')

  // 3. Should redirect to campaign map
  await page.waitForURL('/', { timeout: 10_000 })

  // 4. Verify stage 1 active, stage 2 locked
  await expect(page.locator('[data-testid="stage-1"]')).toHaveAttribute('data-status', 'active')
  await expect(page.locator('[data-testid="stage-2"]')).toHaveAttribute('data-status', 'locked')

  // 5. Play stage 1 — retry up to 5 times if we lose (ultra_easy bot, RNG can cause losses)
  let won = false
  for (let attempt = 0; attempt < 5; attempt++) {
    await page.locator('[data-testid="stage-1"]').click()
    await page.waitForURL(/\/game\/1/, { timeout: 5_000 })

    const result = await playGameToWin(page)

    if (result === 'victory') {
      won = true
      break
    }

    // Lost or drew — go back to campaign and retry
    const backButton = page.locator('[data-testid="back-to-campaign"]')
    if (await backButton.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await backButton.click()
    } else {
      await page.goto('/')
    }
    await page.waitForURL('/', { timeout: 5_000 })
  }

  expect(won).toBe(true)

  // 6. After victory — click Back to Campaign
  await page.locator('[data-testid="back-to-campaign"]').click()
  await page.waitForURL('/', { timeout: 5_000 })

  // 7. Stage 2 should now be active (immediate — from newHighestUnlockedStage piggybacked)
  await expect(page.locator('[data-testid="stage-2"]')).toHaveAttribute('data-status', 'active')

  // 8. Logout
  await page.click('text=Logout')
  await page.waitForURL('/login', { timeout: 5_000 })

  // 9. Login with same credentials
  await page.fill('input[placeholder="Username"]', username)
  await page.fill('input[placeholder="Password"]', password)
  await page.click('button[type="submit"]')
  await page.waitForURL('/', { timeout: 10_000 })

  // 10. Stage 2 should still be active (proves DB persistence, not just in-memory)
  await expect(page.locator('[data-testid="stage-2"]')).toHaveAttribute('data-status', 'active')
})
