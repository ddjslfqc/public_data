# OKR - 我的目标、添加目标、审核 流程接口文档

**Base URL**: `https://ios.yceil.com/`

**通用请求头**:
| Header | 说明 |
|--------|------|
| `X-User-Id` | 当前登录用户ID |
| `Content-Type` | `application/json` |

**通用响应格式**:
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": { ... }
}
```

---

## 业务流程

```
┌──────────────────────────────────────────────────────────────────────┐
│                         我的目标页面                                   │
│                     GET /my-goal                                      │
│  展示：周期筛选 + 当前目标(含对齐信息) + 目标列表(含KR和对齐信息)        │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            │ 点击"添加目标"
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     选择上级范围                                       │
│                  GET /align-options                                   │
│  返回：可选部门列表 + 可选人员列表                                      │
│  用户选择某个部门或某个人                                               │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            │ 选择部门/人员后
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   获取可对齐的 KR 列表                                 │
│    GET /alignable-krs?deptId=2  或  ?targetUserId=5                   │
│  返回该部门/人员下已审批通过的 KR，供选择对齐                            │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            │ 选择目标 KR，填写目标信息 + KR 列表
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         创建目标                                      │
│                      POST /create                                     │
│  传入 parentKrId 完成 O→KR 对齐                                        │
│  目标创建后 approvalStatus=3（无需审批）                                │
│  KR 创建人 == 目标创建人 → 自动通过                                    │
│  KR 创建人 != 目标创建人 → 待审批                                      │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            │ KR 待审批时（目标创建人视角）
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     查看待审批 KR                                      │
│               GET /pending/kr/user                                     │
│  目标创建人查看自己目标下需要审批的 KR                                   │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            │ 审批
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       审批 KR                                         │
│                   POST /kr/approve                                     │
│  通过(1) 或 拒绝(2)                                                     │
│  审批通过后 KR 创建人才能更新进度                                        │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 接口列表

| # | 方法 | 接口 | 说明 |
|---|------|------|------|
| 1 | GET | `/my-goal` | 我的目标聚合页 |
| 2 | GET | `/align-options` | 选择上级范围（部门列表 + 人员列表） |
| 3 | GET | `/alignable-krs` | 获取可对齐的 KR 列表（支持按部门/人员筛选） |
| 4 | POST | `/create` | 创建目标 |
| 5 | GET | `/pending/kr/user` | 我的待审批 KR 列表 |
| 6 | POST | `/kr/approve` | 审批 KR |
| 7 | POST | `/kr/update-progress` | 提交 KR 进度（待审批） |
| 8 | POST | `/attachment/upload` | 上传 KR 进度附件 |
| 9 | GET | `/pending/progress/user` | 待审批进度列表 |
| 10 | POST | `/kr/progress/approve` | 审批进度更新 |

---

## 1. 我的目标聚合页

查看自己的目标，支持周期筛选。

```
GET /mobile/okr/my-goal?periodType=quarter-2
```

**请求头**: `X-User-Id`

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `periodType` | String | 否 | `quarter-2` / `quarter-3` / `quarter-4` / `year`，不传返回全部 |

**响应**:
```json
{
  "code": 200,
  "data": {
    "periods": [
      { "label": "Q2 2026", "value": "quarter-2", "active": true },
      { "label": "Q3 2026", "value": "quarter-3", "active": false },
      { "label": "Q4 2026", "value": "quarter-4", "active": false },
      { "label": "年度目标", "value": "year", "active": false }
    ],
    "currentObjective": {
      "id": 3,
      "title": "Q2 华东区增长目标",
      "periodType": "quarter",
      "periodLabel": "季度",
      "startDate": "2026-04-01",
      "endDate": "2026-06-30",
      "status": 0,
      "statusLabel": "进行中",
      "progress": 25,
      "approvalStatus": 3,
      "completedKrCount": 0,
      "totalKrCount": 2,
      "progressText": "0/2 模块 (25%)",
      "parentId": null,
      "parentKrId": 10,
      "parentKr": {
        "id": 10,
        "title": "营收增长20%",
        "objectiveId": 1,
        "objective": {
          "id": 1,
          "title": "2026年公司级战略目标",
          "userId": 1
        }
      }
    },
    "objectives": [
      {
        "id": 3,
        "title": "Q2 华东区增长目标",
        "createTime": "2026-06-20T10:00:00",
        "periodType": "quarter",
        "periodLabel": "季度",
        "status": 0,
        "statusLabel": "进行中",
        "progress": 25,
        "approvalStatus": 3,
        "parentId": null,
        "parentKrId": 10,
        "parentKr": {
          "id": 10,
          "title": "营收增长20%",
          "objectiveId": 1,
          "objective": {
            "id": 1,
            "title": "2026年公司级战略目标",
            "userId": 1
          }
        },
        "keyResults": [
          {
            "id": 7,
            "title": "上海站增长40%",
            "targetValue": 40.0,
            "weight": 50,
            "currentValue": 10.0,
            "unit": "%",
            "status": 0,
            "approvalStatus": 1,
            "achieved": false
          }
        ]
      }
    ]
  }
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `periods` | Array | 周期筛选选项，`active` 表示当前选中 |
| `currentObjective` | Object | 当前周期第一个目标，空时无此字段 |
| `currentObjective.parentKr` | Object | 对齐的上级 KR 信息（含所属 O） |
| `currentObjective.status` | Integer | 0-进行中，1-已完成 |
| `currentObjective.statusLabel` | String | 进行中 / 已完成 |
| `currentObjective.completedKrCount` | Integer | 已完成 KR 数 |
| `currentObjective.totalKrCount` | Integer | KR 总数 |
| `currentObjective.progressText` | String | 进度文本，如 "0/2 模块 (25%)" |
| `objectives` | Array | 所有目标列表 |
| `objectives[].keyResults` | Array | 目标下的 KR 列表 |
| `objectives[].keyResults[].achieved` | Boolean | `status==1` 时为 true |
| `objectives[].parentKr` | Object | 对齐的上级 KR 信息 |

---

## 2. 选择上级范围

添加目标时，先选择要对齐的部门或人员。

```
GET /mobile/okr/align-options
```

**请求头**: `X-User-Id`

**响应**:
```json
{
  "code": 200,
  "data": {
    "departments": [
      { "id": 1, "name": "公司管理层" },
      { "id": 2, "name": "华东区" },
      { "id": 3, "name": "华南区" }
    ],
    "users": [
      { "id": 1, "name": "张总" },
      { "id": 5, "name": "李经理" },
      { "id": 6, "name": "王主管" }
    ]
  }
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `departments` | Array | 可选部门列表（从已有目标中提取） |
| `departments[].id` | Long | 部门ID |
| `departments[].name` | String | 部门名称 |
| `users` | Array | 可选人员列表（同部门其他用户，不含自己） |
| `users[].id` | Long | 用户ID |
| `users[].name` | String | 用户名称 |

---

## 3. 获取可对齐的 KR 列表

根据选择的部门或人员，获取其下已审批通过的 KR，作为上级对齐目标。

```
GET /mobile/okr/alignable-krs?deptId=2
GET /mobile/okr/alignable-krs?targetUserId=5
GET /mobile/okr/alignable-krs
```

**请求头**: `X-User-Id`

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `deptId` | Long | 否 | 部门ID，筛选该部门下的 KR |
| `targetUserId` | Long | 否 | 人员ID，筛选该人员目标下的 KR |
| 都不传 | - | - | 默认返回同部门其他人已审批通过的 KR |

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 10,
      "title": "营收增长20%",
      "targetValue": 20.0,
      "currentValue": 5.0,
      "unit": "%",
      "status": 0,
      "objective": {
        "id": 1,
        "title": "2026年公司级战略目标",
        "userId": 1
      }
    },
    {
      "id": 11,
      "title": "客户满意度提升至95%",
      "targetValue": 95.0,
      "currentValue": 80.0,
      "unit": "%",
      "status": 0,
      "objective": {
        "id": 1,
        "title": "2026年公司级战略目标",
        "userId": 1
      }
    }
  ]
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | KR ID（创建目标时传入 `parentKrId`） |
| `title` | String | KR 标题 |
| `targetValue` | Double | 目标值 |
| `currentValue` | Double | 当前进度 |
| `unit` | String | 单位 |
| `status` | Integer | 0-进行中，1-已完成 |
| `objective.id` | Long | 所属 O ID |
| `objective.title` | String | 所属 O 标题 |
| `objective.userId` | Long | 所属 O 创建人 |

**筛选规则**: 只返回已审批通过的目标（`approvalStatus=3`）下已审批通过的 KR（`approvalStatus=1`）。

---

## 4. 创建目标

```
POST /mobile/okr/create
```

**请求头**: `X-User-Id`

**请求体**:
```json
{
  "title": "Q2 华东区增长目标",
  "description": "对齐公司级营收增长KR",
  "periodType": "quarter",
  "startDate": "2026-04-01",
  "endDate": "2026-06-30",
  "deptId": 2,
  "parentKrId": 10,
  "objectiveType": 1,
  "keyResults": [
    {
      "title": "上海站增长40%",
      "targetValue": 40.0,
      "weight": 50,
      "unit": "%",
      "sortOrder": 0,
      "userId": 5
    },
    {
      "title": "杭州站增长35%",
      "targetValue": 35.0,
      "weight": 50,
      "unit": "%",
      "sortOrder": 1,
      "userId": 6
    }
  ]
}
```

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | String | 是 | 目标标题 |
| `description` | String | 否 | 目标说明 |
| `periodType` | String | 是 | `month`-月度，`quarter`-季度，`year`-年度 |
| `startDate` | String | 是 | 开始日期，格式 `yyyy-MM-dd` |
| `endDate` | String | 是 | 结束日期，格式 `yyyy-MM-dd` |
| `deptId` | Long | 是 | 所属部门ID |
| `parentKrId` | Long | 否 | 对齐的上级 KR ID（从 `alignable-krs` 接口获取） |
| `objectiveType` | Integer | 否 | 0-个人，1-部门，默认0 |
| `keyResults` | Array | 否 | KR 列表 |
| `keyResults[].title` | String | 是 | KR 标题 |
| `keyResults[].targetValue` | Double | 是 | 目标值 |
| `keyResults[].weight` | Integer | 否 | 权重，默认平均分配，所有 KR 权重总和为100 |
| `keyResults[].unit` | String | 否 | 单位，如 `%`、`万元`、`个` |
| `keyResults[].sortOrder` | Integer | 否 | 排序号，从0开始 |
| `keyResults[].userId` | Long | 否 | KR 创建人ID，默认=当前用户 |

**响应**:
```json
{
  "code": 200,
  "data": 3
}
```
`data` 为新创建的目标 ID。

**审批规则**:
- 目标(O) 创建后 `approvalStatus=3`，**无需审批**
- KR 的 `userId` == 当前用户 → `approvalStatus=1`（自动通过）
- KR 的 `userId` != 当前用户 → `approvalStatus=0`（待审批）

---

## 5. 我的待审批 KR 列表

目标创建人查看自己目标下需要审批的 KR。

```
GET /mobile/okr/pending/kr/user
```

**请求头**: `X-User-Id`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 8,
      "objectiveId": 3,
      "title": "杭州站增长35%",
      "targetValue": 35.0,
      "weight": 50,
      "currentValue": 0.0,
      "unit": "%",
      "status": 0,
      "sortOrder": 1,
      "approvalStatus": 0,
      "approvalUserId": null,
      "approvalRemark": null,
      "approvalTime": null,
      "userId": 6,
      "createTime": "2026-06-26T10:00:00",
      "updateTime": "2026-06-26T10:00:00",
      "objectiveTitle": "Q2 华东区增长目标",
      "objectiveUserId": 5,
      "deptId": 2
    }
  ]
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | KR ID |
| `objectiveId` | Long | 所属 O ID |
| `title` | String | KR 标题 |
| `targetValue` | Double | 目标值 |
| `weight` | Integer | 权重 |
| `currentValue` | Double | 当前进度 |
| `unit` | String | 单位 |
| `status` | Integer | 0-进行中，1-已完成 |
| `approvalStatus` | Integer | 0-待审批 |
| `userId` | Long | KR 创建人 |
| `objectiveTitle` | String | 所属 O 标题 |
| `objectiveUserId` | Long | 所属 O 创建人 |
| `deptId` | Long | 所属部门ID |

---

## 6. 审批 KR

目标创建人对 KR 进行审批（通过或拒绝）。

```
POST /mobile/okr/kr/approve
```

**请求头**: `X-User-Id`（审批人，必须是目标创建人）

**请求体**:
```json
{
  "id": 8,
  "approvalStatus": 1,
  "approvalRemark": "目标合理，同意"
}
```

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 是 | KR ID |
| `approvalStatus` | Integer | 是 | 1-通过，2-拒绝 |
| `approvalRemark` | String | 否 | 审批意见 |

**响应**:
```json
{
  "code": 200,
  "msg": "操作成功"
}
```

**约束**:
- 只有目标创建人才能审批该目标下的 KR
- 已审批过的 KR（`approvalStatus != 0`）不可重复审批
- 审批通过后，KR 创建人才能更新进度

---

## 7. 更新 KR 进度

KR 负责人（`userId`）在审批通过后更新当前进度，可附带说明与附件。

```
POST /mobile/okr/kr/update-progress
```

**请求头**: `X-User-Id`（必须是 KR 负责人）

**请求体**:
```json
{
  "id": 7,
  "currentValue": 40.0,
  "remark": "本周完成阶段性目标",
  "attachmentIds": ["101", "102"]
}
```

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 是 | KR ID |
| `currentValue` | Double | 是 | 当前进度值 |
| `remark` | String | 否 | 进度说明 |
| `attachmentIds` | Array | 否 | 附件 ID 列表（先调用上传接口获取） |

**响应**:
```json
{
  "code": 200,
  "data": {
    "id": 7,
    "title": "我的KR1",
    "targetValue": 100.0,
    "currentValue": 0.0,
    "pendingProgressValue": 40.0,
    "unit": "%",
    "status": 0,
    "approvalStatus": 1,
    "progressApprovalStatus": 0,
    "achieved": false
  }
}
```

**说明**: 提交后 `progressApprovalStatus=0`（进度待审批），`currentValue` 仍为审批前值；审批通过后 `currentValue` 更新为 `pendingProgressValue`。

**约束**:
- 只有 KR 负责人（`userId`）可更新；`userId` 为空时视为本人
- `approvalStatus` 必须为 `1`（KR 已通过）
- 存在 `progressApprovalStatus=0` 时不可重复提交
- `currentValue` 不应超过 `targetValue`（具体规则由后端校验）

---

## 8. 上传 KR 进度附件

更新进度前，先上传附件获取 `attachmentIds`。

```
POST /mobile/okr/attachment/upload?krId=7
```

**请求头**: `X-User-Id`

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `krId` | Long | 是 | KR ID |
| `file` | File | 是 | multipart 文件字段 |

**响应**:
```json
{
  "code": 200,
  "data": {
    "id": "101",
    "fileName": "progress.jpg",
    "filePath": "http://.../progress.jpg",
    "fileType": "image"
  }
}
```

---

## 9. 进度更新审批

目标创建人审批 KR 负责人提交的进度更新。

### 9.1 待审批进度列表

```
GET /mobile/okr/pending/progress/user
```

**请求头**: `X-User-Id`（目标创建人）

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 12,
      "krId": 7,
      "objectiveId": 3,
      "title": "我的KR1",
      "currentValue": 40.0,
      "targetValue": 100.0,
      "unit": "%",
      "remark": "本周完成阶段性目标",
      "objectiveTitle": "王健的O",
      "userId": 5,
      "createTime": "2026-06-30T10:00:00"
    }
  ]
}
```

### 9.2 审批进度更新

```
POST /mobile/okr/kr/progress/approve
```

**请求体**:
```json
{
  "id": 12,
  "approvalStatus": 1,
  "approvalRemark": "同意"
}
```

| 字段 | 说明 |
|------|------|
| `id` | 进度更新记录 ID（非 KR ID） |
| `approvalStatus` | 1-通过，2-拒绝 |

**约束**: 审批通过后 KR 的 `currentValue` 更新，`progressApprovalStatus=1`。

---

## 完整操作流程示例

### 场景：部门领导创建目标，对齐到公司级 KR

**Step 1 - 进入我的目标页面**
```
GET /mobile/okr/my-goal?periodType=quarter-2
→ 展示当前周期目标列表，点击"添加目标"
```

**Step 2 - 选择上级范围**
```
GET /mobile/okr/align-options
→ 返回部门列表: [{id:1, name:"公司管理层"}, {id:2, name:"华东区"}, ...]
→ 返回人员列表: [{id:1, name:"张总"}, {id:5, name:"李经理"}, ...]
→ 选择"公司管理层"部门(id=1) 或 直接选择"张总"(id=1)
```

**Step 3 - 获取可对齐的 KR**
```
GET /mobile/okr/alignable-krs?deptId=1
→ 返回公司管理层下已审批通过的 KR 列表
→ 选择 "营收增长20%" (id=10)
```

**Step 4 - 创建目标**
```
POST /mobile/okr/create
{
  "title": "Q2 华东区增长目标",
  "periodType": "quarter",
  "startDate": "2026-04-01",
  "endDate": "2026-06-30",
  "deptId": 2,
  "parentKrId": 10,
  "keyResults": [
    { "title": "上海站增长40%", "targetValue": 40.0, "weight": 50, "userId": 5 },
    { "title": "杭州站增长35%", "targetValue": 35.0, "weight": 50, "userId": 6 }
  ]
}
→ 返回目标ID: 3
→ 上海站KR (userId=5, 创建人==目标创建人) → 自动通过
→ 杭州站KR (userId=6, 创建人!=目标创建人) → 待审批
```

**Step 5 - 查看待审批 KR**
```
GET /mobile/okr/pending/kr/user
→ 返回杭州站KR (id=8, approvalStatus=0)
```

**Step 6 - 审批 KR**
```
POST /mobile/okr/kr/approve
{
  "id": 8,
  "approvalStatus": 1,
  "approvalRemark": "同意"
}
→ 审批通过，杭州站KR 创建人(userId=6) 可以更新进度
```

---

## 审批状态流转

```
创建 KR
  │
  ├── userId == 目标创建人
  │     └── approvalStatus = 1 (自动通过) → 可直接更新进度
  │
  └── userId != 目标创建人
        └── approvalStatus = 0 (待审批)
              │
              ├── 目标创建人审批 → 通过(1) → 可更新进度
              │
              └── 目标创建人审批 → 拒绝(2) → 不可更新进度
```