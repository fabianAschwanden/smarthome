import { expect, test } from '@playwright/test';

test('App-Shell und Notes-Seite laden', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'smarthome' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Notes' })).toBeVisible();
});
