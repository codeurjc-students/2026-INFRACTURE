import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";

import Home, { clientLoader } from "~/routes/home";

it("renders catalogue data persisted by the real backend", async () => {
  const loaderData = await clientLoader();

  expect(
    loaderData.componentTemplates,
    "Contract failure: the API did not return the seeded HTTP Service template",
  ).toEqual(
    expect.arrayContaining([
      expect.objectContaining({
        key: "http-service",
        name: "HTTP Service",
        type: "HTTP_SERVICE",
      }),
    ]),
  );

  render(<Home loaderData={loaderData} />);

  expect(
    screen.getByRole("heading", { name: "HTTP Service" }),
    "Rendering failure: the persisted template did not reach the catalogue view",
  ).toBeInTheDocument();
});
