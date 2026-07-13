# OKR 全链路开发指南（PC 端）

> 供 PC 管理端同步开发 OKR 功能使用。  
> 与 Android App、后端 `szyc-mobile` 当前实现保持一致。  
> 文档版本：2026-07-13

---

## 1. 环境与约定

| 项 | 说明 |
|---|---|
| **Base URL** | `https://ios.yceil.com/` |
| **接口前缀** | `/mobile/okr` |
| **鉴权** | 所有请求 Header：`X-User-Id: {当前登录用户 ID}` |
| **Content-Type** | JSON：`application/json`；上传：`multipart/form-data` |
| **后端工程** | `lianjiang/szyc-modules/szyc-mobile`（`OkrController`、`OkrPeerEvalController`） |
| **Android 参考** | `hiddendanger/.../data/OkrApi.kt`、`OkrModels.kt` |

### 1.1 通用响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": { }
}
```

`code = 200` 表示成功；失败时展示 `msg`。

---

## 2. 核心概念

### 2.1 数据模型

```
Objective（目标 O）
  ├── 归属：userId、deptId、周期 startDate ~ endDate
  ├── 对齐：parentKrId → 挂到上级某条 KR
  └── KeyResult（关键结果 KR）× N
        ├── 负责人：userId（可与 O 创建人不同）
        ├── 目标值 targetValue / 当前值 currentValue / unit
        └── 审批：approvalStatus
```

### 2.2 对齐关系（重点）

对齐是 **O → 上级 KR**，不是 O 对 O：

```
公司 O
  └── KR「营收同比增长 20%」
        ├── 华东区 O（parentKrId = 该 KR id）
        └── 研发部 O（parentKrId = 该 KR id）
```

- 一个 KR 可挂多个下级 O
- `parentKrId = null` 表示链顶目标
- 组织对齐树：`GET /alignment-tree` 返回扁平 `objectives[]`，前端按 `parentKrId` 拼多链森林

### 2.3 周期参数

| 查询参数 `periodType` | 含义 | 创建时 `periodType` 字段 |
|----------------------|------|--------------------------|
| `quarter-1` ~ `quarter-4` | 季度 Tab | 传 `quarter` + 对应 start/end |
| `year` | 年度 | 传 `year` |

**季度与日期对应（当年）：**

| periodType | startDate | endDate |
|------------|-----------|---------|
| quarter-1 | 01-01 | 03-31 |
| quarter-2 | 04-01 | 06-30 |
| quarter-3 | 07-01 | 09-30 |
| quarter-4 | 10-01 | 12-31 |
| year | 01-01 | 12-31 |

**360 互评周期**：固定使用 **上一已结束季度**（与「我的目标」当前 Tab 不同）。

| 当前月份 | 互评 period |
|----------|-------------|
| 1–3 月 | quarter-4 |
| 4–6 月 | quarter-1 |
| 7–9 月 | quarter-2 |
| 10–12 月 | quarter-3 |

---

## 3. 业务全景

```
录入：选部门 → 选对齐上级 KR → 填 O + KR → POST /create
  ↓
KR 审批：负责人≠创建人 → 待审批 → POST /kr/approve
  ↓
进度更新：POST /update-record/create → 上级审批 → approve/reject
  ↓
协作：评论、组织查看、360 复盘与互评
```

---

## 4. 模块一：我的 OKR（查看）

### 4.1 接口

| 能力 | 方法 | 路径 |
|------|------|------|
| 我的目标列表 | GET | `/mobile/okr/my-goal?periodType=quarter-2` |
| 目标详情 | GET | `/mobile/okr/detail/{id}` |
| KR 详情 | GET | `/mobile/okr/kr/detail/{id}` |

### 4.2 `my-goal` 响应要点

- `periods[]`：周期 Tab 列表
- `currentObjective`：顶部当前目标（含 KR、对齐信息）
- `objectives[]`：目标列表，每条含 `keyResults[]`、`parentKr`、`progressText`
- **O** 的 `approvalStatus = 3`（无需审批）；**KR** 才有审批状态

### 4.3 PC 页面建议

- 周期 Tab + O/KR 列表
- 点击进 KR 详情（含评论、更新记录、附件）

---

## 5. 模块二：添加目标（录入）—— PC 必做

### 5.1 操作流程

| 步骤 | 用户操作 | 接口 |
|:----:|----------|------|
| 1 | 选择所属部门 | `GET /dept-options` |
| 2 | 选择是否对齐上级 | — |
| 3a | 按**部门**选对齐 KR | `GET /align-objectives?deptId={id}` |
| 3b | 按**上级人员**选 KR | `GET /align-options` → `GET /alignable-krs?userId={id}` |
| 4 | 选中一条上级 KR | 记录 `parentKrId` |
| 5 | 填写目标标题、说明 | — |
| 6 | 填写 ≥1 条 KR | 可指定 KR 负责人 `userId` |
| 7 | 提交 | `POST /create` |

### 5.2 对齐相关接口

| 接口 | 说明 |
|------|------|
| `GET /dept-options` | 全部部门下拉 |
| `GET /user-options?deptId=` | 人员下拉（可选） |
| `GET /align-options` | 部门 + 同部门人员（添加目标选上级用） |
| `GET /align-objectives?deptId=` | 部门下可对齐目标（含已通过 KR） |
| `GET /align-objectives?targetUserId=` | 指定人员下可对齐目标 |
| `GET /alignable-krs?userId=` | 指定用户的 KR 列表 |

### 5.3 创建请求 `POST /mobile/okr/create`

```json
{
  "title": "Q3 研发效能目标",
  "description": "可选说明",
  "periodType": "quarter",
  "startDate": "2026-07-01",
  "endDate": "2026-09-30",
  "deptId": 2,
  "parentKrId": 10,
  "objectiveType": 1,
  "keyResults": [
    {
      "title": "OKR 模块上线",
      "targetValue": 100,
      "unit": "%",
      "weight": 50,
      "sortOrder": 0,
      "userId": 10005
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|------|:----:|------|
| title | 是 | 目标标题 |
| periodType | 是 | 创建传 `quarter` 或 `year` |
| startDate / endDate | 是 | `yyyy-MM-dd` |
| deptId | 是 | 所属部门 |
| parentKrId | 否 | 对齐的上级 KR；不对齐则不传 |
| objectiveType | 否 | 0 个人 / 1 部门，默认 0 |
| keyResults | 是 ≥1 | KR 列表 |
| keyResults[].userId | 否 | KR 负责人，默认当前用户 |
| keyResults[].targetValue | 是 | 目标值（App 用百分比） |

### 5.4 创建后后端规则

| 对象 | approvalStatus | 说明 |
|------|:--------------:|------|
| Objective | 3 | 目标**无需审批** |
| KR | 0 或 1 | 见下方 KR 审批规则 |

**KR 初始审批规则：**

1. KR 负责人有直属上级（`sys_dept.leader`）→ **待审批(0)**，由直属上级或 O 创建人审批
2. KR 负责人 ≠ O 创建人，且无直属上级约束 → **待审批(0)**，由 O 创建人审批
3. KR 负责人 = O 创建人 → **自动通过(1)**

### 5.5 特殊：仅向已有 O 追加 KR

`alignMode = 1` 时不创建新 O，只往指定目标下加 KR（仅 O 创建人可操作）：

```json
{
  "alignMode": 1,
  "parentKrId": 10,
  "keyResults": [ { "title": "...", "targetValue": 100 } ]
}
```

### 5.6 PC 录入页 UI 建议

```
┌─────────────────────────────────────────┐
│ 添加目标                                 │
├─────────────────────────────────────────┤
│ 周期：[Q2 2026 ▼]  部门：[研发部 ▼]      │
│ ☑ 对齐上级 KR                           │
│   方式：(●)按部门  ( )按上级人员          │
│   上级 KR：[营收+20% ▼]（树/列表选择）   │
│ 目标标题：[________________]            │
│ 说明：    [________________]            │
├─────────────────────────────────────────┤
│ 关键结果 KR（至少 1 条）                 │
│ ┌──────┬────────┬──────┬────────┐    │
│ │ 标题 │ 目标值% │ 负责人│  操作  │    │
│ ├──────┼────────┼──────┼────────┤    │
│ │ ...  │  100   │ 张三  │  删除  │    │
│ └──────┴────────┴──────┴────────┘    │
│                        [+ 添加 KR]      │
│                          [提交]         │
└─────────────────────────────────────────┘
```

---

## 6. 模块三：KR 审批

| 能力 | 方法 | 路径 |
|------|------|------|
| 我的待审批 KR | GET | `/mobile/okr/pending/kr/user` |
| 部门待审批 KR | GET | `/mobile/okr/pending/kr/dept?deptId=` |
| 审批 KR | POST | `/mobile/okr/kr/approve` |

**审批请求体：**

```json
{
  "id": 123,
  "approvalStatus": 1,
  "approvalRemark": "通过"
}
```

| approvalStatus | 含义 |
|:--------------:|------|
| 1 | 通过（KR 可更新进度） |
| 2 | 拒绝 |

**待审批列表常见字段：** `objectiveTitle`、`krOwnerName`、`approvalRoleLabel`、`contextLine` 等，用于展示审批上下文。

---

## 7. 模块四：进度更新与审批

> 进度更新走 **更新记录 + 审批**，不是直接 `PUT /kr/progress`（该接口为无需审核场景保留）。

### 7.1 提交进度

`POST /mobile/okr/update-record/create`

```json
{
  "okrType": "kr",
  "okrId": 123,
  "currentValue": 65,
  "content": "本周完成联调",
  "attachments": []
}
```

`okrType`：`objective`（目标）或 `kr`（关键结果）。

### 7.2 进度审批人规则

1. 优先：**直属上级**（部门 leader）
2. 否则：对齐链上 **上级 KR 所属 O 的创建人**
3. **顶层 O（无 parentKrId）** → 更新自动通过

### 7.3 进度审批接口

| 能力 | 方法 | 路径 |
|------|------|------|
| 我的待审进度 | GET | `/mobile/okr/update-record/pending` |
| 通过 | POST | `/mobile/okr/update-record/approve` |
| 驳回 | POST | `/mobile/okr/update-record/reject` |
| 历史记录 | GET | `/mobile/okr/update-record/list?okrType=kr&okrId=` |

**审批请求体：**

```json
{
  "id": 456,
  "approvalRemark": "同意"
}
```

### 7.4 KR 附件上传

`POST /mobile/okr/kr/attachment/upload?krId={id}`  
`multipart/form-data`，字段名 `file`。

---

## 8. 模块五：KR 评论

| 能力 | 方法 | 路径 |
|------|------|------|
| 发表评论 | POST | `/mobile/okr/kr/comment/create` |
| KR 评论列表 | GET | `/mobile/okr/kr/comment/list/{krId}` |
| 我收到的 | GET | `/mobile/okr/kr/comment/received` |
| 我发出的 | GET | `/mobile/okr/kr/comment/sent` |
| 删除评论 | DELETE | `/mobile/okr/kr/comment/delete/{commentId}` |

**创建评论：**

```json
{
  "krId": 123,
  "content": "评论内容"
}
```

---

## 9. 模块六：组织 OKR（按人员 / 按对齐）—— 开发中

### 9.1 数据源

`GET /mobile/okr/alignment-tree?periodType=quarter-2&deptId=`

| 参数 | 必填 | 说明 |
|------|:----:|------|
| periodType | 是 | 周期 |
| deptId | 否 | 部门筛选（含上下闭包，保留完整对齐链） |

**响应 `data` 结构：**

```json
{
  "periodType": "quarter-2",
  "periodLabel": "Q2 2026",
  "departments": [ { "id": 1, "name": "研发部" } ],
  "objectives": [
    {
      "id": 1,
      "title": "公司级战略目标",
      "userId": 10001,
      "ownerName": "张总",
      "deptId": 1,
      "deptName": "管理层",
      "progress": 38,
      "progressText": "1/2 KR (38%)",
      "parentKrId": null,
      "parentKr": null,
      "keyResults": [ { "id": 10, "title": "营收+20%", "targetValue": 20, "currentValue": 8, "unit": "%" } ]
    }
  ],
  "stats": {
    "objectiveCount": 3,
    "krCount": 5,
    "rootChainCount": 1,
    "orphanObjectiveCount": 0
  }
}
```

### 9.2 前端两种展示（Android 已实现，PC 对齐）

**Tab 1：按成员**

- 将 `objectives[]` 按 `userId` 分组
- 列表：姓名、部门、目标数、平均进度
- 点进详情：该人全部 O + KR（只读）

**Tab 2：按对齐**

- 链顶：`parentKrId` 为空，或上级 KR 不在当前数据集
- 递归：O → 其 KR → 挂在该 KR 下的下级 O
- 列表带缩进展示层级

### 9.3 PC 页面结构

```
组织 OKR
├── 周期 Tab + 部门筛选
├── Tab：[按成员] [按对齐]
├── 成员列表 → 成员详情（O/KR 只读）
└── 对齐列表（缩进树形）
```

### 9.4 对齐树 Web 参考

独立可视化页（ECharts）：`docs/okr-alignment-tree.html`  
接口说明：`docs/okr-alignment-tree-api.md`

---

## 10. 模块七：360 互评 + Q2 复盘

### 10.1 员工侧流程

```
1. 填写 Q2 复盘（产出、技能成长、合作人）
   PUT /peer-eval/review-prep
2. 保存后，为每位合作人生成「待我评价」任务
3. 看待评列表 GET /peer-eval/tasks
4. 对合作人打分+评语 POST /peer-eval/submit
5. 查看收到的评价 GET /peer-eval/received（匿名汇总）
```

### 10.2 互评接口清单

| 能力 | 方法 | 路径 |
|------|------|------|
| 互评摘要 | GET | `/mobile/okr/peer-eval/summary?period=` |
| 获取复盘 | GET | `/mobile/okr/peer-eval/review-prep?period=` |
| 保存复盘 | PUT | `/mobile/okr/peer-eval/review-prep` |
| 待我评价 | GET | `/mobile/okr/peer-eval/tasks?period=` |
| 提交评价 | POST | `/mobile/okr/peer-eval/submit` |
| 我收到的 | GET | `/mobile/okr/peer-eval/received?period=` |
| 我发出的详情 | GET | `/mobile/okr/peer-eval/submission?period=&targetUserId=` |
| 同事列表 | GET | `/mobile/okr/peer-eval/colleagues` |
| 增量加合作人 | POST | `/mobile/okr/peer-eval/add-collaborator` |

### 10.3 保存复盘请求体

```json
{
  "period": "quarter-2",
  "projectOutput": "Q2 项目产出与收获…",
  "skillGrowth": "技能成长…",
  "collaboratorUserIds": [10002, 10003, 10004]
}
```

**注意：**

- `collaboratorUserIds` 至少 1 人，保存时**全量覆盖**
- 保存后合作人收到「评我」任务；**不会**立即在自己的待评里看到对方

### 10.4 360 全员展示（PC 开发中）—— 管理视角

| 能力 | 方法 | 路径 |
|------|------|------|
| 全员互评进度看板 | GET | `/mobile/okr/peer-eval/org/overview?period=&deptId=` |
| 查看指定成员复盘 | GET | `/mobile/okr/peer-eval/org/review-prep?period=&userId=` |

看板展示：复盘是否完成、发出/收到评价数、完成率等；**不含评价正文**（保护匿名）。

### 10.5 PC 页面结构

```
360 互评管理
├── 周期（上一已结束季度）
├── 部门筛选
├── 全员表格（姓名、复盘状态、互评进度）
└── 点击行 → 复盘详情（只读）
```

详细字段见：`docs/okr-peer-eval-ios-api.md`

---

## 11. 状态码字典

### 11.1 业务状态 status

| 值 | 含义 |
|:--:|------|
| 0 | 进行中 |
| 1 | 已完成 |
| 2 | 未达成 |

### 11.2 KR 创建审批 approvalStatus

| 值 | 含义 |
|:--:|------|
| 0 | 待审批 |
| 1 | 已通过 |
| 2 | 已拒绝 |
| 3 | 无需审批（仅 Objective） |

### 11.3 进度更新记录 status

| 值 | 含义 |
|:--:|------|
| 0 | 待审核 |
| 1 | 已通过 |
| 2 | 已拒绝 |

---

## 12. PC 页面开发清单

| 优先级 | 页面 | 核心接口 |
|:------:|------|----------|
| P0 | 我的 OKR | `my-goal`、`detail`、`kr/detail` |
| P0 | **添加目标** | `dept-options`、`align-objectives` / `alignable-krs`、`create` |
| P0 | KR 审批 | `pending/kr/user`、`kr/approve` |
| P0 | 进度更新 + 审批 | `update-record/*` |
| P0 | **组织 OKR（按人/对齐）** | `alignment-tree` |
| P0 | **360 全员看板** | `peer-eval/org/overview`、`org/review-prep` |
| P1 | KR 评论 | `kr/comment/*` |
| P1 | 员工复盘/互评 | `peer-eval/review-prep`、`tasks`、`submit` |
| P2 | 对齐树可视化 | `alignment-tree` + ECharts |

### 建议开发顺序

```
① 添加目标（录入）
② 我的 OKR + KR 详情
③ KR 审批 + 进度审批（可合并为「审批中心」）
④ 组织 OKR（按成员 / 按对齐）
⑤ 360 全员看板
⑥ 员工复盘与互评
⑦ 对齐树 ECharts 可视化
```

---

## 13. PC 与 App 差异建议

| 维度 | App | PC 可增强 |
|------|-----|----------|
| 录入 | 手机分步向导 | 一屏表单：左对齐树，右 O+KR 表格 |
| 组织查看 | 两个 Tab | 加部门树、导出 Excel |
| 360 管理 | 全员列表 | 表格筛选、完成率统计图 |
| 审批 | 分散入口 | 统一「审批中心」：KR + 进度 |

---

## 14. 完整接口索引

### 14.1 目标与对齐 `/mobile/okr`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/my-goal` | 我的目标 |
| GET | `/alignment-tree` | 组织对齐树（扁平） |
| GET | `/detail/{id}` | 目标详情 |
| GET | `/kr/detail/{id}` | KR 详情 |
| POST | `/create` | 创建目标 |
| PUT | `/update` | 更新目标 |
| DELETE | `/delete/{id}` | 删除目标 |
| GET | `/dept-options` | 部门下拉 |
| GET | `/user-options` | 人员下拉 |
| GET | `/align-options` | 对齐选项 |
| GET | `/align-objectives` | 可对齐目标（含 KR） |
| GET | `/alignable-krs` | 指定用户 KR |
| GET | `/pending/kr/user` | 我的待审 KR |
| POST | `/kr/approve` | 审批 KR |
| POST | `/update-record/create` | 提交进度更新 |
| GET | `/update-record/pending` | 待审进度 |
| POST | `/update-record/approve` | 通过进度 |
| POST | `/update-record/reject` | 驳回进度 |
| GET | `/update-record/list` | 更新历史 |
| POST | `/kr/comment/create` | 发评论 |
| GET | `/kr/comment/list/{krId}` | 评论列表 |
| GET | `/kr/comment/received` | 我收到的评论 |
| GET | `/kr/comment/sent` | 我发出的评论 |

### 14.2 360 互评 `/mobile/okr/peer-eval`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/summary` | 互评摘要 |
| GET | `/review-prep` | 获取复盘 |
| PUT | `/review-prep` | 保存复盘 |
| GET | `/tasks` | 待我评价 |
| POST | `/submit` | 提交互评 |
| GET | `/received` | 我收到的评价 |
| GET | `/submission` | 我发出的评价详情 |
| GET | `/colleagues` | 同事列表 |
| POST | `/add-collaborator` | 增量加合作人 |
| GET | `/org/overview` | 全员互评看板 |
| GET | `/org/review-prep` | 指定成员复盘（只读） |

---

## 15. 参考文件

| 类型 | 路径 |
|------|------|
| Android 接口 | `hiddendanger/src/main/java/com/fuusy/hiddendanger/data/OkrApi.kt` |
| Android 模型 | `hiddendanger/.../data/OkrModels.kt` |
| 组织 OKR 聚合 | `hiddendanger/.../data/OrgOkrModels.kt` |
| 互评接口文档 | `docs/okr-peer-eval-ios-api.md` |
| 对齐树 API | `docs/okr-alignment-tree-api.md` |
| 对齐树 Web | `docs/okr-alignment-tree.html` |
| 我的目标流程 | `okr-my-goal-flow.md` |
| 后端 Controller | `szyc-mobile/.../OkrController.java` |
| 后端互评 | `szyc-mobile/.../OkrPeerEvalController.java` |

---

## 16. 联调示例

```bash
# 我的目标
curl -s 'https://ios.yceil.com/mobile/okr/my-goal?periodType=quarter-2' \
  -H 'X-User-Id: 10001'

# 组织对齐树
curl -s 'https://ios.yceil.com/mobile/okr/alignment-tree?periodType=quarter-2&deptId=2' \
  -H 'X-User-Id: 10001'

# 360 全员看板
curl -s 'https://ios.yceil.com/mobile/okr/peer-eval/org/overview?period=quarter-2' \
  -H 'X-User-Id: 10001'
```

---

**文档维护**：功能变更时请同步更新本文与 `OkrApi.kt`。
