import axios from "axios";

import { FILTER_TEXT_CATEGORY_KEY } from "@app/Constants";
import type { HubRequestParams, SearchResultDto, TaskDto } from "./models";

const BASE_URL = "/api/tasks";

export const serializeTaskRequestParams = (
  params: HubRequestParams,
): URLSearchParams => {
  const query = new URLSearchParams();

  if (params.filters) {
    for (const filter of params.filters) {
      if (
        typeof filter.value === "string" ||
        typeof filter.value === "number"
      ) {
        const value = String(filter.value);
        if (!value) continue;
        const key =
          filter.field === FILTER_TEXT_CATEGORY_KEY
            ? "filterText"
            : filter.field;
        query.append(key, value);
      } else {
        const key =
          filter.field === FILTER_TEXT_CATEGORY_KEY
            ? "filterText"
            : filter.field;
        for (const item of filter.value.list) {
          query.append(key, item);
        }
      }
    }
  }

  if (params.sort) {
    query.append("sort_by", `${params.sort.field}:${params.sort.direction}`);
  }

  if (params.page) {
    query.append(
      "offset",
      String((params.page.pageNumber - 1) * params.page.itemsPerPage),
    );
    query.append("limit", String(params.page.itemsPerPage));
  }

  return query;
};

export const getTasks = (params: HubRequestParams) =>
  axios
    .get<SearchResultDto<TaskDto>>(BASE_URL, {
      params: serializeTaskRequestParams(params),
    })
    .then((response) => response.data);
