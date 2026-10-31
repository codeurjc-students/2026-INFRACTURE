import { expect, test } from "@playwright/test";

test("renders the persisted catalogue through the real application", async ({
  page,
  request,
}, testInfo) => {
  await test.step("Doctor: disposable backend is healthy", async () => {
    const health = await request.get(
      "http://127.0.0.1:8081/actuator/health/readiness",
    );
    expect(health.ok()).toBeTruthy();
    expect(await health.json()).toMatchObject({ status: "UP" });
  });

  const expectedNames = [
    "HTTP Service",
    "PostgreSQL",
    "RabbitMQ",
    "Redis",
    "Worker",
    "Worker variant 1",
    "Worker variant 2",
    "Worker variant 3",
    "Worker variant 4",
    "Worker variant 5",
  ];
  const allNames = [...expectedNames, "Worker variant 6"];

  const templates =
    await test.step("Read the public catalogue API", async () => {
      const response = await request.get(
        "http://127.0.0.1:8081/api/v1/component-templates",
      );
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.content).toHaveLength(10);
      expect(body.page).toEqual({
        size: 10,
        number: 0,
        totalElements: 11,
        totalPages: 2,
      });
      expect(body.content.map((item: { name: string }) => item.name)).toEqual(
        expectedNames,
      );
      for (const item of body.content) {
        expect(Object.keys(item).sort()).toEqual([
          "contract",
          "initialConfiguration",
          "key",
          "name",
          "type",
        ]);
        expect(item.key).toEqual(expect.any(String));
        expect(item.type).toEqual(expect.any(String));
      }
      await testInfo.attach("catalogue-api.json", {
        body: JSON.stringify(body, null, 2),
        contentType: "application/json",
      });
      return body as {
        content: { key: string; name: string; type: string }[];
        page: {
          size: number;
          number: number;
          totalElements: number;
          totalPages: number;
        };
      };
    });

  await test.step("Open the catalogue through the browser and real API proxy", async () => {
    const responsePromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === "/api/v1/component-templates",
    );
    await page.goto("/catalogue");
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
    for (const template of templates.content) {
      const card = page.getByRole("listitem").filter({
        has: page.getByRole("heading", { name: template.name, exact: true }),
      });
      await expect(card.getByText(template.key, { exact: true })).toBeVisible();
      await expect(
        card.getByText(template.type, { exact: true }),
      ).toBeVisible();
    }
  });

  await test.step("Keep existing cards when the next page fails", async () => {
    await page.route("**/api/v1/component-templates?*", (route) => {
      if (new URL(route.request().url()).searchParams.get("page") === "1") {
        return route.abort("failed");
      }
      return route.continue();
    });
    await page.getByRole("button", { name: "More results" }).click();
    await expect(page.getByRole("alert")).toContainText(
      "Could not load more component templates.",
    );
    await expect(page.getByRole("listitem")).toHaveCount(10);
    await testInfo.attach("catalogue-later-error.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
    await page.unroute("**/api/v1/component-templates?*");
  });

  await test.step("Retry and load the final real page without duplicate cards", async () => {
    const nextResponse = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === "/api/v1/component-templates" &&
        url.searchParams.get("page") === "1"
      );
    });
    await page.getByRole("button", { name: "Try again" }).click();
    const response = await nextResponse;
    expect(response.status()).toBe(200);
    const lastPage = await response.json();
    expect(lastPage.content.map((item: { key: string }) => item.key)).toEqual([
      "worker-variant-6",
    ]);
    await expect(
      page.getByRole("list").getByRole("heading", { level: 2 }),
    ).toHaveText(allNames);
    await expect(page.getByRole("listitem")).toHaveCount(11);
    await expect(page.getByRole("alert")).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "More results" }),
    ).toHaveCount(0);
    await testInfo.attach("catalogue-all-pages.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
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

test("shows a missing page and allows returning to the catalogue", async ({
  page,
}, testInfo) => {
  await test.step("Open an unknown route", async () => {
    await page.goto("/verification-missing-page");
    await expect(
      page.getByRole("heading", { name: "Page not found", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText("The requested page could not be found."),
    ).toBeVisible();
    await testInfo.attach("missing-page.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });
  await test.step("Navigate back to the catalogue URL", async () => {
    await page.goto("/catalogue");
    await expect(
      page.getByRole("list").getByRole("heading", { level: 2 }),
    ).toHaveCount(10);
  });
});
