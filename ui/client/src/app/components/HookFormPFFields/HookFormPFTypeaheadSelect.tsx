import React from "react";
import type { FieldValues, Path } from "react-hook-form";

import {
  Button,
  MenuToggle,
  type MenuToggleElement,
  Select,
  SelectList,
  SelectOption,
  TextInputGroup,
  TextInputGroupMain,
  TextInputGroupUtilities,
} from "@patternfly/react-core";
import { TimesIcon } from "@patternfly/react-icons";

import {
  type BaseHookFormPFGroupControllerProps,
  HookFormPFGroupController,
} from "./HookFormPFGroupController";

export type HookFormPFTypeaheadSelectProps<
  TFieldValues extends FieldValues,
  TName extends Path<TFieldValues>,
> = BaseHookFormPFGroupControllerProps<TFieldValues, TName> & {
  options: string[];
  placeholder?: string;
};

export const HookFormPFTypeaheadSelect = <
  TFieldValues extends FieldValues = FieldValues,
  TName extends Path<TFieldValues> = Path<TFieldValues>,
>(
  props: HookFormPFTypeaheadSelectProps<TFieldValues, TName>,
) => {
  const { options, placeholder, ...groupControllerProps } = props;

  return (
    <HookFormPFGroupController<TFieldValues, TName>
      {...groupControllerProps}
      renderInput={({ field }) => (
        <TypeaheadSelectInput
          options={options}
          placeholder={placeholder}
          value={field.value as string}
          onChange={(val) => field.onChange(val as TFieldValues[TName])}
          fieldId={props.fieldId}
        />
      )}
    />
  );
};

export interface TypeaheadSelectInputProps {
  options: string[];
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  fieldId: string;
}

const CREATE_OPTION_PREFIX = "create:";

export const TypeaheadSelectInput: React.FC<TypeaheadSelectInputProps> = ({
  options,
  placeholder,
  value,
  onChange,
  fieldId,
}) => {
  const [isOpen, setIsOpen] = React.useState(false);
  const [inputValue, setInputValue] = React.useState(value ?? "");
  const textInputRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    setInputValue(value ?? "");
  }, [value]);

  const filteredOptions = options.filter((opt) =>
    opt.toLowerCase().includes(inputValue?.toLowerCase().trim() ?? ""),
  );

  const hasExactMatch = options.some(
    (opt) => opt.toLowerCase() === inputValue?.toLowerCase().trim(),
  );

  const onSelect = (
    _event: React.MouseEvent | undefined,
    selection: string | number | undefined,
  ) => {
    if (typeof selection !== "string") return;

    if (selection.startsWith(CREATE_OPTION_PREFIX)) {
      const created = selection.slice(CREATE_OPTION_PREFIX.length);
      onChange(created);
      setInputValue(created);
    } else {
      onChange(selection);
      setInputValue(selection);
    }
    setIsOpen(false);
    textInputRef.current?.focus();
  };

  const onTextInputChange = (
    _event: React.FormEvent<HTMLInputElement>,
    val: string,
  ) => {
    setInputValue(val);
    if (!isOpen) {
      setIsOpen(true);
    }
  };

  const onInputKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    switch (event.key) {
      case "Enter":
        if (!isOpen) {
          setIsOpen(true);
        } else if (inputValue && !hasExactMatch) {
          onChange(inputValue);
          setIsOpen(false);
        }
        break;
      case "Escape":
      case "Tab":
        setIsOpen(false);
        if (inputValue) {
          onChange(inputValue);
        }
        break;
    }
  };

  const onClear = () => {
    setInputValue("");
    onChange("" as string);
    textInputRef.current?.focus();
  };

  const toggle = (toggleRef: React.Ref<MenuToggleElement>) => (
    <MenuToggle
      ref={toggleRef}
      variant="typeahead"
      onClick={() => setIsOpen(!isOpen)}
      isExpanded={isOpen}
      isFullWidth
    >
      <TextInputGroup isPlain>
        <TextInputGroupMain
          value={inputValue}
          onClick={() => {
            if (!isOpen) setIsOpen(true);
          }}
          onChange={onTextInputChange}
          onKeyDown={onInputKeyDown}
          id={`${fieldId}-typeahead-input`}
          autoComplete="off"
          innerRef={textInputRef}
          placeholder={placeholder}
          role="combobox"
          isExpanded={isOpen}
          aria-controls={`${fieldId}-typeahead-listbox`}
        />
        <TextInputGroupUtilities>
          {!!inputValue && (
            <Button
              variant="plain"
              onClick={onClear}
              aria-label="Clear input value"
            >
              <TimesIcon aria-hidden />
            </Button>
          )}
        </TextInputGroupUtilities>
      </TextInputGroup>
    </MenuToggle>
  );

  return (
    <Select
      id={`${fieldId}-typeahead-select`}
      isOpen={isOpen}
      selected={value}
      onSelect={onSelect}
      onOpenChange={(open) => setIsOpen(open)}
      toggle={toggle}
    >
      <SelectList id={`${fieldId}-typeahead-listbox`}>
        {filteredOptions.map((opt) => (
          <SelectOption key={opt} value={opt}>
            {opt}
          </SelectOption>
        ))}
        {inputValue.trim() && !hasExactMatch && (
          <SelectOption
            key="create"
            value={`${CREATE_OPTION_PREFIX}${inputValue}`}
          >
            {`Create "${inputValue}"`}
          </SelectOption>
        )}
        {!filteredOptions.length && !inputValue.trim() && (
          <SelectOption isDisabled value="no-results">
            No options available
          </SelectOption>
        )}
      </SelectList>
    </Select>
  );
};
