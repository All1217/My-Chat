<template>
    <div class="lobby">
        <!-- 顶部标题区 -->
        <div class="lobby-header">
            <h1 class="lobby-title">功能大厅</h1>
        </div>

        <!-- 卡片网格 -->
        <div class="card-grid">
            <div v-for="card in cards" :key="card.name" class="feature-card" :style="{ '--accent': card.color }"
                @click="handleCardClick(card)">
                <div class="card-icon-box">
                    <el-icon :size="40" class="card-icon">
                        <component :is="card.icon" />
                    </el-icon>
                </div>
                <h3 class="card-title">{{ card.title }}</h3>
                <p class="card-desc">{{ card.desc }}</p>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
    ChatDotRound,
    User,
    Setting,
    HomeFilled,
    UploadFilled,
    Collection
} from '@element-plus/icons-vue'

const router = useRouter()

interface FeatureCard {
    name: string
    title: string
    desc: string
    icon: any
    color: string
    route: string
}

const cards: FeatureCard[] = [
    {
        name: 'chat',
        title: '即刻聊天',
        desc: '与 AI 自由对话，获取即时回答',
        icon: ChatDotRound,
        color: '#437dff',
        route: 'chat',
    },
    {
        name: 'roleplay',
        title: '角色扮演',
        desc: '选择角色，沉浸式情景互动',
        icon: User,
        color: '#9d48ff',
        route: 'chat',
    },
    {
        name: 'kb-chat',
        title: '知识库问答',
        desc: '基于知识库内容与 AI 对话',
        icon: Collection,
        color: '#00b578',
        route: 'chat',
    },
    {
        name: 'settings',
        title: '设置',
        desc: '个性化配置你的 AI 助手',
        icon: Setting,
        color: '#ff484e',
        route: 'settings',
    },
    {
        name: 'store',
        title: '知识库管理',
        desc: '个性化配置你的 AI 助手',
        icon: UploadFilled,
        color: '#9d48ff',
        route: 'store',
    },
    {
        name: 'home',
        title: '回到首页',
        desc: '返回 My Chat 主界面',
        icon: HomeFilled,
        color: '#437dff',
        route: 'home',
    },
]

function handleCardClick(card: FeatureCard) {
    router.push({ name: card.route })
}
</script>

<style scoped lang="less">
.lobby {
    min-height: 100vh;
    background: linear-gradient(135deg, #f8f5ff 0%, #f0f5ff 50%, #fff 100%);
    padding: 50px 80px;

    .lobby-header {
        text-align: center;
        margin-bottom: 60px;

        .lobby-title {
            font-size: 52px;
            font-weight: 800;
            background: linear-gradient(135deg, #9d48ff, #437dff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 12px;
        }

        .lobby-subtitle {
            font-size: 18px;
            color: #999;
            letter-spacing: 2px;
        }
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(5, 1fr);
        gap: 30px;
        max-width: 1400px;
        margin: 0 auto;

        .feature-card {
            position: relative;
            background: #fff;
            border-radius: 20px;
            padding: 40px 20px 30px;
            text-align: center;
            cursor: pointer;
            transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
            overflow: hidden;

            .card-icon-box {
                display: inline-flex;
                justify-content: center;
                align-items: center;
                width: 80px;
                height: 80px;
                border-radius: 20px;
                background: var(--accent);
                margin-bottom: 22px;
                transition: transform 0.35s ease, box-shadow 0.35s ease;

                .card-icon {
                    color: #fff;
                }
            }

            .card-title {
                font-size: 18px;
                font-weight: 700;
                color: #1a1a2e;
                margin-bottom: 8px;
                transition: color 0.3s;
            }

            .card-desc {
                font-size: 13px;
                color: #999;
                line-height: 1.6;
            }

            &:hover {
                transform: translateY(-8px);
                box-shadow: 0 16px 40px rgba(0, 0, 0, 0.12);

                .card-icon-box {
                    transform: scale(1.08);
                    box-shadow: 0 8px 24px rgba(var(--accent-rgb, 0), 0.35);
                }

                .card-title {
                    color: var(--accent);
                }
            }
        }
    }
}

// 响应式适配
@media (max-width: 1200px) {
    .lobby .card-grid {
        grid-template-columns: repeat(3, 1fr);
    }
}

@media (max-width: 768px) {
    .lobby {
        padding: 30px 20px;

        .lobby-header .lobby-title {
            font-size: 36px;
        }

        .card-grid {
            grid-template-columns: repeat(2, 1fr);
            gap: 16px;
        }
    }
}
</style>