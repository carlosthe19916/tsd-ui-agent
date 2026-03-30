import { useQuery } from "@tanstack/react-query";

import { getHealthReady } from "@app/api/health-api";

const HEALTH_READY_QUERY_KEY = "health-ready";

export const useFetchHealthReady = () => {
  return useQuery({
    queryKey: [HEALTH_READY_QUERY_KEY],
    queryFn: getHealthReady,
    refetchInterval: 30_000,
  });
};
