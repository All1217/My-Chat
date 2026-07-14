import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

export function useFileUpload() {
    const fileInputRef = ref<HTMLInputElement>()
    const selectedFiles = ref<File[]>([])
    const showFilePanel = ref(false)

    const MAX_FILE_SIZE = 10 * 1024 * 1024
    const MAX_FILE_COUNT = 10

    watch(selectedFiles, (arr) => {
        if (arr.length > 0) showFilePanel.value = true
    }, { deep: true })

    function triggerFilePicker() {
        fileInputRef.value?.click()
    }

    function handleFileSelect(event: Event) {
        const input = event.target as HTMLInputElement
        const rawFiles = Array.from(input.files ?? [])
        input.value = ''

        for (const file of rawFiles) {
            if (selectedFiles.value.length >= MAX_FILE_COUNT) {
                ElMessage.warning(`最多上传 ${MAX_FILE_COUNT} 个文件`)
                break
            }
            const ext = '.' + file.name.split('.').pop()?.toLowerCase()
            if (!file.type.startsWith('image/')
                && !file.type.startsWith('text/')
                && file.type !== 'application/pdf'
                && !['.txt', '.md', '.pdf'].includes(ext)) {
                ElMessage.warning(`不支持的文件类型: ${file.name}`)
                continue
            }
            if (file.size > MAX_FILE_SIZE) {
                ElMessage.warning(`文件过大（最大 10MB）: ${file.name}`)
                continue
            }
            if (selectedFiles.value.some(f => f.name === file.name && f.size === file.size)) {
                continue
            }
            selectedFiles.value.push(file)
        }
    }

    function fileIcon(file: File): string {
        if (file.type.startsWith('image/')) return '🖼️'
        if (file.type === 'application/pdf' || file.name.endsWith('.pdf')) return '📄'
        return '📝'
    }

    function formatFileSize(bytes: number): string {
        if (bytes < 1024) return bytes + ' B'
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
    }

    /** 取出待发送文件并清空列表 */
    function consumeFiles(): File[] {
        const copy = [...selectedFiles.value]
        selectedFiles.value = []
        return copy
    }

    return {
        fileInputRef,
        selectedFiles,
        showFilePanel,
        triggerFilePicker,
        handleFileSelect,
        fileIcon,
        formatFileSize,
        consumeFiles,
    }
}