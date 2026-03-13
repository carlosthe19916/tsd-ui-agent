import React, { useState } from "react";

import { useLocalStorage } from "@app/hooks/useStorage";

const DARK_MODE_KEY = "pf-v6-theme-dark";
const STORAGE_KEY = "theme-preference";

export const THEME_MODES = {
  SYSTEM: "system",
  LIGHT: "light",
  DARK: "dark",
} as const;

export type ThemeMode = (typeof THEME_MODES)[keyof typeof THEME_MODES];

const getSystemTheme = (): Exclude<ThemeMode, "system"> => {
  if (typeof window === "undefined" || !window.matchMedia) return "light";
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
};

const isThemeModeValid = (value: string): value is ThemeMode => {
  return ["system", "light", "dark"].includes(value);
};

interface ThemeState {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  isDark: boolean;
}

export const ThemeContext = React.createContext<ThemeState>({
  mode: "system",
  setMode: () => {},
  isDark: false,
});

interface IThemeProviderProps {
  children: React.ReactNode;
}
export const ThemeProvider: React.FC<IThemeProviderProps> = ({ children }) => {
  const [mode, setMode] = useLocalStorage<ThemeMode>({
    key: STORAGE_KEY,
    defaultValue: "system",
  });

  const sanitizedMode: ThemeMode = isThemeModeValid(mode) ? mode : "system";

  const setSanitizedMode = React.useCallback(
    (value: string) => {
      if (value && isThemeModeValid(value)) {
        setMode(value);
      } else {
        setMode("system");
      }
    },
    [setMode],
  );

  const [systemTheme, setSystemTheme] = useState<"light" | "dark">(
    getSystemTheme,
  );

  const isDark =
    sanitizedMode === THEME_MODES.DARK ||
    (sanitizedMode === THEME_MODES.SYSTEM && systemTheme === "dark");

  React.useEffect(() => {
    const htmlElement = document.documentElement;
    const themeMeta = document.querySelector('meta[name="theme-color"]');

    if (isDark) {
      htmlElement.classList.add(DARK_MODE_KEY);
      themeMeta?.setAttribute("content", "#000000");
    } else {
      htmlElement.classList.remove(DARK_MODE_KEY);
      themeMeta?.setAttribute("content", "#ffffff");
    }
  }, [isDark]);

  React.useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const handleChange = () => {
      setSystemTheme(getSystemTheme());
    };

    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener("change", handleChange);
      return () => mediaQuery.removeEventListener("change", handleChange);
    }
  }, []);

  return (
    <ThemeContext.Provider
      value={{ isDark, mode: sanitizedMode, setMode: setSanitizedMode }}
    >
      {children}
    </ThemeContext.Provider>
  );
};
