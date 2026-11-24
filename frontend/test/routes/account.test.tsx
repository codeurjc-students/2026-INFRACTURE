import { fireEvent, render, screen, within } from "@testing-library/react";
import { Blob as NodeBlob, File as NodeFile } from "node:buffer";
import { useEffect } from "react";
import { createMemoryRouter, RouterProvider, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  changeEmail,
  changePassword,
  getCurrentUser,
  removeAvatar,
  updateProfile,
  uploadAvatar,
} from "~/features/auth/api/auth-service";
import { cropToSquare } from "~/features/auth/crop-image";
import type { UserDTO } from "~/features/auth/model/UserDTO";
import AccountRoute, { clientAction as accountAction } from "~/routes/account";
import PrivateLayout, { clientLoader } from "~/routes/private-layout";
import { HttpError } from "~/shared/api/api-fetch";

vi.mock("~/features/auth/api/auth-service");
vi.mock("~/features/auth/crop-image");

// jsdom loads no images, so the cropper never measures one. This stand-in reports a crop at once.
const CROPPED_AREA = { x: 20, y: 0, width: 400, height: 400 };
vi.mock("react-easy-crop", () => ({
  default: function Cropper(props: {
    onCropComplete: (area: unknown, pixels: unknown) => void;
    setImageRef: (ref: { current: HTMLImageElement }) => void;
    mediaProps: { onError: () => void };
    image: string;
  }) {
    const { onCropComplete, setImageRef } = props;
    useEffect(() => {
      setImageRef({ current: document.createElement("img") });
      onCropComplete({}, CROPPED_AREA);
    }, [onCropComplete, setImageRef]);
    return <img alt="Photo to crop" src={props.image} onError={props.mediaProps.onError} />;
  },
}));

const activeRouters: ReturnType<typeof createMemoryRouter>[] = [];

const privateLoader = ({ request }: { request: Request }) =>
  clientLoader({ request } as Parameters<typeof clientLoader>[0]);

function LoginPage() {
  const { search } = useLocation();

  return <p>Login page {new URLSearchParams(search).get("redirectTo")}</p>;
}

function renderAccount() {
  const router = createMemoryRouter(
    [
      {
        id: "routes/private-layout",
        loader: privateLoader,
        Component: PrivateLayout,
        children: [
          {
            path: "/account",
            Component: AccountRoute,
            action: ({ request }) =>
              accountAction({ request } as Parameters<typeof accountAction>[0]),
          },
        ],
      },
      { path: "/login", Component: LoginPage },
    ],
    { initialEntries: ["/account"] },
  );
  activeRouters.push(router);
  render(<RouterProvider router={router} />);
}

beforeEach(() => {
  vi.stubGlobal("URL", Object.assign(URL, {
    createObjectURL: vi.fn(() => "blob:photo"),
    revokeObjectURL: vi.fn(),
  }));
});

afterEach(() => {
  activeRouters.splice(0).forEach((router) => router.dispose());
  vi.resetAllMocks();
  vi.unstubAllGlobals();
});

const account = {
  id: 7,
  username: "alice",
  displayName: "Alice",
  email: "alice@example.com",
} as UserDTO;

function fill(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } });
}

async function section(name: string) {
  return within(await screen.findByRole("region", { name }));
}

describe("account forms", () => {
  it("saves the profile and shows the refreshed account", async () => {
    const saved = { ...account, displayName: "Alice Cooper", username: "alice_2" };
    vi.mocked(getCurrentUser).mockResolvedValueOnce(account).mockResolvedValue(saved);
    vi.mocked(updateProfile).mockResolvedValue(saved);
    renderAccount();
    const profile = await section("Profile");

    fill(profile.getByLabelText("Name"), "Alice Cooper");
    fill(profile.getByLabelText("Username"), "ALICE_2");
    fireEvent.click(profile.getByRole("button", { name: "Save profile" }));

    expect(await profile.findByText("Profile saved")).toBeInTheDocument();
    expect(profile.getByRole("status")).toHaveFocus();
    expect(updateProfile).toHaveBeenCalledWith({ displayName: "Alice Cooper", username: "ALICE_2" });
    expect(await screen.findByText("alice_2")).toBeInTheDocument();
    expect(profile.getByLabelText("Username")).toHaveValue("alice_2");
  });

  it("keeps the new email, clears the password and shows the conflict without success", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(changeEmail).mockRejectedValue(new HttpError(409, "Email is already in use"));
    renderAccount();
    const email = await section("Email");

    fill(email.getByLabelText("New email"), "taken@example.com");
    fill(email.getByLabelText("Current password"), "my current secret");
    fireEvent.click(email.getByRole("button", { name: "Change email" }));

    expect(await email.findByRole("alert")).toHaveTextContent("Email is already in use");
    expect(email.queryByRole("status")).not.toBeInTheDocument();
    expect(email.getByLabelText("New email")).toHaveValue("taken@example.com");
    expect(email.getByLabelText("Current password")).toHaveValue("");
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
  });

  it("clears both passwords after a rejected and after a saved change", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(changePassword)
      .mockRejectedValueOnce(new HttpError(400, "Current password is incorrect"))
      .mockResolvedValueOnce(account);
    renderAccount();
    const password = await section("Password");

    fill(password.getByLabelText("Current password"), "wrong secret");
    fill(password.getByLabelText("New password"), "A new secret 2027!");
    fireEvent.click(password.getByRole("button", { name: "Change password" }));

    expect(await password.findByRole("alert")).toHaveTextContent("Current password is incorrect");
    expect(password.getByLabelText("Current password")).toHaveValue("");
    expect(password.getByLabelText("New password")).toHaveValue("");

    fill(password.getByLabelText("Current password"), "right secret");
    fill(password.getByLabelText("New password"), "A new secret 2027!");
    fireEvent.click(password.getByRole("button", { name: "Change password" }));

    expect(await password.findByText("Password changed")).toBeInTheDocument();
    expect(password.getByRole("status")).toHaveFocus();
    expect(password.queryByRole("alert")).not.toBeInTheDocument();
    expect(password.getByLabelText("Current password")).toHaveValue("");
    expect(password.getByLabelText("New password")).toHaveValue("");
  });

  it("moves keyboard focus to the result of the save", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(updateProfile).mockRejectedValue(new HttpError(409, "Username is already in use"));
    renderAccount();
    const profile = await section("Profile");

    fireEvent.click(profile.getByRole("button", { name: "Save profile" }));

    expect(await profile.findByRole("alert")).toHaveFocus();
  });

  it("does not show a server failure as a saved change", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(updateProfile).mockRejectedValue(new HttpError(500, "Internal detail"));
    renderAccount();
    const profile = await section("Profile");

    fireEvent.click(profile.getByRole("button", { name: "Save profile" }));

    expect(await profile.findByRole("alert")).toHaveTextContent("Something went wrong");
    expect(profile.queryByText("Internal detail")).not.toBeInTheDocument();
    expect(profile.queryByRole("status")).not.toBeInTheDocument();
  });

  it("sends an expired session to the login and back to the account", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    vi.mocked(updateProfile).mockRejectedValue(new HttpError(401, "Authentication is required."));
    renderAccount();
    const profile = await section("Profile");

    fireEvent.click(profile.getByRole("button", { name: "Save profile" }));

    expect(await screen.findByText("Login page /account")).toBeInTheDocument();
  });
});

describe("private account page", () => {
  it("shows the signed-in account", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({
      username: "alice",
      displayName: "Alice",
      email: "alice@example.com",
    } as UserDTO);
    renderAccount();

    expect(
      await screen.findByRole("heading", { name: "Your account" }),
    ).toBeInTheDocument();
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Your account" }),
    ).toHaveTextContent("A");
  });

  it("sends a visitor without a session to the login and remembers the page", async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new HttpError(401, "Unauthorized"),
    );
    renderAccount();

    expect(await screen.findByText("Login page /account")).toBeInTheDocument();
  });
});

describe("profile photo", () => {
  const photo = new File(["raw photo"], "me.jpg", { type: "image/jpeg" });
  const cropped = new NodeBlob(["cropped png"], { type: "image/png" }) as unknown as Blob;

  // The router carries the upload to the action in a Request, which is Node's. jsdom swaps in its own
  // FormData, Blob and File, which Node's Request cannot read, so these tests use Node's (a browser has
  // only one kind).
  beforeEach(async () => {
    const nodeFormData = (await new Response(new URLSearchParams()).formData()).constructor;
    vi.stubGlobal("FormData", nodeFormData);
    vi.stubGlobal("Blob", NodeBlob);
    vi.stubGlobal("File", NodeFile);
  });
  const withPhoto = { ...account, avatarUrl: "/api/v1/users/me/avatar?v=new" };

  // The button opens the hidden file input; the test hands the file to that input directly.
  function choose(file: File) {
    const input = screen
      .getByRole("region", { name: "Profile photo" })
      .querySelector<HTMLInputElement>("input[type=file]")!;
    fireEvent.change(input, { target: { files: [file] } });
  }

  it("crops the chosen photo, uploads the square and shows it in the header", async () => {
    vi.mocked(getCurrentUser).mockResolvedValueOnce({ ...account, avatarUrl: null }).mockResolvedValue(withPhoto);
    vi.mocked(cropToSquare).mockResolvedValue(cropped);
    vi.mocked(uploadAvatar).mockResolvedValue(withPhoto);
    renderAccount();
    const card = await section("Profile photo");
    expect(card.getByRole("button", { name: "Upload photo" })).toBeInTheDocument();
    expect(card.queryByRole("button", { name: "Remove photo" })).not.toBeInTheDocument();

    choose(photo);
    fireEvent.change(card.getByLabelText("Zoom"), { target: { value: "2" } });
    fireEvent.click(card.getByRole("button", { name: "Save photo" }));

    expect(await card.findByText("Photo saved")).toBeInTheDocument();
    expect(card.getByRole("status")).toHaveFocus();
    expect(cropToSquare).toHaveBeenCalledWith(expect.any(HTMLImageElement), CROPPED_AREA);
    expect(await (vi.mocked(uploadAvatar).mock.calls[0][0] as Blob).text()).toBe("cropped png");
    expect(card.queryByLabelText("Zoom")).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:photo");
    await screen.findByRole("button", { name: "Change photo" });
    expect(screen.getByRole("link", { name: "Your account" }).querySelector("img"))
      .toHaveAttribute("src", "/api/v1/users/me/avatar?v=new");
  });

  it("keeps the cropper and the current photo when the storage is down", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(withPhoto);
    vi.mocked(cropToSquare).mockResolvedValue(cropped);
    vi.mocked(uploadAvatar).mockRejectedValue(
      new HttpError(503, "Image storage is unavailable. Try again later."),
    );
    renderAccount();
    const card = await section("Profile photo");

    choose(photo);
    fireEvent.click(card.getByRole("button", { name: "Save photo" }));

    expect(await card.findByRole("alert")).toHaveTextContent("Image storage is unavailable. Try again later.");
    expect(card.getByRole("alert")).toHaveFocus();
    expect(card.getByRole("button", { name: "Save photo" })).toBeEnabled();

    fireEvent.click(card.getByRole("button", { name: "Cancel" }));
    expect(card.getByRole("button", { name: "Change photo" })).toBeInTheDocument();
    expect(card.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("removes the photo and shows the initials again", async () => {
    vi.mocked(getCurrentUser).mockResolvedValueOnce(withPhoto).mockResolvedValue({ ...account, avatarUrl: null });
    vi.mocked(removeAvatar).mockResolvedValue({ ...account, avatarUrl: null });
    renderAccount();
    const card = await section("Profile photo");

    fireEvent.click(card.getByRole("button", { name: "Remove photo" }));

    expect(await card.findByText("Photo removed")).toBeInTheDocument();
    expect(removeAvatar).toHaveBeenCalled();
    expect(await card.findByRole("button", { name: "Upload photo" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Your account" })).toHaveTextContent("A");
  });

  it("refuses files that are not photos without sending anything", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    renderAccount();
    const card = await section("Profile photo");

    choose(new File(["<svg/>"], "logo.svg", { type: "image/svg+xml" }));

    expect(card.getByRole("alert")).toHaveTextContent("Choose a PNG, JPEG or WebP image.");
    expect(card.queryByLabelText("Zoom")).not.toBeInTheDocument();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    expect(uploadAvatar).not.toHaveBeenCalled();
  });

  it("says when the chosen file cannot be read as an image", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(account);
    renderAccount();
    const card = await section("Profile photo");

    choose(new File(["not really"], "broken.png", { type: "image/png" }));
    fireEvent.error(card.getByAltText("Photo to crop"));

    expect(card.getByRole("alert")).toHaveTextContent("This image could not be read. Choose another one.");
    expect(card.getByRole("button", { name: "Upload photo" })).toBeInTheDocument();
  });
});
