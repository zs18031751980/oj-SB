import { test, expect, request as pwRequest, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';

const API = 'http://127.0.0.1:6173';
const PSQL_CANDIDATES = [
  process.env.PSQL_BIN || '',
  '/nix/store/0m6z1g5zrgngyysmgj7qsvcz0xz8l8a9-postgresql-17.11/bin/psql',
  'psql',
];

let token = '';
let userInfo: Record<string, unknown> = {};
let announcementTitle = '';
let discussionTitle = '';
let contestTitle = '';

function psql(sql: string) {
  for (const bin of PSQL_CANDIDATES) {
    if (!bin) continue;
    try {
      execFileSync(bin, ['-h', '127.0.0.1', '-p', '5432', '-U', 'oj', '-d', 'oj', '-c', sql], { stdio: 'ignore' });
      return true;
    } catch { /* try next */ }
  }
  return false;
}

async function authenticate(page: Page) {
  await page.addInitScript(([t, i]) => {
    localStorage.setItem('auth_storage_mode', 'local');
    localStorage.setItem('access_token', t as string);
    localStorage.setItem('user_info', JSON.stringify(i));
  }, [token, userInfo] as const);
}

test.beforeAll(async () => {
  const ctx = await pwRequest.newContext();
  const login = await ctx.post(`${API}/auth/login/password`, {
    headers: { 'X-CSRF-Protection': '1' },
    data: { identifier: 'smoke', password: 'SmokePass123!', remember: true },
  });
  const body = await login.json();
  token = body.tokens.access_token;
  userInfo = body.user_info;
  const auth = { Authorization: `Bearer ${token}` };

  announcementTitle = `E2E公告-${Date.now()}`;
  await ctx.post(`${API}/announcement`, { headers: auth, data: {
    title: announcementTitle, content: '端到端测试公告内容', category: 'notice', permission: 'member', is_published: true } });

  discussionTitle = `E2E讨论-${Date.now()}`;
  await ctx.post(`${API}/discussions`, { headers: auth, data: {
    title: discussionTitle, content: '端到端测试讨论内容', category: 'general', tags: 'e2e' } });

  await ctx.post(`${API}/submissions`, { headers: auth, data: { problem_id: 1001, code: 'print(1)', language: 'python' } });
  await ctx.dispose();

  contestTitle = `E2E公开赛-${Date.now()}`;
  psql(`insert into contests (title, description, contest_type, status, start_time, end_time, is_public, lifecycle_state, created_by) `
    + `values ('${contestTitle}', '端到端测试比赛', 'ACM', 'ongoing', now() - interval '1 hour', now() + interval '1 hour', true, 'SCHEDULED', 1)`);
});

test('题库列表与题目详情从后端正常加载', async ({ page }) => {
  await authenticate(page);
  await page.goto('/problems');
  await expect(page.getByText('两数之和').first()).toBeVisible({ timeout: 20000 });
  await expect(page.locator('body')).toContainText('反转字符串');
  await page.getByText('两数之和').first().click();
  await expect(page).toHaveURL(/\/problems\/1001/, { timeout: 20000 });
  await expect(page.locator('body')).toContainText('输入格式');
});

test('比赛列表显示已发布比赛与实时状态', async ({ page }) => {
  await authenticate(page);
  await page.goto('/contests');
  await expect(page.getByText(contestTitle)).toBeVisible({ timeout: 20000 });
  await expect(page.locator('body')).toContainText('进行中');
});

test('公告页显示后端已发布公告', async ({ page }) => {
  await authenticate(page);
  await page.goto('/announcements');
  await expect(page.getByText(announcementTitle)).toBeVisible({ timeout: 20000 });
});

test('讨论区显示后端讨论', async ({ page }) => {
  await authenticate(page);
  await page.goto('/discussion');
  await expect(page.getByText(discussionTitle).first()).toBeVisible({ timeout: 20000 });
});

test('提交记录页显示后端历史提交', async ({ page }) => {
  await authenticate(page);
  await page.goto('/submissions');
  await expect(page.locator('body')).toContainText('两数之和', { timeout: 20000 });
});

test('个人资料页显示当前登录用户', async ({ page }) => {
  await authenticate(page);
  await page.goto('/profile');
  await expect(page.getByRole('button', { name: /编辑资料/ })).toBeVisible({ timeout: 30000 });
  await expect(page.locator('body')).toContainText(/smoke/i, { timeout: 30000 });
});

test('未登录访问受保护页面会被引导到登录页', async ({ page }) => {
  await page.goto('/favorites');
  await expect(page).toHaveURL(/\/login/, { timeout: 20000 });
});

test('可通过登录页使用本地账号密码登录', async ({ page }) => {
  await page.goto('/login');
  await page.getByPlaceholder('学号或账号').fill('smoke');
  await page.getByPlaceholder('密码').fill('SmokePass123!');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 30000 });
  await expect.poll(() => page.evaluate(() => localStorage.getItem('access_token'))).toBeTruthy();
});

test('题目收藏在详情页与收藏页之间保持一致', async ({ page }) => {
  await authenticate(page);
  await page.goto('/problems/1002');
  await expect(page.getByTitle('收藏题目')).toBeVisible({ timeout: 20000 });
  await page.getByTitle('收藏题目').click();
  await expect(page.getByTitle('取消收藏')).toBeVisible({ timeout: 20000 });
  await page.goto('/favorites');
  await expect(page.getByText('反转字符串').first()).toBeVisible({ timeout: 20000 });
  await page.goto('/problems/1002');
  await page.getByTitle('取消收藏').click();
  await expect(page.getByTitle('收藏题目')).toBeVisible({ timeout: 20000 });
});

test('讨论区可通过界面创建新讨论', async ({ page }) => {
  await authenticate(page);
  await page.goto('/discussion');
  await expect(page.getByRole('button', { name: /发布讨论/ })).toBeVisible({ timeout: 20000 });
  await page.getByRole('button', { name: /发布讨论/ }).click();
  const title = `E2E界面讨论-${Date.now()}`;
  await page.getByPlaceholder('请输入标题').fill(title);
  await page.getByPlaceholder('请输入内容...').fill('来自 Playwright 的端到端讨论内容');
  await page.getByRole('button', { name: '发布', exact: true }).click();
  await expect(page.getByText(title).first()).toBeVisible({ timeout: 20000 });
});

test('比赛详情页可进入并显示状态', async ({ page }) => {
  await authenticate(page);
  await page.goto('/contests');
  await page.locator('.ui-card', { hasText: contestTitle }).getByRole('button', { name: /进入比赛/ }).click();
  await expect(page).toHaveURL(/\/contests\/\d+/, { timeout: 20000 });
  await expect(page.locator('body')).toContainText(contestTitle);
});

test('管理后台用户与比赛页面可加载', async ({ page }) => {
  await authenticate(page);
  await page.goto('/admin/users');
  await expect(page.locator('body')).toContainText(/smoke/i, { timeout: 20000 });
  await page.goto('/admin/contests');
  await expect(page.getByText(contestTitle)).toBeVisible({ timeout: 20000 });
});

test('排行榜页面可加载', async ({ page }) => {
  await authenticate(page);
  await page.goto('/rankings');
  await expect(page.locator('body')).toContainText('排行榜', { timeout: 20000 });
});

test('个人资料可通过界面更新', async ({ page }) => {
  await authenticate(page);
  await page.goto('/profile');
  await page.getByRole('button', { name: /编辑资料/ }).click();
  const name = `E2E昵称-${Date.now()}`;
  await page.getByPlaceholder('你的昵称').fill(name);
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.locator('body')).toContainText(name, { timeout: 20000 });
});

test('管理后台可创建公告', async ({ page }) => {
  await authenticate(page);
  await page.goto('/admin/announcements');
  await page.getByRole('button', { name: /新建公告/ }).click();
  const title = `E2E后台公告-${Date.now()}`;
  await page.getByPlaceholder('公告标题').fill(title);
  await page.getByPlaceholder('输入 Markdown 内容...').fill('来自 Playwright 的公告内容');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.getByText(title).first()).toBeVisible({ timeout: 20000 });
});
