import { expect, test } from "@playwright/test";

test("renders the persisted catalogue through the real application", async ({
  page,
}) => {
  await page.goto("/");

  await expect(
    page.getByRole("heading", { name: "Component catalogue" }),
  ).toBeVisible();
  await expect(
    page.getByRole("list").getByRole("heading", { level: 2 }),
  ).toHaveText([
    "HTTP Service",
    "Load Generator",
    "PostgreSQL",
    "RabbitMQ",
    "Redis",
    "Worker",
  ]);
});
