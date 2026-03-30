import axios from "axios";

export interface HealthCheckData {
  [key: string]: string;
}

export interface HealthCheck {
  name: string;
  status: "UP" | "DOWN";
  data?: HealthCheckData;
}

export interface HealthResponse {
  status: "UP" | "DOWN";
  checks: HealthCheck[];
}

export const getHealthReady = () =>
  axios
    .get<HealthResponse>("/q/health/ready", {
      validateStatus: () => true,
    })
    .then((response) => response.data);
