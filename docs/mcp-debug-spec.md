# Breeze Dev Helper — MCP 调试功能规格文档

## 1. 背景与问题

Breeze Dev Helper 通过内置 HTTP/SSE MCP Server（默认端口 19876）向 AI 客户端（Claude 等）暴露调试控制能力。  
现有实现（v0.2.x）存在以下核心问题，导致调试工作流不可用：

### 1.1 现有问题清单

| 问题 | 严重度 | 说明 |
|------|--------|------|
| `remove_breakpoint` 参数为 `fileUrl`，与 `add_breakpoint` 的 `className` 不一致 | 高 | AI 必须先调 `list_breakpoints` 取内部路径，再调 remove，两步绑定、易出错 |
| `list_breakpoints` 只返回 `fileUrl`（绝对路径），AI 难以理解属于哪个类 | 高 | 绝对路径对 AI 无意义，应返回人类可读的 `className:line` |
| 缺少 `list_run_configurations` 工具 | 高 | `launch_remote_debug` 描述中已说明需要先调它，但工具根本不存在 |
| 缺少执行控制工具（resume / step_over / step_into / step_out） | 高 | 命中断点后 AI 无法继续执行，调试流程无法闭环 |
| 缺少 `get_debug_status` 工具 | 中 | AI 无法感知当前调试状态，只能盲目调 `read_variables` 才能知道会话是否存在 |
| 缺少 `evaluate_expression` 工具 | 中 | 只能看变量列表，无法在当前帧对任意表达式求值 |
| 工具名 `launch_remote_debug` 命名有误导性 | 低 | 该工具可启动任意 Debug 配置，不限于 Remote JVM，应改名为 `launch_debug` |

---

## 2. 设计目标

1. **完整的调试工作流**：AI 能够独立完成"设置断点 → 启动调试 → 命中断点 → 检查变量 → 单步执行 → 继续运行 → 停止"全流程。
2. **一致的接口语义**：所有断点操作均使用 `className + line`（人类可读），不要求 AI 了解内部文件路径。
3. **状态可见**：AI 可随时查询当前调试状态，不需要通过错误响应来反推。
4. **最小化往返**：常用操作一次调用完成，避免多步依赖（如 list → remove）。

---

## 3. 工具规格

### 3.1 工具总览

| 工具名 | 分类 | 变更 |
|--------|------|------|
| `add_breakpoint` | 断点管理 | 不变 |
| `remove_breakpoint` | 断点管理 | **改造**：参数从 `fileUrl+line` 改为 `className+line` |
| `list_breakpoints` | 断点管理 | **改造**：输出增加 `className` 字段 |
| `list_run_configurations` | 调试会话 | **新增** |
| `launch_debug` | 调试会话 | **重命名**（原 `launch_remote_debug`） |
| `get_debug_status` | 调试会话 | **新增** |
| `stop_debug` | 调试会话 | 不变 |
| `resume_debug` | 执行控制 | **新增** |
| `step_over` | 执行控制 | **新增** |
| `step_into` | 执行控制 | **新增** |
| `step_out` | 执行控制 | **新增** |
| `read_variables` | 状态检查 | 不变 |
| `evaluate_expression` | 状态检查 | **新增** |
| `navigate_to_code` | 导航 | 不变 |

---

### 3.2 断点管理

#### `add_breakpoint`
```
参数：
  className  string  必填  全限定类名，如 com.example.MyService
  line       int     必填  1-based 行号
  condition  string  选填  断点条件表达式，如 x > 10

成功返回：Breakpoint added at com.example.MyService:42
失败返回：Source file not found for class: ... / Breakpoint already exists at ...
```

#### `remove_breakpoint` ⚠️ 改造
```
参数（原）：fileUrl string, line int       ← 要求内部路径，AI 不友好
参数（新）：className string, line int     ← 与 add_breakpoint 对称，AI 友好

成功返回：Breakpoint removed at com.example.MyService:42
失败返回：No breakpoint found at com.example.MyService:42
```

#### `list_breakpoints` ⚠️ 改造
```
参数：无

输出（新格式，每个断点包含）：
  className  string   全限定类名（新增）
  line       int      1-based 行号
  enabled    boolean  是否启用
  condition  string   条件表达式（可选）

示例输出：
  Java line breakpoints in project 'my-project' (2 total):
    • com.example.MyService:42 [enabled]
    • com.example.OrderController:88 [disabled] condition: orderId > 0
```

---

### 3.3 调试会话

#### `list_run_configurations` 新增
```
参数：无

返回：项目中所有 Run Configuration 的列表
  name  string  配置名称
  type  string  配置类型（如 Remote, Application, JUnit）

示例输出：
  Run Configurations in project 'my-project' (3 total):
    • [Remote] Remote Debug
    • [Application] Main
    • [JUnit] OrderServiceTest
```

#### `launch_debug` 重命名（原 launch_remote_debug）
```
参数：
  configurationName  string  必填  Run Configuration 的精确名称

成功返回：Launched Run Configuration 'Remote Debug' in Debug mode.
失败返回：Run Configuration not found: '...' Available: [...]
```

#### `get_debug_status` 新增
```
参数：无

返回：当前调试状态概览
  state    enum  "NO_SESSION" | "RUNNING" | "PAUSED"
  name     string  会话名（有会话时）
  location string  暂停位置 className:line（PAUSED 时）

示例输出（无会话）：
  No active debug session.

示例输出（运行中）：
  Debug session 'Remote Debug' is RUNNING.

示例输出（暂停）：
  Debug session 'Remote Debug' is PAUSED at com.example.MyService:42
```

#### `stop_debug`
```
参数：无
不变。
```

---

### 3.4 执行控制（全部新增）

所有执行控制工具均要求存在一个**处于 PAUSED 状态**的调试会话。

#### `resume_debug`
```
参数：无
行为：等同 IDE 中的 Resume Program（F9）
成功返回：Resumed debug session 'Remote Debug'. Program is now running.
失败返回：No paused debug session. / No active debug session.
```

#### `step_over`
```
参数：无
行为：等同 Step Over（F8）——执行当前行，不进入方法调用
成功返回：Stepped over in session 'Remote Debug'. Now at com.example.MyService:43
失败返回：No paused debug session.
注意：step 后需等待 IDE 重新暂停，工具会等待最多 5 秒后返回新的暂停位置。
```

#### `step_into`
```
参数：无
行为：等同 Step Into（F7）——进入当前行的方法调用
成功返回：Stepped into in session 'Remote Debug'. Now at com.example.OrderService:15
失败返回：No paused debug session.
```

#### `step_out`
```
参数：无
行为：等同 Step Out（Shift+F8）——从当前方法返回到调用方
成功返回：Stepped out in session 'Remote Debug'. Now at com.example.MyService:44
失败返回：No paused debug session.
```

---

### 3.5 状态检查

#### `read_variables`
```
参数：无
不变。返回当前暂停帧的所有局部变量（名称 + 值 + 类型）。
```

#### `evaluate_expression` 新增
```
参数：
  expression  string  必填  在当前调试帧中求值的表达式，如 "user.getName()" 或 "list.size()"

成功返回：
  Expression: user.getName()
  Result: "Alice"  [java.lang.String]

失败返回：
  Evaluation error: Cannot find symbol 'user'
  
前置条件：存在处于 PAUSED 状态的调试会话
```

---

## 4. 典型工作流

### 4.1 首次 Debug 流程（AI 视角）
```
1. list_run_configurations()         → 了解项目有哪些可用配置
2. add_breakpoint(className, line)   → 在目标位置设置断点
3. launch_debug(configurationName)   → 启动调试会话
4. get_debug_status()                → 轮询直到状态变为 PAUSED
5. read_variables()                  → 检查当前变量
6. evaluate_expression("expr")       → 按需对表达式求值
7. step_over() / step_into()         → 单步执行
8. resume_debug()                    → 继续运行
9. stop_debug()                      → 调试结束
```

### 4.2 快速断点调整
```
1. list_breakpoints()                → 确认现有断点
2. remove_breakpoint(className, line) → 移除旧断点（用 className，不需要 fileUrl）
3. add_breakpoint(className, newLine) → 添加新断点
```

---

## 5. 实现要点

### 5.1 `remove_breakpoint` 重构
- 接受 `className + line`，内部通过 `JavaPsiFacade.findClasses()` 解析出 `VirtualFile`
- 复用 `BreakpointAddTool` 中已有的 `resolveSourceFile()` 逻辑（抽取到 `BreakpointHelper`）

### 5.2 `list_breakpoints` 改造
- 现有代码已有 `fileUrl`，需反向解析出 className
- 使用 `VirtualFileManager.findFileByUrl(fileUrl)` → `JavaPsiFacade` 查找 `PsiClass` → `getQualifiedName()`
- 输出保持可读文本，包含 className

### 5.3 步进工具的"等待暂停"模式
- `XDebugSession` 的 `stepOver/stepInto/stepOut` 是异步的——调用后程序立即运行，不知道什么时候再次暂停
- 方案：调用步进方法后，通过 `XDebugSessionListener` 监听 `sessionPaused()` 事件，最多等待 10 秒
- 等待到暂停后，读取新的 `currentStackFrame.sourcePosition` 返回给 AI

### 5.4 `evaluate_expression` 实现
- 使用 `XStackFrame.getEvaluator()` 获取 `XDebuggerEvaluator`（可能为 null，需检查）
- 使用 `XExpressionImpl.fromText(expression, null, EvaluationMode.EXPRESSION)` 构造表达式
- 通过 `XEvaluationCallback` 异步获取结果，最多等待 10 秒

### 5.5 `BreakpointHelper` 扩展
- 将 `resolveSourceFile(project, className)` 从 `BreakpointAddTool` 抽取到 `BreakpointHelper`
- `remove_breakpoint` 和 `list_breakpoints` 均复用该方法

---

## 6. 不在本期范围内

- 条件断点设置（`add_breakpoint` 已支持读取，本期增加写入支持）
- 异常断点（Exception Breakpoint）
- 日志断点（Logpoint）
- 多帧变量检查（当前只读 top frame）
- 远程多会话管理（多个并发 debug session）
