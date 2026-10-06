import { expect, test } from '@playwright/test';

test('抽奖页样式不会覆盖主站登录按钮', async ({ page }) => {
  const styleOf = (el: HTMLElement) => {
    const s = getComputedStyle(el);
    return { bg: s.backgroundColor, radius: s.borderRadius, shadow: s.boxShadow };
  };
  await page.goto('/');
  const loginButton = page.getByRole('button', { name: '登录' });
  const before = await loginButton.evaluate(styleOf);
  // 访问抽奖页后回到主站，按钮样式必须保持一致（不被抽奖页作用域样式污染）
  await page.goto('/chou');
  await page.goto('/');
  const after = await loginButton.evaluate(styleOf);
  expect(after).toEqual(before);
  expect(after.bg).not.toBe('rgba(0, 0, 0, 0)');
});

test('题库使用克制的组件字重与圆角', async ({ page }) => {
  await page.goto('/problems');

  await expect(page.getByRole('heading', { name: '在线题库' })).toHaveCSS('font-weight', '600');
  const sidebarAllProblems = page.getByRole('button', { name: '全部题目', exact: true }).first();
  await expect(sidebarAllProblems).toHaveCSS('font-weight', '500');
  await expect(sidebarAllProblems).toHaveCSS('border-radius', '6px');
});

test('题库以工具栏和独立筛选卡呈现主要操作', async ({ page }) => {
  await page.goto('/problems');

  await expect(page.getByTestId('problem-filter-card')).toBeVisible();
  await expect(page.getByTestId('problem-toolbar')).toBeVisible();
  const activeSegmented = page.getByRole('button', { name: '全部题目', exact: true }).last();
  await expect(activeSegmented).toHaveAttribute('aria-pressed', 'true');
});
