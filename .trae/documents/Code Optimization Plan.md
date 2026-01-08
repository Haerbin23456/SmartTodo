# SmartTodo 代码质量优化计划 (Optimization Plan)

本文档旨在针对当前 SmartTodo 项目的代码质量进行系统性提升。虽然现有代码在架构设计上表现优秀，但在工程严谨性、可测试性和健壮性方面仍有优化空间。

## 1. 核心目标
- **解耦硬编码**：将配置、提示词和常量从逻辑代码中分离。
- **提升健壮性**：增强网络异常处理和 API 错误恢复机制。
- **规范化**：统一时间处理标准和代码风格。
- **可测试化**：引入单元测试，确保核心业务逻辑（如任务解析）的正确性。

---

## 2. 优化阶段规划

### 阶段 1：基础规范化与解耦 (Foundational Cleanup)
- **[ ] 提取常量 (Constants Extraction)**
  - 创建 `com.example.smarttodo.util.Constants` 对象。
  - 提取 `DeepSeekHelper` 中的模型名称 (`deepseek-chat`)、默认 URL 和系统提示词模板。
  - 提取数据库表名和状态字符串。
- **[ ] 统一时间处理 (Unified Time Management)**
  - 封装 `TimeUtils` 工具类。
  - 统一使用 `Instant` 或 `LocalDateTime` 进行内部处理，仅在 UI 显示层进行格式化。

### 阶段 2：可靠性增强 (Reliability & Robustness)
- **[ ] API 错误处理升级**
  - 在 `DeepSeekHelper` 中引入指数退避重试机制 (Exponential Backoff)。
  - 细化错误分类：区分网络超时、API 额度不足、解析错误，并向用户提供更有针对性的反馈。
- **[ ] 数据库事务优化**
  - 确保 `TaskProcessor` 中的多步数据库操作（插入原始消息 + 更新任务）在事务中运行，保证原子性。

### 阶段 3：测试工程化 (Testing Infrastructure)
- **[ ] 建立单元测试环境**
  - 配置 `Robolectric` 或 `MockK` 框架。
  - **[ ] 任务处理器测试 (`TaskProcessorTest`)**：模拟不同 AI 返回结果，验证数据库更新逻辑是否符合预期。
  - **[ ] AI 逻辑测试 (`DeepSeekHelperTest`)**：验证 Prompt 拼接逻辑和 JSON 解析逻辑。

### 阶段 4：功能与体验微调 (Refinements)
- **[ ] 增强通知过滤逻辑**
  - 引入更智能的文本比对算法（如编辑距离），防止由于通知内容微小变动导致的重复触发。
- **[ ] 依赖注入 (Optional)**
  - 考虑引入 `Hilt` 或 `Koin` 来管理 `AppDatabase` 和 `Repository` 的实例，提升代码的可维护性。

---

## 3. 待处理任务清单 (Action Items)

| 优先级 | 任务描述 | 涉及文件 |
| :--- | :--- | :--- |
| **高** | 提取 Prompt 和 API 配置常量 | `DeepSeekHelper.kt` |
| **高** | 建立核心逻辑的单元测试 | `app/src/test` |
| **中** | 封装统一的时间工具类 | `TimeUtils.kt` |
| **中** | 增加 API 调用重试机制 | `DeepSeekHelper.kt` |
| **低** | 优化通知防抖算法 | `MyNotificationService.kt` |

---

## 4. 验收标准
1. 所有硬编码字符串（非 UI 文本）已提取到常量类。
2. 断网或 API 报错时，应用不会崩溃，且用户能看到明确的错误提示。
3. 单元测试覆盖率达到核心逻辑的 80% 以上。
4. `TaskProcessor` 逻辑在事务中执行。
