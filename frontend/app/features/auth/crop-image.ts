export type CropArea = { x: number; y: number; width: number; height: number };

// Side of the uploaded avatar. The server accepts 128 to 1024 pixels, and a 512 px PNG stays far below
// its 2 MB limit whatever the photo was.
export const AVATAR_SIZE = 512;

// Draws the chosen square of the image, in the image's own pixels, onto a 512 px canvas and encodes it
// as PNG. The browser has already decoded the image to show it, so nothing is downloaded again.
export function cropToSquare(image: HTMLImageElement, area: CropArea): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = AVATAR_SIZE;
  canvas.height = AVATAR_SIZE;
  const context = canvas.getContext("2d");
  if (!context) {
    return Promise.reject(new Error("The browser cannot draw the image"));
  }
  context.imageSmoothingQuality = "high";
  context.drawImage(image, area.x, area.y, area.width, area.height, 0, 0, AVATAR_SIZE, AVATAR_SIZE);
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("The browser cannot encode the image"))),
      "image/png",
    );
  });
}
