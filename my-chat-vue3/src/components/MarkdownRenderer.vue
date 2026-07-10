<template>
    <div class="markdown-body" v-html="sanitizedHtml"></div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'

// 创建自定义的 marked 配置
const markedOptions = {
    highlight: function (code: string, lang: string) {
        if (lang && hljs.getLanguage(lang)) {
            return hljs.highlight(code, { language: lang }).value
        }
        return hljs.highlightAuto(code).value
    },
    breaks: true,  // 支持换行转 <br>
    gfm: true      // 支持 GitHub Flavored Markdown
}

// 使用类型安全的配置方式：扩展 marked 的类型定义
declare module 'marked' {
    interface MarkedOptions {
        highlight?: (code: string, lang: string) => string
    }
}

// 应用配置
marked.setOptions(markedOptions)

const props = defineProps<{
    content: string
}>()

// 将 Markdown 转为 HTML
const renderedHtml = computed(() => {
    if (!props.content) return ''
    return marked.parse(props.content, { async: false }) as string
})

// 清洗 HTML 防止 XSS 攻击
const sanitizedHtml = computed(() => {
    return DOMPurify.sanitize(renderedHtml.value)
})

// 动态添加highlight.js样式
onMounted(() => {
    // 确保highlight.js样式生效
    if (!document.querySelector('link[href*="github-dark.css"]')) {
        const link = document.createElement('link')
        link.rel = 'stylesheet'
        link.href = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/styles/github-dark.min.css'
        document.head.appendChild(link)
    }
})
</script>

<style>
/* 全局样式，不使用scoped */
.markdown-body {
    line-height: 1.6;
    word-break: break-word;
    color: #24292e;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
    font-size: 16px;
}

/* 标题样式 */
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
    margin-top: 24px;
    margin-bottom: 16px;
    font-weight: 600;
    line-height: 1.25;
}

.markdown-body h1 {
    padding-bottom: 0.3em;
    font-size: 2em;
    border-bottom: 1px solid #eaecef;
}

.markdown-body h2 {
    padding-bottom: 0.3em;
    font-size: 1.5em;
    border-bottom: 1px solid #eaecef;
}

.markdown-body h3 {
    font-size: 1.25em;
}

.markdown-body h4 {
    font-size: 1em;
}

.markdown-body h5 {
    font-size: 0.875em;
}

.markdown-body h6 {
    font-size: 0.85em;
    color: #6a737d;
}

/* 段落 */
.markdown-body p {
    margin-top: 0;
    margin-bottom: 16px;
}

/* 代码块样式 */
.markdown-body pre {
    background-color: #0d1117 !important;
    border-radius: 6px !important;
    padding: 16px !important;
    overflow-x: auto !important;
    margin: 16px 0 !important;
    font-size: 85% !important;
    line-height: 1.45 !important;
}

.markdown-body pre code {
    background-color: transparent !important;
    padding: 0 !important;
    border-radius: 0 !important;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace !important;
    font-size: 13px !important;
    color: #c9d1d9 !important;
}

/* 行内代码 */
.markdown-body code {
    background-color: rgba(175, 184, 193, 0.2) !important;
    padding: 0.2em 0.4em !important;
    border-radius: 6px !important;
    font-size: 85% !important;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace !important;
    color: #24292e !important;
}

/* 引用块 */
.markdown-body blockquote {
    padding: 0 1em !important;
    color: #6a737d !important;
    border-left: 0.25em solid #dfe2e5 !important;
    margin: 0 0 16px 0 !important;
}

.markdown-body blockquote > :first-child {
    margin-top: 0 !important;
}

.markdown-body blockquote > :last-child {
    margin-bottom: 0 !important;
}

/* 列表 */
.markdown-body ul,
.markdown-body ol {
    padding-left: 2em !important;
    margin-top: 0 !important;
    margin-bottom: 16px !important;
}

/* 无序列表样式 */
.markdown-body ul {
    list-style-type: disc !important;
}

.markdown-body ul ul {
    list-style-type: circle !important;
}

.markdown-body ul ul ul {
    list-style-type: square !important;
}

/* 有序列表样式 */
.markdown-body ol {
    list-style-type: decimal !important;
}

.markdown-body ol ol {
    list-style-type: lower-alpha !important;
}

.markdown-body ol ol ol {
    list-style-type: lower-roman !important;
}

.markdown-body li {
    margin-bottom: 4px !important;
    display: list-item !important;
    list-style-type: inherit !important;
}

.markdown-body li > p {
    margin-top: 16px !important;
    display: inline !important;
}

.markdown-body ul ul,
.markdown-body ul ol,
.markdown-body ol ul,
.markdown-body ol ol {
    margin-top: 0 !important;
    margin-bottom: 0 !important;
}

/* 链接 */
.markdown-body a {
    color: #0969da !important;
    text-decoration: none !important;
    background-color: transparent !important;
}

.markdown-body a:hover {
    text-decoration: underline !important;
}

.markdown-body a:active,
.markdown-body a:hover {
    outline-width: 0 !important;
}

/* 表格 - 重点修复 */
.markdown-body table {
    border-collapse: collapse !important;
    border-spacing: 0 !important;
    display: block !important;
    width: 100% !important;
    overflow: auto !important;
    margin: 16px 0 !important;
}

.markdown-body table th {
    font-weight: 600 !important;
    background-color: #f6f8fa !important;
}

.markdown-body table th,
.markdown-body table td {
    padding: 6px 13px !important;
    border: 1px solid #d0d7de !important;
}

.markdown-body table tr {
    background-color: #ffffff !important;
    border-top: 1px solid #d0d7de !important;
}

.markdown-body table tr:nth-child(2n) {
    background-color: #f6f8fa !important;
}

/* 图片 */
.markdown-body img {
    max-width: 100% !important;
    box-sizing: content-box !important;
    background-color: #ffffff !important;
    border-radius: 6px !important;
    margin: 16px 0 !important;
}

.markdown-body img[align=right] {
    padding-left: 20px !important;
}

.markdown-body img[align=left] {
    padding-right: 20px !important;
}

/* 水平线 */
.markdown-body hr {
    height: 0.25em !important;
    padding: 0 !important;
    margin: 24px 0 !important;
    background-color: #e1e4e8 !important;
    border: 0 !important;
}

/* 任务列表 */
.markdown-body .task-list-item {
    list-style-type: none !important;
}

.markdown-body .task-list-item-checkbox {
    margin: 0 0.2em 0.25em -1.6em !important;
    vertical-align: middle !important;
}

/* 强调 */
.markdown-body strong {
    font-weight: 600 !important;
}

.markdown-body em {
    font-style: italic !important;
}

/* 键盘 */
.markdown-body kbd {
    display: inline-block !important;
    padding: 3px 5px !important;
    font: 11px 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace !important;
    line-height: 10px !important;
    color: #24292e !important;
    vertical-align: middle !important;
    background-color: #fafbfc !important;
    border: 1px solid #d1d5da !important;
    border-radius: 6px !important;
    box-shadow: inset 0 -1px 0 #d1d5da !important;
}
</style>