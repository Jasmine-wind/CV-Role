export interface EmbeddingSummary {
  embeddingStatus: string
  totalChunks: number
  successChunks: number
  failedChunks: number
  embeddingModel: string | null
  embeddingDimension: number | null
}
