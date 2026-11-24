import { expect, test, type Page } from "@playwright/test";

// A 600 x 400 photo drawn by the browser: a wide image, so the crop to a square really cuts it.
async function photo(page: Page) {
  const dataUrl = await page.evaluate(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 600;
    canvas.height = 400;
    const context = canvas.getContext("2d")!;
    const gradient = context.createLinearGradient(0, 0, 600, 400);
    gradient.addColorStop(0, "#f97316");
    gradient.addColorStop(1, "#1d4ed8");
    context.fillStyle = gradient;
    context.fillRect(0, 0, 600, 400);
    context.fillStyle = "#fef3c7";
    context.beginPath();
    context.arc(300, 170, 90, 0, Math.PI * 2);
    context.fill();
    return canvas.toDataURL("image/jpeg", 0.9);
  });
  return Buffer.from(dataUrl.split(",")[1], "base64");
}

// Width and height from the IHDR header of a PNG.
function pngSize(bytes: Buffer) {
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}

test("uploads a cropped profile photo, shows it in a circle and removes it", async ({ page }, testInfo) => {
  const id = Date.now().toString(36);
  const card = page.getByRole("region", { name: "Profile photo" });
  const headerAvatar = page.getByRole("link", { name: "Your account" });

  await page.goto("/register");
  await page.getByLabel("Name", { exact: true }).fill("Photo User");
  await page.getByLabel("Username", { exact: true }).fill(`photo_${id}`);
  await page.getByLabel("Email", { exact: true }).fill(`photo.${id}@example.test`);
  await page.getByLabel("Password", { exact: true }).fill("A secure browser password 2026!");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL("/");
  await page.goto("/account");
  await expect(headerAvatar).toHaveText("PU");

  await test.step("Choose a wide photo and crop it with the mouse, the zoom and the keyboard", async () => {
    const chooser = page.waitForEvent("filechooser");
    await card.getByRole("button", { name: "Upload photo" }).click();
    await (await chooser).setFiles({ name: "holiday.jpg", mimeType: "image/jpeg", buffer: await photo(page) });

    const cropArea = card.getByRole("group", { name: "Visible part of the photo" });
    await expect(cropArea).toBeVisible();
    await card.getByLabel("Zoom").fill("1.5");
    const box = (await cropArea.boundingBox())!;
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width / 2 + 30, box.y + box.height / 2, { steps: 5 });
    await page.mouse.up();
    await cropArea.focus();
    await page.keyboard.press("ArrowLeft");
    await testInfo.attach("profile-photo-crop.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("Save it: the server stores a 512 px square and the header shows it", async () => {
    await card.getByRole("button", { name: "Save photo" }).click();
    await expect(card.getByRole("status")).toContainText("Photo saved");
    await expect(card.getByRole("button", { name: "Change photo" })).toBeVisible();

    const image = headerAvatar.locator("img");
    await expect(image).toHaveAttribute("src", /^\/api\/v1\/users\/me\/avatar\?v=[0-9a-f-]{36}$/);
    await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBe(512);
    const stored = await page.request.get((await image.getAttribute("src"))!);
    expect(stored.headers()["content-type"]).toBe("image/png");
    expect(pngSize(await stored.body())).toEqual({ width: 512, height: 512 });
    expect(await image.evaluate((element) => getComputedStyle(element.parentElement!).borderRadius))
      .not.toBe("0px");
    await testInfo.attach("profile-photo-saved.png", {
      body: await page.screenshot({ fullPage: true }),
      contentType: "image/png",
    });
  });

  await test.step("A file that is not a photo is refused in the browser", async () => {
    const chooser = page.waitForEvent("filechooser");
    await card.getByRole("button", { name: "Change photo" }).click();
    await (await chooser).setFiles({ name: "notes.txt", mimeType: "text/plain", buffer: Buffer.from("hello") });
    await expect(card.getByRole("alert")).toContainText("Choose a PNG, JPEG or WebP image.");
  });

  await test.step("Remove it and see the initials again", async () => {
    await card.getByRole("button", { name: "Remove photo" }).click();
    await expect(card.getByRole("status")).toContainText("Photo removed");
    await expect(headerAvatar).toHaveText("PU");
    await expect(headerAvatar.locator("img")).toHaveCount(0);
    expect((await page.request.get("/api/v1/users/me/avatar")).status()).toBe(404);
  });
});
