import { expect, test, type Page } from "@playwright/test";

const suffix = Date.now().toString(36);
const account = {
  displayName: "Browser User",
  username: `ui_${suffix}`,
  email: `ui.${suffix}@example.test`,
  password: "A secure browser password 2026!",
};

async function signIn(page: Page) {
  await page.getByLabel("Email", { exact: true }).fill(account.email);
  await page.getByLabel("Password", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

async function sessionCookies(page: Page) {
  return (await page.context().cookies())
    .filter((cookie) => cookie.name === "AuthToken" || cookie.name === "RefreshToken");
}

test("registers, signs out and returns to a private page after signing in again", async ({
  page,
}, testInfo) => {
  await test.step("Register and arrive signed in", async () => {
    await page.goto("/register");
    await page.getByLabel("Name", { exact: true }).fill(account.displayName);
    await page.getByLabel("Username", { exact: true }).fill(account.username);
    await page.getByLabel("Email", { exact: true }).fill(account.email);
    await page.getByLabel("Password", { exact: true }).fill(account.password);
    await page.getByRole("button", { name: "Create account" }).click();

    await expect(page).toHaveURL("/");
    await expect(page.getByRole("link", { name: account.displayName })).toBeVisible();
    expect((await sessionCookies(page)).map(({ name, httpOnly }) => [name, httpOnly]).sort())
      .toEqual([["AuthToken", true], ["RefreshToken", true]]);
  });

  await test.step("Sign out, clear the session cookies and reject private requests", async () => {
    await page.getByRole("button", { name: "Sign out" }).click();

    await expect(page).toHaveURL("/login");
    expect(await sessionCookies(page)).toEqual([]);
    expect((await page.request.get("/api/v1/users/me")).status()).toBe(401);
  });

  await test.step("Open a private page and come back to it after signing in", async () => {
    await page.goto("/account");
    await expect(page).toHaveURL("/login?redirectTo=%2Faccount");

    await signIn(page);
    await expect(page).toHaveURL("/account");
    await expect(page.getByText(account.email)).toBeVisible();
    await testInfo.attach("account.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Stay signed in when the access token expires", async () => {
    const [authToken] = (await sessionCookies(page)).filter((cookie) => cookie.name === "AuthToken");
    await page.context().addCookies([{ ...authToken, value: "stale-or-expired-token" }]);

    await page.reload();
    await expect(page).toHaveURL("/account");
    await expect(page.getByText(account.email)).toBeVisible();
    const [renewed] = (await sessionCookies(page)).filter((cookie) => cookie.name === "AuthToken");
    expect(renewed.value).not.toBe("stale-or-expired-token");
  });

  await test.step("Recover from a session the backend no longer accepts", async () => {
    await page.context().addCookies(
      (await sessionCookies(page)).map((cookie) => ({ ...cookie, value: "stale-or-expired-token" })),
    );

    await page.reload();
    await expect(page).toHaveURL("/login?redirectTo=%2Faccount");
    await testInfo.attach("login-recovery.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });

    await signIn(page);
    await expect(page).toHaveURL("/account");
  });
});
