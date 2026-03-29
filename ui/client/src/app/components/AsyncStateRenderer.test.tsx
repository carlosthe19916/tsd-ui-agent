import { render, screen } from "@testing-library/react";
import { AsyncStateRenderer } from "./AsyncStateRenderer";

describe("AsyncStateRenderer", () => {
  it("renders loading spinner when isLoading is true", () => {
    render(
      <AsyncStateRenderer isLoading={true} isError={false}>
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.queryByText("Content")).not.toBeInTheDocument();
  });

  it("renders custom loading content when provided", () => {
    render(
      <AsyncStateRenderer
        isLoading={true}
        isError={false}
        loadingContent={<div>Custom Loading...</div>}
      >
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.getByText("Custom Loading...")).toBeInTheDocument();
    expect(screen.queryByText("Content")).not.toBeInTheDocument();
  });

  it("renders error state when isError is true", () => {
    render(
      <AsyncStateRenderer isLoading={false} isError={true}>
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.queryByText("Content")).not.toBeInTheDocument();
  });

  it("renders custom error content when provided", () => {
    render(
      <AsyncStateRenderer
        isLoading={false}
        isError={true}
        errorContent={<div>Something went wrong!</div>}
      >
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.getByText("Something went wrong!")).toBeInTheDocument();
    expect(screen.queryByText("Content")).not.toBeInTheDocument();
  });

  it("renders empty state when isEmpty is true", () => {
    render(
      <AsyncStateRenderer isLoading={false} isError={false} isEmpty={true}>
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.queryByText("Content")).not.toBeInTheDocument();
  });

  it("renders custom empty content when provided", () => {
    render(
      <AsyncStateRenderer
        isLoading={false}
        isError={false}
        isEmpty={true}
        emptyContent={<div>No items found</div>}
      >
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.getByText("No items found")).toBeInTheDocument();
    expect(screen.queryByText("Content")).not.toBeInTheDocument();
  });

  it("renders children when data is available", () => {
    render(
      <AsyncStateRenderer isLoading={false} isError={false}>
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.getByText("Content")).toBeInTheDocument();
  });

  it("prioritizes loading over error state", () => {
    render(
      <AsyncStateRenderer isLoading={true} isError={true}>
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.queryByText("Content")).not.toBeInTheDocument();
  });

  it("prioritizes error over empty state", () => {
    render(
      <AsyncStateRenderer
        isLoading={false}
        isError={true}
        isEmpty={true}
        errorContent={<div>Error occurred</div>}
      >
        <div>Content</div>
      </AsyncStateRenderer>,
    );

    expect(screen.getByText("Error occurred")).toBeInTheDocument();
  });
});
