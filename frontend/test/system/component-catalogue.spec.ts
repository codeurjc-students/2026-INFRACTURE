import { expect, test } from "@playwright/test";

test("renders the persisted catalogue through the real application", async ({
  page,
  request,
}, testInfo) => {
  await test.step("Doctor: disposable backend is healthy", async () => {
    const health = await request.get("http://127.0.0.1:8081/actuator/health");
    expect(health.ok()).toBeTruthy();
    expect(await health.json()).toMatchObject({ status: "UP" });
  });

  const expectedNames = [
    "HTTP Service",
    "Load Generator",
    "PostgreSQL",
    "RabbitMQ",
    "Redis",
    "Worker",
  ];

  const templates = await test.step("Read the public catalogue API", async () => {
    const response = await request.get(
      "http://127.0.0.1:8081/api/v1/component-templates",
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveLength(6);
    expect(body.map((item: { name: string }) => item.name)).toEqual(expectedNames);
    for (const item of body) {
      expect(Object.keys(item).sort()).toEqual(["key", "name", "type"]);
      expect(item.key).toEqual(expect.any(String));
      expect(item.type).toEqual(expect.any(String));
    }
    await testInfo.attach("catalogue-api.json", {
      body: JSON.stringify(body, null, 2),
      contentType: "application/json",
    });
    return body as { key: string; name: string; type: string }[];
  });

  await test.step("Open the catalogue through the browser and real API proxy", async () => {
    const responsePromise = page.waitForResponse(
      (response) => new URL(response.url()).pathname === "/api/v1/component-templates",
    );
    await page.goto("/");
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(templates);
    await expect(page).toHaveTitle("Component catalogue | Infracture");
    await expect(
      page.getByRole("heading", { name: "Component catalogue", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("list").getByRole("heading", { level: 2 }),
    ).toHaveText(expectedNames);
    for (const template of templates) {
      const card = page.getByRole("listitem").filter({
        has: page.getByRole("heading", { name: template.name, exact: true }),
      });
      await expect(card.getByText(template.key, { exact: true })).toBeVisible();
      await expect(card.getByText(template.type, { exact: true })).toBeVisible();
    }
  });

  await test.step("Reload and retain the persisted catalogue", async () => {
    await page.reload();
    await expect(
      page.getByRole("list").getByRole("heading", { level: 2 }),
    ).toHaveText(expectedNames);
    await testInfo.attach("catalogue.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
    await testInfo.attach("catalogue.aria.txt", {
      body: await page.locator("body").ariaSnapshot(),
      contentType: "text/plain",
    });
  });
});

test("shows a missing page and allows returning to the catalogue", async ({ page }, testInfo) => {
  await test.step("Open an unknown route", async () => {
    await page.goto("/verification-missing-page");
    await expect(page.getByRole("heading", { name: "404", exact: true })).toBeVisible();
    await expect(page.getByText("The requested page could not be found.")).toBeVisible();
    await testInfo.attach("missing-page.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });
  await test.step("Navigate back to the catalogue URL", async () => {
    await page.goto("/");
    await expect(
      page.getByRole("list").getByRole("heading", { level: 2 }),
    ).toHaveCount(6);
  });
});
