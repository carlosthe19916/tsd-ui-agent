import { render, screen, fireEvent } from "@testing-library/react";
import { ButtonVariant } from "@patternfly/react-core";
import { ConfirmDialog } from "./ConfirmDialog";

describe("ConfirmDialog", () => {
  const defaultProps = {
    isOpen: true,
    title: "Confirm Action",
    message: "Are you sure?",
    confirmBtnLabel: "Confirm",
    cancelBtnLabel: "Cancel",
    confirmBtnVariant: ButtonVariant.primary,
    onClose: vi.fn(),
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  };

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders dialog when isOpen is true", () => {
    render(<ConfirmDialog {...defaultProps} />);

    expect(screen.getByText("Confirm Action")).toBeInTheDocument();
    expect(screen.getByText("Are you sure?")).toBeInTheDocument();
  });

  it("does not render when isOpen is false", () => {
    render(<ConfirmDialog {...defaultProps} isOpen={false} />);

    expect(screen.queryByText("Confirm Action")).not.toBeInTheDocument();
  });

  it("calls onConfirm when confirm button is clicked", () => {
    const onConfirm = vi.fn();
    render(<ConfirmDialog {...defaultProps} onConfirm={onConfirm} />);

    fireEvent.click(screen.getByRole("button", { name: "confirm" }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("calls onCancel when cancel button is clicked", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />);

    fireEvent.click(screen.getByRole("button", { name: "cancel" }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("renders custom message content", () => {
    render(
      <ConfirmDialog
        {...defaultProps}
        message={<span data-testid="custom-msg">Custom message</span>}
      />,
    );

    expect(screen.getByTestId("custom-msg")).toBeInTheDocument();
  });

  it("disables buttons when inProgress is true", () => {
    render(<ConfirmDialog {...defaultProps} inProgress={true} />);

    expect(screen.getByRole("button", { name: "confirm" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "cancel" })).toBeDisabled();
  });

  it("renders without cancel button when onCancel is not provided", () => {
    render(<ConfirmDialog {...defaultProps} onCancel={undefined} />);

    expect(screen.getByRole("button", { name: "confirm" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "cancel" }),
    ).not.toBeInTheDocument();
  });

  it("renders with danger variant", () => {
    render(
      <ConfirmDialog
        {...defaultProps}
        confirmBtnVariant={ButtonVariant.danger}
        confirmBtnLabel="Delete"
      />,
    );

    expect(screen.getByRole("button", { name: "confirm" })).toBeInTheDocument();
  });
});
