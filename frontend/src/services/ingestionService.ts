import { api } from "@/services/api";

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface IngestionPipelineNode {
  id: number;
  nodeId: string;
  nodeType: string;
  settings?: Record<string, unknown> | null;
  condition?: Record<string, unknown> | null;
  nextNodeId?: string | null;
}

export interface IngestionPipeline {
  id: string;
  name: string;
  description?: string | null;
  createdBy?: string | null;
  nodes?: IngestionPipelineNode[];
  createTime?: string;
  updateTime?: string;
}

export interface IngestionPipelinePayload {
  name: string;
  description?: string | null;
  nodes?: Array<{
    nodeId: string;
    nodeType: string;
    settings?: Record<string, unknown> | null;
    condition?: Record<string, unknown> | null;
    nextNodeId?: string | null;
  }>;
}


export async function getIngestionPipelines(pageNo = 1, pageSize = 10, keyword?: string) {
  return api.get<PageResult<IngestionPipeline>, PageResult<IngestionPipeline>>(
    "/ingestion/pipelines",
    {
      params: { pageNo, pageSize, keyword: keyword || undefined }
    }
  );
}

export async function getIngestionPipeline(id: string) {
  return api.get<IngestionPipeline, IngestionPipeline>(`/ingestion/pipelines/${id}`);
}

export async function createIngestionPipeline(payload: IngestionPipelinePayload) {
  return api.post<IngestionPipeline, IngestionPipeline>("/ingestion/pipelines", payload);
}

export async function updateIngestionPipeline(id: string, payload: IngestionPipelinePayload) {
  return api.put<IngestionPipeline, IngestionPipeline>(`/ingestion/pipelines/${id}`, payload);
}

export async function deleteIngestionPipeline(id: string) {
  await api.delete(`/ingestion/pipelines/${id}`);
}

