import dayjs from "dayjs";

import { RENDER_DATETIME_FORMAT, RENDER_DATE_FORMAT } from "@app/Constants";
import type { ToolbarLabel } from "@patternfly/react-core";

// ToolbarChip

export const getToolbarChipKey = (value: string | ToolbarLabel) => {
  return typeof value === "string" ? value : value.key;
};

// Dates

export const formatDate = (value?: string | null) => {
  return value ? dayjs(value).format(RENDER_DATE_FORMAT) : null;
};

export const formatDateTime = (value?: string | null) => {
  return value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : null;
};

export const duplicateFieldCheck = <T>(
  fieldKey: keyof T,
  itemList: T[],
  currentItem: T | null,
  fieldValue: T[keyof T],
) =>
  (currentItem && currentItem[fieldKey] === fieldValue) ||
  !itemList.some((item) => item[fieldKey] === fieldValue);

export const duplicateNameCheck = <T extends { name?: string }>(
  itemList: T[],
  currentItem: T | null,
  nameValue: T["name"],
) => duplicateFieldCheck("name", itemList, currentItem, nameValue);

// biome-ignore lint/suspicious/noExplicitAny: allowed
export const dedupeFunction = (arr: any[]) =>
  arr?.filter(
    (value, index, self) =>
      index === self.findIndex((t) => t.value === value.value),
  );

export const numStr = (num: number | undefined): string => {
  if (num === undefined) return "";
  return String(num);
};

export const parseMaybeNumericString = (
  numOrStr: string | undefined | null,
): string | number | null => {
  if (numOrStr === undefined || numOrStr === null) return null;
  const num = Number(numOrStr);
  return Number.isNaN(num) ? numOrStr : num;
};

// biome-ignore lint/complexity/noBannedTypes: safe to use
export const objectKeys = <T extends Object>(obj: T) =>
  Object.keys(obj) as (keyof T)[];

export const getValidatedFromErrors = (
  error: unknown | undefined,
  dirty: boolean | undefined,
  isTouched: boolean | undefined,
) => {
  return error && (dirty || isTouched) ? "error" : "default";
};

export const getValidatedFromError = (error: unknown | undefined) => {
  return error ? "error" : "default";
};

/**
 * Uses native string localCompare method with numeric option enabled.
 *
 * @param locale to be used by string compareFn
 */
export const localeNumericCompare = (
  a: string,
  b: string,
  locale: string,
): number => a.localeCompare(b, locale ?? "en", { numeric: true });

export const getString = (input: string | (() => string)) =>
  typeof input === "function" ? input() : input;

export const getFilenameFromContentDisposition = (
  contentDisposition: string,
): string | null => {
  const match = contentDisposition.match(/filename="?([^"]+)"?/);
  return match ? match[1] : null;
};

/**
 * Compares all types by converting them to string.
 * Nullish entities are converted to empty string.
 * @see localeNumericCompare
 * @param locale to be used by string compareFn
 */
export const universalComparator = (
  // biome-ignore lint/suspicious/noExplicitAny: allowed
  a: any,
  // biome-ignore lint/suspicious/noExplicitAny: allowed
  b: any,
  locale: string,
) => {
  if (typeof a === "number" && typeof b === "number") {
    return a - b;
  }
  return localeNumericCompare(String(a ?? ""), String(b ?? ""), locale);
};

export const parseBooleanIfPossible = (value?: string): boolean => {
  return value?.toLocaleLowerCase() === "true";
};

export const toCamelCase = (value: string) => {
  return value.charAt(0).toUpperCase() + value.slice(1).replace("_", " ");
};
