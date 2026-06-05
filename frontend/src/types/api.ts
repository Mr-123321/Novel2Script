export interface ApiError {
  status: number;
  title: string;
  detail: string;
  errorCode?: string;
  timestamp?: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
