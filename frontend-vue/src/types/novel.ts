/** Novel status in the processing pipeline */
export type NovelStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'PARSED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED';

export interface Novel {
  id: number;
  title: string;
  author?: string;
  fileName: string;
  fileSize: number;
  totalChars: number;
  chapterCount: number;
  status: NovelStatus;
  createdAt: string;
  updatedAt: string;
}

export interface NovelUploadResponse {
  novelId: number;
  title: string;
  chapterCount: number;
  totalChars: number;
  status: NovelStatus;
}
