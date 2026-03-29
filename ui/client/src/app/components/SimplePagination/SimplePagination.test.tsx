import { render, screen } from "@testing-library/react";
import { SimplePagination } from "./SimplePagination";

describe("SimplePagination", () => {
  const defaultPaginationProps = {
    itemCount: 100,
    perPage: 10,
    page: 1,
    onSetPage: vi.fn(),
    onPerPageSelect: vi.fn(),
  };

  it("renders top pagination", () => {
    const { container } = render(
      <SimplePagination
        paginationProps={defaultPaginationProps}
        isTop={true}
      />,
    );

    expect(container.querySelector("#pagination-top")).toBeInTheDocument();
  });

  it("renders bottom pagination", () => {
    const { container } = render(
      <SimplePagination
        paginationProps={defaultPaginationProps}
        isTop={false}
      />,
    );

    expect(container.querySelector("#pagination-bottom")).toBeInTheDocument();
  });

  it("renders with id prefix", () => {
    const { container } = render(
      <SimplePagination
        paginationProps={defaultPaginationProps}
        isTop={true}
        idPrefix="my-table"
      />,
    );

    expect(
      container.querySelector("#my-table-pagination-top"),
    ).toBeInTheDocument();
  });

  it("renders compact when isCompact is true", () => {
    render(
      <SimplePagination
        paginationProps={defaultPaginationProps}
        isTop={true}
        isCompact={true}
      />,
    );

    expect(screen.getByLabelText(/pagination/i)).toBeInTheDocument();
  });
});
