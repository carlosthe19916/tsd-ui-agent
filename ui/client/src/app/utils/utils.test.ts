import {
  formatDate,
  formatDateTime,
  duplicateFieldCheck,
  duplicateNameCheck,
  numStr,
  parseMaybeNumericString,
  getValidatedFromErrors,
  getValidatedFromError,
  localeNumericCompare,
  getString,
  universalComparator,
  parseBooleanIfPossible,
  toCamelCase,
  getToolbarChipKey,
  dedupeFunction,
  getFilenameFromContentDisposition,
} from "./utils";

describe("utils", () => {
  describe("getToolbarChipKey", () => {
    it("returns string value directly", () => {
      expect(getToolbarChipKey("test")).toBe("test");
    });

    it("returns key from ToolbarLabel object", () => {
      expect(getToolbarChipKey({ key: "myKey", node: "label" })).toBe("myKey");
    });
  });

  describe("formatDate", () => {
    it("formats a valid date string", () => {
      const result = formatDate("2025-06-15");
      expect(result).toContain("2025");
    });

    it("returns null for null input", () => {
      expect(formatDate(null)).toBeNull();
    });

    it("returns null for undefined input", () => {
      expect(formatDate(undefined)).toBeNull();
    });
  });

  describe("formatDateTime", () => {
    it("formats a valid datetime string", () => {
      const result = formatDateTime("2025-06-15T14:30:00Z");
      expect(result).toContain("2025");
    });

    it("returns null for null input", () => {
      expect(formatDateTime(null)).toBeNull();
    });
  });

  describe("duplicateFieldCheck", () => {
    const items = [
      { id: 1, name: "alpha" },
      { id: 2, name: "beta" },
    ];

    it("returns true when value is unique", () => {
      expect(duplicateFieldCheck("name", items, null, "gamma")).toBe(true);
    });

    it("returns false when value is duplicate", () => {
      expect(duplicateFieldCheck("name", items, null, "alpha")).toBe(false);
    });

    it("returns true when current item has same value (editing)", () => {
      expect(duplicateFieldCheck("name", items, items[0], "alpha")).toBe(true);
    });
  });

  describe("duplicateNameCheck", () => {
    const items = [{ name: "existing" }, { name: "another" }];

    it("returns true for unique name", () => {
      expect(duplicateNameCheck(items, null, "new-name")).toBe(true);
    });

    it("returns false for duplicate name", () => {
      expect(duplicateNameCheck(items, null, "existing")).toBe(false);
    });
  });

  describe("numStr", () => {
    it("converts number to string", () => {
      expect(numStr(42)).toBe("42");
    });

    it("returns empty string for undefined", () => {
      expect(numStr(undefined)).toBe("");
    });

    it("converts zero", () => {
      expect(numStr(0)).toBe("0");
    });
  });

  describe("parseMaybeNumericString", () => {
    it("returns number for numeric string", () => {
      expect(parseMaybeNumericString("42")).toBe(42);
    });

    it("returns string for non-numeric string", () => {
      expect(parseMaybeNumericString("hello")).toBe("hello");
    });

    it("returns null for null", () => {
      expect(parseMaybeNumericString(null)).toBeNull();
    });

    it("returns null for undefined", () => {
      expect(parseMaybeNumericString(undefined)).toBeNull();
    });
  });

  describe("getValidatedFromErrors", () => {
    it("returns error when error and dirty", () => {
      expect(getValidatedFromErrors("error", true, false)).toBe("error");
    });

    it("returns error when error and touched", () => {
      expect(getValidatedFromErrors("error", false, true)).toBe("error");
    });

    it("returns default when no error", () => {
      expect(getValidatedFromErrors(undefined, true, true)).toBe("default");
    });

    it("returns default when error but not dirty or touched", () => {
      expect(getValidatedFromErrors("error", false, false)).toBe("default");
    });
  });

  describe("getValidatedFromError", () => {
    it("returns error when error exists", () => {
      expect(getValidatedFromError("some error")).toBe("error");
    });

    it("returns default when no error", () => {
      expect(getValidatedFromError(undefined)).toBe("default");
    });
  });

  describe("localeNumericCompare", () => {
    it("compares strings with numeric awareness", () => {
      expect(localeNumericCompare("item2", "item10", "en")).toBeLessThan(0);
    });

    it("compares equal strings", () => {
      expect(localeNumericCompare("abc", "abc", "en")).toBe(0);
    });
  });

  describe("getString", () => {
    it("returns string directly", () => {
      expect(getString("hello")).toBe("hello");
    });

    it("calls function and returns result", () => {
      expect(getString(() => "computed")).toBe("computed");
    });
  });

  describe("getFilenameFromContentDisposition", () => {
    it("extracts filename from header", () => {
      expect(
        getFilenameFromContentDisposition('attachment; filename="report.pdf"'),
      ).toBe("report.pdf");
    });

    it("returns null when no filename", () => {
      expect(getFilenameFromContentDisposition("inline")).toBeNull();
    });
  });

  describe("universalComparator", () => {
    it("compares numbers directly", () => {
      expect(universalComparator(1, 2, "en")).toBeLessThan(0);
      expect(universalComparator(2, 1, "en")).toBeGreaterThan(0);
    });

    it("compares strings", () => {
      expect(universalComparator("a", "b", "en")).toBeLessThan(0);
    });

    it("handles null values", () => {
      expect(universalComparator(null, "a", "en")).toBeLessThan(0);
    });
  });

  describe("parseBooleanIfPossible", () => {
    it("parses true", () => {
      expect(parseBooleanIfPossible("true")).toBe(true);
    });

    it("parses TRUE (case insensitive)", () => {
      expect(parseBooleanIfPossible("TRUE")).toBe(true);
    });

    it("returns false for false string", () => {
      expect(parseBooleanIfPossible("false")).toBe(false);
    });

    it("returns false for undefined", () => {
      expect(parseBooleanIfPossible(undefined)).toBe(false);
    });
  });

  describe("toCamelCase", () => {
    it("capitalizes first letter", () => {
      expect(toCamelCase("hello")).toBe("Hello");
    });

    it("replaces underscore with space", () => {
      expect(toCamelCase("hello_world")).toBe("Hello world");
    });
  });

  describe("dedupeFunction", () => {
    it("removes duplicates by value", () => {
      const arr = [
        { value: "a", label: "A" },
        { value: "b", label: "B" },
        { value: "a", label: "A copy" },
      ];
      const result = dedupeFunction(arr);
      expect(result).toHaveLength(2);
    });

    it("returns empty array for empty input", () => {
      expect(dedupeFunction([])).toHaveLength(0);
    });
  });
});
