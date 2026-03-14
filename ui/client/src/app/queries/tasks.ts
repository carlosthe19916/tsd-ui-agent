import { useQuery } from "@tanstack/react-query";

import type { HubRequestParams } from "@app/api/models";
import { getTasks } from "@app/api/task-api";

const TASK_QUERY_KEY = "tasks";

export const useFetchTasks = (params: HubRequestParams) => {
  return useQuery({
    queryKey: [TASK_QUERY_KEY, params],
    queryFn: () => getTasks(params),
  });
};
