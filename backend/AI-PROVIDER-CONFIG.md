# AI Provider 配置指南

## 🚀 快速开始（3步搞定）

### 1️⃣ 复制配置模板

```bash
cd backend/novel2script-api/src/main/resources
cp application-dev.yml.example application-dev.yml
```

### 2️⃣ 配置至少一个 AI Provider

编辑 `application-dev.yml`，填入你的 API Key（只需配置一个即可）：

```yaml
spring:
  ai:
    default-provider: deepseek  # 默认使用 DeepSeek
    providers:
      deepseek:
        api-key: sk-your-actual-api-key-here  # ← 填入你的真实 API Key
        base-url: https://api.deepseek.com
        chat:
          options:
            model: deepseek-chat
            temperature: 0.7
            max-tokens: 4096
```

### 3️⃣ 启动应用

```bash
mvn spring-boot:run
```

启动时会自动验证配置，你会看到：

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  AI Provider Configuration Validation
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Provider 'deepseek' configured successfully
   - Base URL: https://api.deepseek.com
   - Model: deepseek-chat

✅ Default provider: deepseek (ready to use)

📊 Summary: 1 valid provider(s) configured
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 📋 支持的 AI Provider

| Provider | 推荐场景 | 价格 | 中文支持 | 获取地址 |
|----------|---------|------|---------|---------|
| **DeepSeek** ⭐ | 通用、开发测试 | 💰 便宜 | ✅ 优秀 | [deepseek.com](https://platform.deepseek.com) |
| **OpenAI GPT-4** | 高质量输出 | 💎 较贵 | ✅ 良好 | [openai.com](https://platform.openai.com) |
| **通义千问 Qwen** | 中文内容 | 💰💰 适中 | ✅ 优秀 | [aliyun.com](https://dashscope.aliyun.com) |
| **Claude** | 创意写作 | 💎 较贵 | ⚠️ 一般 | [anthropic.com](https://console.anthropic.com) |

---

## ⚙️ 配置说明

### 最小配置（只需一个 Provider）

```yaml
spring:
  ai:
    default-provider: deepseek
    providers:
      deepseek:
        api-key: your-api-key
        base-url: https://api.deepseek.com
        chat:
          options:
            model: deepseek-chat
            temperature: 0.7
            max-tokens: 4096
```

### 多 Provider 配置（可选）

```yaml
spring:
  ai:
    default-provider: deepseek  # 默认使用这个
    providers:
      deepseek:
        api-key: sk-deepseek-key
        base-url: https://api.deepseek.com
        chat:
          options:
            model: deepseek-chat
      
      openai:
        api-key: sk-openai-key  # 备用
        base-url: https://api.openai.com
        chat:
          options:
            model: gpt-4o
      
      qwen:
        api-key: ${QWEN_API_KEY:}  # 留空表示不启用
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        chat:
          options:
            model: qwen-max
```

---

## 🔍 配置验证

应用启动时会自动验证：

### ✅ 成功情况

```
✅ Provider 'deepseek' configured successfully
✅ Default provider: deepseek (ready to use)
📊 Summary: 1 valid provider(s) configured
```

### ❌ 失败情况及解决方案

#### 情况1：没有配置任何 Provider

```
❌ No AI providers configured!

Please configure at least one AI provider in application-dev.yml:
...
```

**解决：** 在 `application-dev.yml` 中配置至少一个 Provider

#### 情况2：所有 Provider 都没有 API Key

```
❌ No valid AI providers found! All providers are missing API keys.

Available providers to configure:
  - deepseek (add api-key in application-dev.yml)
  - openai (add api-key in application-dev.yml)
```

**解决：** 为至少一个 Provider 填入有效的 API Key

#### 情况3：Provider 初始化失败

```
❌ Failed to create ChatModel for provider 'deepseek': Invalid API key
```

**解决：** 检查 API Key 是否正确，网络连接是否正常

---

## 💡 常见问题

### Q1: 我只配置了一个 Provider，可以吗？

**A:** ✅ 完全可以！系统只需要至少一个有效的 Provider 就能运行。

### Q2: 如何切换默认的 AI Provider？

**A:** 修改 `spring.ai.default-provider` 的值：

```yaml
spring:
  ai:
    default-provider: openai  # 改为 openai
```

### Q3: API Key 从哪里获取？

**A:** 访问各平台的官网：
- **DeepSeek:** https://platform.deepseek.com/api-keys
- **OpenAI:** https://platform.openai.com/api-keys
- **通义千问:** https://dashscope.console.aliyun.com/apiKey
- **Claude:** https://console.anthropic.com/settings/keys

### Q4: 可以使用环境变量吗？

**A:** ✅ 可以！两种方式：

**方式1：在 YAML 中使用占位符**
```yaml
spring:
  ai:
    providers:
      deepseek:
        api-key: ${DEEPSEEK_API_KEY}
```

**方式2：启动时传入**
```bash
java -jar app.jar --spring.ai.providers.deepseek.api-key=sk-your-key
```

### Q5: 为什么启动时报错 "No AI providers configured"？

**A:** 请检查：
1. ✅ `application-dev.yml` 文件是否存在
2. ✅ 是否至少配置了一个 provider 的 `api-key`
3. ✅ `api-key` 是否为空字符串或占位符（如 `${XXX:}`）
4. ✅ `spring.profiles.active` 是否为 `dev`

### Q6: 可以同时启用多个 Provider 吗？

**A:** ✅ 可以！系统会为每个有 API Key 的 Provider 创建 ChatModel，运行时可以根据任务类型自动选择最合适的模型。

---

## 🔒 安全提示

1. **不要提交 API Key 到 Git**
   - `application-dev.yml` 已在 `.gitignore` 中
   - 使用环境变量管理敏感信息

2. **定期轮换 API Key**
   - 建议每 3-6 个月更换一次
   - 发现泄露立即撤销

3. **限制 API 使用额度**
   - 在各大平台设置月度预算上限
   - 监控使用情况

---

## 📚 相关文档

- [Spring AI 官方文档](https://spring.io/projects/spring-ai)
- [Multi-Model Configuration](../novel2script-infrastructure/src/main/java/com/novel2script/infrastructure/config/MultiModelProperties.java)
- [AI Model Router](../novel2script-infrastructure/src/main/java/com/novel2script/infrastructure/config/AiModelRouter.java)

---

**需要帮助？** 查看启动日志中的详细错误信息，或联系团队技术负责人。
