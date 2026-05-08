export interface FileTreeNode {
    name: string
    path: string
    directory: boolean
    children?: FileTreeNode[]
}
export interface FileInfo {
    name: string
    path: string
    directory: boolean
    size: number
    createdAt: string
    modifiedAt: string
    // 前端附加字段
    previewable?: boolean
}