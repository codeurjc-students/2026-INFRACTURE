import { afterEach, describe, expect, it, vi } from "vitest";

import { AVATAR_SIZE, cropToSquare } from "~/features/auth/crop-image";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("crop to square", () => {
  it("draws the chosen area of the photo on a 512 px square and encodes it as PNG", async () => {
    const drawImage = vi.fn();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
      drawImage,
    } as unknown as CanvasRenderingContext2D);
    const png = new Blob(["png"], { type: "image/png" });
    const toBlob = vi
      .spyOn(HTMLCanvasElement.prototype, "toBlob")
      .mockImplementation(function (this: HTMLCanvasElement, callback) {
        expect([this.width, this.height]).toEqual([AVATAR_SIZE, AVATAR_SIZE]);
        callback(png);
      });
    const image = document.createElement("img");

    await expect(cropToSquare(image, { x: 40, y: 10, width: 300, height: 300 })).resolves.toBe(png);

    expect(drawImage).toHaveBeenCalledWith(image, 40, 10, 300, 300, 0, 0, 512, 512);
    expect(toBlob).toHaveBeenCalledWith(expect.any(Function), "image/png");
  });

  it("fails when the browser cannot draw or encode the photo", async () => {
    const image = document.createElement("img");
    const area = { x: 0, y: 0, width: 10, height: 10 };
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);

    await expect(cropToSquare(image, area)).rejects.toThrow("cannot draw");

    getContext.mockReturnValue({ drawImage: vi.fn() } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation((callback) => callback(null));
    await expect(cropToSquare(image, area)).rejects.toThrow("cannot encode");
  });
});
