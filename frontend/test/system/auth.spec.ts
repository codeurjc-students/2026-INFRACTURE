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

test("edits the profile and credentials, then signs in with the new data", async ({ browser, page }, testInfo) => {
  const id = `${Date.now().toString(36)}`;
  const person = {
    displayName: "Editing User",
    username: `edit_${id}`,
    email: `edit.${id}@example.test`,
    password: "A secure browser password 2026!",
  };
  const newEmail = `moved.${person.email}`;
  const newPassword = "Another browser password 2027!";
  const section = (name: string) => page.getByRole("region", { name });

  await page.setViewportSize({ width: 360, height: 780 });
  await page.goto("/register");
  await page.getByLabel("Name", { exact: true }).fill(person.displayName);
  await page.getByLabel("Username", { exact: true }).fill(person.username);
  await page.getByLabel("Email", { exact: true }).fill(person.email);
  await page.getByLabel("Password", { exact: true }).fill(person.password);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL("/");

  const otherDevice = await browser.newContext({ baseURL: testInfo.project.use.baseURL });
  const otherPage = await otherDevice.newPage();
  await otherPage.goto("/login");
  await otherPage.getByLabel("Email", { exact: true }).fill(person.email);
  await otherPage.getByLabel("Password", { exact: true }).fill(person.password);
  await otherPage.getByRole("button", { name: "Sign in" }).click();
  await expect(otherPage).toHaveURL("/");

  await test.step("Edit the profile with the keyboard at mobile width", async () => {
    await page.goto("/account");
    const profile = section("Profile");
    await profile.getByLabel("Name", { exact: true }).fill("Edited User");
    await profile.getByLabel("Name", { exact: true }).press("Enter");
    await expect(profile.getByRole("status")).toContainText("Profile saved");
    await expect(page.getByRole("main").getByText("Edited User", { exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });

  await test.step("A wrong password keeps the new email and shows the error", async () => {
    const email = section("Email");
    await email.getByLabel("New email").fill(newEmail);
    await email.getByLabel("Current password").fill("Not my browser password 2026!");
    await email.getByRole("button", { name: "Change email" }).click();
    await expect(email.getByRole("alert")).toContainText("Current password is incorrect");
    await expect(email.getByLabel("New email")).toHaveValue(newEmail);
    await expect(email.getByLabel("Current password")).toHaveValue("");
    await testInfo.attach("account-error-mobile.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Change email and password while staying signed in", async () => {
    const email = section("Email");
    await email.getByLabel("Current password").fill(person.password);
    await email.getByRole("button", { name: "Change email" }).click();
    await expect(email.getByRole("status")).toContainText("Email changed");
    await expect(page.getByRole("main").getByText(newEmail, { exact: true })).toBeVisible();

    const password = section("Password");
    await password.getByLabel("Current password").fill(person.password);
    await password.getByLabel("New password").fill(newPassword);
    await password.getByRole("button", { name: "Change password" }).click();
    await expect(password.getByRole("status")).toContainText("Password changed");
    await page.reload();
    await expect(page).toHaveURL("/account");
    await testInfo.attach("account-saved-mobile.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("The other session has ended", async () => {
    await otherPage.goto("/account");
    await expect(otherPage).toHaveURL("/login?redirectTo=%2Faccount");
    await otherDevice.close();
  });

  await test.step("Sign out and sign in with the new data", async () => {
    await page.goto("/");
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page).toHaveURL("/login");
    await page.getByLabel("Email", { exact: true }).fill(newEmail);
    await page.getByLabel("Password", { exact: true }).fill(newPassword);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL("/");
    await page.goto("/account");
    await expect(page.getByRole("main").getByText(newEmail, { exact: true })).toBeVisible();
    await expect(page.getByRole("main").getByText("Edited User", { exact: true })).toBeVisible();
  });
});
