import type { DisallowCharacters } from "@app/utils/type-utils";
import { objectKeys } from "@app/utils/utils";
import React from "react";
import { useLocation, useNavigate } from "react-router-dom";

export type TSerializedParams<TURLParamKey extends string> = Partial<
  Record<TURLParamKey, string | null>
>;

export interface IUseUrlParamsArgs<
  TDeserializedParams,
  TPersistenceKeyPrefix extends string,
  TURLParamKey extends string,
> {
  isEnabled?: boolean;
  persistenceKeyPrefix?: DisallowCharacters<TPersistenceKeyPrefix, ":">;
  keys: DisallowCharacters<TURLParamKey, ":">[];
  defaultValue: TDeserializedParams;
  serialize: (
    params: Partial<TDeserializedParams>,
  ) => TSerializedParams<TURLParamKey>;
  deserialize: (
    serializedParams: TSerializedParams<TURLParamKey>,
  ) => TDeserializedParams;
}

export type TURLParamStateTuple<TDeserializedParams> = [
  TDeserializedParams,
  (newParams: Partial<TDeserializedParams>) => void,
];

export const useUrlParams = <
  TDeserializedParams,
  TKeyPrefix extends string,
  TURLParamKey extends string,
>({
  isEnabled = true,
  persistenceKeyPrefix,
  keys,
  defaultValue,
  serialize,
  deserialize,
}: IUseUrlParamsArgs<
  TDeserializedParams,
  TKeyPrefix,
  TURLParamKey
>): TURLParamStateTuple<TDeserializedParams> => {
  type TPrefixedURLParamKey = TURLParamKey | `${TKeyPrefix}:${TURLParamKey}`;

  const navigate = useNavigate();
  const location = useLocation();

  const withPrefix = (key: TURLParamKey): TPrefixedURLParamKey =>
    persistenceKeyPrefix ? `${persistenceKeyPrefix}:${key}` : key;

  const withPrefixes = (
    serializedParams: TSerializedParams<TURLParamKey>,
  ): TSerializedParams<TPrefixedURLParamKey> =>
    persistenceKeyPrefix
      ? objectKeys(serializedParams).reduce(
          (obj, key) => {
            obj[withPrefix(key)] = serializedParams[key];
            return obj;
          },
          {} as TSerializedParams<TPrefixedURLParamKey>,
        )
      : (serializedParams as TSerializedParams<TPrefixedURLParamKey>);

  const setParams = (newParams: Partial<TDeserializedParams>) => {
    const pathname = location.pathname;
    const existingSearchParams = new URLSearchParams(window.location.search);
    const newPrefixedSerializedParams = withPrefixes(serialize(newParams));

    navigate(
      {
        pathname,
        search: trimAndStringifyUrlParams({
          existingSearchParams,
          newPrefixedSerializedParams,
        }),
      },
      { replace: true },
    );
  };

  const urlParams = new URLSearchParams(location.search);

  let allParamsEmpty = true;
  let params: TDeserializedParams = defaultValue;
  if (isEnabled) {
    const serializedParams = keys.reduce(
      (obj, key) => {
        obj[key] = urlParams.get(withPrefix(key));
        return obj;
      },
      {} as TSerializedParams<TURLParamKey>,
    );
    allParamsEmpty = keys.every((key) => !serializedParams[key]);
    params = allParamsEmpty ? defaultValue : deserialize(serializedParams);
  }

  // biome-ignore lint/correctness/useExhaustiveDependencies: allowed
  React.useEffect(() => {
    if (allParamsEmpty) setParams(defaultValue);
  }, [allParamsEmpty]);

  return [params, setParams];
};

export const trimAndStringifyUrlParams = <TPrefixedURLParamKey extends string>({
  existingSearchParams = new URLSearchParams(),
  newPrefixedSerializedParams,
}: {
  existingSearchParams?: URLSearchParams;
  newPrefixedSerializedParams: TSerializedParams<TPrefixedURLParamKey>;
}) => {
  const existingPrefixedSerializedParams =
    Object.fromEntries(existingSearchParams);

  for (const key of objectKeys(newPrefixedSerializedParams)) {
    if (newPrefixedSerializedParams[key] === undefined) {
      delete newPrefixedSerializedParams[key];
    }
    if (newPrefixedSerializedParams[key] === null) {
      delete newPrefixedSerializedParams[key];
      delete existingPrefixedSerializedParams[key];
    }
  }

  const newParams = new URLSearchParams({
    ...existingPrefixedSerializedParams,
    ...newPrefixedSerializedParams,
  });
  newParams.sort();
  return newParams.toString();
};
