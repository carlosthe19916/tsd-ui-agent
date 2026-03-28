import axios from "axios";

import { FILTER_TEXT_CATEGORY_KEY } from "@app/Constants";
import type {
  HubRequestParams,
  New,
  PlanDto,
  SearchResultDto,
  TaskDto,
} from "./models";

const BASE_URL = "/api/tasks";

export const createTask = (task: New<TaskDto>): Promise<TaskDto> =>
  axios.post<TaskDto>(BASE_URL, task).then((res) => res.data);

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

export const createTaskPlan = (taskId: number, plan: New<PlanDto>) =>
  axios
    .post<PlanDto>(`${BASE_URL}/${taskId}/plan`, plan)
    .then((response) => response.data);

export const updateTaskPlan = (taskId: number, plan: PlanDto) =>
  axios
    .put<PlanDto>(`${BASE_URL}/${taskId}/plan`, plan)
    .then((response) => response.data);

export const getTaskPlan = (taskId: number) =>
  axios
    .get<PlanDto>(`${BASE_URL}/${taskId}/plan`)
    .then((response) => response.data);

export const openVSCode = (taskId: number) =>
  axios
    .post<PlanDto>(`${BASE_URL}/${taskId}/plan/open-vscode`)
    .then((response) => response.data);

export const openTerminal = (taskId: number) =>
  axios
    .post<PlanDto>(`${BASE_URL}/${taskId}/plan/open-terminal`)
    .then((response) => response.data);

export const executePlan = (taskId: number) =>
  axios
    .post<PlanDto>(`${BASE_URL}/${taskId}/plan/execute`)
    .then((response) => response.data);

export const createChangeRequest = (taskId: number) =>
  axios
    .post<PlanDto>(`${BASE_URL}/${taskId}/plan/change-request`)
    .then((response) => response.data);

export const generatePlan = (taskId: number) =>
  axios
    .post<PlanDto>(`${BASE_URL}/${taskId}/plan/generate-plan`)
    .then((response) => response.data);

export const runAllPlanPhases = (taskId: number) =>
  axios
    .post<PlanDto>(`${BASE_URL}/${taskId}/plan/run-all`)
    .then((response) => response.data);

export const enrichRequirement = (taskId: number) =>
  axios
    .post<PlanDto>(`${BASE_URL}/${taskId}/plan/enrich-requirement`)
    .then((response) => response.data);

export const patchTask = (taskId: number, task: Partial<TaskDto>) =>
  axios
    .patch<TaskDto>(`${BASE_URL}/${taskId}`, task)
    .then((response) => response.data);

export const patchTaskPlan = (taskId: number, plan: Partial<PlanDto>) =>
  axios
    .patch<PlanDto>(`${BASE_URL}/${taskId}/plan`, plan)
    .then((response) => response.data);

export const streamPlanOutput = async function* (
  taskId: number,
  signal?: AbortSignal,
): AsyncGenerator<string> {
  const response = await fetch(`/api/tasks/${taskId}/plan/output`, { signal });
  if (!response.ok) throw new Error(`Stream failed: ${response.status}`);
  const reader = response.body?.getReader();
  if (!reader) return;
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";
      for (const line of lines) {
        if (line.startsWith("data:")) {
          yield line.slice(5);
        }
      }
    }
  } finally {
    reader.cancel();
  }
};

export const getTask = (taskId: number): Promise<TaskDto> =>
  axios.get<TaskDto>(`${BASE_URL}/${taskId}`).then((res) => res.data);

export const streamChatOutput = async function* (
  taskId: number,
  signal?: AbortSignal,
): AsyncGenerator<string> {
  const response = await fetch(`/api/tasks/${taskId}/chat/output`, { signal });
  if (!response.ok) throw new Error(`Stream failed: ${response.status}`);
  const reader = response.body?.getReader();
  if (!reader) return;
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";
      for (const line of lines) {
        if (line.startsWith("data:")) {
          yield line.slice(5);
        }
      }
    }
  } finally {
    reader.cancel();
  }
};

export const sendChatMessage = async function* (
  taskId: number,
  content: string,
): AsyncGenerator<string> {
  const response = await fetch(`/api/tasks/${taskId}/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ content }),
  });

  if (!response.ok) {
    throw new Error(`Chat request failed: ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) return;

  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";

    for (const line of lines) {
      if (line.startsWith("data:")) {
        yield line.slice(5);
      }
    }
  }
};
