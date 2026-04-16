<template>
    <div class="message">
        <div class="message-list-wrap">
            <ul>
                <li class="message-user">
                    <p class="message-content">
                        测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容测试内容
                    </p>
                    <div class="tool">
                        <div class="tool-item" title="复制文本">
                            <CopyDocument style="width: 20px; height: 20px;" />
                        </div>
                    </div>
                </li>
                <li class="message-ai">
                    <div class="message-content">
                        <MarkdownRenderer :content="messageContent" />
                    </div>
                    <div class="tool">
                        <div class="tool-item" title="复制文本">
                            <CopyDocument style="width: 20px; height: 20px;" />
                        </div>
                    </div>
                </li>
            </ul>
        </div>
        <div class="chat-box">
            <textarea placeholder="输入你的问题……"></textarea>
            <button class="send-btn">
                <Promotion style="width: 16px; height: 16px; color: #fff;" />
            </button>
        </div>
    </div>
</template>
<script setup lang="ts">
import MarkdownRenderer from '@/components/MarkdownRenderer.vue';
// import {ref} from 'vue';
const messageContent: string = `
# 1 什么是webpack？和vite、Vue有何区别？

这是一个很好的问题，这三者经常一起出现，但角色完全不同。简单来说：

-   **Webpack** 和 **Vite** 是**构建工具**（打包器），负责处理项目的编译、打包、优化。
-   **Vue** 是一个**前端框架**，负责编写页面组件和逻辑。

它们不是同一层面的东西，所以“区别”更多是协作关系。下面分开解释。

## 1.1 什么是 Webpack？

Webpack 是一个**静态模块打包器**。它会把项目中的所有文件（JS、CSS、图片等）都视为**模块**，然后根据它们的依赖关系，打包成少量的浏览器能直接运行的文件（比如 \`bundle.js\`）。

**核心特点：**
-   **一切皆模块**：JS 里可以 \`import\` CSS、图片等。
-   **强大的Loader和插件生态**：用 \`babel-loader\` 转译新语法，用 \`css-loader\` 处理 CSS，用 \`HtmlWebpackPlugin\` 生成 HTML。
-   **配置复杂**：尤其旧项目里，光配置开发服务器、热更新、不同环境的打包规则，可能就要写上百行配置。

**工作流程：** 从入口文件开始，递归分析所有 \`import/require\`，把整个依赖树打包成几个最终文件。

## 1.2 Webpack 和 Vite 的区别（同为构建工具）

Vite 是新一代的构建工具，它**不是要完全替代 Webpack**，而是解决了 Webpack 在开发体验上的痛点。

| 维度                   | **Webpack (传统)**                                       | **Vite (现代)**                                              |
| :--------------------- | :------------------------------------------------------- | :----------------------------------------------------------- |
| **开发服务器启动速度** | **慢**。需要先打包整个项目，再启动服务。项目越大越慢。   | **极快**。不打包，利用浏览器原生 ES Modules 直接提供源码。   |
| **热更新速度**         | **慢**。修改一个文件，可能重打包相关模块（即使有 HMR）。 | **极快**。只替换编辑的模块，其余不变，秒级更新。             |
| **生产环境打包**       | 成熟稳定，分包、优化、Tree Shaking 都很完善。            | 默认使用 **Rollup** 打包，性能也很好，但某些复杂配置可能不如 Webpack 灵活。 |
| **配置复杂度**         | 复杂，需要了解各种 loader、plugin 概念。                 | 简单，开箱即用（支持 TS、CSS 预处理器等）。                  |
| **生态**               | 极其庞大，几乎任何需求都能找到现成 loader/plugin。       | 发展很快，兼容大部分 Rollup 插件，但某些专为 Webpack 设计的插件可能没法直接用。 |

**一句话总结：**
> **Vite 在开发时更快、更简单；Webpack 在生产打包和生态上更成熟。** 新项目一般推荐 Vite，老项目或特殊需求可能继续用 Webpack。

## 1.3 Webpack / Vite 和 Vue 的区别

它们是**不同层级**的东西，可以这样理解：

-   **Vue（框架）**：好比**房子的户型设计图**——告诉你客厅、卧室、厨房怎么布局，并提供一些预制的墙、门、窗（组件）。
-   **Webpack / Vite（构建工具）**：好比**施工队和工具**——帮你把设计图变成真正的房子：砌墙（编译模板）、装水电（处理依赖）、刷漆（压缩代码）等。

**实际协作关系：**
-   你写 \`.vue\` 文件（Vue 组件）。
-   Webpack 或 Vite 通过**相应的插件**（\`vue-loader\` 或 \`@vitejs/plugin-vue\`）把 \`.vue\` 文件编译成浏览器能识别的 JS。
-   开发时，Vite 帮你快速看到效果；上线前，Webpack/Vite 帮你打包优化。

**举个例子：**
\`\`\`bash
# 用 Vue CLI（底层是 Webpack）创建项目
npm create vue@3   # 现在也支持选择 Vite 或 Webpack

# 或者直接用 Vite 创建 Vue 项目
npm create vite@latest my-vue-app -- --template vue
\`\`\`

### 1.4 总结对比表

|                 | **Webpack**                        | **Vite**                       | **Vue**              |
| :-------------- | :--------------------------------- | :----------------------------- | :------------------- |
| **类别**        | 构建工具                           | 构建工具                       | 前端框架             |
| **核心作用**    | 打包模块、处理资源                 | 快速开发构建、生产打包         | 构建用户界面（UI）   |
| **开发时**      | 先打包再提供服务                   | 直接利用 ES Modules，不打包    | 编写组件、响应式数据 |
| **与 Vue 关系** | 通过 \`vue-loader\` 编译 \`.vue\` 文件 | 通过 \`@vitejs/plugin-vue\` 编译 | Vue 本身不负责打包   |
| **当前推荐度**  | 维护老项目、特殊需求               | **新项目首选**                 | 学习前端框架时选它   |

**建议：** 如果你是新手学习 Vue，直接用 **Vite** 创建项目最省心。等遇到需要深度定制打包流程的场景时，再回看 Webpack 也不迟。
`
</script>
<style scoped lang="less">
// 无序、有序列表每一行前面的小黑点或者序号没有正确显示
.message {
    position: relative;
    width: 50vw;
    height: 100vh;
    max-height: 100vh;
    padding-bottom: 165px;
    padding-top: 15px;

    .message-list-wrap {
        height: 100%;

        ul {
            display: flex;
            flex-direction: column;
            height: 100%;
            overflow: auto;

            .message-user {
                align-self: flex-end;
                max-width: 85%;
                margin-left: auto;
                margin-bottom: 15px;

                .message-content {
                    padding: 10px;
                    font-size: 15px;
                    color: #000;
                    background-color: #edf3fe;
                    border-radius: 10px;
                }
            }

            .message-ai {

                .message-content {
                    font-size: 15px;
                    color: #000;
                    border-radius: 10px;
                    /* 确保Markdown内容能正确显示 */
                    overflow: visible;

                    /* Markdown渲染器样式 */
                    .markdown-body {
                        background-color: transparent;
                        padding: 0;
                        margin: 0;

                        /* 确保表格能正确显示 */
                        table {
                            margin: 10px 0;
                            border: 1px solid #ddd !important;

                            th,
                            td {
                                border: 1px solid #ddd !important;
                                padding: 8px 12px !important;
                            }
                        }

                        /* 确保代码块能正确显示 */
                        pre {
                            margin: 10px 0 !important;
                            background-color: #f9fafb !important;
                        }

                        /* 确保列表能正确显示 */
                        ul,
                        ol {
                            padding-left: 2em !important;
                        }

                        ul {
                            list-style-type: disc !important;
                        }

                        ol {
                            list-style-type: decimal !important;
                        }

                        li {
                            display: list-item !important;
                        }
                    }
                }
            }

            .tool {
                display: flex;
                height: 23px;
                padding: 3px 0 0 10px;

                .tool-item {
                    height: 20px;
                    cursor: pointer;
                }
            }
        }

        ul::-webkit-scrollbar {
            display: none;
        }
    }

    .chat-box {
        position: absolute;
        left: 0;
        bottom: 15px;
        width: 100%;
        height: 150px;
        background-color: #ffffff;
        box-shadow: 2px 2px 8px rgba(0, 0, 0, 0.1);
        border-radius: 10px;
        overflow: hidden;
        z-index: 15;

        textarea {
            padding: 15px;
            width: 100%;
            height: 100%;
            border: none;
            resize: none;
            outline: none;
            font-size: 16px;
        }

        textarea::placeholder {
            font-size: 16px;
        }

        .send-btn {
            position: absolute;
            right: 20px;
            bottom: 15px;
            width: 25px;
            height: 25px;
            border-radius: 50%;
            background-color: #437dff;
        }
    }
}
</style>